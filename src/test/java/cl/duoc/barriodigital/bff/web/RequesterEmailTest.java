package cl.duoc.barriodigital.bff.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequesterEmailTest {

    @Test
    void sinJwtElHeaderViajaVacio() {
        assertEquals("", RequestsController.resolveUserEmail(null));
    }

    @Test
    void prefiereEmailYLoRecorta() {
        Jwt jwt = jwt()
                .claim("email", "  vecino@example.com  ")
                .claim("preferred_username", "otro@example.com")
                .build();

        assertEquals("vecino@example.com", RequestsController.resolveUserEmail(jwt));
    }

    @Test
    void siNoHayEmailUsaPreferredUsername() {
        Jwt jwt = jwt()
                .claim("preferred_username", "alex@duoc.cl")
                .build();

        assertEquals("alex@duoc.cl", RequestsController.resolveUserEmail(jwt));
    }

    @Test
    void sinNingunClaimElHeaderViajaVacio() {
        assertEquals("", RequestsController.resolveUserEmail(jwt().build()));
    }

    @Test
    void unEmailConSaltoDeLineaNoSeUsa() {
        Jwt jwt = jwt()
                .claim("email", "vecino\n@example.com")
                .claim("preferred_username", "alex@duoc.cl")
                .build();

        assertEquals("alex@duoc.cl", RequestsController.resolveUserEmail(jwt));
    }

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("sub-1");
    }
}
