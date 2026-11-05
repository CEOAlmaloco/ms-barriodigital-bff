package cl.duoc.barriodigital.bff.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Arma los headers de identidad con los datos del JWT que el BFF ya validó.
 * Los servicios internos no reciben el token: reciben estos headers.
 */
public final class CatalogCaller {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_USER_NAME = "X-User-Name";

    /** Identidad lista para reenviar. {@code userName} ya viene codificada en URL (UTF-8). */
    public record Identity(String userId, String roles, String userName) {
    }

    /** No se instancia: solo se usan los métodos estáticos. */
    private CatalogCaller() {
    }

    /** Junta id, roles y nombre para catalog o mq-admin. Sin JWT, la identidad queda vacía. */
    public static Identity identity(Authentication authentication) {
        Jwt jwt = jwt(authentication);
        if (jwt == null) {
            return new Identity(null, null, null);
        }
        return new Identity(
                userId(jwt),
                rolesHeader(authentication),
                encodeHeaderValue(userName(jwt))
        );
    }

    /** Saca el id de la persona: prefiere el claim oid de Entra y, si no viene, sub. */
    public static String userId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        String oid = readableClaim(jwt.getClaimAsString("oid"));
        if (oid != null) {
            return oid;
        }
        return readableClaim(jwt.getSubject());
    }

    /** Nombre legible: primero el claim name y, si no viene, preferred_username. */
    public static String userName(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        String name = readableClaim(jwt.getClaimAsString("name"));
        if (name != null) {
            return name;
        }
        return readableClaim(jwt.getClaimAsString("preferred_username"));
    }

    /**
     * Codifica un valor para header en URL (UTF-8), con el mismo criterio que X-Procedure-Label.
     * El espacio queda como %20 y el + como %2B; el servicio interno lo lee con URLDecoder.
     */
    public static String encodeHeaderValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UriUtils.encode(value, StandardCharsets.UTF_8);
    }

    /** Devuelve los roles separados por comas, o null si no hay JWT. */
    public static String rolesHeader(Authentication authentication) {
        Jwt jwt = jwt(authentication);
        if (jwt == null) {
            return null;
        }
        String fromClaim = joinClaim(jwt.getClaim("roles"));
        if (fromClaim != null) {
            return fromClaim;
        }
        return joinAuthorities(authentication);
    }

    /** Extrae el JWT cuando la autenticación ya fue convertida por Spring Security. */
    private static Jwt jwt(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return null;
        }
        return jwtAuth.getToken();
    }

    /** Junta una lista o un texto del claim {@code roles}. */
    private static String joinClaim(Object rolesClaim) {
        if (rolesClaim instanceof Collection<?> collection) {
            String joined = collection.stream()
                    .filter(item -> item != null && !item.toString().isBlank())
                    .map(item -> item.toString().trim())
                    .collect(Collectors.joining(","));
            return joined.isEmpty() ? null : joined;
        }
        if (rolesClaim instanceof String single && !single.isBlank()) {
            return single.trim();
        }
        return null;
    }

    /** Si el JWT no trae {@code roles}, usa las autoridades ya convertidas y les quita el prefijo {@code ROLE_}. */
    private static String joinAuthorities(Authentication authentication) {
        String joined = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .filter(role -> !role.isBlank())
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    /** Recorta el claim y descarta un valor vacío o con salto de línea, porque eso no puede ir en un header. */
    private static String readableClaim(String raw) {
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
