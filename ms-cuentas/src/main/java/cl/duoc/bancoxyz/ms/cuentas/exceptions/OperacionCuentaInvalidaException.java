package cl.duoc.bancoxyz.ms.cuentas.exceptions;

public class OperacionCuentaInvalidaException extends RuntimeException {

    public OperacionCuentaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
