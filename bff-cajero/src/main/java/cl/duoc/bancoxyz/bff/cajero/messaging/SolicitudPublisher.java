package cl.duoc.bancoxyz.bff.cajero.messaging;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import cl.duoc.bancoxyz.bff.cajero.events.SolicitudIngresadaEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SolicitudPublisher {

    private static final long SEGUNDOS_ESPERA = 5;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topicoIngresadas;

    public SolicitudPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${topicos.solicitudes-ingresadas}") String topicoIngresadas) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topicoIngresadas = topicoIngresadas;
    }

    public void publicar(SolicitudIngresadaEvent evento) throws Exception {
        String clave = String.valueOf(evento.cuentaId());
        String json = objectMapper.writeValueAsString(evento);
        SendResult<String, String> resultado = kafkaTemplate.send(topicoIngresadas, clave, json)
                .get(SEGUNDOS_ESPERA, TimeUnit.SECONDS);
        log.info("[KAFKA] Solicitud {} publicada en {} particion {} offset {} (clave {}, app {}, usuario {})",
                evento.solicitudId(), topicoIngresadas, resultado.getRecordMetadata().partition(),
                resultado.getRecordMetadata().offset(), clave, evento.clienteOAuth(), evento.usuario());
    }
}
