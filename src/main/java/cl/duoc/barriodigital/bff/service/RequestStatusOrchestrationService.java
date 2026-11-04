package cl.duoc.barriodigital.bff.service;

import cl.duoc.barriodigital.bff.service.AdmissionException.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * Coordina el cambio de estado: si el destino es ADMITIDO, reserva cupo en catalog y después pide el cambio a requests; si requests lo rechaza, intenta devolver el cupo.
 * Los demás estados se reenvían directo, sin cupo. Es síncrona, sin transacción distribuida, reintentos ni recuperación durable.
 */
@Service
public class RequestStatusOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(RequestStatusOrchestrationService.class);
    private final RequestsProxyService requests;
    private final CatalogProxyService catalog;

    /** Recibe el proxy de requests y el de catalog. */
    public RequestStatusOrchestrationService(RequestsProxyService requests, CatalogProxyService catalog) {
        this.requests = requests;
        this.catalog = catalog;
    }

    /**
     * Cambia el estado. Si no es ADMITIDO, solo reenvía a requests.
     * Si es ADMITIDO, exige rol Funcionario y un usuario (403 y 401 si faltan), reserva el cupo y guarda el cambio.
     */
    public ResponseEntity<Map<String, Object>> updateStatus(
            String id, Map<String, Object> payload, String userId, String roles
    ) {
        return updateStatus(id, payload, userId, roles, null);
    }

    /**
     * Igual que {@link #updateStatus(String, Map, String, String)}.
     * Si el nombre viene, requests lo guarda en el historial del cambio de estado.
     */
    public ResponseEntity<Map<String, Object>> updateStatus(
            String id, Map<String, Object> payload, String userId, String roles, String userName
    ) {
        Object rawStatus = payload.get("status");
        if (!(rawStatus instanceof String targetStatus) || !"ADMITIDO".equals(targetStatus.trim())) {
            if (userName == null || userName.isBlank()) {
                return requests.updateStatus(id, payload, userId, roles);
            }
            return requests.updateStatus(id, payload, userId, roles, userName.trim());
        }
        // También protege dev: nunca le asigna Funcionario al caller sin JWT.
        if (roles == null || Arrays.stream(roles.split(","))
                .map(String::trim)
                .noneMatch(role -> "Funcionario".equals(role) || "ROLE_Funcionario".equals(role))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un Funcionario puede cambiar el estado");
        }
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT sin oid/sub");
        }

        String attempt = UUID.randomUUID().toString();
        ResponseEntity<Map<String, Object>> found;
        try {
            found = requests.getById(id, userId, roles);
        } catch (ResourceAccessException ex) {
            throw failure(Code.ADMISSION_DEPENDENCY_UNAVAILABLE, "lookup", attempt, id, null, null);
        } catch (RestClientException ex) {
            throw failure(Code.ADMISSION_DEPENDENCY_INVALID_RESPONSE, "lookup", attempt, id, null, null);
        }
        if (recognizedRejection(found, "/api/requests/" + id)) {
            return found;
        }
        if (found.getStatusCode().is5xxServerError()) {
            throw failure(Code.ADMISSION_DEPENDENCY_UNAVAILABLE, "lookup", attempt, id, null,
                    found.getStatusCode().value());
        }
        Map<String, Object> resource = found.getBody();
        if (found.getStatusCode().value() != 200 || resource == null
                || !id.equals(resource.get("id"))
                || !(resource.get("procedureType") instanceof String type)
                || !type.matches("[a-z0-9_-]{1,40}")) {
            throw failure(Code.ADMISSION_DEPENDENCY_INVALID_RESPONSE, "lookup", attempt, id, null,
                    found.getStatusCode().value());
        }

        int reserveStatus;
        try {
            reserveStatus = catalog.reserve(type).value();
        } catch (RestClientException ex) {
            throw failure(Code.QUOTA_RESERVATION_UNCONFIRMED, "reserve", attempt, id, type, null);
        }
        if (reserveStatus == 409) {
            throw failure(Code.QUOTA_EXHAUSTED, "reserve", attempt, id, type, reserveStatus);
        }
        if (reserveStatus == 404) {
            throw failure(Code.PROCEDURE_TYPE_UNAVAILABLE, "reserve", attempt, id, type, reserveStatus);
        }
        if (reserveStatus != 200) {
            throw failure(Code.QUOTA_RESERVATION_UNCONFIRMED, "reserve", attempt, id, type, reserveStatus);
        }

        RequestsProxyService.StatusChangeResult changed;
        try {
            changed = userName == null || userName.isBlank()
                    ? requests.updateStatusForAdmission(id, payload, userId, roles)
                    : requests.updateStatusForAdmission(id, payload, userId, roles, userName.trim());
        } catch (RestClientException ex) {
            throw failure(Code.ADMISSION_RESULT_UNCONFIRMED, "status", attempt, id, type, null);
        }
        ResponseEntity<Map<String, Object>> response = changed.response();
        int status = response.getStatusCode().value();
        if (status == 200) {
            if (!changed.readable() || response.getBody() == null
                    || !id.equals(response.getBody().get("id"))
                    || !"ADMITIDO".equals(response.getBody().get("status"))) {
                throw failure(Code.ADMISSION_RESPONSE_UNAVAILABLE, "status", attempt, id, type, status);
            }
            return response;
        }
        if (!changed.readable() || !recognizedRejection(response, "/api/requests/" + id + "/status")) {
            throw failure(Code.ADMISSION_RESULT_UNCONFIRMED, "status", attempt, id, type, status);
        }

        // Solo rechazos controlados de la operación esperada demuestran que este intento no guardó.
        log.warn("admission attempt={} request={} type={} phase=status http={} category=REJECTED compensation=pending",
                attempt, safeId(id), type, status);
        int releaseStatus;
        try {
            releaseStatus = catalog.release(type).value();
        } catch (RestClientException ex) {
            throw failure(Code.QUOTA_COMPENSATION_UNCONFIRMED, "release", attempt, id, type, null);
        }
        if (releaseStatus != 200) {
            throw failure(Code.QUOTA_COMPENSATION_UNCONFIRMED, "release", attempt, id, type, releaseStatus);
        }
        log.info("admission attempt={} request={} type={} phase=release http=200 originalHttp={} compensation=confirmed",
                attempt, safeId(id), type, status);
        return response;
    }

    /** Reconoce un rechazo esperado de requests (400, 403, 404 o 409) para saber que este intento no se guardó. */
    private static boolean recognizedRejection(ResponseEntity<Map<String, Object>> response, String path) {
        Map<String, Object> body = response.getBody();
        int status = response.getStatusCode().value();
        if (body == null || !(body.get("status") instanceof Number bodyStatus)
                || bodyStatus.intValue() != status || !path.equals(body.get("path"))
                || !(body.get("message") instanceof String message)) {
            return false;
        }
        return switch (status) {
            case 400 -> message.equals("status es obligatorio")
                    || message.equals("status invalido. Valores: INGRESADO, ADMITIDO, EN_GESTION, EN_TERRENO, RESUELTO, RECHAZADO")
                    || message.equals("rejectionReason es obligatorio cuando el estado es RECHAZADO")
                    || message.equals("rejectionReason no puede superar 500 caracteres")
                    || message.equals("Falta identidad del usuario (header X-User-Id). Debe venir del BFF tras validar el JWT.");
            case 403 -> message.equals("Solo un Funcionario puede cambiar el estado");
            case 404 -> message.equals("Tramite no encontrado");
            case 409 -> message.matches("No se puede pasar de (INGRESADO|ADMITIDO|EN_GESTION|EN_TERRENO|RESUELTO|RECHAZADO) a ADMITIDO");
            default -> false;
        };
    }

    /** Registra el fallo y lo convierte en un error público, sin detalles internos. */
    private static AdmissionException failure(
            Code code, String phase, String attempt, String id, String type, Integer httpStatus
    ) {
        log.warn("admission attempt={} request={} type={} phase={} http={} category={} compensation={}",
                attempt, safeId(id), type, phase, httpStatus, code,
                "release".equals(phase) ? "unconfirmed" : "not-attempted");
        return new AdmissionException(code);
    }

    /** Sustituye saltos de línea del id para que no alteren el log. */
    private static String safeId(String id) {
        return id.replaceAll("[\\r\\n\\t]", "_");
    }
}
