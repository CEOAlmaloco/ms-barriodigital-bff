package cl.duoc.barriodigital.bff.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Proxy hacia ms-barriodigital-requests.
 * Reenvía X-User-Id / X-User-Roles tomados del JWT (nunca del body del cliente).
 */
@Service
public class RequestsProxyService {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient requestsRestClient;

    public RequestsProxyService(RestClient requestsRestClient) {
        this.requestsRestClient = requestsRestClient;
    }

    public ResponseEntity<Map<String, Object>> create(
            Map<String, Object> body,
            String userId,
            String roles
    ) {
        return requestsRestClient.post()
                .uri("/api/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_USER_ID, userId)
                .header(HEADER_USER_ROLES, nullToEmpty(roles))
                .body(body)
                .exchange((request, response) -> ResponseEntity
                        .status(response.getStatusCode())
                        .contentType(resolveContentType(response.getHeaders().getContentType()))
                        .body(response.bodyTo(MAP_TYPE)));
    }

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

    private static MediaType resolveContentType(MediaType contentType) {
        return contentType != null ? contentType : MediaType.APPLICATION_JSON;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
