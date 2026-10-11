package cl.duoc.bancoxyz.ms.clientes.advice;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cl.duoc.bancoxyz.ms.clientes.exceptions.ClienteNoEncontradoException;
import cl.duoc.bancoxyz.ms.clientes.exceptions.SolicitudInvalidaException;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ClienteNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> manejarNoEncontrado(ClienteNoEncontradoException ex) {
        return cuerpo(HttpStatus.NOT_FOUND, "No encontrado", ex.getMessage());
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<Map<String, String>> manejarSolicitudInvalida(SolicitudInvalidaException ex) {
        return cuerpo(HttpStatus.BAD_REQUEST, "Solicitud invalida", ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> manejarCuerpoIlegible(HttpMessageNotReadableException ex) {
        return cuerpo(HttpStatus.BAD_REQUEST, "Solicitud invalida", "El cuerpo de la solicitud no es un JSON valido");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> manejarConflicto(ObjectOptimisticLockingFailureException ex) {
        return cuerpo(HttpStatus.CONFLICT, "Conflicto de actualizacion",
                "El cliente fue modificado por otra operacion al mismo tiempo, vuelve a intentarlo");
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<Map<String, String>> manejarLimiteExcedido(RequestNotPermitted ex) {
        return cuerpo(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes",
                "El servicio esta recibiendo mas solicitudes de las permitidas, reintenta en unos segundos");
    }

    private ResponseEntity<Map<String, String>> cuerpo(HttpStatus estado, String error, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("error", error, "mensaje", mensaje));
    }
}
