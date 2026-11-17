package cl.duoc.barriodigital.bff.adapter.in.web.dto;

/**
 * Body de {@code POST /api/requests/{id}/rating} (EP3-74 / EP3-77).
 * score y comment los valida requests.
 */
public record RateRequestDto(Integer score, String comment) {
}
