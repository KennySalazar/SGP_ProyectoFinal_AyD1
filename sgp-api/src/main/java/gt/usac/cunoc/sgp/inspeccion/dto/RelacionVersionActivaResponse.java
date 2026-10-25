package gt.usac.cunoc.sgp.inspeccion.dto;

import gt.usac.cunoc.sgp.formulario.dto.MapeoCampoResponse;
import java.util.List;
import java.util.UUID;

/**
 * Información sobre la relación entre la versión del formulario con que se levantó la inspección y
 * la versión activa del sistema (RN-FRM-06).
 */
public record RelacionVersionActivaResponse(
    boolean esVersionActiva,
    String codigoVersionInspeccion,
    String codigoVersionActiva,
    UUID versionActivaId,
    boolean mapeosDeclarados,
    int totalEquivalentes,
    int totalNuevos,
    int totalEliminados,
    List<MapeoCampoResponse> mapeos) {}
