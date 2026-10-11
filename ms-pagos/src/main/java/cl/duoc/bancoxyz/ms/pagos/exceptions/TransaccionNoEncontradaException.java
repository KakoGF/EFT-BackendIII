package cl.duoc.bancoxyz.ms.pagos.exceptions;

public class TransaccionNoEncontradaException extends RuntimeException {

    public TransaccionNoEncontradaException(String solicitudId) {
        super("No existe la transaccion " + solicitudId);
    }
}
