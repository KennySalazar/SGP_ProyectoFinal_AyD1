package gt.usac.cunoc.sgp.sync.dto;

import java.util.UUID;

public record EstimacionSalidaResponse(
    UUID versionFormularioId,
    String codigoVersionFormulario,
    int numeroPuentes,
    int numeroInspeccionesPrevias,
    long tamanoEstimadoBytes) {}
