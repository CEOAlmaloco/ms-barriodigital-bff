package cl.duoc.barriodigital.bff.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Arma el header {@code X-User-Roles} con los roles del JWT que el BFF ya validó.
 * Catalog no lee el token: recibe ese header.
 */
public final class CatalogCaller {

    public static final String HEADER_USER_ROLES = "X-User-Roles";

    /** No se instancia: solo se usa {@link #rolesHeader}. */
    private CatalogCaller() {
    }

    /** Devuelve los roles separados por comas, o null si no hay JWT. */
    public static String rolesHeader(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return null;
        }
        String fromClaim = joinClaim(jwtAuth.getToken().getClaim("roles"));
        if (fromClaim != null) {
            return fromClaim;
        }
        return joinAuthorities(authentication);
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
}
