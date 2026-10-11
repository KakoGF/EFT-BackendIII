package cl.duoc.bancoxyz.ms.clientes.dtos.core;

import java.math.BigDecimal;

public record CuentaCoreDTO(
        Long cuentaId,
        Long clienteId,
        String titular,
        String tipo,
        BigDecimal saldo,
        String estado) {
}
