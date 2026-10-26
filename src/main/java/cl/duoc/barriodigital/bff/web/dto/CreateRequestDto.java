package cl.duoc.barriodigital.bff.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body del alta de un trámite: descripción, tipo y dirección.
 * No trae título ni solicitante: el título lo genera requests y la identidad sale del JWT.
 */
public record CreateRequestDto(
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Size(max = 100) String procedureType,
        @NotBlank @Size(max = 300) String address
) {
}
