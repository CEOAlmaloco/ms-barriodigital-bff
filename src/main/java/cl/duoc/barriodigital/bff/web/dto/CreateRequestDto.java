package cl.duoc.barriodigital.bff.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body del alta. Sin title (autogenerado en requests) ni solicitanteId (JWT). */
public record CreateRequestDto(
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Size(max = 100) String procedureType,
        @NotBlank @Size(max = 300) String address
) {
}
