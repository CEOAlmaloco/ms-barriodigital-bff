package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.ReportProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Proxy de report (EP3-44). Roles por ruta en {@code AzureSecurityConfig} (EP3-07).
 * Downstream solo recibe {@code X-User-Id} y {@code X-User-Roles}.
 */
@RestController
public class ReportController {

    private final ReportProxyService reportProxyService;

    public ReportController(ReportProxyService reportProxyService) {
        this.reportProxyService = reportProxyService;
    }

    @RequestMapping("/api/report/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, Authentication authentication)
            throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        return reportProxyService.forward(request, body, CatalogCaller.identity(authentication));
    }
}
