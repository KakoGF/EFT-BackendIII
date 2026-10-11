package cl.duoc.bancoxyz.ms.pagos.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransaccionDTO(
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
