package cl.duoc.bancoxyz.ms.cuentas.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CuentaDTO(
        Long cuentaId,
        Long clienteId,
        String titular,
        String tipo,
        BigDecimal saldo,
        String estado,
        LocalDate fechaApertura,
        String motivoEstado) {
}
