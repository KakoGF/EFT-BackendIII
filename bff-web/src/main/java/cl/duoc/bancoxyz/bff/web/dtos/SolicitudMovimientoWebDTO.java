package cl.duoc.bancoxyz.bff.web.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SolicitudMovimientoWebDTO(
        String solicitudId,
        Long cuentaId,
        String tipoMovimiento,
        BigDecimal monto,
        Long cuentaDestinoId,
        String referenciaPago,
        String estado,
        String motivo,
        String mensaje,
        BigDecimal saldoFinal,
        LocalDateTime fechaRegistro,
        LocalDateTime fechaResolucion) {
}
