package cl.duoc.bancoxyz.ms.validaciones.services;

public record ResultadoValidacion(
        boolean aprobado,
        String motivo,
        String tipoAlerta,
        String nivelAlerta,
        String detalleAlerta) {

    public static ResultadoValidacion aprobar() {
        return new ResultadoValidacion(true, null, null, null, null);
    }

    public static ResultadoValidacion aprobarConAlerta(String tipoAlerta, String nivel, String detalle) {
        return new ResultadoValidacion(true, null, tipoAlerta, nivel, detalle);
    }

    public static ResultadoValidacion rechazar(String codigo, String detalle) {
        return new ResultadoValidacion(false, codigo + ": " + detalle, null, null, null);
    }

    public static ResultadoValidacion rechazarConAlerta(String codigo, String detalle, String nivel) {
        return new ResultadoValidacion(false, codigo + ": " + detalle, codigo, nivel, detalle);
    }

    public boolean generaAlerta() {
        return tipoAlerta != null;
    }
}
