package gt.usac.cunoc.sgp.formulario.dto;

import java.time.Instant;
import java.util.UUID;

public record EstadoCalibracionResponse(
    UUID versionFormularioId,
    String estado,
    String rotuloCalibracion,
    UUID aprobadoPorId,
    String aprobadoPorNombre,
    Instant aprobadoEn) {}
