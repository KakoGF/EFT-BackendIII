package cl.duoc.bancoxyz.bff.web.dtos.core;

public record ClienteCoreDTO(
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
