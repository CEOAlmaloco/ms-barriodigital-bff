package cl.duoc.barriodigital.bff.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * EP1-13: solo rol Admin (hasRole). Vecino autenticado → 403.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/ping")
    public Map<String, Object> adminPing(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "ms-barriodigital-bff");
        body.put("status", "ok");
        body.put("message", "Área Admin — autorizado");
        if (jwt != null) {
            body.put("subject", jwt.getSubject());
            body.put("roles", jwt.getClaimAsStringList("roles"));
        }
        if (authentication != null) {
            List<String> authorities = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());
            body.put("authorities", authorities);
        }
        return body;
    }
}
