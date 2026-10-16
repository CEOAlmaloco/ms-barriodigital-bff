package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.CatalogProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El POST reenvía el label del tipo según catalog en X-Procedure-Label, codificado en URL (UTF-8).
 * Si catalog no trae label, el header no sale. El body no se toca.
 */
@SpringBootTest(properties = {
        "barriodigital.security.jwt.entra-decoder=false",
        "spring.main.allow-bean-definition-overriding=true",
        "barriodigital.requests.base-url=http://requests-test"
})
@AutoConfigureMockMvc
@Import({
        RequestCreateProcedureLabelForwardTest.TestJwtDecoderConfig.class,
        RequestCreateProcedureLabelForwardTest.RequestsClientStub.class
})
class RequestCreateProcedureLabelForwardTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    CatalogProxyService catalogProxyService;

    @BeforeEach
    void resetServer() {
        RequestsClientStub.server.reset();
    }

    @Test
    void postReenviaElLabelConTildeCodificado() throws Exception {
        when(catalogProxyService.procedures()).thenReturn(List.of(
                Map.of("value", "alumbrado", "label", "Alumbrado público", "active", true)
        ));
        RequestsClientStub.server.expect(requestTo("http://requests-test/api/requests"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "oid-1"))
                .andExpect(header("X-Procedure-Label", "Alumbrado%20p%C3%BAblico"))
                .andExpect(content().json("""
                        {"description":"Poste sin luz","procedureType":"alumbrado","address":"Calle 1"}
                        """, true))
                .andExpect(content().string(not(containsString("procedureLabel"))))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"abc\",\"status\":\"INGRESADO\"}"));

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Poste sin luz","procedureType":"alumbrado","address":"Calle 1"}
                                """)
                        .with(vecino()))
                .andExpect(status().isCreated());

        RequestsClientStub.server.verify();
    }

    @Test
    void postSinLabelEnCatalogNoMandaElHeader() throws Exception {
        Map<String, Object> sinLabel = new HashMap<>();
        sinLabel.put("value", "bache");
        sinLabel.put("active", true);
        when(catalogProxyService.procedures()).thenReturn(List.of(sinLabel));
        RequestsClientStub.server.expect(requestTo("http://requests-test/api/requests"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "oid-1"))
                .andExpect(headerDoesNotExist("X-Procedure-Label"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"abc\"}"));

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Hueco","procedureType":"bache","address":"Calle 1"}
                                """)
                        .with(vecino()))
                .andExpect(status().isCreated());

        RequestsClientStub.server.verify();
    }

    @Test
    void laCodificacionUsaPorcientoVeinteYVuelveIgualConUrlDecoder() {
        String encoded = RequestsController.encodeProcedureLabel("Agua + alcantarillado ñ");

        assertEquals("Agua%20%2B%20alcantarillado%20%C3%B1", encoded);
        assertEquals("Agua + alcantarillado ñ", URLDecoder.decode(encoded, StandardCharsets.UTF_8));
        assertNull(RequestsController.encodeProcedureLabel(null));
        assertNull(RequestsController.encodeProcedureLabel("   "));
    }

    private static RequestPostProcessor vecino() {
        return jwt()
                .jwt(token -> token
                        .claim("oid", "oid-1")
                        .claim("roles", List.of("Vecino")))
                .authorities(new SimpleGrantedAuthority("ROLE_Vecino"));
    }

    @TestConfiguration
    static class RequestsClientStub {
        static MockRestServiceServer server;

        @Bean(name = "requestsRestClient")
        @Primary
        RestClient requestsRestClient() {
            RestClient.Builder builder = RestClient.builder();
            server = MockRestServiceServer.bindTo(builder).build();
            return builder.baseUrl("http://requests-test").build();
        }
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
