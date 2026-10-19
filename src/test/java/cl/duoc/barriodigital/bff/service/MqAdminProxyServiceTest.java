package cl.duoc.barriodigital.bff.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MqAdminProxyServiceTest {

    private MockRestServiceServer server;
    private MqAdminProxyService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new MqAdminProxyService(builder.baseUrl("http://mq-test").build());
    }

    @Test
    void postReenvia201ElCuerpoYElRolSinElToken() {
        server.expect(requestTo("http://mq-test/api/mq/queues"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Roles", "Admin"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(request -> assertNull(request.getHeaders().getFirst("Authorization")))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"q.demo\"}"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mq/queues");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.addHeader("Authorization", "Bearer no-debe-salir");

        ResponseEntity<byte[]> response = service.forward(
                request,
                "{\"name\":\"q.demo\"}".getBytes(StandardCharsets.UTF_8),
                "Admin"
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("{\"name\":\"q.demo\"}", new String(response.getBody(), StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void getReenvia200() {
        server.expect(requestTo("http://mq-test/api/mq/queues"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"name\":\"q.demo\"}]", MediaType.APPLICATION_JSON));

        ResponseEntity<byte[]> response = service.forward(
                new MockHttpServletRequest("GET", "/api/mq/queues"),
                new byte[0],
                "Admin"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("[{\"name\":\"q.demo\"}]", new String(response.getBody(), StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void deleteConservaElQueryString() {
        server.expect(requestTo(
                        "http://mq-test/api/mq/bindings?exchange=cmd.topic&queue=q.demo&routingKey=crew.ticket"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-User-Roles", "Admin"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/mq/bindings");
        request.setQueryString("exchange=cmd.topic&queue=q.demo&routingKey=crew.ticket");

        ResponseEntity<byte[]> response = service.forward(request, new byte[0], "Admin");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        server.verify();
    }

    @Test
    void errorDeNegocioSeCopia() {
        server.expect(requestTo("http://mq-test/api/mq/queues/q.demo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":404,\"message\":\"La cola no existe\"}"));

        ResponseEntity<byte[]> response = service.forward(
                new MockHttpServletRequest("GET", "/api/mq/queues/q.demo"),
                new byte[0],
                "Admin"
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(
                "{\"status\":404,\"message\":\"La cola no existe\"}",
                new String(response.getBody(), StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void errorInternoNoReenviaElCuerpoTecnico() {
        server.expect(requestTo("http://mq-test/api/mq/queues"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html>stacktrace</html>"));

        ResponseEntity<byte[]> response = service.forward(
                new MockHttpServletRequest("GET", "/api/mq/queues"),
                new byte[0],
                "Admin"
        );

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertArrayEquals(generic(502, "Bad Gateway"), response.getBody());
        server.verify();
    }

    @Test
    void sinConexionResponde503YNo502() {
        server.expect(requestTo("http://mq-test/api/mq/queues"))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.forward(
                new MockHttpServletRequest("GET", "/api/mq/queues"),
                new byte[0],
                "Admin"
        ));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertEquals(MqAdminProxyService.UNAVAILABLE, ex.getReason());
        server.verify();
    }

    @Test
    void timeoutResponde503() {
        server.expect(requestTo("http://mq-test/api/mq/queues"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.forward(
                new MockHttpServletRequest("POST", "/api/mq/queues"),
                "{\"name\":\"q.demo\"}".getBytes(StandardCharsets.UTF_8),
                "Admin"
        ));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertEquals(MqAdminProxyService.UNAVAILABLE, ex.getReason());
        server.verify();
    }

    private static byte[] generic(int status, String error) {
        return ("{\"status\":" + status + ",\"error\":\"" + error
                + "\",\"message\":\"" + MqAdminProxyService.GENERIC_ERROR + "\"}")
                .getBytes(StandardCharsets.UTF_8);
    }
}
