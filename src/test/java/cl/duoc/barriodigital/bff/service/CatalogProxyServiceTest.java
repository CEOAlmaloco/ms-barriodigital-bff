package cl.duoc.barriodigital.bff.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        ResponseEntity<Object> response = service.list();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
        assertEquals("bache", body.get(0).get("value"));
        server.verify();
    }

    @Test
    void listConservaElObjetoDeError() {
        server.expect(requestTo("http://catalog-test/api/catalog/procedures"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":500,\"message\":\"Oracle no responde\"}"));

        ResponseEntity<Object> response = service.list();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(500, ((Number) body.get("status")).intValue());
        assertEquals("Oracle no responde", body.get("message"));
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

    @Test
    void proceduresDevuelveLosTiposParaValidar() {
        server.expect(requestTo("http://catalog-test/api/catalog/procedures"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "[{\"value\":\"bache\",\"label\":\"Bache en calle\",\"active\":true,\"dailyQuota\":10}]",
                        MediaType.APPLICATION_JSON));

        List<Map<String, Object>> procedures = service.procedures();

        assertEquals(1, procedures.size());
        assertEquals("bache", procedures.get(0).get("value"));
        assertEquals(true, procedures.get(0).get("active"));
        server.verify();
    }

    @Test
    void proceduresConCatalogEn500Responde503() {
        server.expect(requestTo("http://catalog-test/api/catalog/procedures"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":500,\"message\":\"Oracle no responde\"}"));

        assertCatalogUnavailable();
        server.verify();
    }

    @Test
    void proceduresConCatalogEn404Responde503() {
        server.expect(requestTo("http://catalog-test/api/catalog/procedures"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertCatalogUnavailable();
        server.verify();
    }

    @Test
    void proceduresSinConexionResponde503() {
        server.expect(requestTo("http://catalog-test/api/catalog/procedures"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        assertCatalogUnavailable();
        server.verify();
    }

    @Test
    void proceduresConTimeoutResponde503() {
        server.expect(requestTo("http://catalog-test/api/catalog/procedures"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        assertCatalogUnavailable();
        server.verify();
    }

    @Test
    void proceduresConCuerpoIlegibleResponde503() {
        server.expect(requestTo("http://catalog-test/api/catalog/procedures"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"no\":\"es una lista\"}", MediaType.APPLICATION_JSON));

        assertCatalogUnavailable();
        server.verify();
    }

    @Test
    void proceduresConCuerpoVacioResponde503() {
        server.expect(requestTo("http://catalog-test/api/catalog/procedures"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess());

        assertCatalogUnavailable();
        server.verify();
    }

    private void assertCatalogUnavailable() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.procedures());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertEquals(CatalogProxyService.CATALOG_UNAVAILABLE, ex.getReason());
    }
}
