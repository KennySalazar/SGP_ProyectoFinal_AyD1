package gt.usac.cunoc.sgp.mantenimiento.dto;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoMantenimiento;
import java.time.Instant;
import java.util.UUID;

public record ReglaMantenimientoResponse(
    UUID id,
    String codigo,
    Integer numeroRevision,
    UUID versionFormularioId,
    String versionFormularioCodigo,
    String elementoRef,
    JsonNode condicion,
    TipoMantenimiento tipoMantenimiento,
    String intervencionSugerida,
    JsonNode reglaPrioridad,
    boolean activa,
    boolean provisional,
    String estadoValidacion,
    UUID creadoPorId,
    UUID validadoPorId,
    Instant validadoEn,
    Instant creadoEn) {}
