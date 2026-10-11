package cl.duoc.bancoxyz.ms.validaciones.events;

import java.math.BigDecimal;
import java.time.Instant;

public record TransaccionSolicitadaEvent(
        String eventId,
        String tipoEvento,
        Instant fechaEvento,
        String solicitudId,
        Long transaccionId,
        Long cuentaId,
        Long cuentaDestinoId,
        Long clienteId,
        String tipoTransaccion,
        BigDecimal monto,
        String canal,
        String tipoCuenta,
        BigDecimal saldoDisponible) {
}
