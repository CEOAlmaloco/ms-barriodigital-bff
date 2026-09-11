package cl.duoc.barriodigital.bff.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class SecurityJsonErrors {

    private SecurityJsonErrors() {
    }

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

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
