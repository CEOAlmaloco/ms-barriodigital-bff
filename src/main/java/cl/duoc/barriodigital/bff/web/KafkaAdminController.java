package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.KafkaAdminProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Expone la administración de Kafka. Solo Admin. kafka-admin recibe identidad, token interno e IP; nunca el JWT.
 */
@RestController
public class KafkaAdminController {

    private final KafkaAdminProxyService kafkaAdminProxyService;

    public KafkaAdminController(KafkaAdminProxyService kafkaAdminProxyService) {
        this.kafkaAdminProxyService = kafkaAdminProxyService;
    }

    /** Reenvía cualquier petición bajo {@code /api/kafka}. */
    @RequestMapping("/api/kafka/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, Authentication authentication)
            throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        return kafkaAdminProxyService.forward(request, body, CatalogCaller.identity(authentication));
    }
}
