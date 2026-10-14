package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.CatalogProxyService;
import cl.duoc.barriodigital.bff.service.RequestsProxyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RequestsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RequestsProxyService requestsProxyService;

    @MockBean
    CatalogProxyService catalogProxyService;

    private static final List<Map<String, Object>> CATALOG = List.of(
            Map.of("value", "bache", "label", "Bache en calle", "active", true),
            Map.of("value", "ruido", "label", "Ruido molesto", "active", false)
    );

    @Test
    void postCreaSinTitleEnBody() throws Exception {
        when(catalogProxyService.procedures()).thenReturn(CATALOG);
        when(requestsProxyService.create(anyMap(), eq("dev-user"), eq("Vecino"), eq("dev-user")))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED)
                        .body(Map.of("id", "uuid-1", "status", "INGRESADO", "title", "Bache en calle — Calle 1")));

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Hueco","procedureType":"bache","address":"Calle 1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("uuid-1"));
    }

    @Test
    void postConTipoActivoReenviaElBodySinTocarlo() throws Exception {
        when(catalogProxyService.procedures()).thenReturn(CATALOG);
        when(requestsProxyService.create(anyMap(), eq("dev-user"), eq("Vecino"), eq("dev-user")))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", "uuid-2")));

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Hueco","procedureType":" Bache ","address":"Calle 1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("uuid-2"));

        verify(requestsProxyService).create(
                eq(Map.of("description", "Hueco", "procedureType", " Bache ", "address", "Calle 1")),
                eq("dev-user"),
                eq("Vecino"),
                eq("dev-user")
        );
    }

    @Test
    void postConTipoInexistenteResponde400SinLlamarARequests() throws Exception {
        when(catalogProxyService.procedures()).thenReturn(CATALOG);

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Hueco","procedureType":"semaforo","address":"Calle 1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"status":400,"error":"Bad Request","message":"procedureType invalido","path":"/api/requests"}
                        """, true));

        verify(requestsProxyService, never()).create(anyMap(), any(), any(), any());
    }

    @Test
    void postConTipoInactivoResponde400SinLlamarARequests() throws Exception {
        when(catalogProxyService.procedures()).thenReturn(CATALOG);

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Fiesta","procedureType":"ruido","address":"Calle 1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"status":400,"error":"Bad Request","message":"El tipo de tramite no esta disponible","path":"/api/requests"}
                        """, true));

        verify(requestsProxyService, never()).create(anyMap(), any(), any(), any());
    }

    @Test
    void postConCatalogCaidoResponde503SinLlamarARequests() throws Exception {
        when(catalogProxyService.procedures()).thenThrow(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                CatalogProxyService.CATALOG_UNAVAILABLE
        ));

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Hueco","procedureType":"bache","address":"Calle 1"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("""
                        {"status":503,"error":"Service Unavailable","message":"El catalogo de tipos de tramite no esta disponible. Intenta nuevamente en unos minutos.","path":"/api/requests"}
                        """, true));

        verify(requestsProxyService, never()).create(anyMap(), any(), any(), any());
    }

    @Test
    void postConTipoVacioNoConsultaCatalogNiRequests() throws Exception {
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Hueco","procedureType":"   ","address":"Calle 1"}
                                """))
                .andExpect(status().isBadRequest());

        verify(catalogProxyService, never()).procedures();
        verify(requestsProxyService, never()).create(anyMap(), any(), any(), any());
    }

    @Test
    void getTransicionesDelegaEnElProxy() throws Exception {
        when(requestsProxyService.transitions()).thenReturn(ResponseEntity.ok(Map.of(
                "INGRESADO", List.of("ADMITIDO", "RECHAZADO"),
                "RESUELTO", List.of()
        )));

        mockMvc.perform(get("/api/requests/meta/transitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.INGRESADO[0]").value("ADMITIDO"))
                .andExpect(jsonPath("$.INGRESADO[1]").value("RECHAZADO"));
    }

    @Test
    void getListaConFecha() throws Exception {
        when(requestsProxyService.list(
                eq("INGRESADO"),
                eq("2026-09-01"),
                eq("2026-09-13"),
                eq("dev-user"),
                eq("Vecino")
        )).thenReturn(ResponseEntity.ok(List.of(Map.of("id", "1"))));

        mockMvc.perform(get("/api/requests")
                        .param("status", "INGRESADO")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"));
    }

    @Test
    void postSinCamposResponde400() throws Exception {
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putStatusPropaga200() throws Exception {
        when(requestsProxyService.updateStatus(eq("id-1"), anyMap(), eq("dev-user"), eq("Vecino")))
                .thenReturn(ResponseEntity.ok(Map.of("id", "id-1", "status", "ADMITIDO")));

        mockMvc.perform(put("/api/requests/id-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ADMITIDO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADMITIDO"));
    }

    @Test
    void putStatusPropaga409() throws Exception {
        when(requestsProxyService.updateStatus(eq("id-1"), anyMap(), eq("dev-user"), eq("Vecino")))
                .thenReturn(ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("status", 409, "message", "No se puede pasar de INGRESADO a EN_TERRENO")));

        mockMvc.perform(put("/api/requests/id-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EN_TERRENO\"}"))
                .andExpect(status().isConflict());
    }
}
