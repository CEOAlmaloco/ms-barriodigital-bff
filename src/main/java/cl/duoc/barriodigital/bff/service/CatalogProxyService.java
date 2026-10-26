package cl.duoc.barriodigital.bff.service;

import cl.duoc.barriodigital.bff.web.CatalogCaller;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodySpec;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Reenvía a catalog el listado, el alta y la edición de tipos, y también la reserva y la devolución de cupo.
 * Propaga status y body: un 400, 403, 404 o 409 del dominio no se convierte en 500, y un 5xx no muestra el error interno.
 */
@Service
public class CatalogProxyService {

    public static final String CATALOG_UNAVAILABLE =
            "El catalogo de tipos de tramite no esta disponible. Intenta nuevamente en unos minutos.";

    public static final String CATALOG_ERROR = "No pudimos procesar la solicitud. Intenta nuevamente.";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Object> JSON_BODY =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient catalogRestClient;

    /** Recibe el cliente HTTP de catalog. */
    public CatalogProxyService(@Qualifier("catalogRestClient") RestClient catalogRestClient) {
        this.catalogRestClient = catalogRestClient;
    }

    /** Pide la lista de tipos y la devuelve al cliente. */
    public ResponseEntity<Object> list() {
        return exchangeJson(catalogRestClient.get().uri("/api/catalog/procedures"));
    }

    /**
     * Pide los tipos para validar el alta. No reenvía el body al cliente.
     * Sin conexión, timeout, status distinto de 2xx o cuerpo ilegible, responde 503.
     */
    public List<Map<String, Object>> procedures() {
        List<Map<String, Object>> procedures;
        try {
            procedures = catalogRestClient.get()
                    .uri("/api/catalog/procedures")
                    .exchange((request, response) -> response.getStatusCode().is2xxSuccessful()
                            ? response.bodyTo(LIST_TYPE)
                            : null);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, CATALOG_UNAVAILABLE, ex);
        }
        if (procedures == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, CATALOG_UNAVAILABLE);
        }
        return procedures;
    }

    /** Crea un tipo y manda el rol en {@code X-User-Roles}. */
    public ResponseEntity<Map<String, Object>> create(Map<String, Object> body, String rolesHeader) {
        RequestBodySpec spec = catalogRestClient.post()
                .uri("/api/catalog/procedures")
                .contentType(MediaType.APPLICATION_JSON);
        return exchangeMap(withRoles(spec, rolesHeader).body(body));
    }

    /** Edita un tipo y manda el rol en {@code X-User-Roles}. */
    public ResponseEntity<Map<String, Object>> update(String value, Map<String, Object> body, String rolesHeader) {
        RequestBodySpec spec = catalogRestClient.put()
                .uri("/api/catalog/procedures/{value}", value)
                .contentType(MediaType.APPLICATION_JSON);
        return exchangeMap(withRoles(spec, rolesHeader).body(body));
    }

    /** Reserva un cupo. Solo usa el status HTTP, sin reintentos. */
    public HttpStatusCode reserve(String value) {
        return catalogRestClient.post()
                .uri("/api/catalog/procedures/{value}/reserve", value)
                .exchange((request, response) -> response.getStatusCode());
    }

    /** Devuelve un cupo. Solo usa el status HTTP, sin reintentos. */
    public HttpStatusCode release(String value) {
        return catalogRestClient.post()
                .uri("/api/catalog/procedures/{value}/release", value)
                .exchange((request, response) -> response.getStatusCode());
    }

    /** Agrega {@code X-User-Roles} si viene un rol. */
    private static RequestBodySpec withRoles(RequestBodySpec spec, String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return spec;
        }
        return spec.header(CatalogCaller.HEADER_USER_ROLES, rolesHeader);
    }

    /** Lee la respuesta de un tipo. Un 5xx se reemplaza por un mensaje genérico. */
    private ResponseEntity<Map<String, Object>> exchangeMap(RequestHeadersSpec<?> spec) {
        return spec.exchange((request, response) -> {
            if (response.getStatusCode().is5xxServerError()) {
                return ResponseEntity.status(response.getStatusCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(serverError(response.getStatusCode()));
            }
            return ResponseEntity
                .status(response.getStatusCode())
                .contentType(resolveContentType(response.getHeaders().getContentType()))
                .body(response.bodyTo(MAP_TYPE));
        });
    }

    /** Lee el listado: un 200 es la lista y un 5xx no reenvía detalles internos de catalog. */
    private ResponseEntity<Object> exchangeJson(RequestHeadersSpec<?> spec) {
        return spec.exchange((request, response) -> {
            if (response.getStatusCode().is5xxServerError()) {
                return ResponseEntity.status(response.getStatusCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body((Object) serverError(response.getStatusCode()));
            }
            return ResponseEntity
                .status(response.getStatusCode())
                .contentType(resolveContentType(response.getHeaders().getContentType()))
                .body(response.bodyTo(JSON_BODY));
        });
    }

    /** Arma el JSON genérico de un fallo interno. */
    private static Map<String, Object> serverError(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return Map.of(
                "status", status.value(),
                "error", resolved == null ? "Server Error" : resolved.getReasonPhrase(),
                "message", CATALOG_ERROR
        );
    }

    /** Si catalog no indica el tipo de contenido, asume JSON. */
    private static MediaType resolveContentType(MediaType contentType) {
        return contentType != null ? contentType : MediaType.APPLICATION_JSON;
    }
}
