package gt.usac.cunoc.sgp.inspeccion.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record AcompananteRequest(
    UUID usuarioId,
    @NotBlank(message = "El nombre del acompañante es obligatorio") String nombre,
    String numeroColegiado) {}
