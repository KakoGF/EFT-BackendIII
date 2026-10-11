package cl.duoc.bancoxyz.ms.pagos.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cl.duoc.bancoxyz.ms.pagos.events.AjusteCuentaResultadoEvent;
import cl.duoc.bancoxyz.ms.pagos.exceptions.EventoInvalidoException;
import cl.duoc.bancoxyz.ms.pagos.services.TransaccionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AjusteResultadoListener {

    private final ObjectMapper objectMapper;
    private final TransaccionService transaccionService;

    @KafkaListener(id = "listenerResultadosAjuste", groupId = "${spring.application.name}",
            topics = "${topicos.cuentas-ajustes-resultado}")
    public void recibir(ConsumerRecord<String, String> registro) throws Exception {
        log.info("[KAFKA] Recibido evento en {}-{}@{} (clave {})",
                registro.topic(), registro.partition(), registro.offset(), registro.key());
        transaccionService.procesarResultadoAjuste(leer(registro.value()));
    }

    private AjusteCuentaResultadoEvent leer(String json) {
        AjusteCuentaResultadoEvent evento;
        try {
            evento = objectMapper.readValue(json, AjusteCuentaResultadoEvent.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new EventoInvalidoException("El mensaje no es un AjusteCuentaResultadoEvent valido", ex);
        }
        if (evento == null || evento.eventId() == null || evento.solicitudId() == null || evento.resultado() == null) {
            throw new EventoInvalidoException("Al evento le faltan campos obligatorios");
        }
        return evento;
    }
}
