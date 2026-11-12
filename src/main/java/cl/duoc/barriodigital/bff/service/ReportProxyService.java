package cl.duoc.barriodigital.bff.service;

import cl.duoc.barriodigital.bff.web.CatalogCaller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

import java.nio.charset.StandardCharsets;

/**
 * Reenvío a report con {@code X-User-Id} y {@code X-User-Roles} (EP3-44 / EP3-07). Sin JWT ni token interno.
 */
@Service
public class ReportProxyService {

    public static final String UNAVAILABLE =
            "El servicio de reportes no esta disponible. Intenta nuevamente en unos minutos.";

    public static final String GENERIC_ERROR = "No pudimos procesar la solicitud. Intenta nuevamente.";

    private final RestClient reportRestClient;

    public ReportProxyService(@Qualifier("reportRestClient") RestClient reportRestClient) {
        this.reportRestClient = reportRestClient;
    }

    /** Reenvía la petición. Un corte de red se vuelve 503. */
    public ResponseEntity<byte[]> forward(
            HttpServletRequest request, byte[] body, CatalogCaller.Identity identity
    ) {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        try {
            RequestBodySpec spec = reportRestClient.method(method).uri(builder -> {
                builder.path(request.getRequestURI());
                String query = request.getQueryString();
                if (query != null && !query.isBlank()) {
                    builder.replaceQuery(query);
                }
                return builder.build(true);
            });
            spec.headers(headers -> copyIdentity(headers, request, identity));
            RequestHeadersSpec<?> call = hasBody(body) ? spec.body(body) : spec;
            return call.exchange((clientRequest, response) -> toResponse(response));
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE, ex);
        }
    }

    private void copyIdentity(HttpHeaders headers, HttpServletRequest request, CatalogCaller.Identity identity) {
        if (identity != null) {
            if (hasText(identity.userId())) {
                headers.set(CatalogCaller.HEADER_USER_ID, identity.userId());
            }
            if (hasText(identity.roles())) {
                headers.set(CatalogCaller.HEADER_USER_ROLES, identity.roles());
            }
        }
        if (request.getContentType() != null) {
            headers.set(HttpHeaders.CONTENT_TYPE, request.getContentType());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean hasBody(byte[] body) {
        return body != null && body.length > 0;
    }

    private ResponseEntity<byte[]> toResponse(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws java.io.IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.is5xxServerError()) {
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(genericError(status));
        }
        byte[] payload = response.bodyTo(byte[].class);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType != null) {
            builder.contentType(contentType);
        }
        if (payload == null || payload.length == 0) {
            return builder.build();
        }
        return builder.body(payload);
    }

    private static byte[] genericError(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String error = resolved == null ? "Server Error" : resolved.getReasonPhrase();
        String json = "{\"status\":" + status.value()
                + ",\"error\":\"" + error
                + "\",\"message\":\"" + GENERIC_ERROR + "\"}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
