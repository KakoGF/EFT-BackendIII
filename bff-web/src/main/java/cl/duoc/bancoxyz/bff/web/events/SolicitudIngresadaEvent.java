package cl.duoc.bancoxyz.bff.web.events;

import java.math.BigDecimal;
import java.time.Instant;

public record SolicitudIngresadaEvent(
        String eventId,
        String tipoEvento,
        Instant fechaEvento,
        String solicitudId,
        Long cuentaId,
        String tipoMovimiento,
        BigDecimal monto,
        String canal,
        String descripcion,
        Long cuentaDestinoId,
        String referenciaPago,
        String clienteOAuth,
        String usuario) {
}
