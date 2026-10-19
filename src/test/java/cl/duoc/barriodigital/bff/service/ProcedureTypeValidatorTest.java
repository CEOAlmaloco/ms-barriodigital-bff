package cl.duoc.barriodigital.bff.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcedureTypeValidatorTest {

    private final CatalogProxyService catalogProxyService = mock(CatalogProxyService.class);
    private final ProcedureTypeValidator validator = new ProcedureTypeValidator(catalogProxyService);

    @Test
    void tipoActivoPasaAunqueVengaConEspaciosYMayusculasYDevuelveSuLabel() {
        when(catalogProxyService.procedures()).thenReturn(List.of(
                Map.of("value", "bache", "label", "Bache en calle", "active", true)
        ));

        assertEquals("Bache en calle", validator.requireActive(" Bache "));
    }

    @Test
    void devuelveElLabelConTildeRecortado() {
        when(catalogProxyService.procedures()).thenReturn(List.of(
                Map.of("value", "alumbrado", "label", "  Alumbrado público  ", "active", true)
        ));

        assertEquals("Alumbrado público", validator.requireActive("alumbrado"));
    }

    @Test
    void tipoActivoSinLabelPasaYDevuelveNull() {
        Map<String, Object> sinLabel = new HashMap<>();
        sinLabel.put("value", "agua");
        sinLabel.put("active", true);
        when(catalogProxyService.procedures()).thenReturn(List.of(
                sinLabel,
                Map.of("value", "ruido", "label", "   ", "active", true)
        ));

        assertNull(validator.requireActive("agua"));
        assertNull(validator.requireActive("ruido"));
    }

    @Test
    void tipoInexistenteEs400() {
        when(catalogProxyService.procedures()).thenReturn(List.of(
                Map.of("value", "bache", "label", "Bache en calle", "active", true)
        ));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> validator.requireActive("semaforo"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("procedureType invalido", ex.getReason());
    }

    @Test
    void tipoInactivoOSinActiveEs400() {
        Map<String, Object> sinActive = new HashMap<>();
        sinActive.put("value", "agua");
        when(catalogProxyService.procedures()).thenReturn(List.of(
                Map.of("value", "ruido", "label", "Ruido molesto", "active", false),
                sinActive
        ));

        ResponseStatusException inactivo = assertThrows(ResponseStatusException.class,
                () -> validator.requireActive("ruido"));
        ResponseStatusException sinCampo = assertThrows(ResponseStatusException.class,
                () -> validator.requireActive("agua"));

        assertEquals(HttpStatus.BAD_REQUEST, inactivo.getStatusCode());
        assertEquals("El tipo de tramite no esta disponible", inactivo.getReason());
        assertEquals("El tipo de tramite no esta disponible", sinCampo.getReason());
    }

    @Test
    void nullOVacioNoConsultaCatalog() {
        assertNull(validator.requireActive(null));
        assertNull(validator.requireActive("   "));

        verify(catalogProxyService, never()).procedures();
    }
}
