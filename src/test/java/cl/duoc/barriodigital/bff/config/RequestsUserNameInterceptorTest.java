package cl.duoc.barriodigital.bff.config;

import cl.duoc.barriodigital.bff.service.RequestsProxyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Toda llamada a requests lleva X-User-Name, también las lecturas, para que staff_member guarde el nombre real. */
class RequestsUserNameInterceptorTest {

    private static final String BASE = "http://requests-test/api/requests";
    private static final String ENCODED = "Ana%20Mar%C3%ADa%20Hu%C3%A9nchul";

    private MockRestServiceServer server;
    private RequestsProxyService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().requestInterceptor(new RequestsUserNameInterceptor());
        server = MockRestServiceServer.bindTo(builder).build();
        service = new RequestsProxyService(builder.baseUrl("http://requests-test").build());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lasLecturasLlevanElNombreCodificadoDelJwt() {
        authenticate(Map.of("oid", "oid-1", "name", "Ana María Huénchul"));
        expectGet(BASE + "/id-1", "{}");
        expectGet(BASE + "/id-1/actividad", "{}");
        expectGet(BASE + "/assignees", "[]");
        expectGet(BASE, "{\"content\":[]}");
        server.expect(requestTo(BASE + "/id-1/attachment"))
                .andExpect(header("X-User-Name", ENCODED))
                .andRespond(withSuccess(new byte[] {1}, MediaType.IMAGE_PNG));

        service.getById("id-1", "oid-1", "Funcionario");
        service.actividad("id-1", "oid-1", "Funcionario");
        service.assignees("oid-1", "Funcionario");
        service.list(null, null, null, null, null, null, null, null, null, null, null, "oid-1", "Funcionario");
        service.getAttachment("id-1", "oid-1", "Funcionario");

        server.verify();
    }

    @Test
    void sinClaimNameUsaPreferredUsername() {
        authenticate(Map.of("oid", "oid-1", "preferred_username", "ana@municipio.cl"));
        server.expect(requestTo(BASE + "/assignees"))
                .andExpect(header("X-User-Name", "ana%40municipio.cl"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        service.assignees("oid-1", "Admin");

        server.verify();
    }

    @Test
    void noCambiaElHeaderQueLaLlamadaYaPuso() {
        authenticate(Map.of("oid", "oid-1", "name", "Ana María Huénchul"));
        server.expect(requestTo(BASE + "/id-1/take"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Name", "Nombre%20Explicito"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.take("id-1", "oid-1", "Funcionario", "Nombre%20Explicito");

        server.verify();
    }

    @Test
    void sinJwtNoAgregaElHeader() {
        server.expect(requestTo(BASE + "/id-1"))
                .andExpect(headerDoesNotExist("X-User-Name"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.getById("id-1", "dev-user", "Vecino");

        server.verify();
    }

    private void expectGet(String url, String body) {
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Name", ENCODED))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static void authenticate(Map<String, Object> claims) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_Funcionario"))));
    }
}
