package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.ProcedureTypeValidator;
import cl.duoc.barriodigital.bff.service.RequestsProxyService;
import cl.duoc.barriodigital.bff.service.RequestStatusOrchestrationService;
import cl.duoc.barriodigital.bff.web.dto.AssignRequestDto;
import cl.duoc.barriodigital.bff.web.dto.ChangeStatusDto;
import cl.duoc.barriodigital.bff.web.dto.CreateRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
     * Label y plazo viajan en {@code X-Procedure-Label} y {@code X-Procedure-Sla-Days}; si catalog no los trae, no se mandan.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody CreateRequestDto body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return create(body, null, jwt);
    }

    /** Crea un trámite con una sola imagen opcional, reenviada por el canal privado a requests. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createWithAttachment(
            @Valid @RequestPart("request") CreateRequestDto body,
            @RequestPart(value = "attachment", required = false) MultipartFile attachment,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return create(body, attachment, jwt);
    }

    private ResponseEntity<Map<String, Object>> create(
            CreateRequestDto body,
            MultipartFile attachment,
            Jwt jwt
    ) {
        ProcedureTypeValidator.TypeSnapshot type = procedureTypeValidator.requireActive(body.procedureType());
        String procedureLabel = type == null ? null : type.label();
        Integer procedureSlaDays = type == null ? null : type.slaBusinessDays();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("description", body.description());
        payload.put("procedureType", body.procedureType());
        payload.put("address", body.address());
        putIfPresent(payload, "sector", body.sector());
        putIfPresent(payload, "locationReference", body.locationReference());
        if (body.latitude() != null) {
            payload.put("latitude", body.latitude());
        }
        if (body.longitude() != null) {
            payload.put("longitude", body.longitude());
        }
        putIfPresent(payload, "locationPrecision", body.locationPrecision());
        if (attachment != null) {
            return requestsProxyService.createWithAttachment(
                    payload,
                    attachment,
                    resolveUserId(jwt),
                    resolveRoles(jwt),
                    resolveUserName(jwt),
                    resolveUserEmail(jwt),
                    encodeProcedureLabel(procedureLabel),
                    procedureSlaDays
            );
        }
        return requestsProxyService.create(
                payload,
                resolveUserId(jwt),
                resolveRoles(jwt),
                encodeUserName(resolveUserName(jwt)),
                resolveUserEmail(jwt),
                encodeProcedureLabel(procedureLabel),
                procedureSlaDays
        );
    }

    /**
     * Codifica el label en URL (UTF-8) porque un header no transporta tildes de forma segura.
     * El espacio queda como %20 y el + como %2B; requests lo lee con URLDecoder.
     */
    static String encodeProcedureLabel(String label) {
        return CatalogCaller.encodeHeaderValue(label);
    }

    /**
     * Codifica el nombre en URL (UTF-8) porque un header no transporta tildes de forma segura.
     * Requests lo decodifica con URLDecoder, igual que el label del tipo.
     */
    static String encodeUserName(String userName) {
        return CatalogCaller.encodeHeaderValue(userName);
    }

    /** No reenvía un texto vacío: requests trata la ausencia como “no indicado”. */
    private static void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value.trim());
        }
    }

    /** Devuelve los cambios de estado permitidos. */
    @GetMapping("/meta/transitions")
    public ResponseEntity<Map<String, List<String>>> transitions() {
        return requestsProxyService.transitions();
    }

    /** Lista de funcionarios para asignar/reasignar (antes de {@code /{id}}). */
    @GetMapping("/assignees")
    public ResponseEntity<Object> assignees(@AuthenticationPrincipal Jwt jwt) {
        return requestsProxyService.assignees(resolveUserId(jwt), resolveRoles(jwt));
    }

    /** Devuelve un trámite. El id de usuario y los roles salen del JWT. */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return requestsProxyService.getById(id, resolveUserId(jwt), resolveRoles(jwt));
    }

    /** Descarga la imagen sin exponerla públicamente; requests vuelve a comprobar acceso al trámite. */
    @GetMapping("/{id}/attachment")
    public ResponseEntity<byte[]> getAttachment(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return requestsProxyService.getAttachment(id, resolveUserId(jwt), resolveRoles(jwt));
    }

    /**
     * Lista trámites paginados (EP3-30 / EP3-45).
     * Reenvía page, size, sort, q y filtros; la respuesta paginada viene de requests.
     */
    @GetMapping
    public ResponseEntity<Object> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String procedureType,
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false) String open,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return requestsProxyService.list(
                status, from, to, page, size, sort, q, procedureType, sector, assignee, open,
                resolveUserId(jwt), resolveRoles(jwt));
    }

    /**
     * Cambia el estado. Funcionario o Admin (EP3-45).
     * Si el destino es ADMITIDO, la coordinación valida motivo/responsable y reserva cupo.
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
        if (body.interventionReason() != null) {
            payload.put("interventionReason", body.interventionReason());
        }
        String roles = jwt == null ? resolveRoles(null) : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                .collect(Collectors.joining(","));
        String name = jwt == null ? null : encodeUserName(resolveUserName(jwt));
        return statusOrchestration.updateStatus(id, payload, resolveUserId(jwt), roles, name);
    }

    /** Toma un trámite sin responsable (solo Funcionario). */
    @PostMapping("/{id}/take")
    public ResponseEntity<Map<String, Object>> take(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return requestsProxyService.take(
                id,
                resolveUserId(jwt),
                resolveRoles(jwt),
                encodeUserName(resolveUserName(jwt))
        );
    }

    /** Asigna o reasigna (Admin cualquier trámite; Funcionario solo los suyos). */
    @PutMapping("/{id}/assignee")
    public ResponseEntity<Map<String, Object>> assign(
            @PathVariable String id,
            @Valid @RequestBody AssignRequestDto body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assigneeId", body.assigneeId());
        if (body.reason() != null) {
            payload.put("reason", body.reason());
        }
        return requestsProxyService.assign(
                id,
                payload,
                resolveUserId(jwt),
                resolveRoles(jwt),
                encodeUserName(resolveUserName(jwt))
        );
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
                encodeUserName(resolveUserName(jwt))
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
        String id = CatalogCaller.userId(jwt);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT sin oid/sub");
        }
        return id;
    }

    /**
     * Nombre para el snapshot del alta. Primero el claim name; si no viene, preferred_username.
     * En perfil dev, sin JWT, usa el mismo usuario de prueba que el id.
     */
    static String resolveUserName(Jwt jwt) {
        if (jwt == null) {
            return "dev-user";
        }
        String name = CatalogCaller.userName(jwt);
        return name == null ? "" : name;
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
