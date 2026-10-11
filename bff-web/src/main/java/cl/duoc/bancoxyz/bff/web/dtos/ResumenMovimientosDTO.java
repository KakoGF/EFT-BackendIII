package cl.duoc.bancoxyz.bff.web.dtos;

import java.math.BigDecimal;

public record ResumenMovimientosDTO(
        BigDecimal totalAbonos,
        BigDecimal totalCargos,
        BigDecimal saldoNetoMovimientos,
        int cantidadMovimientos) {
}
