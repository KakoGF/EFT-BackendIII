package cl.duoc.bancoxyz.ms.cuentas.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cl.duoc.bancoxyz.ms.cuentas.events.AjusteCuentaSolicitadoEvent;
import cl.duoc.bancoxyz.ms.cuentas.exceptions.EventoInvalidoException;
import cl.duoc.bancoxyz.ms.cuentas.services.AjusteCuentaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AjusteCuentaListener {

    private final ObjectMapper objectMapper;
    private final AjusteCuentaService ajusteCuentaService;

    @KafkaListener(id = "listenerAjustesCuenta", groupId = "${spring.application.name}",
            topics = "${topicos.cuentas-ajustes}")
    public void recibir(ConsumerRecord<String, String> registro) throws Exception {
        log.info("[KAFKA] Recibido ajuste en {}-{}@{} (clave {})",
                registro.topic(), registro.partition(), registro.offset(), registro.key());
        ajusteCuentaService.aplicar(leer(registro.value()));
    }

    private AjusteCuentaSolicitadoEvent leer(String json) {
        AjusteCuentaSolicitadoEvent evento;
        try {
            evento = objectMapper.readValue(json, AjusteCuentaSolicitadoEvent.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new EventoInvalidoException("El mensaje no es un AjusteCuentaSolicitadoEvent valido", ex);
        }
        if (evento == null || evento.eventId() == null || evento.solicitudId() == null
                || evento.cuentaOrigenId() == null || evento.tipoTransaccion() == null || evento.monto() == null) {
            throw new EventoInvalidoException("Al ajuste le faltan campos obligatorios");
        }
        return evento;
    }
}
