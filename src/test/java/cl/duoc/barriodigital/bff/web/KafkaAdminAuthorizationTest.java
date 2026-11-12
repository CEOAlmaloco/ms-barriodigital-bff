package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.KafkaAdminProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** EP3-44: /api/kafka/** solo Admin. */
@SpringBootTest(properties = "barriodigital.security.jwt.entra-decoder=false")
@AutoConfigureMockMvc
@Import(KafkaAdminAuthorizationTest.TestJwtDecoderConfig.class)
class KafkaAdminAuthorizationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    KafkaAdminProxyService kafkaAdminProxyService;

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/kafka/topics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verify(kafkaAdminProxyService, never()).forward(
                any(HttpServletRequest.class), any(), any(CatalogCaller.Identity.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Vecino", "Funcionario", "Auditor"})
    void rolDistintoDeAdminRecibe403(String role) throws Exception {
        mockMvc.perform(get("/api/kafka/topics")
                        .with(jwt()
                                .jwt(token -> token.claim("roles", List.of(role)))
                                .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verify(kafkaAdminProxyService, never()).forward(
                any(HttpServletRequest.class), any(), any(CatalogCaller.Identity.class));
    }

    @Test
    void adminListaLosTopicos() throws Exception {
        when(kafkaAdminProxyService.forward(any(HttpServletRequest.class), any(), eq(adminIdentity())))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("[{\"name\":\"requests.events\"}]".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/kafka/topics").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("requests.events"));

        verify(kafkaAdminProxyService).forward(any(HttpServletRequest.class), any(), eq(adminIdentity()));
    }

    @Test
    void kafkaAdminCaidoResponde503ConElCuerpoDelHandler() throws Exception {
        when(kafkaAdminProxyService.forward(any(HttpServletRequest.class), any(), eq(adminIdentity())))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, KafkaAdminProxyService.UNAVAILABLE));

        mockMvc.perform(get("/api/kafka/topics").with(admin()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value(KafkaAdminProxyService.UNAVAILABLE))
                .andExpect(jsonPath("$.path").value("/api/kafka/topics"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt()
                .jwt(token -> token
                        .claim("roles", List.of("Admin"))
                        .claim("oid", "admin-1")
                        .claim("name", "Admin Test"))
                .authorities(new SimpleGrantedAuthority("ROLE_Admin"));
    }

    private static CatalogCaller.Identity adminIdentity() {
        return new CatalogCaller.Identity("admin-1", "Admin", "Admin%20Test");
    }

    @TestConfiguration
    static class TestJwtDecoderConfig {
        @Bean
        @Primary
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("test")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        }
    }
}
