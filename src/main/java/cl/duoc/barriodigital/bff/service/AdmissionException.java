package cl.duoc.barriodigital.bff.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Errores públicos de la admisión.
 * Cada código trae un status HTTP y un mensaje que se puede mostrar, sin detalles internos.
 */
public class AdmissionException extends ResponseStatusException {

    /** Cupo agotado, tipo no disponible o una respuesta de catalog o requests que no se pudo confirmar. */
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

        /** Guarda el status HTTP y el mensaje de ese caso. */
        Code(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    private final Code code;

    /** Crea el error con el status y el mensaje del código. */
    public AdmissionException(Code code) {
        super(code.status, code.message);
        this.code = code;
    }

    /** Devuelve el nombre del código para incluirlo en el JSON. */
    public String code() {
        return code.name();
    }
}
