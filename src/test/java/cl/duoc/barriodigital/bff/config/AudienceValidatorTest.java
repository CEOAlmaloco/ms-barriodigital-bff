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

    private static final String AUD_URI = "api://a0773f3e-abc6-4b53-86fc-9d33a2eddef3";
    private static final String AUD_GUID = "a0773f3e-abc6-4b53-86fc-9d33a2eddef3";

    private final JwtValidationConfig.AudienceValidator validator =
            new JwtValidationConfig.AudienceValidator(Set.of(AUD_URI, AUD_GUID));

    @Test
    void aceptaAudienceAppIdUri() {
        Jwt jwt = jwtWithAud(AUD_URI);
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void aceptaAudienceGuidComoEmiteEntra() {
        Jwt jwt = jwtWithAud(AUD_GUID);
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
                .audience(List.of(AUD_URI, "otro"))
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
