package cl.duoc.bancoxyz.ms.notificaciones.events;

import java.math.BigDecimal;
import java.time.Instant;

public record TransaccionCompletadaEvent(
        String eventId,
        String tipoEvento,
        Instant fechaEvento,
        String solicitudId,
        Long transaccionId,
        String tipoTransaccion,
        Long cuentaOrigenId,
        Long cuentaDestinoId,
        Long clienteId,
        String titular,
        BigDecimal monto,
        String canal,
        String estado,
        String motivo,
        BigDecimal saldoFinal) {
}
