package cl.duoc.bancoxyz.ms.cuentas.events;

import java.time.Instant;

public record CuentaEvent(
        String eventId,
        String tipoEvento,
        Instant fechaEvento,
        Long cuentaId,
        Long clienteId,
        String titular,
        String tipo,
        String estado,
        String motivo) {
}
