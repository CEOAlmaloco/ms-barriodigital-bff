package cl.duoc.barriodigital.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * EP1-12: issuer (vía issuer-uri + JWKS), exp (default), audience del contrato.
 */
@Configuration
@Profile("!dev")
public class JwtValidationConfig {

    @Bean
    @ConditionalOnProperty(name = "barriodigital.security.jwt.entra-decoder", havingValue = "true", matchIfMissing = true)
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.audiences}") String audiencesCsv) {

        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUri);

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new AudienceValidator(parseAudiences(audiencesCsv));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    private static Set<String> parseAudiences(String csv) {
        Set<String> audiences = new HashSet<>();
        if (csv == null || csv.isBlank()) {
            return audiences;
        }
        Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(audiences::add);
        return audiences;
    }

    /**
     * El token debe traer al menos uno de los audiences esperados en el claim {@code aud}.
     */
    static final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

        private final Set<String> allowed;

        AudienceValidator(Set<String> allowed) {
            this.allowed = allowed;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            if (allowed.isEmpty()) {
                return OAuth2TokenValidatorResult.success();
            }

            List<String> tokenAudiences = new ArrayList<>();
            Object aud = jwt.getClaims().get("aud");
            if (aud instanceof String s) {
                tokenAudiences.add(s);
            } else if (aud instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) {
                        tokenAudiences.add(item.toString());
                    }
                }
            }

            boolean match = tokenAudiences.stream().anyMatch(allowed::contains);
            if (match) {
                return OAuth2TokenValidatorResult.success();
            }

            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "Audience inválido. Esperado uno de " + allowed + ", recibido " + tokenAudiences,
                    null);
            return OAuth2TokenValidatorResult.failure(error);
        }
    }
}
