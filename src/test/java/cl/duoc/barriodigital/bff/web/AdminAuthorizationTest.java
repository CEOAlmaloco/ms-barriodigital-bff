package cl.duoc.barriodigital.bff.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EP1-13: Admin → 200; Vecino → 403 en /api/admin/ping.
 */
@SpringBootTest(properties = "barriodigital.security.jwt.entra-decoder=false")
@AutoConfigureMockMvc
@Import(AdminAuthorizationTest.TestJwtDecoderConfig.class)
class AdminAuthorizationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void adminConRolAdminResponde200() throws Exception {
        mockMvc.perform(get("/api/admin/ping")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.message").value("Área Admin — autorizado"));
    }

    @Test
    void vecinoEnAdminResponde403() throws Exception {
        mockMvc.perform(get("/api/admin/ping")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Vecino"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value("/api/admin/ping"));
    }

    @Test
    void sinTokenEnAdminResponde401() throws Exception {
        mockMvc.perform(get("/api/admin/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void vecinoPuedePegarleAPingNormal() throws Exception {
        mockMvc.perform(get("/api/ping")
                        .with(jwt()
                                .jwt(j -> j.claim("roles", List.of("Vecino")).subject("vecino-test"))
                                .authorities(new SimpleGrantedAuthority("ROLE_Vecino"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("vecino-test"));
    }

    @TestConfiguration
    static class TestJwtDecoderConfig {
        @Bean
        @Primary
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("test")
                    .claim("roles", List.of("Vecino"))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        }
    }
}
