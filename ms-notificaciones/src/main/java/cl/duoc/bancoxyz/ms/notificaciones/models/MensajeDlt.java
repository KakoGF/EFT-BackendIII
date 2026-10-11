package cl.duoc.bancoxyz.ms.notificaciones.models;

import java.time.LocalDateTime;

public record MensajeDlt(
        String topicoDlt,
        String topicoOriginal,
        String grupoConsumidor,
        int particion,
        long offset,
        String tipoError,
        String detalleError,
        String contenido,
        LocalDateTime fechaRecepcion) {
}
