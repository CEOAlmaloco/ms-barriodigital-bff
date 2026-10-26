package cl.duoc.barriodigital.bff.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Body de PUT {@code /api/requests/{id}/status}.
 * El estado y la obligación del motivo al pasar a RECHAZADO los interpreta requests.
 */
public record ChangeStatusDto(
        String status,
        @Size(max = 500) String rejectionReason
) {
}
