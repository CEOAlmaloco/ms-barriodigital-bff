package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.AdmissionException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce los {@code ResponseStatusException} del BFF a JSON con status, error, message y path, la misma forma que SecurityJsonErrors.
 * Si el error es una admisión, incluye su código. Sin este manejador Spring deja message fuera del cuerpo, y el 400 de {@code @Valid} no pasa por aquí.
 */
@RestControllerAdvice
public class BffExceptionHandler {

    /** Arma ese JSON a partir de la excepción y la ruta. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException ex, HttpServletRequest request) {
        int status = ex.getStatusCode().value();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("error", errorName(status));
        body.put("message", ex.getReason() == null ? "" : ex.getReason());
        body.put("path", request.getRequestURI());
        if (ex instanceof AdmissionException admission) {
            body.put("code", admission.code());
        }
        return ResponseEntity.status(status).body(body);
    }

    /** Convierte el status HTTP en su frase, por ejemplo Not Found. */
    private static String errorName(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved != null ? resolved.getReasonPhrase() : "Error";
    }
}
