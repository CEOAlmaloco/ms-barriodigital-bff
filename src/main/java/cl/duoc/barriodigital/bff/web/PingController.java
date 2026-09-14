package cl.duoc.barriodigital.bff.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PingController {

    @GetMapping("/ping")
    public Map<String, Object> ping(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "ms-barriodigital-bff");
        body.put("status", "ok");
        body.put("message", "BFF BarrioDigital arriba");

        if (jwt != null) {
            body.put("subject", jwt.getSubject());
            body.put("roles", jwt.getClaimAsStringList("roles"));
        } else {
            body.put("auth", "perfil-dev (sin JWT)");
        }
        return body;
    }
}
