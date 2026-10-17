package gt.usac.cunoc.sgp.inspeccion.dto;

public record ProgresoSeccionResponse(
    String seccion,
    String titulo,
    int camposRequeridos,
    int camposCompletados,
    double porcentaje,
    boolean completada) {}
