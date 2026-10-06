package gt.usac.cunoc.sgp.puente.dto;

import gt.usac.cunoc.sgp.puente.model.EstadoSolicitudAltaPuente;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SolicitudAltaPuenteResponse(
    UUID id,
    String nombre,
    DepartamentoResponse departamento,
    MunicipioResponse municipio,
    String ruta,
    BigDecimal kilometraje,
    double latitud,
    double longitud,
    String justificacion,
    EstadoSolicitudAltaPuente estado,
    String motivoDecision,
    OffsetDateTime revisadoEn,
    OffsetDateTime creadoEn) {}
