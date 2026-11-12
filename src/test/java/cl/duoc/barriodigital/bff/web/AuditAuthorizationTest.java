package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.AuditProxyService;
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

/** EP3-44: /api/audit/** Admin y Auditor. */
@SpringBootTest(properties = "barriodigital.security.jwt.entra-decoder=false")
@AutoConfigureMockMvc
@Import(AuditAuthorizationTest.TestJwtDecoderConfig.class)
class AuditAuthorizationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuditProxyService auditProxyService;

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/audit/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verify(auditProxyService, never()).forward(
                any(HttpServletRequest.class), any(), any(CatalogCaller.Identity.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Vecino", "Funcionario"})
    void rolSinAccesoRecibe403(String role) throws Exception {
        mockMvc.perform(get("/api/audit/timeline").with(withRole(role)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verify(auditProxyService, never()).forward(
                any(HttpServletRequest.class), any(), any(CatalogCaller.Identity.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Auditor"})
    void rolPermitidoRecibe200(String role) throws Exception {
        when(auditProxyService.forward(any(HttpServletRequest.class), any(), eq(identity(role))))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"content\":[]}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/audit/events").with(withRole(role)))
                .andExpect(status().isOk());

        verify(auditProxyService).forward(any(HttpServletRequest.class), any(), eq(identity(role)));
    }

    @Test
    void auditCaidoResponde503ConElCuerpoDelHandler() throws Exception {
        when(auditProxyService.forward(any(HttpServletRequest.class), any(), eq(identity("Admin"))))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, AuditProxyService.UNAVAILABLE));

        mockMvc.perform(get("/api/audit/events").with(withRole("Admin")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(AuditProxyService.UNAVAILABLE))
                .andExpect(jsonPath("$.path").value("/api/audit/events"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor withRole(String role) {
        return jwt()
                .jwt(token -> token
                        .claim("roles", List.of(role))
                        .claim("oid", role.toLowerCase() + "-1")
                        .claim("name", role + " Test"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static CatalogCaller.Identity identity(String role) {
        return new CatalogCaller.Identity(role.toLowerCase() + "-1", role, role + "%20Test");
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
