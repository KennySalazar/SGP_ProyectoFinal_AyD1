package gt.usac.cunoc.sgp.inspeccion.dto;

import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc;
import java.time.Instant;
import java.util.UUID;

public record ConfirmacionEstadoIcResponse(
    UUID inspeccionId,
    ResultadoCalculoIc calculo,
    EstadoCondicion estadoConfirmado,
    String justificacion,
    UUID realizadoPorId,
    Instant realizadoEn,
    boolean confirmacionVigente) {}
