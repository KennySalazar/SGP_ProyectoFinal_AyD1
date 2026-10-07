package gt.usac.cunoc.sgp.puente.dto;

import java.util.UUID;

public record PuenteCercanoResponse(
    UUID id,
    String codigo,
    String nombre,
    boolean activo,
    double distanciaMetros,
    double latitud,
    double longitud) {}
