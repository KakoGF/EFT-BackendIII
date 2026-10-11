package cl.duoc.bancoxyz.bff.web.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CuentaWebDetalleDTO(
        Long cuentaId,
        String titular,
        BigDecimal saldo,
        String tipo,
        String estado,
        LocalDate fechaApertura,
        ClienteWebDTO cliente,
        List<MovimientoWebDTO> movimientos,
        ResumenMovimientosDTO resumen) {
}
