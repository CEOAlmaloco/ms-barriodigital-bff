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

class CatalogProxyServiceTest {

    private MockRestServiceServer server;
    private CatalogProxyService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("http://catalog-test").build();
        service = new CatalogProxyService(client);
    }

    @Test
    void listReenviaLosTipos() {
        server.expect(requestTo("http://catalog-test/api/catalog/procedures"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "[{\"value\":\"bache\",\"label\":\"Bache en calle\",\"active\":true}]",
                        MediaType.APPLICATION_JSON));

        ResponseEntity<List<Map<String, Object>>> response = service.list();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("bache", response.getBody().get(0).get("value"));
        server.verify();
    }

    @Test
    void createReenvia201YElRolDelToken() {
        server.expect(requestTo("http://catalog-test/api/catalog/procedures"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Roles", "Admin"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"value\":\"semaforo\",\"label\":\"Semáforo\",\"active\":true}"));

        ResponseEntity<Map<String, Object>> response = service.create(
                Map.of("value", "semaforo", "label", "Semáforo", "active", true),
                "Admin"
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("semaforo", response.getBody().get("value"));
        server.verify();
    }

    @Test
    void updateReenviaElCodigoEnLaUrl() {
        server.expect(requestTo("http://catalog-test/api/catalog/procedures/bache"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-User-Roles", "Admin"))
                .andRespond(withSuccess(
                        "{\"value\":\"bache\",\"label\":\"Bache grave\",\"active\":false}",
                        MediaType.APPLICATION_JSON));

        ResponseEntity<Map<String, Object>> response = service.update(
                "bache",
                Map.of("label", "Bache grave", "active", false, "dailyQuota", 4),
                "Admin"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("bache", response.getBody().get("value"));
        assertEquals("Bache grave", response.getBody().get("label"));
        server.verify();
    }

    @Test
    void conflicto409NoSeConvierteEn500() {
        server.expect(requestTo("http://catalog-test/api/catalog/procedures"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":409,\"message\":\"El codigo value ya existe\"}"));

        ResponseEntity<Map<String, Object>> response = service.create(
                Map.of("value", "bache", "label", "Bache"),
                "Admin"
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("El codigo value ya existe", response.getBody().get("message"));
        server.verify();
    }
}
