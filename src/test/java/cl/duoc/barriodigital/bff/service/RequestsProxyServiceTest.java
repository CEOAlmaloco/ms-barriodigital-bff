package cl.duoc.barriodigital.bff.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RequestsProxyServiceTest {

    private MockRestServiceServer server;
    private RequestsProxyService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("http://requests-test").build();
        service = new RequestsProxyService(client);
    }

    @Test
    void createReenviaHeadersDeIdentidad() {
        server.expect(requestTo("http://requests-test/api/requests"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "oid-1"))
                .andExpect(header("X-User-Roles", "Vecino"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"abc\",\"status\":\"INGRESADO\"}"));

        ResponseEntity<Map<String, Object>> response = service.create(
                Map.of("description", "Hueco", "procedureType", "bache", "address", "Calle 1"),
                "oid-1",
                "Vecino"
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        server.verify();
    }

    @Test
    void getByIdReenvia404() {
        server.expect(requestTo("http://requests-test/api/requests/no-existe"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Id", "oid-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":404}"));

        ResponseEntity<Map<String, Object>> response = service.getById("no-existe", "oid-1", "Vecino");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        server.verify();
    }

    @Test
    void listConFechaSimple() {
        server.expect(requestTo("http://requests-test/api/requests?status=INGRESADO&from=2026-09-01&to=2026-09-13"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"1\"}]", MediaType.APPLICATION_JSON));

        ResponseEntity<List<Map<String, Object>>> response =
                service.list("INGRESADO", "2026-09-01", "2026-09-13", "oid-f", "Funcionario");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        server.verify();
    }
}
