package cl.duoc.barriodigital.bff.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void pingSinTokenEnDevResponde200() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("ms-barriodigital-bff"));
    }

    @Test
    void pingConJwtMockIncluyeSubject() throws Exception {
        mockMvc.perform(get("/api/ping").with(jwt().jwt(j -> j.subject("vecino-test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("vecino-test"));
    }
}
