package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.RequestsProxyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EP1.5-06: PUT /status — Funcionario 200; Vecino/Admin 403; sin token 401.
 */
@SpringBootTest(properties = "barriodigital.security.jwt.entra-decoder=false")
@AutoConfigureMockMvc
@Import(RequestStatusAuthorizationTest.TestJwtDecoderConfig.class)
class RequestStatusAuthorizationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RequestsProxyService requestsProxyService;

    @Test
    void funcionarioCambiaEstado200() throws Exception {
        when(requestsProxyService.updateStatus(eq("id-1"), anyMap(), any(), any()))
                .thenReturn(ResponseEntity.ok(Map.of("id", "id-1", "status", "ADMITIDO")));

        mockMvc.perform(put("/api/requests/id-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ADMITIDO\"}")
                        .with(jwt()
                                .jwt(j -> j.claim("oid", "func-1").claim("roles", List.of("Funcionario")))
                                .authorities(new SimpleGrantedAuthority("ROLE_Funcionario"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADMITIDO"));

        verify(requestsProxyService).updateStatus(eq("id-1"), anyMap(), eq("func-1"), eq("Funcionario"));
    }

    @Test
    void vecinoResponde403SinLlamarARequests() throws Exception {
        mockMvc.perform(put("/api/requests/id-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ADMITIDO\"}")
                        .with(jwt()
                                .jwt(j -> j.claim("roles", List.of("Vecino")))
                                .authorities(new SimpleGrantedAuthority("ROLE_Vecino"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verify(requestsProxyService, never()).updateStatus(anyString(), anyMap(), any(), any());
    }

    @Test
    void adminResponde403() throws Exception {
        mockMvc.perform(put("/api/requests/id-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ADMITIDO\"}")
                        .with(jwt()
                                .jwt(j -> j.claim("roles", List.of("Admin")))
                                .authorities(new SimpleGrantedAuthority("ROLE_Admin"))))
                .andExpect(status().isForbidden());

        verify(requestsProxyService, never()).updateStatus(anyString(), anyMap(), any(), any());
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(put("/api/requests/id-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ADMITIDO\"}"))
                .andExpect(status().isUnauthorized());
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
