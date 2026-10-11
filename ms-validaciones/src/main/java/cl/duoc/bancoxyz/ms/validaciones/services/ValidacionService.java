package cl.duoc.bancoxyz.ms.validaciones.services;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import cl.duoc.bancoxyz.ms.validaciones.dtos.EstadisticasDTO;
import cl.duoc.bancoxyz.ms.validaciones.events.AlertaSeguridadEvent;
import cl.duoc.bancoxyz.ms.validaciones.events.TransaccionSolicitadaEvent;
import cl.duoc.bancoxyz.ms.validaciones.events.TransaccionValidadaEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ValidacionService {

    private final ReglasValidacionService reglasValidacionService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topicoValidadas;
    private final String topicoAlertas;
    private final String instancia;
    private final long latenciaSimuladaMs;

    private final Set<String> eventosProcesados = ConcurrentHashMap.newKeySet();
    private final Set<Integer> particionesAtendidas = ConcurrentHashMap.newKeySet();
    private final AtomicLong procesadas = new AtomicLong();
    private final AtomicLong aprobadas = new AtomicLong();
    private final AtomicLong rechazadas = new AtomicLong();
    private final AtomicLong duplicadas = new AtomicLong();
    private final AtomicLong alertas = new AtomicLong();

    public ValidacionService(
            ReglasValidacionService reglasValidacionService,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${topicos.transacciones-validadas}") String topicoValidadas,
            @Value("${topicos.alertas-seguridad}") String topicoAlertas,
            @Value("${spring.application.name}:${server.port}") String instancia,
            @Value("${validaciones.latencia-simulada-ms}") long latenciaSimuladaMs) {
        this.reglasValidacionService = reglasValidacionService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topicoValidadas = topicoValidadas;
        this.topicoAlertas = topicoAlertas;
        this.instancia = instancia;
        this.latenciaSimuladaMs = latenciaSimuladaMs;
    }

    public void validar(TransaccionSolicitadaEvent evento, int particion, long offset) throws Exception {
        if (eventosProcesados.contains(evento.eventId())) {
            duplicadas.incrementAndGet();
            log.warn("[DUPLICADO][{}] Evento {} ya validado antes: se descarta", instancia, evento.eventId());
            return;
        }

        Thread.sleep(latenciaSimuladaMs);
        ResultadoValidacion resultado = reglasValidacionService.evaluar(evento);

        TransaccionValidadaEvent validada = new TransaccionValidadaEvent(
                UUID.randomUUID().toString(), "TRANSACCION_VALIDADA", Instant.now(),
                evento.solicitudId(), evento.transaccionId(), evento.cuentaId(), evento.tipoTransaccion(),
                evento.monto(), evento.canal(),
                resultado.aprobado() ? "APROBADO" : "RECHAZADO",
                resultado.motivo(), instancia);

        String clave = String.valueOf(evento.cuentaId());
        kafkaTemplate.send(topicoValidadas, clave, objectMapper.writeValueAsString(validada)).get(5, TimeUnit.SECONDS);

        if (resultado.generaAlerta()) {
            AlertaSeguridadEvent alerta = new AlertaSeguridadEvent(
                    UUID.randomUUID().toString(), "ALERTA_SEGURIDAD", Instant.now(), resultado.tipoAlerta(),
                    resultado.nivelAlerta(), evento.cuentaId(), evento.clienteId(), evento.canal(),
                    evento.solicitudId(), resultado.detalleAlerta());
            kafkaTemplate.send(topicoAlertas, clave, objectMapper.writeValueAsString(alerta)).get(5, TimeUnit.SECONDS);
            alertas.incrementAndGet();
            log.warn("[ALERTA][{}] {} nivel {} para la cuenta {}: {}", instancia, resultado.tipoAlerta(),
                    resultado.nivelAlerta(), evento.cuentaId(), resultado.detalleAlerta());
        }

        eventosProcesados.add(evento.eventId());
        particionesAtendidas.add(particion);
        procesadas.incrementAndGet();
        if (resultado.aprobado()) {
            aprobadas.incrementAndGet();
        } else {
            rechazadas.incrementAndGet();
        }
        log.info("[SAGA] Validacion [{}] particion {} offset {}: {} {} cuenta {} (canal {}, tipo cuenta {}) -> {}{}",
                instancia, particion, offset, evento.tipoTransaccion(), evento.monto(), evento.cuentaId(),
                evento.canal(), evento.tipoCuenta(), validada.resultado(),
                resultado.motivo() == null ? "" : " (" + resultado.motivo() + ")");
    }

    public EstadisticasDTO estadisticas() {
        List<Integer> particiones = particionesAtendidas.stream().sorted().toList();
        return new EstadisticasDTO(instancia, particiones,
                procesadas.get(), aprobadas.get(), rechazadas.get(), duplicadas.get(), alertas.get());
    }
}
