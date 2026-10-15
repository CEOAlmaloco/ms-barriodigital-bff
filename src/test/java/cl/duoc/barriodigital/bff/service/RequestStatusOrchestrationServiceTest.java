package cl.duoc.barriodigital.bff.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RequestStatusOrchestrationServiceTest {

    private final RequestsProxyService requests = mock(RequestsProxyService.class);
    private final CatalogProxyService catalog = mock(CatalogProxyService.class);
    private final RequestStatusOrchestrationService service = new RequestStatusOrchestrationService(requests, catalog);
    private final Map<String, Object> payload = Map.of("status", " ADMITIDO ", "procedureType", "tipo-del-cliente");

    @ParameterizedTest
    @ValueSource(strings = {"Vecino", "Admin", "", "Auditor"})
    void sinPermisoNoConsultaNiReserva(String roles) {
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.updateStatus("id-1", payload, "func-1", roles)).getStatusCode().value());
        verifyNoInteractions(requests, catalog);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECHAZADO", "EN_GESTION", "admitido", "INVALIDO", ""})
    void otrosDestinosConservanElProxyYLaValidacion(String status) {
        Map<String, Object> body = Map.of("status", status);
        service.updateStatus("id-1", body, "func-1", "Funcionario");
        verify(requests).updateStatus("id-1", body, "func-1", "Funcionario");
        verifyNoMoreInteractions(requests);
        verifyNoInteractions(catalog);
    }

    @Test
    void consultaElTipoPersistidoReservaYCambiaSinLiberar() {
        lookup();
        reserve();
        var response = ResponseEntity.<Map<String, Object>>ok(Map.of("id", "id-1", "status", "ADMITIDO"));
        changed(response, true);
        assertSame(response, call());
        var order = inOrder(requests, catalog);
        order.verify(requests).getById("id-1", "func-1", "Funcionario");
        order.verify(catalog).reserve("bache");
        order.verify(requests).updateStatusForAdmission("id-1", payload, "func-1", "Funcionario");
        order.verifyNoMoreInteractions();
    }

    @Test
    void consulta404ConservaElErrorSinEscrituras() {
        var response = rejection(404, "Tramite no encontrado", "/api/requests/id-1");
        when(requests.getById("id-1", "func-1", "Funcionario")).thenReturn(response);
        assertSame(response, call());
        verifyNoInteractions(catalog);
        verify(requests).getById("id-1", "func-1", "Funcionario");
        verifyNoMoreInteractions(requests);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "../bache", "Bache", "tipo con espacios"})
    void consultaInvalidaNoProduceEscrituras(String type) {
        when(requests.getById("id-1", "func-1", "Funcionario"))
                .thenReturn(ResponseEntity.ok(Map.of("id", "id-1", "procedureType", type)));
        expect("ADMISSION_DEPENDENCY_INVALID_RESPONSE");
        verifyNoInteractions(catalog);
    }

    @Test
    void consultaSinTipoNoUsaUnoPorDefecto() {
        when(requests.getById("id-1", "func-1", "Funcionario")).thenReturn(ResponseEntity.ok(Map.of("id", "id-1")));
        expect("ADMISSION_DEPENDENCY_INVALID_RESPONSE");
        verifyNoInteractions(catalog);
    }

    @Test
    void consultaSinConexionNoProduceEscrituras() {
        when(requests.getById("id-1", "func-1", "Funcionario")).thenThrow(new ResourceAccessException("detalle interno"));
        expect("ADMISSION_DEPENDENCY_UNAVAILABLE");
        verifyNoInteractions(catalog);
    }

    @Test
    void consultaIlegibleNoProduceEscrituras() {
        when(requests.getById("id-1", "func-1", "Funcionario")).thenThrow(new RestClientException("JSON ilegible"));
        expect("ADMISSION_DEPENDENCY_INVALID_RESPONSE");
        verifyNoInteractions(catalog);
    }

    @Test
    void consulta5xxNoProduceEscrituras() {
        when(requests.getById("id-1", "func-1", "Funcionario")).thenReturn(ResponseEntity.internalServerError().build());
        expect("ADMISSION_DEPENDENCY_UNAVAILABLE");
        verifyNoInteractions(catalog);
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 409, 500, 503})
    void reservaRechazadaOInciertaNoEnviaPutNiRelease(int status) {
        lookup();
        when(catalog.reserve("bache")).thenReturn(HttpStatus.valueOf(status));
        expect(status == 404 ? "PROCEDURE_TYPE_UNAVAILABLE"
                : status == 409 ? "QUOTA_EXHAUSTED" : "QUOTA_RESERVATION_UNCONFIRMED");
        verify(catalog).reserve("bache");
        verifyNoMoreInteractions(catalog);
        verify(requests).getById("id-1", "func-1", "Funcionario");
        verifyNoMoreInteractions(requests);
    }

    @Test
    void respuestaPerdidaDeReservaNoSeRepiteNiSeCompensa() {
        lookup();
        when(catalog.reserve("bache")).thenThrow(new ResourceAccessException("timeout"));
        expect("QUOTA_RESERVATION_UNCONFIRMED");
        verify(catalog).reserve("bache");
        verifyNoMoreInteractions(catalog);
        verify(requests).getById("id-1", "func-1", "Funcionario");
        verifyNoMoreInteractions(requests);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 404, 409})
    void rechazoConfirmadoCompensaUnaVezYConservaElError(int status) {
        lookup();
        reserve();
        String message = switch (status) {
            case 400 -> "status es obligatorio";
            case 403 -> "Solo un Funcionario puede cambiar el estado";
            case 404 -> "Tramite no encontrado";
            default -> "No se puede pasar de ADMITIDO a ADMITIDO";
        };
        var rejected = rejection(status, message, "/api/requests/id-1/status");
        changed(rejected, true);
        when(catalog.release("bache")).thenReturn(HttpStatus.OK);
        assertSame(rejected, call());
        var order = inOrder(requests, catalog);
        order.verify(requests).getById("id-1", "func-1", "Funcionario");
        order.verify(catalog).reserve("bache");
        order.verify(requests).updateStatusForAdmission("id-1", payload, "func-1", "Funcionario");
        order.verify(catalog).release("bache");
        order.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 500, 0})
    void compensacionFallidaOInciertaTieneErrorEspecifico(int status) {
        lookup();
        reserve();
        changed(rejection(409, "No se puede pasar de ADMITIDO a ADMITIDO", "/api/requests/id-1/status"), true);
        if (status == 0) when(catalog.release("bache")).thenThrow(new ResourceAccessException("timeout"));
        else when(catalog.release("bache")).thenReturn(HttpStatus.valueOf(status));
        expect("QUOTA_COMPENSATION_UNCONFIRMED");
        verify(catalog).reserve("bache");
        verify(catalog).release("bache");
        verifyNoMoreInteractions(catalog);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 409})
    void erroresSinRechazoReconocibleNoLiberan(int status) {
        lookup();
        reserve();
        changed(rejection(status, "detalle interno", "/otra/operacion"), true);
        expect("ADMISSION_RESULT_UNCONFIRMED");
        verify(catalog).reserve("bache");
        verifyNoMoreInteractions(catalog);
    }

    @Test
    void putSinRespuestaNoLibera() {
        lookup();
        reserve();
        when(requests.updateStatusForAdmission("id-1", payload, "func-1", "Funcionario"))
                .thenThrow(new RestClientException("respuesta perdida"));
        expect("ADMISSION_RESULT_UNCONFIRMED");
        verify(catalog).reserve("bache");
        verifyNoMoreInteractions(catalog);
    }

    @Test
    void put200IlegibleNoLiberaNiFabricaUnRecurso() {
        lookup();
        reserve();
        changed(ResponseEntity.ok().build(), false);
        expect("ADMISSION_RESPONSE_UNAVAILABLE");
        verify(catalog).reserve("bache");
        verifyNoMoreInteractions(catalog);
    }

    private void lookup() {
        when(requests.getById("id-1", "func-1", "Funcionario"))
                .thenReturn(ResponseEntity.ok(Map.of("id", "id-1", "procedureType", "bache")));
    }

    private void reserve() {
        when(catalog.reserve("bache")).thenReturn(HttpStatus.OK);
    }

    private void changed(ResponseEntity<Map<String, Object>> response, boolean readable) {
        when(requests.updateStatusForAdmission("id-1", payload, "func-1", "Funcionario"))
                .thenReturn(new RequestsProxyService.StatusChangeResult(response, readable));
    }

    private ResponseEntity<Map<String, Object>> call() {
        return service.updateStatus("id-1", payload, "func-1", "Funcionario");
    }

    private void expect(String code) {
        AdmissionException error = assertThrows(AdmissionException.class, this::call);
        assertEquals(code, error.code());
        assertEquals(code.equals("QUOTA_EXHAUSTED") || code.equals("PROCEDURE_TYPE_UNAVAILABLE") ? 409
                : code.equals("ADMISSION_DEPENDENCY_UNAVAILABLE") ? 503 : 502, error.getStatusCode().value());
    }

    private static ResponseEntity<Map<String, Object>> rejection(int status, String message, String path) {
        return ResponseEntity.status(status).body(Map.of("status", status, "message", message, "path", path));
    }
}
