package cl.duoc.bancoxyz.ms.notificaciones.events;

import java.time.Instant;

public record AlertaSeguridadEvent(
        String eventId,
        String tipoEvento,
        Instant fechaEvento,
        String tipoAlerta,
        String nivel,
        Long cuentaId,
        Long clienteId,
        String canal,
        String solicitudId,
        String detalle) {
}
