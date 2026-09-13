package cl.duoc.barriodigital.bff.web;

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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    void postCreaSinTitleEnBody() throws Exception {
        when(requestsProxyService.create(anyMap(), eq("dev-user"), eq("Vecino")))
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
    void getListaConFecha() throws Exception {
        when(requestsProxyService.list(eq("INGRESADO"), eq("2026-09-01"), eq("2026-09-13"), eq("dev-user"), eq("Vecino")))
                .thenReturn(ResponseEntity.ok(List.of(Map.of("id", "1"))));

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
}
