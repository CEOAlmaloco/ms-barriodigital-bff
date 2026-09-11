package cl.duoc.barriodigital.bff.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceValidatorTest {

    private final JwtValidationConfig.AudienceValidator validator =
            new JwtValidationConfig.AudienceValidator(
                    Set.of("api://a0773f3e-abc6-4b53-86fc-9d33a2eddef3"));

    @Test
    void aceptaAudienceDelContrato() {
        Jwt jwt = jwtWithAud("api://a0773f3e-abc6-4b53-86fc-9d33a2eddef3");
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rechazaAudienceAjena() {
        Jwt jwt = jwtWithAud("api://otra-app-cualquiera");
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void aceptaAudienceEnLista() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .audience(List.of("api://a0773f3e-abc6-4b53-86fc-9d33a2eddef3", "otro"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", "test")
                .build();
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    private static Jwt jwtWithAud(String aud) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claims(c -> c.putAll(Map.of(
                        "sub", "test",
                        "aud", aud,
                        "iss", "https://login.microsoftonline.com/ce6e98c4-63f0-4b79-83e5-20925b5fada2/v2.0"
                )))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
