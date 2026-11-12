package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.AuditProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Proxy de audit (EP3-44). Solo Admin y Auditor.
 * Downstream solo recibe {@code X-User-Id} y {@code X-User-Roles}.
 */
@RestController
public class AuditController {

    private final AuditProxyService auditProxyService;

    public AuditController(AuditProxyService auditProxyService) {
        this.auditProxyService = auditProxyService;
    }

    @RequestMapping("/api/audit/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, Authentication authentication)
            throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        return auditProxyService.forward(request, body, CatalogCaller.identity(authentication));
    }
}
