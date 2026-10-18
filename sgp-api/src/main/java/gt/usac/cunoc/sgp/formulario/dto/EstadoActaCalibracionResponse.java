package gt.usac.cunoc.sgp.formulario.dto;

import java.util.UUID;

public record EstadoActaCalibracionResponse(
        UUID versionFormularioId, String estado, String mensaje, ActaCalibracionResponse acta) {}