package gt.usac.cunoc.sgp.mantenimiento.dto;

import java.util.UUID;

public record ResumenReglasVersionResponse(
    UUID versionFormularioId,
    String versionFormularioCodigo,
    boolean tieneReglasActivas,
    long totalReglas,
    long totalActivas,
    long totalProvisionales,
    long totalValidadas,
    String advertenciaSinReglasActivas) {}
