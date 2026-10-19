package cl.duoc.barriodigital.bff.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * El claim {@code roles} del JWT que el BFF ya validó.
 * Catalog no lee el token: recibe este header.
 */
public final class CatalogCaller {

    public static final String HEADER_USER_ROLES = "X-User-Roles";

    private CatalogCaller() {
    }

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

    private static String joinAuthorities(Authentication authentication) {
        String joined = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .filter(role -> !role.isBlank())
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }
}
