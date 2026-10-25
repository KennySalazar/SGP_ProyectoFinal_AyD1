package gt.usac.cunoc.sgp.mantenimiento.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanMantenimientoResponse(
    UUID id,
    UUID puenteId,
    String codigoPuente,
    String nombrePuente,
    boolean puenteActivo,
    int periodicidadMeses,
    LocalDate fechaInicio,
    Integer proximoCiclo,
    LocalDate fechaProximoCiclo,
    UUID responsableUsuarioId,
    UUID aprobadoPorId,
    Instant aprobadoEn,
    List<ActividadPlanMantenimientoResponse> actividades,
    long version) {}
