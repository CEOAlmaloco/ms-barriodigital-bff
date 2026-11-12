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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

import java.net.ConnectException;

class ReportProxyServiceTest {

    private MockRestServiceServer server;
    private ReportProxyService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new ReportProxyService(builder.baseUrl("http://report-test").build());
    }

    @Test
    void reenviaSoloUserIdYRolesSinJwtNiTokenInterno() {
        server.expect(requestTo("http://report-test/api/report/sla?assignee=me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(CatalogCaller.HEADER_USER_ID, "func-1"))
                .andExpect(header(CatalogCaller.HEADER_USER_ROLES, "Funcionario"))
                .andExpect(request -> assertNull(request.getHeaders().getFirst(CatalogCaller.HEADER_USER_NAME)))
                .andExpect(request -> assertNull(request.getHeaders().getFirst(DownstreamHeaders.HEADER_INTERNAL_TOKEN)))
                .andExpect(request -> assertNull(request.getHeaders().getFirst("Authorization")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/report/sla");
        request.setQueryString("assignee=me");

        service.forward(request, new byte[0], new CatalogCaller.Identity("func-1", "Funcionario", "Ana"));

        server.verify();
    }

    @Test
    void servicioCaidoResponde503() {
        server.expect(requestTo("http://report-test/api/report/kpis"))
                .andRespond(withException(new ConnectException("refused")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/report/kpis");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.forward(request, new byte[0], new CatalogCaller.Identity("admin-1", "Admin", null)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertEquals(ReportProxyService.UNAVAILABLE, ex.getReason());
    }
}
