package cl.duoc.bancoxyz.ms.pagos.events;

import java.math.BigDecimal;
import java.time.Instant;

public record AjusteCuentaResultadoEvent(
        String eventId,
        String tipoEvento,
        Instant fechaEvento,
        String solicitudId,
        Long transaccionId,
        String resultado,
        String motivo,
        Long cuentaOrigenId,
        String titularOrigen,
        BigDecimal saldoOrigen,
        Long cuentaDestinoId,
        BigDecimal saldoDestino) {
}
