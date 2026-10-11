package cl.duoc.bancoxyz.ms.validaciones.events;

import java.math.BigDecimal;
import java.time.Instant;

public record TransaccionValidadaEvent(
        String eventId,
        String tipoEvento,
        Instant fechaEvento,
        String solicitudId,
        Long transaccionId,
        Long cuentaId,
        String tipoTransaccion,
        BigDecimal monto,
        String canal,
        String resultado,
        String motivo,
        String validadoPor) {
}
