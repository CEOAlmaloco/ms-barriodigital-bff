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

import java.util.Map;

/**
 * Expone el catálogo: el JWT se valida en el BFF y catalog no lo recibe.
 * GET lista los tipos para cualquier rol autenticado; POST y PUT exigen rol Admin y reenvían ese rol en {@code X-User-Roles}.
 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogProxyService catalogProxyService;

    /** Recibe el proxy hacia catalog. */
    public CatalogController(CatalogProxyService catalogProxyService) {
        this.catalogProxyService = catalogProxyService;
    }

    /** Lista los tipos de trámite. */
    @GetMapping("/procedures")
    public ResponseEntity<Object> procedures() {
        return catalogProxyService.list();
    }

    /** Crea un tipo. El rol sale del JWT ya validado. */
    @PostMapping("/procedures")
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            Authentication authentication
    ) {
        return catalogProxyService.create(body, CatalogCaller.rolesHeader(authentication));
    }

    /** Edita un tipo. El código va en la URL y el rol sale del JWT. */
    @PutMapping("/procedures/{value}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String value,
            @RequestBody Map<String, Object> body,
            Authentication authentication
    ) {
        return catalogProxyService.update(value, body, CatalogCaller.rolesHeader(authentication));
    }
}
