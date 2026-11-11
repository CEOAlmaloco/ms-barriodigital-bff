package cl.duoc.barriodigital.bff.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * Headers hacia servicios internos (EP3-43).
 * La IP solo sale de {@code X-Source-Ip} (Gateway). Nunca de {@code X-Forwarded-For}.
 */
public final class DownstreamHeaders {

    public static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";
    public static final String HEADER_SOURCE_IP = "X-Source-Ip";

    private DownstreamHeaders() {
    }

    /**
     * Lee la IP que puso el Gateway. Si no viene, o viene vacía, retorna null
     * (no se manda el header aguas abajo).
     */
    public static String sourceIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return readable(request.getHeader(HEADER_SOURCE_IP));
    }

    /** Agrega el token interno si está configurado. */
    public static void applyInternalToken(HttpHeaders headers, String token) {
        String value = readable(token);
        if (value != null) {
            headers.set(HEADER_INTERNAL_TOKEN, value);
        }
    }

    /** Agrega {@code X-Source-Ip} solo si hay valor. */
    public static void applySourceIp(HttpHeaders headers, String sourceIp) {
        String value = readable(sourceIp);
        if (value != null) {
            headers.set(HEADER_SOURCE_IP, value);
        }
    }

    private static String readable(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return null;
        }
        return value;
    }
}
