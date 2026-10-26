package cl.duoc.barriodigital.bff.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Escribe los errores de seguridad en JSON.
 * Así un 401 o un 403 tienen la misma forma que el resto de los errores del BFF.
 */
final class SecurityJsonErrors {

    /** No se instancia: solo se usa {@link #write}. */
    private SecurityJsonErrors() {
    }

    /** Escribe status, error, message y path en la respuesta. */
    static void write(HttpServletRequest request, HttpServletResponse response, int status, String error, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String safeMessage = message == null ? error : message;
        String body = """
                {"status":%d,"error":%s,"message":%s,"path":%s}
                """.formatted(
                        status,
                        jsonString(error),
                        jsonString(safeMessage),
                        jsonString(request.getRequestURI())).trim();
        response.getWriter().write(body);
    }

    /** Escapa comillas y barras para que el texto no rompa el JSON. */
    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
