package cl.duoc.barriodigital.bff.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body de PUT {@code /api/requests/{id}/assignee} (EP3-05).
 * {@code reason} es obligatorio al reasignar; requests lo valida con el trámite bloqueado.
 */
public record AssignRequestDto(
        @NotBlank(message = "assigneeId es obligatorio")
        String assigneeId,
        @Size(max = 500) String reason
) {
}
