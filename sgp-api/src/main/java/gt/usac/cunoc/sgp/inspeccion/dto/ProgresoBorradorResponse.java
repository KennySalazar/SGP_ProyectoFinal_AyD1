package gt.usac.cunoc.sgp.inspeccion.dto;

import java.util.List;

public record ProgresoBorradorResponse(
    int totalCamposRequeridos,
    int totalCamposCompletados,
    double porcentajeTotal,
    List<ProgresoSeccionResponse> secciones) {}
