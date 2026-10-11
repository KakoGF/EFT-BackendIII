package cl.duoc.bancoxyz.ms.clientes.exceptions;

public class ClienteNoEncontradoException extends RuntimeException {

    public ClienteNoEncontradoException(Long clienteId) {
        super("No existe el cliente con id " + clienteId);
    }
}
