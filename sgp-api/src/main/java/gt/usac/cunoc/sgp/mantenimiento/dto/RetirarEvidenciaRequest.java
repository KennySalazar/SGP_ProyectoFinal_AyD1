package gt.usac.cunoc.sgp.mantenimiento.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Retiro de una evidencia subida por error")
public record RetirarEvidenciaRequest(
    @Schema(description = "Motivo del retiro, obligatorio") String motivo) {}
