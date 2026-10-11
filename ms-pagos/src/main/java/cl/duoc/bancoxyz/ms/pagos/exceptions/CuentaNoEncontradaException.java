package cl.duoc.bancoxyz.ms.pagos.exceptions;

public class CuentaNoEncontradaException extends RuntimeException {

    public CuentaNoEncontradaException(Long cuentaId) {
        super("No existe la cuenta con id " + cuentaId);
    }
}
