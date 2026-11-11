package cl.duoc.barriodigital.bff.service;

import cl.duoc.barriodigital.bff.config.InternalTokenProperties;
import cl.duoc.barriodigital.bff.web.CatalogCaller;
import cl.duoc.barriodigital.bff.web.DownstreamHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KafkaAdminProxyServiceTest {

    private MockRestServiceServer server;
    private KafkaAdminProxyService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        InternalTokenProperties token = new InternalTokenProperties();
        token.setInternalToken("secret-interno");
        service = new KafkaAdminProxyService(builder.baseUrl("http://kafka-test").build(), token);
    }

    @Test
    void reenviaTokenInternoEIpDelGateway() {
        server.expect(requestTo("http://kafka-test/api/kafka/topics"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(DownstreamHeaders.HEADER_INTERNAL_TOKEN, "secret-interno"))
                .andExpect(header(DownstreamHeaders.HEADER_SOURCE_IP, "203.0.113.25"))
                .andExpect(request -> assertNull(request.getHeaders().getFirst("X-Forwarded-For")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/kafka/topics");
        request.addHeader(DownstreamHeaders.HEADER_SOURCE_IP, "203.0.113.25");
        request.addHeader("X-Forwarded-For", "9.9.9.9");

        service.forward(request, new byte[0], new CatalogCaller.Identity("admin-1", "Admin", "Admin"));

        server.verify();
    }

    @Test
    void sinIpDelGatewayNoMandaHeaderDeIp() {
        server.expect(requestTo("http://kafka-test/api/kafka/topics"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(DownstreamHeaders.HEADER_INTERNAL_TOKEN, "secret-interno"))
                .andExpect(request -> assertNull(request.getHeaders().getFirst(DownstreamHeaders.HEADER_SOURCE_IP)))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/kafka/topics");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.addHeader("X-Forwarded-For", "9.9.9.9");

        service.forward(
                request,
                "{}".getBytes(StandardCharsets.UTF_8),
                new CatalogCaller.Identity("admin-1", "Admin", "Admin")
        );

        server.verify();
    }
}
