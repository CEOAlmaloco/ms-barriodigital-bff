package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.CatalogProxyService;
import jakarta.servlet.http.HttpServletRequest;
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
 * GET lista los tipos para cualquier rol autenticado; POST y PUT exigen rol Admin y reenvían la identidad para auditoría.
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

    /** Crea un tipo. Identidad del JWT e IP del Gateway ({@code X-Source-Ip}). */
    @PostMapping("/procedures")
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return catalogProxyService.create(
                body,
                CatalogCaller.identity(authentication),
                DownstreamHeaders.sourceIp(request)
        );
    }

    /** Edita un tipo. Identidad del JWT e IP del Gateway ({@code X-Source-Ip}). */
    @PutMapping("/procedures/{value}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String value,
            @RequestBody Map<String, Object> body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return catalogProxyService.update(
                value,
                body,
                CatalogCaller.identity(authentication),
                DownstreamHeaders.sourceIp(request)
        );
    }
}
