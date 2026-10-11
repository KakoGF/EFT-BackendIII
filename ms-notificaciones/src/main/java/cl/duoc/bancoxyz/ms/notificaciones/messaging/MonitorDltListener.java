package cl.duoc.bancoxyz.ms.notificaciones.messaging;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import cl.duoc.bancoxyz.ms.notificaciones.models.MensajeDlt;
import cl.duoc.bancoxyz.ms.notificaciones.services.MonitorDltService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MonitorDltListener {

    private static final String SIN_DATO = "desconocido";
    private static final int LARGO_MAXIMO_CONTENIDO = 200;

    private final MonitorDltService monitorDltService;

    @KafkaListener(id = "listenerMonitorDlt", groupId = "${spring.application.name}-monitor-dlt",
            topics = {
                    "${topicos.solicitudes-ingresadas}.DLT",
                    "${topicos.transacciones-solicitadas}.DLT",
                    "${topicos.transacciones-validadas}.DLT",
                    "${topicos.cuentas-ajustes}.DLT",
                    "${topicos.cuentas-ajustes-resultado}.DLT",
                    "${topicos.transacciones-completadas}.DLT",
                    "${topicos.alertas-seguridad}.DLT",
                    "${topicos.cuentas-eventos}.DLT"
            })
    public void recibir(ConsumerRecord<String, String> registro) {
        MensajeDlt mensaje = new MensajeDlt(
                registro.topic(),
                cabecera(registro, KafkaHeaders.DLT_ORIGINAL_TOPIC),
                cabecera(registro, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP),
                registro.partition(),
                registro.offset(),
                nombreCorto(cabecera(registro, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN)),
                detalle(cabecera(registro, KafkaHeaders.DLT_EXCEPTION_MESSAGE)),
                recortar(registro.value()),
                LocalDateTime.now());
        monitorDltService.registrar(mensaje);
    }

    private String cabecera(ConsumerRecord<String, String> registro, String nombre) {
        Header cabecera = registro.headers().lastHeader(nombre);
        if (cabecera == null || cabecera.value() == null) {
            return SIN_DATO;
        }
        return new String(cabecera.value(), StandardCharsets.UTF_8);
    }

    private String nombreCorto(String claseCompleta) {
        return claseCompleta.substring(claseCompleta.lastIndexOf('.') + 1);
    }

    private String detalle(String mensajeError) {
        int separador = mensajeError.lastIndexOf("; ");
        return separador >= 0 ? mensajeError.substring(separador + 2) : mensajeError;
    }

    private String recortar(String contenido) {
        if (contenido == null) {
            return "(vacio)";
        }
        return contenido.length() > LARGO_MAXIMO_CONTENIDO
                ? contenido.substring(0, LARGO_MAXIMO_CONTENIDO) + "..."
                : contenido;
    }
}
