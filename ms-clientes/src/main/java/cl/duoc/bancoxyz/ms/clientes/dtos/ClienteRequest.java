package cl.duoc.bancoxyz.ms.clientes.dtos;

public record ClienteRequest(
        String rut,
        String nombre,
        String email,
        String telefono,
        String direccion,
        Integer edad) {
}
