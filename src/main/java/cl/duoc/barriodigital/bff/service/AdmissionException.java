package cl.duoc.barriodigital.bff.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Errores públicos de la coordinación; los mensajes no contienen detalles internos. */
public class AdmissionException extends ResponseStatusException {

    public enum Code {
        QUOTA_EXHAUSTED(HttpStatus.CONFLICT, "No queda cupo disponible para este tipo de trámite."),
        PROCEDURE_TYPE_UNAVAILABLE(HttpStatus.CONFLICT,
                "El tipo de trámite asociado ya no está disponible en el catálogo."),
        ADMISSION_DEPENDENCY_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY,
                "No pudimos obtener información válida del trámite."),
        ADMISSION_DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
                "No pudimos consultar el trámite. Intenta consultar el listado más tarde."),
        QUOTA_RESERVATION_UNCONFIRMED(HttpStatus.BAD_GATEWAY,
                "No pudimos confirmar la reserva de cupo. Cierra y consulta el listado; la operación requiere revisión."),
        ADMISSION_RESULT_UNCONFIRMED(HttpStatus.BAD_GATEWAY,
                "No pudimos confirmar si el cambio se guardó. Cierra y consulta el listado antes de realizar otra operación."),
        ADMISSION_RESPONSE_UNAVAILABLE(HttpStatus.BAD_GATEWAY,
                "El cambio se guardó, pero no pudimos obtener su respuesta completa. Cierra y actualiza el listado."),
        QUOTA_COMPENSATION_UNCONFIRMED(HttpStatus.BAD_GATEWAY,
                "El cambio fue rechazado y no pudimos confirmar la devolución del cupo. La operación requiere revisión.");

        private final HttpStatus status;
        private final String message;

        Code(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    private final Code code;

    public AdmissionException(Code code) {
        super(code.status, code.message);
        this.code = code;
    }

    public String code() {
        return code.name();
    }
}
