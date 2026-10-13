package cl.duoc.barriodigital.bff.service;

import cl.duoc.barriodigital.bff.web.CatalogCaller;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodySpec;
import org.springframework.web.client.RestClient.RequestHeadersSpec;

import java.util.Map;

/**
 * Proxy hacia ms-barriodigital-catalog (EP1.5-13).
 * Reenvía status y body: 400/403/404/409 del dominio no se convierten en 500.
 */
@Service
public class CatalogProxyService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Object> JSON_BODY =
            new ParameterizedTypeReference<>() {};

    private final RestClient catalogRestClient;

    public CatalogProxyService(@Qualifier("catalogRestClient") RestClient catalogRestClient) {
        this.catalogRestClient = catalogRestClient;
    }

    public ResponseEntity<Object> list() {
        return exchangeJson(catalogRestClient.get().uri("/api/catalog/procedures"));
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
