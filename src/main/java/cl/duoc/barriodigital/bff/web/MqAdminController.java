package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.MqAdminProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Administración de colas vía ms-barriodigital-mq-admin (EP2-26).
 * El JWT se valida acá y solo Admin pasa. mq-admin recibe {@code X-User-Roles}, nunca el token.
 */
@RestController
public class MqAdminController {

    private final MqAdminProxyService mqAdminProxyService;

    public MqAdminController(MqAdminProxyService mqAdminProxyService) {
        this.mqAdminProxyService = mqAdminProxyService;
    }

    @RequestMapping("/api/mq/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, Authentication authentication) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        return mqAdminProxyService.forward(request, body, CatalogCaller.rolesHeader(authentication));
    }
}
