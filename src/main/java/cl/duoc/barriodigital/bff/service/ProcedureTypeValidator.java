package cl.duoc.barriodigital.bff.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Antes del alta, revisa en catalog que el procedureType exista y esté activo.
 * Normaliza igual que requests, con trim y minúsculas; el body que se reenvía no se toca.
 * Si el código viene vacío no consulta catalog: el {@code @NotBlank} del DTO ya responde 400.
 */
@Component
public class ProcedureTypeValidator {

    public static final String UNKNOWN_TYPE = "procedureType invalido";
    public static final String INACTIVE_TYPE = "El tipo de tramite no esta disponible";

    private final CatalogProxyService catalogProxyService;

    /** Recibe el proxy de catalog. */
    public ProcedureTypeValidator(CatalogProxyService catalogProxyService) {
        this.catalogProxyService = catalogProxyService;
    }

    /**
     * Busca el tipo y devuelve su label recortado, para el snapshot que requests guarda.
     * Si no existe o no está activo, responde 400. Si catalog no trae label, o el código viene vacío, devuelve null.
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
