package cl.duoc.bancoxyz.bff.web.dtos;

public record ClienteWebDTO(
        Long clienteId,
        String nombre,
        String email,
        String telefono,
        Integer edad,
        String segmento,
        String nivelRiesgo) {
}
