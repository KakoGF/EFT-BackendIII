package cl.duoc.bancoxyz.ms.clientes.services;

public final class SegmentoCliente {

    private SegmentoCliente() {
    }

    public static String segun(Integer edad) {
        if (edad == null) {
            return "SIN_DATO";
        }
        if (edad < 30) {
            return "JOVEN";
        }
        if (edad < 60) {
            return "ADULTO";
        }
        return "SENIOR";
    }
}
