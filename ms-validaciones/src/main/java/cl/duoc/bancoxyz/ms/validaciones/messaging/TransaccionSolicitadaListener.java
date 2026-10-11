package cl.duoc.bancoxyz.ms.validaciones.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cl.duoc.bancoxyz.ms.validaciones.events.TransaccionSolicitadaEvent;
import cl.duoc.bancoxyz.ms.validaciones.exceptions.EventoInvalidoException;
import cl.duoc.bancoxyz.ms.validaciones.services.ValidacionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransaccionSolicitadaListener {

    private final ObjectMapper objectMapper;
    private final ValidacionService validacionService;

    @KafkaListener(id = "listenerTransaccionesSolicitadas", groupId = "${spring.application.name}",
            topics = "${topicos.transacciones-solicitadas}")
    public void recibir(ConsumerRecord<String, String> registro) throws Exception {
        log.info("[KAFKA] Recibido evento en {}-{}@{} (clave {})",
                registro.topic(), registro.partition(), registro.offset(), registro.key());
        TransaccionSolicitadaEvent evento = leer(registro.value());
        validacionService.validar(evento, registro.partition(), registro.offset());
    }

    private TransaccionSolicitadaEvent leer(String json) {
        TransaccionSolicitadaEvent evento;
        try {
            evento = objectMapper.readValue(json, TransaccionSolicitadaEvent.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new EventoInvalidoException("El mensaje no es un TransaccionSolicitadaEvent valido", ex);
        }
        if (evento == null || evento.eventId() == null || evento.transaccionId() == null
                || evento.cuentaId() == null || evento.tipoTransaccion() == null
                || evento.monto() == null || evento.canal() == null) {
            throw new EventoInvalidoException("Al evento le faltan campos obligatorios");
        }
        return evento;
    }
}
