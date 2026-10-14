package cl.duoc.barriodigital.bff.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Errores propios del BFF (ResponseStatusException) con la misma forma que SecurityJsonErrors:
 * {@code {status, error, message, path}}. Sin esto Spring deja message fuera del cuerpo.
 * El 400 de @Valid no pasa por acá y queda como siempre.
 */
@RestControllerAdvice
public class BffExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException ex, HttpServletRequest request) {
        int status = ex.getStatusCode().value();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("error", errorName(status));
        body.put("message", ex.getReason() == null ? "" : ex.getReason());
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    private static String errorName(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved != null ? resolved.getReasonPhrase() : "Error";
    }
}
