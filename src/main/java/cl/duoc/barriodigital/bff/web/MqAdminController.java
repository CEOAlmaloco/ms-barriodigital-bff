package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.MqAdminProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Expone la administración de colas. El JWT se valida en el BFF y solo el rol Admin pasa.
 * mq-admin recibe {@code X-User-Roles}, nunca el token.
 */
@RestController
public class MqAdminController {

    private final MqAdminProxyService mqAdminProxyService;

    /** Recibe el proxy hacia mq-admin. */
    public MqAdminController(MqAdminProxyService mqAdminProxyService) {
        this.mqAdminProxyService = mqAdminProxyService;
    }

    /** Reenvía cualquier petición bajo {@code /api/mq}, con el rol sacado del JWT. */
    @RequestMapping("/api/mq/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, Authentication authentication) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        return mqAdminProxyService.forward(request, body, CatalogCaller.rolesHeader(authentication));
    }
}
