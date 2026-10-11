package cl.duoc.bancoxyz.ms.pagos.advice;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cl.duoc.bancoxyz.ms.pagos.exceptions.TransaccionNoEncontradaException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TransaccionNoEncontradaException.class)
    public ResponseEntity<Map<String, String>> manejarNoEncontrada(TransaccionNoEncontradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "No encontrado", "mensaje", ex.getMessage()));
    }
}
