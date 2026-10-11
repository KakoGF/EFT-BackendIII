package cl.duoc.bancoxyz.ms.clientes.dtos;

public record ClienteDTO(
        Long clienteId,
        String rut,
        String nombre,
        String email,
        String telefono,
        String direccion,
        Integer edad,
        String segmento,
        String nivelRiesgo,
        Integer alertasSeguridad,
        String ultimaAlerta) {
}
