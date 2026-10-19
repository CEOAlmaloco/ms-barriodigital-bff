package cl.duoc.barriodigital.bff.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Antes del alta, revisa en catalog que el procedureType exista y esté activo.
 * Normaliza igual que requests (trim y minúsculas). El body que se reenvía no se toca.
 * Vacío o null no consulta catalog: el @NotBlank del DTO ya lo corta con 400.
 */
@Component
public class ProcedureTypeValidator {

    public static final String UNKNOWN_TYPE = "procedureType invalido";
    public static final String INACTIVE_TYPE = "El tipo de tramite no esta disponible";

    private final CatalogProxyService catalogProxyService;

    public ProcedureTypeValidator(CatalogProxyService catalogProxyService) {
        this.catalogProxyService = catalogProxyService;
    }

    /**
     * Devuelve el label del tipo encontrado, recortado, para el snapshot de requests.
     * Null si el item de catalog no trae label (o viene vacío) y si el código viene vacío.
     */
    public String requireActive(String rawProcedureType) {
        if (rawProcedureType == null || rawProcedureType.isBlank()) {
            return null;
        }
        String code = rawProcedureType.trim().toLowerCase(Locale.ROOT);

        List<Map<String, Object>> procedures = catalogProxyService.procedures();
        Map<String, Object> found = procedures.stream()
                .filter(item -> item != null && code.equals(item.get("value")))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, UNKNOWN_TYPE));

        if (!Boolean.TRUE.equals(found.get("active"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INACTIVE_TYPE);
        }
        return found.get("label") instanceof String label && !label.isBlank() ? label.trim() : null;
    }
}
