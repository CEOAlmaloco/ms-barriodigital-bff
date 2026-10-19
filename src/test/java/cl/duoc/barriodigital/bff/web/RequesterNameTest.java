package cl.duoc.barriodigital.bff.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequesterNameTest {

    @Test
    void sinJwtUsaElUsuarioDePrueba() {
        assertEquals("dev-user", RequestsController.resolveUserName(null));
    }

    @Test
    void prefiereNameYLoRecorta() {
        Jwt jwt = jwt()
                .claim("name", "  Alex Ampuero  ")
                .claim("preferred_username", "alex@duoc.cl")
                .build();

        assertEquals("Alex Ampuero", RequestsController.resolveUserName(jwt));
    }

    @Test
    void siNoHayNameUsaPreferredUsername() {
        Jwt jwt = jwt()
                .claim("preferred_username", "alex@duoc.cl")
                .build();

        assertEquals("alex@duoc.cl", RequestsController.resolveUserName(jwt));
    }

    @Test
    void sinNingunClaimElHeaderViajaVacio() {
        assertEquals("", RequestsController.resolveUserName(jwt().build()));
    }

    @Test
    void unNameConSaltoDeLineaNoSeUsa() {
        Jwt jwt = jwt()
                .claim("name", "Alex\nAmpuero")
                .claim("preferred_username", "alex@duoc.cl")
                .build();

        assertEquals("alex@duoc.cl", RequestsController.resolveUserName(jwt));
    }

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("sub-1");
    }
}
