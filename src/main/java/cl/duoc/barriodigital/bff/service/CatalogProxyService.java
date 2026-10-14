package cl.duoc.barriodigital.bff.service;

import cl.duoc.barriodigital.bff.web.CatalogCaller;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
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
 * Proxy hacia ms-barriodigital-catalog (EP1.5-13).
 * Reenvía status y body: 400/403/404/409 del dominio no se convierten en 500.
 */
@Service
public class CatalogProxyService {

    public static final String CATALOG_UNAVAILABLE =
            "El catalogo de tipos de tramite no esta disponible. Intenta nuevamente en unos minutos.";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Object> JSON_BODY =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient catalogRestClient;

    public CatalogProxyService(@Qualifier("catalogRestClient") RestClient catalogRestClient) {
        this.catalogRestClient = catalogRestClient;
    }

    public ResponseEntity<Object> list() {
        return exchangeJson(catalogRestClient.get().uri("/api/catalog/procedures"));
    }

    /**
     * Tipos de trámite para validar el alta de requests. No reenvía nada al cliente:
     * sin conexión, timeout, status no 2xx o cuerpo ilegible, responde 503.
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

    public ResponseEntity<Map<String, Object>> create(Map<String, Object> body, String rolesHeader) {
        RequestBodySpec spec = catalogRestClient.post()
                .uri("/api/catalog/procedures")
                .contentType(MediaType.APPLICATION_JSON);
        return exchangeMap(withRoles(spec, rolesHeader).body(body));
    }

    public ResponseEntity<Map<String, Object>> update(String value, Map<String, Object> body, String rolesHeader) {
        RequestBodySpec spec = catalogRestClient.put()
                .uri("/api/catalog/procedures/{value}", value)
                .contentType(MediaType.APPLICATION_JSON);
        return exchangeMap(withRoles(spec, rolesHeader).body(body));
    }

    private static RequestBodySpec withRoles(RequestBodySpec spec, String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return spec;
        }
        return spec.header(CatalogCaller.HEADER_USER_ROLES, rolesHeader);
    }

    private ResponseEntity<Map<String, Object>> exchangeMap(RequestHeadersSpec<?> spec) {
        return spec.exchange((request, response) -> ResponseEntity
                .status(response.getStatusCode())
                .contentType(resolveContentType(response.getHeaders().getContentType()))
                .body(response.bodyTo(MAP_TYPE)));
    }

    /** Un 200 es una lista. Un error es un objeto. Los dos se reenvían con su status. */
    private ResponseEntity<Object> exchangeJson(RequestHeadersSpec<?> spec) {
        return spec.exchange((request, response) -> ResponseEntity
                .status(response.getStatusCode())
                .contentType(resolveContentType(response.getHeaders().getContentType()))
                .body(response.bodyTo(JSON_BODY)));
    }

    private static MediaType resolveContentType(MediaType contentType) {
        return contentType != null ? contentType : MediaType.APPLICATION_JSON;
    }
}
