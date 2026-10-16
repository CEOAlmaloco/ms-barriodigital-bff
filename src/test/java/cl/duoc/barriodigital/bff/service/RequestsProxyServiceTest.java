package cl.duoc.barriodigital.bff.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RequestsProxyServiceTest {

    private MockRestServiceServer server;
    private RequestsProxyService service;
    private RequestStatusOrchestrationService orchestration;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("http://requests-test").build();
        service = new RequestsProxyService(client);
        orchestration = new RequestStatusOrchestrationService(service,
                new CatalogProxyService(builder.baseUrl("http://catalog-test").build()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "rejected", "uncertain", "unreadable"})
    void admisionCoordinaProxiesHttpConOrdenYCuerposReales(String outcome) {
        server.expect(requestTo("http://requests-test/api/requests/id-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Id", "func-1"))
                .andExpect(header("X-User-Roles", "Funcionario"))
                .andRespond(withSuccess("{\"id\":\"id-1\",\"procedureType\":\"bache\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://catalog-test/api/catalog/procedures/bache/reserve"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().string(""))
                .andRespond(withSuccess("{ilegible", MediaType.APPLICATION_JSON));
        int status = outcome.equals("rejected") ? 409 : outcome.equals("uncertain") ? 500 : 200;
        String responseBody = outcome.equals("unreadable") ? "{ilegible"
                : outcome.equals("rejected") ? """
                    {"status":409,"message":"No se puede pasar de ADMITIDO a ADMITIDO","path":"/api/requests/id-1/status"}
                    """ : "{\"id\":\"id-1\",\"status\":\"ADMITIDO\"}";
        server.expect(requestTo("http://requests-test/api/requests/id-1/status"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-User-Id", "func-1"))
                .andExpect(header("X-User-Roles", "Funcionario"))
                .andExpect(content().json("{\"status\":\" ADMITIDO \"}"))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON).body(responseBody));
        if (outcome.equals("rejected")) {
            server.expect(requestTo("http://catalog-test/api/catalog/procedures/bache/release"))
                    .andExpect(method(HttpMethod.POST)).andExpect(content().string(""))
                    .andRespond(withSuccess("{ilegible", MediaType.APPLICATION_JSON));
        }
        if (outcome.equals("success") || outcome.equals("rejected")) {
            assertEquals(status, orchestration.updateStatus("id-1", Map.of("status", " ADMITIDO "),
                    "func-1", "Funcionario").getStatusCode().value());
        } else {
            AdmissionException error = assertThrows(AdmissionException.class, () -> orchestration.updateStatus(
                    "id-1", Map.of("status", " ADMITIDO "), "func-1", "Funcionario"));
            assertEquals(outcome.equals("unreadable") ? "ADMISSION_RESPONSE_UNAVAILABLE"
                    : "ADMISSION_RESULT_UNCONFIRMED", error.code());
        }
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 409, 500})
    void admisionConservaHttpAunqueElCuerpoSeaIlegible(int status) {
        server.expect(requestTo("http://requests-test/api/requests/id-1/status"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-User-Id", "func-1"))
                .andExpect(header("X-User-Roles", "Funcionario"))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON).body("{ilegible"));
        var result = service.updateStatusForAdmission("id-1", Map.of("status", "ADMITIDO"), "func-1", "Funcionario");
        assertEquals(status, result.response().getStatusCode().value());
        assertFalse(result.readable());
        server.verify();
    }

    @Test
    void createReenviaHeadersDeIdentidad() {
        server.expect(requestTo("http://requests-test/api/requests"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "oid-1"))
                .andExpect(header("X-User-Roles", "Vecino"))
                .andExpect(header("X-User-Name", "Alex Ampuero"))
                .andExpect(header("X-User-Email", "vecino@example.com"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"abc\",\"status\":\"INGRESADO\"}"));

        ResponseEntity<Map<String, Object>> response = service.create(
                Map.of("description", "Hueco", "procedureType", "bache", "address", "Calle 1"),
                "oid-1",
                "Vecino",
                "Alex Ampuero",
                "vecino@example.com"
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        server.verify();
    }

    @Test
    void transitionsReenviaElMapa() {
        server.expect(requestTo("http://requests-test/api/requests/meta/transitions"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"INGRESADO\":[\"ADMITIDO\",\"RECHAZADO\"]}",
                        MediaType.APPLICATION_JSON));

        ResponseEntity<Map<String, List<String>>> response = service.transitions();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("ADMITIDO", "RECHAZADO"), response.getBody().get("INGRESADO"));
        server.verify();
    }

    @Test
    void getByIdReenvia404() {
        server.expect(requestTo("http://requests-test/api/requests/no-existe"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Id", "oid-1"))
                .andExpect(header("X-User-Roles", "Vecino"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":404}"));

        ResponseEntity<Map<String, Object>> response =
                service.getById("no-existe", "oid-1", "Vecino");

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

    @Test
    void updateStatusReenviaHeadersY200() {
        server.expect(requestTo("http://requests-test/api/requests/id-1/status"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-User-Id", "func-1"))
                .andExpect(header("X-User-Roles", "Funcionario"))
                .andRespond(withSuccess("{\"id\":\"id-1\",\"status\":\"ADMITIDO\"}", MediaType.APPLICATION_JSON));

        ResponseEntity<Map<String, Object>> response = service.updateStatus(
                "id-1",
                Map.of("status", "ADMITIDO"),
                "func-1",
                "Funcionario"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ADMITIDO", response.getBody().get("status"));
        server.verify();
    }

    @Test
    void updateStatusPropaga409() {
        server.expect(requestTo("http://requests-test/api/requests/id-1/status"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":409,\"message\":\"No se puede pasar de INGRESADO a EN_TERRENO\"}"));

        ResponseEntity<Map<String, Object>> response = service.updateStatus(
                "id-1",
                Map.of("status", "EN_TERRENO"),
                "func-1",
                "Funcionario"
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        server.verify();
    }

    @Test
    void updateStatusPropaga404() {
        server.expect(requestTo("http://requests-test/api/requests/no-existe/status"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":404}"));

        ResponseEntity<Map<String, Object>> response = service.updateStatus(
                "no-existe",
                Map.of("status", "ADMITIDO"),
                "func-1",
                "Funcionario"
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        server.verify();
    }
}
