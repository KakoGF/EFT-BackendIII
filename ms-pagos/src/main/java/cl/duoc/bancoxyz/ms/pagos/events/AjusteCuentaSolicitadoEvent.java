package cl.duoc.bancoxyz.ms.pagos.events;

import java.math.BigDecimal;
import java.time.Instant;

public record AjusteCuentaSolicitadoEvent(
        String eventId,
        String tipoEvento,
        Instant fechaEvento,
        String solicitudId,
        Long transaccionId,
        String tipoTransaccion,
        Long cuentaOrigenId,
        Long cuentaDestinoId,
        BigDecimal monto,
        String canal,
        String descripcion) {
}
