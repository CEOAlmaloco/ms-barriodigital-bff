package cl.duoc.barriodigital.bff.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodySpec;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reenvía los trámites a requests.
 * Manda {@code X-User-Id} y {@code X-User-Roles} tomados del JWT, nunca del body. {@code X-User-Name}, {@code X-User-Email} y {@code X-Procedure-Label} solo van en el alta, como snapshot que requests guarda al crear.
 */
@Service
public class RequestsProxyService {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_USER_NAME = "X-User-Name";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    /** Ya viene codificado en URL (UTF-8) desde el controller. */
    public static final String HEADER_PROCEDURE_LABEL = "X-Procedure-Label";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, List<String>>> TRANSITIONS_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient requestsRestClient;

    /** Recibe el cliente HTTP de requests. */
    public RequestsProxyService(@Qualifier("requestsRestClient") RestClient requestsRestClient) {
        this.requestsRestClient = requestsRestClient;
    }

    /** Crea el trámite y manda también nombre, correo y, si viene, el label del tipo. */
    public ResponseEntity<Map<String, Object>> create(
            Map<String, Object> body,
            String userId,
            String roles,
            String userName,
            String userEmail,
            String procedureLabel
    ) {
        RequestBodySpec spec = requestsRestClient.post()
                .uri("/api/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_USER_ID, userId)
                .header(HEADER_USER_ROLES, nullToEmpty(roles))
                .header(HEADER_USER_NAME, nullToEmpty(userName))
                .header(HEADER_USER_EMAIL, nullToEmpty(userEmail));
        return withProcedureLabel(spec, procedureLabel)
                .body(body)
                .exchange((request, response) -> ResponseEntity
                        .status(response.getStatusCode())
                        .contentType(resolveContentType(response.getHeaders().getContentType()))
                        .body(response.bodyTo(MAP_TYPE)));
    }

    /** Pide los cambios de estado permitidos. */
    public ResponseEntity<Map<String, List<String>>> transitions() {
        return requestsRestClient.get()
                .uri("/api/requests/meta/transitions")
                .exchange((request, response) -> ResponseEntity
                        .status(response.getStatusCode())
                        .contentType(resolveContentType(response.getHeaders().getContentType()))
                        .body(response.bodyTo(TRANSITIONS_TYPE)));
    }

    /** Pide un trámite por su id, con el usuario y los roles en headers. */
    public ResponseEntity<Map<String, Object>> getById(String id, String userId, String roles) {
        return requestsRestClient.get()
                .uri("/api/requests/{id}", id)
                .header(HEADER_USER_ID, userId)
                .header(HEADER_USER_ROLES, nullToEmpty(roles))
                .exchange((request, response) -> ResponseEntity
                        .status(response.getStatusCode())
                        .contentType(resolveContentType(response.getHeaders().getContentType()))
                        .body(response.bodyTo(MAP_TYPE)));
    }

    /** Pide el listado. Si vienen estado o fechas, los manda como query. */
    public ResponseEntity<List<Map<String, Object>>> list(
            String status,
            String from,
            String to,
            String userId,
            String roles
    ) {
        String uri = UriComponentsBuilder.fromPath("/api/requests")
                .queryParamIfPresent("status", Optional.ofNullable(blankToNull(status)))
                .queryParamIfPresent("from", Optional.ofNullable(blankToNull(from)))
                .queryParamIfPresent("to", Optional.ofNullable(blankToNull(to)))
                .build(true)
                .toUriString();

        return requestsRestClient.get()
                .uri(uri)
                .header(HEADER_USER_ID, userId)
                .header(HEADER_USER_ROLES, nullToEmpty(roles))
                .exchange((request, response) -> ResponseEntity
                        .status(response.getStatusCode())
                        .contentType(resolveContentType(response.getHeaders().getContentType()))
                        .body(response.bodyTo(LIST_TYPE)));
    }

    /**
     * Cambia el estado y devuelve el mismo status que respondió requests, por ejemplo 200, 400, 404 o 409.
     * No convierte un error de requests en 500.
     */
    public ResponseEntity<Map<String, Object>> updateStatus(
            String id,
            Map<String, Object> body,
            String userId,
            String roles
    ) {
        return statusRequest(id, body, userId, roles)
                .exchange((request, response) -> ResponseEntity
                        .status(response.getStatusCode())
                        .contentType(resolveContentType(response.getHeaders().getContentType()))
                        .body(response.bodyTo(MAP_TYPE)));
    }

    /**
     * Igual que el cambio de estado, pero indica si el cuerpo se pudo leer.
     * Conserva el status HTTP aunque falle la lectura, para que la admisión sepa si el trámite quedó guardado.
     */
    public StatusChangeResult updateStatusForAdmission(
            String id, Map<String, Object> body, String userId, String roles
    ) {
        return statusRequest(id, body, userId, roles).exchange((request, response) -> {
            var status = response.getStatusCode();
            try {
                return new StatusChangeResult(ResponseEntity.status(status)
                        .contentType(resolveContentType(response.getHeaders().getContentType()))
                        .body(response.bodyTo(MAP_TYPE)), true);
            } catch (Exception ex) {
                // Solo la conversión de esta respuesta: no convierte un fallo de envío en rechazo.
                return new StatusChangeResult(ResponseEntity.status(status).build(), false);
            }
        });
    }

    /** La respuesta del cambio de estado y si su cuerpo se pudo leer. */
    public record StatusChangeResult(ResponseEntity<Map<String, Object>> response, boolean readable) {
    }

    /** Arma el PUT de cambio de estado con {@code X-User-Id} y {@code X-User-Roles}. */
    private RequestBodySpec statusRequest(String id, Map<String, Object> body, String userId, String roles) {
        return requestsRestClient.put()
                .uri("/api/requests/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_USER_ID, userId)
                .header(HEADER_USER_ROLES, nullToEmpty(roles))
                .body(body);
    }

    /** Agrega {@code X-Procedure-Label} solo si viene. Si no, omite el header en lugar de mandarlo vacío. */
    private static RequestBodySpec withProcedureLabel(RequestBodySpec spec, String procedureLabel) {
        if (procedureLabel == null || procedureLabel.isBlank()) {
            return spec;
        }
        return spec.header(HEADER_PROCEDURE_LABEL, procedureLabel);
    }

    /** Si la respuesta no indica el tipo, asume JSON. */
    private static MediaType resolveContentType(MediaType contentType) {
        return contentType != null ? contentType : MediaType.APPLICATION_JSON;
    }

    /** Convierte un texto vacío en null para no mandar un filtro en blanco. */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    /** Convierte null en texto vacío para que el header siempre exista. */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
