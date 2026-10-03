package gt.usac.cunoc.sgp.puente.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PuenteResponse(
    UUID id,
    String codigo,
    String nombre,
    DepartamentoResponse departamento,
    MunicipioResponse municipio,
    String ruta,
    BigDecimal kilometraje,
    double latitud,
    double longitud,
    CoordenadaUtmResponse utm,
    boolean activo,
    String estadoActual,
    BigDecimal indiceCondicionActual,
    LocalDate fechaUltimaInspeccion,
    OffsetDateTime creadoEn) {}
