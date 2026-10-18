package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.MqAdminProxyService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EP2-26: /api/mq/** solo Admin. Sin token, 401. mq-admin caído, 503 del BffExceptionHandler.
 */
@SpringBootTest(properties = "barriodigital.security.jwt.entra-decoder=false")
@AutoConfigureMockMvc
@Import(MqAdminAuthorizationTest.TestJwtDecoderConfig.class)
class MqAdminAuthorizationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    MqAdminProxyService mqAdminProxyService;

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/mq/queues"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verify(mqAdminProxyService, never()).forward(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Vecino", "Funcionario", "Auditor"})
    void rolDistintoDeAdminRecibe403(String role) throws Exception {
        mockMvc.perform(post("/api/mq/queues")
                        .with(jwt()
                                .jwt(token -> token.claim("roles", List.of(role)))
                                .authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"q.demo\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verify(mqAdminProxyService, never()).forward(any(), any(), any());
    }

    @Test
    void adminListaLasColas() throws Exception {
        when(mqAdminProxyService.forward(any(HttpServletRequest.class), any(), eq("Admin")))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("[{\"name\":\"q.demo\"}]".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/mq/queues").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("q.demo"));

        verify(mqAdminProxyService).forward(any(HttpServletRequest.class), any(), eq("Admin"));
    }

    @Test
    void adminCreaUnaColaYElBffResponde201() throws Exception {
        when(mqAdminProxyService.forward(any(HttpServletRequest.class), any(), eq("Admin")))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"q.demo\"}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post("/api/mq/queues")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"q.demo\",\"durable\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("q.demo"));
    }

    @Test
    void mqAdminCaidoResponde503ConElCuerpoDelHandler() throws Exception {
        when(mqAdminProxyService.forward(any(HttpServletRequest.class), any(), eq("Admin")))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, MqAdminProxyService.UNAVAILABLE));

        mockMvc.perform(get("/api/mq/queues").with(admin()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value(MqAdminProxyService.UNAVAILABLE))
                .andExpect(jsonPath("$.path").value("/api/mq/queues"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt()
                .jwt(token -> token.claim("roles", List.of("Admin")))
                .authorities(new SimpleGrantedAuthority("ROLE_Admin"));
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
