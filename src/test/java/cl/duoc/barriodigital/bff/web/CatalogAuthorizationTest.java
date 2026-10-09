package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.CatalogProxyService;
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

import static org.mockito.ArgumentMatchers.anyMap;
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
 * EP1.5-13: GET para cualquier rol autenticado. POST y PUT solo Admin.
 */
@SpringBootTest(properties = "barriodigital.security.jwt.entra-decoder=false")
@AutoConfigureMockMvc
@Import(CatalogAuthorizationTest.TestJwtDecoderConfig.class)
class CatalogAuthorizationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    CatalogProxyService catalogProxyService;

    @Test
    void vecinoPuedeListarElCatalogo() throws Exception {
        when(catalogProxyService.list()).thenReturn(ResponseEntity.ok(List.of(
                Map.of("value", "bache", "label", "Bache en calle")
        )));

        mockMvc.perform(get("/api/catalog/procedures")
                        .with(jwt()
                                .jwt(token -> token.claim("roles", List.of("Vecino")))
                                .authorities(new SimpleGrantedAuthority("ROLE_Vecino"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value("bache"));
    }

    @Test
    void sinTokenElListadoResponde401() throws Exception {
        mockMvc.perform(get("/api/catalog/procedures"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void vecinoNoPuedeCrearNiEditar() throws Exception {
        mockMvc.perform(post("/api/catalog/procedures")
                        .with(jwt()
                                .jwt(token -> token.claim("roles", List.of("Vecino")))
                                .authorities(new SimpleGrantedAuthority("ROLE_Vecino")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"semaforo\",\"label\":\"Semáforo\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/catalog/procedures/bache")
                        .with(jwt()
                                .jwt(token -> token.claim("roles", List.of("Funcionario")))
                                .authorities(new SimpleGrantedAuthority("ROLE_Funcionario")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Bache grave\",\"active\":false,\"dailyQuota\":4}"))
                .andExpect(status().isForbidden());

        verify(catalogProxyService, never()).create(anyMap(), org.mockito.ArgumentMatchers.any());
        verify(catalogProxyService, never()).update(
                org.mockito.ArgumentMatchers.any(),
                anyMap(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void adminCreaYEditaYElProxyRecibeElRolDelToken() throws Exception {
        when(catalogProxyService.create(anyMap(), eq("Admin"))).thenReturn(ResponseEntity
                .status(201)
                .body(Map.of("value", "semaforo", "label", "Semáforo")));
        when(catalogProxyService.update(eq("bache"), anyMap(), eq("Admin"))).thenReturn(ResponseEntity.ok(
                Map.of("value", "bache", "label", "Bache grave")
        ));

        mockMvc.perform(post("/api/catalog/procedures")
                        .with(jwt()
                                .jwt(token -> token.claim("roles", List.of("Admin")))
                                .authorities(new SimpleGrantedAuthority("ROLE_Admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"semaforo\",\"label\":\"Semáforo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value("semaforo"));

        mockMvc.perform(put("/api/catalog/procedures/bache")
                        .with(jwt()
                                .jwt(token -> token.claim("roles", List.of("Admin")))
                                .authorities(new SimpleGrantedAuthority("ROLE_Admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Bache grave\",\"active\":false,\"dailyQuota\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("bache"));

        verify(catalogProxyService).create(anyMap(), eq("Admin"));
        verify(catalogProxyService).update(eq("bache"), anyMap(), eq("Admin"));
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
