package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.config.BarrioRoleGateFilter;
import cl.duoc.barriodigital.bff.service.CatalogProxyService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EP3-45: take / assignee / assignees por rol.
 */
@SpringBootTest(properties = "barriodigital.security.jwt.entra-decoder=false")
@AutoConfigureMockMvc
@Import(AssignmentAuthorizationTest.TestJwtDecoderConfig.class)
class AssignmentAuthorizationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RequestsProxyService requestsProxyService;

    @MockBean
    CatalogProxyService catalogProxyService;

    @Test
    void funcionarioToma200() throws Exception {
        when(requestsProxyService.take(eq("id-1"), eq("func-1"), eq("Funcionario"), any()))
                .thenReturn(ResponseEntity.ok(Map.of("id", "id-1", "hasAssignee", true)));

        mockMvc.perform(post("/api/requests/id-1/take")
                        .with(jwt()
                                .jwt(j -> j.claim("oid", "func-1").claim("roles", List.of("Funcionario")))
                                .authorities(new SimpleGrantedAuthority("ROLE_Funcionario"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAssignee").value(true));
    }

    @Test
    void adminNoPuedeTomar() throws Exception {
        mockMvc.perform(post("/api/requests/id-1/take")
                        .with(jwt()
                                .jwt(j -> j.claim("oid", "admin-1").claim("roles", List.of("Admin")))
                                .authorities(new SimpleGrantedAuthority("ROLE_Admin"))))
                .andExpect(status().isForbidden());
        verify(requestsProxyService, never()).take(anyString(), any(), any(), any());
    }

    @Test
    void adminAsigna200() throws Exception {
        when(requestsProxyService.assign(eq("id-1"), anyMap(), eq("admin-1"), eq("Admin"), any()))
                .thenReturn(ResponseEntity.ok(Map.of("id", "id-1")));

        mockMvc.perform(put("/api/requests/id-1/assignee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"func-2\"}")
                        .with(jwt()
                                .jwt(j -> j.claim("oid", "admin-1").claim("roles", List.of("Admin")))
                                .authorities(new SimpleGrantedAuthority("ROLE_Admin"))))
                .andExpect(status().isOk());
    }

    @Test
    void vecinoNoListaAssignees() throws Exception {
        mockMvc.perform(get("/api/requests/assignees")
                        .with(jwt()
                                .jwt(j -> j.claim("roles", List.of("Vecino")))
                                .authorities(new SimpleGrantedAuthority("ROLE_Vecino"))))
                .andExpect(status().isForbidden());
        verify(requestsProxyService, never()).assignees(any(), any());
    }

    @Test
    void tokenSinRolEnAssignees403() throws Exception {
        mockMvc.perform(get("/api/requests/assignees")
                        .with(jwt().jwt(j -> j.claim("oid", "x")).authorities()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(BarrioRoleGateFilter.MSG_NO_ROLE));
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
