package cl.duoc.bancoxyz.ms.cuentas.dtos;

import java.math.BigDecimal;

public record AperturaCuentaRequest(
        Long clienteId,
        String titular,
        String tipo,
        BigDecimal depositoInicial) {
}
