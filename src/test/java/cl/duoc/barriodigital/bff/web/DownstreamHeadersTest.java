package cl.duoc.barriodigital.bff.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class DownstreamHeadersTest {

    @Test
    void leeSoloXSourceIpYNuncaForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.addHeader(DownstreamHeaders.HEADER_SOURCE_IP, "203.0.113.25");

        assertThat(DownstreamHeaders.sourceIp(request)).isEqualTo("203.0.113.25");
    }

    @Test
    void sinGatewayNoHayIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(DownstreamHeaders.sourceIp(request)).isNull();
    }
}
