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
 * Expone los trámites. Saca del JWT el oid o el sub, los roles y, en el alta, el nombre y el correo, y se los manda a requests por header.
 * Requests no recibe el token.
 */
@RestController
@RequestMapping("/api/requests")
public class RequestsController {

    private final RequestsProxyService requestsProxyService;
    private final ProcedureTypeValidator procedureTypeValidator;
    private final RequestStatusOrchestrationService statusOrchestration;

    /** Recibe el proxy de requests, el validador de tipos y la coordinación del cambio de estado. */
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
     * Crea un trámite después de consultar catalog: 400 si el tipo no existe o está inactivo, 503 si catalog no responde, y en esos casos requests no se llama.
     * El label viaja en {@code X-Procedure-Label} para el snapshot; si catalog no trae label, no se manda.
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
     * Codifica el label en URL (UTF-8) porque un header no transporta tildes de forma segura.
     * El espacio queda como %20 y el + como %2B; requests lo lee con URLDecoder.
     */
    static String encodeProcedureLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        return UriUtils.encode(label, StandardCharsets.UTF_8);
    }

    /** Devuelve los cambios de estado permitidos. */
    @GetMapping("/meta/transitions")
    public ResponseEntity<Map<String, List<String>>> transitions() {
        return requestsProxyService.transitions();
    }

    /** Devuelve un trámite. El id de usuario y los roles salen del JWT. */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return requestsProxyService.getById(id, resolveUserId(jwt), resolveRoles(jwt));
    }

    /** Lista trámites. Puede filtrar por estado y por fechas. El id de usuario y los roles salen del JWT. */
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
     * Cambia el estado. Fuera del perfil dev, el 403 de quien no tiene rol Funcionario lo corta la seguridad y no llega aquí.
     * Si el destino es ADMITIDO, la coordinación reserva el cupo en catalog antes de guardar el cambio.
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
        String name = jwt == null ? null : resolveUserName(jwt);
        return statusOrchestration.updateStatus(id, payload, resolveUserId(jwt), roles, name);
    }

    /** Historial del trámite. Requests oculta las gestiones si quien pide es el vecino. */
    @GetMapping("/{id}/actividad")
    public ResponseEntity<Map<String, Object>> actividad(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return requestsProxyService.actividad(id, resolveUserId(jwt), resolveRoles(jwt));
    }

    /** Registra una gestión. En producción la seguridad exige rol Funcionario antes de llegar aquí. */
    @PostMapping("/{id}/gestiones")
    public ResponseEntity<Map<String, Object>> registrarGestion(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return requestsProxyService.registrarGestion(
                id,
                body,
                resolveUserId(jwt),
                resolveRoles(jwt),
                resolveUserName(jwt)
        );
    }

    /**
     * Saca el id de la persona: prefiere el claim oid de Entra y, si no viene, sub.
     * Sin JWT, en perfil dev, usa un usuario de prueba. Si el JWT no trae oid ni sub, responde 401.
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
     * En perfil dev, sin JWT, usa el mismo usuario de prueba que el id.
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
     * Correo para el snapshot del alta: primero el claim email y, si no viene, preferred_username. En tokens v2 de Entra el email a menudo no viene.
     * No se lee del body y, sin JWT, el header viaja vacío.
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

    /** Recorta el claim y descarta un valor vacío o con salto de línea, porque eso no puede ir en un header. */
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

    /** Junta los roles del claim {@code roles} separados por comas. Sin JWT, en perfil dev, usa Vecino. */
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
