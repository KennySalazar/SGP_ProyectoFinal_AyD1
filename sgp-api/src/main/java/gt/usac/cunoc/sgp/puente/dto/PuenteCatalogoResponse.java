package gt.usac.cunoc.sgp.puente.dto;

import java.util.UUID;

public record PuenteCatalogoResponse(
    UUID id,
    String codigo,
    String nombre,
    DepartamentoResponse departamento,
    MunicipioResponse municipio,
    Double latitud,
    Double longitud,
    boolean activo,
    String estadoActual) {}
