package cl.duoc.bancoxyz.ms.pagos.dtos.core;

import java.math.BigDecimal;

public record CuentaCoreDTO(
        Long cuentaId,
        Long clienteId,
        String titular,
        String tipo,
        BigDecimal saldo,
        String estado) {
}
