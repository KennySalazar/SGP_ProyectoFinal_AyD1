package gt.usac.cunoc.sgp.mantenimiento.dto;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.PrioridadOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrdenMantenimientoResponse(
    UUID id,
    UUID puenteId,
    UUID inspeccionOrigenId,
    UUID reglaOrigenId,
    UUID resultadoIcOrigenId,
    String elementoRef,
    UUID instanciaElementoId,
    TipoOrden tipo,
    EstadoOrden estado,
    String descripcion,
    PrioridadOrden prioridadSugerida,
    PrioridadOrden prioridadConfirmada,
    String justificacionPrioridad,
    JsonNode fundamentoGeneracion,
    UUID creadoPorId,
    UUID aceptadoPorId,
    OffsetDateTime aceptadoEn,
    LocalDate fechaProgramada,
    String motivoCierreSinEjecucion,
    OffsetDateTime creadoEn,
    OffsetDateTime actualizadoEn,
    Long version) {}
