package cl.duoc.barriodigital.bff.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogCallerTest {

    @Test
    void identidadCompletaCodificaElNombre() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("sub-1")
                .claim("oid", "oid-1")
                .claim("name", "María Ñandú")
                .claim("roles", List.of("Admin"))
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_Admin")));

        CatalogCaller.Identity identity = CatalogCaller.identity(authentication);

        assertEquals(new CatalogCaller.Identity(
                "oid-1", "Admin", "Mar%C3%ADa%20%C3%91and%C3%BA"), identity);
    }

    @Test
    void sinJwtLaIdentidadQuedaVacia() {
        assertEquals(new CatalogCaller.Identity(null, null, null), CatalogCaller.identity(null));
    }
}
