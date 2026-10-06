package gt.usac.cunoc.sgp.puente.dto;

import java.util.List;

public record UbicacionTerritorialResponse(
    double latitud,
    double longitud,
    String zonaUtm,
    boolean requiereSeleccion,
    List<CandidatoTerritorialResponse> candidatos) {}
