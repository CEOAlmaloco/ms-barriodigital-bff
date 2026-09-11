package cl.duoc.barriodigital.bff.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * EP1-13: claim {@code roles} de Entra App Roles → {@code ROLE_Admin}, {@code ROLE_Vecino}, etc.
 */
final class EntraRolesJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();

    EntraRolesJwtConverter() {
        delegate.setJwtGrantedAuthoritiesConverter(EntraRolesJwtConverter::extractRoles);
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AbstractAuthenticationToken token = delegate.convert(jwt);
        if (token == null) {
            return new JwtAuthenticationToken(jwt, List.of());
        }
        return token;
    }

    static Collection<GrantedAuthority> extractRoles(Jwt jwt) {
        Set<String> roleNames = new HashSet<>();

        Object rolesClaim = jwt.getClaims().get("roles");
        if (rolesClaim instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null) {
                    roleNames.add(item.toString());
                }
            }
        } else if (rolesClaim instanceof String single && !single.isBlank()) {
            roleNames.add(single);
        }

        // Por si el token trae un solo rol en otro formato raro
        Object roleClaim = jwt.getClaims().get("role");
        if (roleClaim instanceof String single && !single.isBlank()) {
            roleNames.add(single);
        }

        return roleNames.stream()
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
