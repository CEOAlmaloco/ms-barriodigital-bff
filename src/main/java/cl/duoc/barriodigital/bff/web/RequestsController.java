package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.RequestsProxyService;
import cl.duoc.barriodigital.bff.web.dto.CreateRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orquesta trámites. Extrae oid/sub y roles del JWT y los manda a requests por header.
 */
@RestController
@RequestMapping("/api/requests")
public class RequestsController {

    private final RequestsProxyService requestsProxyService;

    public RequestsController(RequestsProxyService requestsProxyService) {
        this.requestsProxyService = requestsProxyService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody CreateRequestDto body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("description", body.description());
        payload.put("procedureType", body.procedureType());
        payload.put("address", body.address());
        return requestsProxyService.create(payload, resolveUserId(jwt), resolveRoles(jwt));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return requestsProxyService.getById(id, resolveUserId(jwt), resolveRoles(jwt));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return requestsProxyService.list(status, from, to, resolveUserId(jwt), resolveRoles(jwt));
    }

    /**
     * Preferir oid de Entra; si no viene, usar sub.
     * En perfil dev sin JWT usa un usuario de prueba.
     */
    static String resolveUserId(Jwt jwt) {
        if (jwt == null) {
            return "dev-user";
        }
        String oid = jwt.getClaimAsString("oid");
        if (oid != null && !oid.isBlank()) {
            return oid;
        }
        String sub = jwt.getSubject();
        if (sub != null && !sub.isBlank()) {
            return sub;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT sin oid/sub");
    }

    static String resolveRoles(Jwt jwt) {
        if (jwt == null) {
            return "Vecino";
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) {
            return "";
        }
        return roles.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.joining(","));
    }
}
