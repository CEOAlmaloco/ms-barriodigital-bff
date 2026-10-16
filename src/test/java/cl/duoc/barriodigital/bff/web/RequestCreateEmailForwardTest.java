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
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EP2-20: el POST reenvía el email del JWT en X-User-Email. El body no puede cambiarlo.
 */
@SpringBootTest(properties = {
        "barriodigital.security.jwt.entra-decoder=false",
        "spring.main.allow-bean-definition-overriding=true",
        "barriodigital.requests.base-url=http://requests-test"
})
@AutoConfigureMockMvc
@Import({
        RequestCreateEmailForwardTest.TestJwtDecoderConfig.class,
        RequestCreateEmailForwardTest.RequestsClientStub.class
})
class RequestCreateEmailForwardTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    CatalogProxyService catalogProxyService;

    @BeforeEach
    void catalogActivo() {
        when(catalogProxyService.procedures()).thenReturn(List.of(
                Map.of("value", "bache", "label", "Bache en calle", "active", true)
        ));
        RequestsClientStub.server.reset();
    }

    @Test
    void postReenviaElEmailDelTokenEIgnoraElDelBody() throws Exception {
        RequestsClientStub.server.expect(requestTo("http://requests-test/api/requests"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "oid-1"))
                .andExpect(header("X-User-Roles", "Vecino"))
                .andExpect(header("X-User-Email", "vecino@example.com"))
                .andExpect(content().string(not(containsString("falso@example.com"))))
                .andExpect(content().string(not(containsString("solicitanteEmail"))))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"abc\",\"status\":\"INGRESADO\"}"));

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Hueco","procedureType":"bache","address":"Calle 1","solicitanteEmail":"falso@example.com"}
                                """)
                        .with(jwt()
                                .jwt(token -> token
                                        .claim("oid", "oid-1")
                                        .claim("roles", List.of("Vecino"))
                                        .claim("email", "vecino@example.com")
                                        .claim("preferred_username", "otro@example.com"))
                                .authorities(new SimpleGrantedAuthority("ROLE_Vecino"))))
                .andExpect(status().isCreated());

        RequestsClientStub.server.verify();
    }

    @Test
    void postSinClaimEmailUsaPreferredUsername() throws Exception {
        RequestsClientStub.server.expect(requestTo("http://requests-test/api/requests"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Email", "alex@duoc.cl"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"abc\"}"));

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Hueco","procedureType":"bache","address":"Calle 1","email":"falso@example.com"}
                                """)
                        .with(jwt()
                                .jwt(token -> token
                                        .claim("oid", "oid-1")
                                        .claim("roles", List.of("Vecino"))
                                        .claim("preferred_username", "alex@duoc.cl"))
                                .authorities(new SimpleGrantedAuthority("ROLE_Vecino"))))
                .andExpect(status().isCreated());

        RequestsClientStub.server.verify();
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
