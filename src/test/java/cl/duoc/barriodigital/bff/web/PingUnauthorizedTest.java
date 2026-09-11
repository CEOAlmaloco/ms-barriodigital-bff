package cl.duoc.barriodigital.bff.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EP1-12: Resource Server activo (sin perfil dev).
 * JwtDecoder de prueba: no llama a Entra; sin Bearer → 401; token basura → 401.
 */
@SpringBootTest(properties = "barriodigital.security.jwt.entra-decoder=false")
@AutoConfigureMockMvc
@Import(PingUnauthorizedTest.TestJwtDecoderConfig.class)
class PingUnauthorizedTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void sinBearerResponde401() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/ping"));
    }

    @Test
    void bearerInvalidoResponde401() throws Exception {
        mockMvc.perform(get("/api/ping").header("Authorization", "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class TestJwtDecoderConfig {
        @Bean
        @Primary
        JwtDecoder jwtDecoder() {
            return token -> {
                if ("token-ok-test".equals(token)) {
                    return Jwt.withTokenValue(token)
                            .header("alg", "none")
                            .subject("test-user")
                            .audience(java.util.List.of("api://a0773f3e-abc6-4b53-86fc-9d33a2eddef3"))
                            .issuedAt(Instant.now())
                            .expiresAt(Instant.now().plusSeconds(3600))
                            .build();
                }
                throw new BadJwtException("Token inválido (test decoder)");
            };
        }
    }
}
