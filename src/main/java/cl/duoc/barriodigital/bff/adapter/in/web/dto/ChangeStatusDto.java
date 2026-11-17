package cl.duoc.barriodigital.bff.adapter.in.web.dto;

/**
 * Body de PUT {@code /api/requests/{id}/status}.
 * El estado y la longitud de los motivos los valida requests (EP3-67 / EP3-69).
 * Así un motivo de más de 500 caracteres llega a requests tras reservar cupo y el BFF puede liberarlo.
 * {@code interventionReason} es obligatorio para el Admin (EP3-04 / EP3-45).
 */
public record ChangeStatusDto(
        String status,
        String rejectionReason,
        String interventionReason
) {
}
