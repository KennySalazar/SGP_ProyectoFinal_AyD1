package gt.usac.cunoc.sgp.inspeccion.dto;

import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import java.math.BigDecimal;

/** Resultado definitivo guardado al publicar; la consulta nunca vuelve a calcularlo. */
public record ResultadoIcPublicadoResponse(
    BigDecimal indiceCalculado,
    EstadoCondicion estadoCalculado,
    EstadoCondicion estadoConfirmado,
    String justificacionAjuste) {}
