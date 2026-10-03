package gt.usac.cunoc.sgp.puente.dto;

public record CoordenadaUtmResponse(
    int zona, String hemisferio, int epsg, double este, double norte) {}
