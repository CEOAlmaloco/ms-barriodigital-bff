package cl.duoc.barriodigital.bff.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body del alta de un trámite: descripción, tipo y dirección.
 * Sector, referencia y punto se reenvían si vienen. No trae título ni solicitante:
 * el título lo genera requests y la identidad sale del JWT.
 */
public record CreateRequestDto(
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Size(max = 100) String procedureType,
        @NotBlank @Size(max = 300) String address,
        @Size(max = 40) String sector,
        @Size(max = 200) String locationReference,
        Double latitude,
        Double longitude,
        @Size(max = 20) String locationPrecision
) {
}
