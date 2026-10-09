package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.CatalogProxyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Catálogo vía ms-barriodigital-catalog (EP1.5-13).
 * El JWT se valida acá. POST y PUT además exigen Admin antes de llamar al dominio.
 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogProxyService catalogProxyService;

    public CatalogController(CatalogProxyService catalogProxyService) {
        this.catalogProxyService = catalogProxyService;
    }

    @GetMapping("/procedures")
    public ResponseEntity<List<Map<String, Object>>> procedures() {
        return catalogProxyService.list();
    }

    @PostMapping("/procedures")
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            Authentication authentication
    ) {
        return catalogProxyService.create(body, CatalogCaller.rolesHeader(authentication));
    }

    @PutMapping("/procedures/{value}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String value,
            @RequestBody Map<String, Object> body,
            Authentication authentication
    ) {
        return catalogProxyService.update(value, body, CatalogCaller.rolesHeader(authentication));
    }
}
