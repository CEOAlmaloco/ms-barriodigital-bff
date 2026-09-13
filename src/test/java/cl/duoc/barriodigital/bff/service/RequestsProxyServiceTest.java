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
    void createReenvia201ConBody() {
        server.expect(requestTo("http://requests-test/api/requests"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"abc\",\"status\":\"INGRESADO\",\"title\":\"Bache\"}"));

        ResponseEntity<Map<String, Object>> response = service.create(Map.of(
                "title", "Bache",
                "description", "Hueco",
                "procedureType", "bache"
        ));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("abc", response.getBody().get("id"));
        assertEquals("INGRESADO", response.getBody().get("status"));
        server.verify();
    }

    @Test
    void getByIdReenvia404SinConvertirloEn500() {
        server.expect(requestTo("http://requests-test/api/requests/no-existe"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":404,\"title\":\"Not Found\"}"));

        ResponseEntity<Map<String, Object>> response = service.getById("no-existe");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, ((Number) response.getBody().get("status")).intValue());
        server.verify();
    }

    @Test
    void listReenviaFiltroStatus() {
        server.expect(requestTo("http://requests-test/api/requests?status=INGRESADO"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "[{\"id\":\"1\",\"status\":\"INGRESADO\"}]",
                        MediaType.APPLICATION_JSON));

        ResponseEntity<List<Map<String, Object>>> response = service.list("INGRESADO", null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        server.verify();
    }
}
