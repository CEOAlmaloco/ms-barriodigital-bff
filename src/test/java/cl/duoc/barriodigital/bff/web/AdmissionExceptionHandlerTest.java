package cl.duoc.barriodigital.bff.web;

import cl.duoc.barriodigital.bff.service.AdmissionException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdmissionExceptionHandlerTest {

    @ParameterizedTest
    @EnumSource(AdmissionException.Code.class)
    void conservaCamposYAgregaCodigoEstable(AdmissionException.Code code) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ErrorController())
                .setControllerAdvice(new BffExceptionHandler()).build();
        var error = new AdmissionException(code);
        mvc.perform(get("/errors/" + code))
                .andExpect(status().is(error.getStatusCode().value()))
                .andExpect(jsonPath("$.status").value(error.getStatusCode().value()))
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.message").value(error.getReason()))
                .andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.path").value("/errors/" + code))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @RestController
    static class ErrorController {
        @GetMapping("/errors/{code}")
        void error(@PathVariable AdmissionException.Code code) {
            throw new AdmissionException(code);
        }
    }
}
