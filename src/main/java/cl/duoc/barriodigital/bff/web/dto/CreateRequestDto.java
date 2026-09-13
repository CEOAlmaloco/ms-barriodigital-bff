package cl.duoc.barriodigital.bff.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRequestDto(
        @NotBlank String title,
        @NotBlank String description,
        @NotBlank String procedureType
) {
}
