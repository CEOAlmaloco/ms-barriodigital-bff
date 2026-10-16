package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.ProcedureTypeValidator;
import cl.duoc.barriodigital.bff.service.RequestsProxyService;
import cl.duoc.barriodigital.bff.service.RequestStatusOrchestrationService;
import cl.duoc.barriodigital.bff.web.dto.ChangeStatusDto;
import cl.duoc.barriodigital.bff.web.dto.CreateRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
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
    private final ProcedureTypeValidator procedureTypeValidator;
    private final RequestStatusOrchestrationService statusOrchestration;

    public RequestsController(
            RequestsProxyService requestsProxyService,
            ProcedureTypeValidator procedureTypeValidator,
            RequestStatusOrchestrationService statusOrchestration
    ) {
        this.requestsProxyService = requestsProxyService;
        this.procedureTypeValidator = procedureTypeValidator;
        this.statusOrchestration = statusOrchestration;
    }

    /**
     * El tipo se valida contra catalog antes de llamar a requests.
     * 400 si no existe o está inactivo, 503 si catalog no responde. En esos casos requests no se llama.
     * El label del tipo viaja en X-Procedure-Label para el snapshot. Sin label en catalog no se manda.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody CreateRequestDto body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String procedureLabel = procedureTypeValidator.requireActive(body.procedureType());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("description", body.description());
        payload.put("procedureType", body.procedureType());
        payload.put("address", body.address());
        return requestsProxyService.create(
                payload,
                resolveUserId(jwt),
                resolveRoles(jwt),
                resolveUserName(jwt),
                resolveUserEmail(jwt),
                encodeProcedureLabel(procedureLabel)
        );
    }

    /**
     * Un header no lleva tildes de forma segura, así que el label va codificado en URL (UTF-8).
     * UriUtils codifica el espacio como %20 y el + como %2B; requests lo lee con URLDecoder.
     */
    static String encodeProcedureLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        return UriUtils.encode(label, StandardCharsets.UTF_8);
    }

    @GetMapping("/meta/transitions")
    public ResponseEntity<Map<String, List<String>>> transitions() {
        return requestsProxyService.transitions();
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
     * EP1.5-06. 403 de no-Funcionario lo corta AzureSecurityConfig (no llega acá).
     * EP1.5-18: el servicio coordina cupo solo para ADMITIDO.
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody ChangeStatusDto body,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", body.status());
        payload.put("rejectionReason", body.rejectionReason());
        // Las autoridades son las mismas que autorizó Security, incluidos role y ROLE_*.
        String roles = jwt == null ? resolveRoles(null) : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                .collect(Collectors.joining(","));
        return statusOrchestration.updateStatus(id, payload, resolveUserId(jwt), roles);
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

    /**
     * Nombre para el snapshot del alta. Primero el claim name; si no viene, preferred_username.
     * En perfil dev sin JWT usa el mismo usuario de prueba que el id.
     */
    static String resolveUserName(Jwt jwt) {
        if (jwt == null) {
            return "dev-user";
        }
        String name = readableClaim(jwt.getClaimAsString("name"));
        if (name != null) {
            return name;
        }
        String preferred = readableClaim(jwt.getClaimAsString("preferred_username"));
        return preferred == null ? "" : preferred;
    }

    /**
     * Email para el snapshot del alta. Primero el claim email; en tokens v2 de Entra a menudo no viene,
     * y entonces se usa preferred_username. No se lee del body. Sin JWT el header viaja vacío.
     */
    static String resolveUserEmail(Jwt jwt) {
        if (jwt == null) {
            return "";
        }
        String email = readableClaim(jwt.getClaimAsString("email"));
        if (email != null) {
            return email;
        }
        String preferred = readableClaim(jwt.getClaimAsString("preferred_username"));
        return preferred == null ? "" : preferred;
    }

    /** Recorta y descarta un valor vacío o con salto de línea, que no puede ir en un header. */
    private static String readableClaim(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return null;
        }
        return value;
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
