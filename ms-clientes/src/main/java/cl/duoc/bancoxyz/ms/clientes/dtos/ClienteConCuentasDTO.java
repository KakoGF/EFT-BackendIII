package cl.duoc.bancoxyz.ms.clientes.dtos;

import java.util.List;

import cl.duoc.bancoxyz.ms.clientes.dtos.core.CuentaCoreDTO;

public record ClienteConCuentasDTO(
        ClienteDTO cliente,
        List<CuentaCoreDTO> cuentas,
        String mensaje) {
}
