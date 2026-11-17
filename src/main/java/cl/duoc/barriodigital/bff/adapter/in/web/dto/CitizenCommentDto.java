package cl.duoc.barriodigital.bff.adapter.in.web.dto;

/**
 * Body de {@code POST /api/requests/{id}/comments} (EP3-64 / EP3-69).
 * La validación de texto vacío o largo la aplica requests.
 */
public record CitizenCommentDto(String text) {
}
