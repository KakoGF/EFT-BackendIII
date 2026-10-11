package cl.duoc.bancoxyz.bff.movil.dtos.core;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransaccionCoreDTO(
        String solicitudId,
        Long transaccionId,
        String tipo,
        Long cuentaOrigenId,
        Long cuentaDestinoId,
        String referenciaPago,
        BigDecimal monto,
        String canal,
        String estado,
        String motivo,
        BigDecimal saldoFinal,
        LocalDateTime fechaRegistro,
        LocalDateTime fechaResolucion,
        String origen) {
}
