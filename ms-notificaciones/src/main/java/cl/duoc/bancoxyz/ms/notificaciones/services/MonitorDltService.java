package cl.duoc.bancoxyz.ms.notificaciones.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cl.duoc.bancoxyz.ms.notificaciones.dto.ResumenDltDTO;
import cl.duoc.bancoxyz.ms.notificaciones.models.MensajeDlt;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MonitorDltService {

    private static final String METRICA = "bancoxyz.dlt.mensajes";
    private static final int MAXIMO_ULTIMOS = 20;

    private final MeterRegistry meterRegistry;
    private final NotificacionService notificacionService;
    private final int umbralAlerta;
    private final long ventanaSegundos;

    private final AtomicLong totalMensajes = new AtomicLong();
    private final Map<String, AtomicLong> mensajesPorTopico = new ConcurrentHashMap<>();
    private final Deque<LocalDateTime> llegadasRecientes = new ConcurrentLinkedDeque<>();
    private final List<MensajeDlt> ultimosMensajes = new CopyOnWriteArrayList<>();

    public MonitorDltService(MeterRegistry meterRegistry, NotificacionService notificacionService,
            @Value("${dlt.alerta.umbral:3}") int umbralAlerta,
            @Value("${dlt.alerta.ventana-segundos:300}") long ventanaSegundos) {
        this.meterRegistry = meterRegistry;
        this.notificacionService = notificacionService;
        this.umbralAlerta = umbralAlerta;
        this.ventanaSegundos = ventanaSegundos;
    }

    public void registrar(MensajeDlt mensaje) {
        totalMensajes.incrementAndGet();
        mensajesPorTopico.computeIfAbsent(mensaje.topicoOriginal(), topico -> new AtomicLong()).incrementAndGet();
        Counter.builder(METRICA)
                .description("Mensajes que no se pudieron procesar y quedaron en un Dead Letter Topic")
                .tag("topico", mensaje.topicoOriginal())
                .register(meterRegistry)
                .increment();

        ultimosMensajes.add(0, mensaje);
        if (ultimosMensajes.size() > MAXIMO_ULTIMOS) {
            ultimosMensajes.remove(ultimosMensajes.size() - 1);
        }

        llegadasRecientes.addLast(mensaje.fechaRecepcion());
        long enVentana = mensajesEnVentana();

        log.error("[DLT] Mensaje no procesado en {} (grupo {}, particion {}, offset {}): {} - {}",
                mensaje.topicoOriginal(), mensaje.grupoConsumidor(), mensaje.particion(), mensaje.offset(),
                mensaje.tipoError(), mensaje.detalleError());

        boolean critica = enVentana >= umbralAlerta;
        if (critica) {
            log.error("[DLT] !!! ALERTA CRITICA: {} mensajes en DLT en los ultimos {} segundos (umbral {}) !!!",
                    enVentana, ventanaSegundos, umbralAlerta);
        }
        notificacionService.notificarSoporteDlt(mensaje, critica, enVentana);
    }

    public ResumenDltDTO resumen() {
        long enVentana = mensajesEnVentana();
        Map<String, Long> porTopico = new TreeMap<>();
        mensajesPorTopico.forEach((topico, cantidad) -> porTopico.put(topico, cantidad.get()));
        return new ResumenDltDTO(totalMensajes.get(), enVentana, umbralAlerta, ventanaSegundos,
                enVentana >= umbralAlerta, porTopico, new ArrayList<>(ultimosMensajes));
    }

    private long mensajesEnVentana() {
        LocalDateTime limite = LocalDateTime.now().minusSeconds(ventanaSegundos);
        while (!llegadasRecientes.isEmpty() && llegadasRecientes.peekFirst().isBefore(limite)) {
            llegadasRecientes.pollFirst();
        }
        return llegadasRecientes.size();
    }
}
