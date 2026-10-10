package cl.duoc.barriodigital.bff.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Body de PUT /api/requests/{id}/status (EP1.5-06).
 * El parse del enum y el motivo obligatorio en RECHAZADO viven en requests.
 */
public record ChangeStatusDto(
        String status,
        @Size(max = 500) String rejectionReason
) {
}
