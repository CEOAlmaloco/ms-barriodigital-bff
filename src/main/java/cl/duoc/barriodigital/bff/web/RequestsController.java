package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.RequestsProxyService;
import cl.duoc.barriodigital.bff.web.dto.CreateRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rutas del BFF que orquestan trámites vía ms-barriodigital-requests (EP1-15).
 * El JWT se valida en el BFF; requests recibe la llamada interna ya autorizada.
 */
@RestController
@RequestMapping("/api/requests")
public class RequestsController {

    private final RequestsProxyService requestsProxyService;

    public RequestsController(RequestsProxyService requestsProxyService) {
        this.requestsProxyService = requestsProxyService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateRequestDto body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", body.title());
        payload.put("description", body.description());
        payload.put("procedureType", body.procedureType());
        return requestsProxyService.create(payload);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String id) {
        return requestsProxyService.getById(id);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return requestsProxyService.list(status, from, to);
    }
}
