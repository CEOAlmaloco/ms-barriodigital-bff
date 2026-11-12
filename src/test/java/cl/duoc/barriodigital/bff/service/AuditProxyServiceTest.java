package cl.duoc.barriodigital.bff.service;

import cl.duoc.barriodigital.bff.web.CatalogCaller;
import cl.duoc.barriodigital.bff.web.DownstreamHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuditProxyServiceTest {

    private MockRestServiceServer server;
    private AuditProxyService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new AuditProxyService(builder.baseUrl("http://audit-test").build());
    }

    @Test
    void reenviaSoloUserIdYRoles() {
        server.expect(requestTo("http://audit-test/api/audit/events?actorRole=Admin"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(CatalogCaller.HEADER_USER_ID, "auditor-1"))
                .andExpect(header(CatalogCaller.HEADER_USER_ROLES, "Auditor"))
                .andExpect(request -> assertNull(request.getHeaders().getFirst(DownstreamHeaders.HEADER_INTERNAL_TOKEN)))
                .andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/audit/events");
        request.setQueryString("actorRole=Admin");

        service.forward(request, new byte[0], new CatalogCaller.Identity("auditor-1", "Auditor", "Aud"));

        server.verify();
    }

    @Test
    void servicioCaidoResponde503() {
        server.expect(requestTo("http://audit-test/api/audit/timeline"))
                .andRespond(withException(new ConnectException("refused")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/audit/timeline");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.forward(request, new byte[0], new CatalogCaller.Identity("admin-1", "Admin", null)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertEquals(AuditProxyService.UNAVAILABLE, ex.getReason());
    }
}
