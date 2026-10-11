package cl.duoc.bancoxyz.bff.cajero.dtos.core;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CuentaCoreDTO(
        Long cuentaId,
        Long clienteId,
        String titular,
        String tipo,
        BigDecimal saldo,
        String estado,
        LocalDate fechaApertura,
        String motivoEstado) {
}
