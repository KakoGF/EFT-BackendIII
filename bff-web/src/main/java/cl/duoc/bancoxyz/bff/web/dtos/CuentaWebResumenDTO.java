package cl.duoc.bancoxyz.bff.web.dtos;

import java.math.BigDecimal;

public record CuentaWebResumenDTO(
        Long cuentaId,
        Long clienteId,
        String titular,
        BigDecimal saldo,
        String tipo,
        String estado) {
}
