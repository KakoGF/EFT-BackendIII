package cl.duoc.bancoxyz.ms.notificaciones.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cl.duoc.bancoxyz.ms.notificaciones.events.AlertaSeguridadEvent;
import cl.duoc.bancoxyz.ms.notificaciones.exceptions.EventoInvalidoException;
import cl.duoc.bancoxyz.ms.notificaciones.services.NotificacionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertaSeguridadListener {

    private final ObjectMapper objectMapper;
    private final NotificacionService notificacionService;

    @KafkaListener(id = "listenerAlertasSeguridadNotificaciones", groupId = "${spring.application.name}",
            topics = "${topicos.alertas-seguridad}")
    public void recibir(ConsumerRecord<String, String> registro) {
        log.info("[KAFKA] Recibido evento en {}-{}@{} (clave {})",
                registro.topic(), registro.partition(), registro.offset(), registro.key());
        notificacionService.notificarAlerta(leer(registro.value()));
    }

    private AlertaSeguridadEvent leer(String json) {
        AlertaSeguridadEvent evento;
        try {
            evento = objectMapper.readValue(json, AlertaSeguridadEvent.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new EventoInvalidoException("El mensaje no es un AlertaSeguridadEvent valido", ex);
        }
        if (evento == null || evento.eventId() == null || evento.tipoAlerta() == null) {
            throw new EventoInvalidoException("Al evento le faltan campos obligatorios");
        }
        return evento;
    }
}
