package cl.duoc.barriodigital.bff.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Proxy hacia ms-barriodigital-requests (EP1-15).
 * Reenvía status y body: 400/404 del dominio no se convierten en 500.
 */
@Service
public class RequestsProxyService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient requestsRestClient;

    public RequestsProxyService(RestClient requestsRestClient) {
        this.requestsRestClient = requestsRestClient;
    }

    public ResponseEntity<Map<String, Object>> create(Map<String, Object> body) {
        return exchangeMap(requestsRestClient.post()
                .uri("/api/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));
    }

    public ResponseEntity<Map<String, Object>> getById(String id) {
        return exchangeMap(requestsRestClient.get().uri("/api/requests/{id}", id));
    }

    public ResponseEntity<List<Map<String, Object>>> list(String status, String from, String to) {
        String uri = UriComponentsBuilder.fromPath("/api/requests")
                .queryParamIfPresent("status", Optional.ofNullable(blankToNull(status)))
                .queryParamIfPresent("from", Optional.ofNullable(blankToNull(from)))
                .queryParamIfPresent("to", Optional.ofNullable(blankToNull(to)))
                .build(true)
                .toUriString();

        return exchangeList(requestsRestClient.get().uri(uri));
    }

    private ResponseEntity<Map<String, Object>> exchangeMap(RequestHeadersSpec<?> spec) {
        return spec.exchange((request, response) -> ResponseEntity
                .status(response.getStatusCode())
                .contentType(resolveContentType(response.getHeaders().getContentType()))
                .body(response.bodyTo(MAP_TYPE)));
    }

    private ResponseEntity<List<Map<String, Object>>> exchangeList(RequestHeadersSpec<?> spec) {
        return spec.exchange((request, response) -> ResponseEntity
                .status(response.getStatusCode())
                .contentType(resolveContentType(response.getHeaders().getContentType()))
                .body(response.bodyTo(LIST_TYPE)));
    }

    private static MediaType resolveContentType(MediaType contentType) {
        return contentType != null ? contentType : MediaType.APPLICATION_JSON;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
