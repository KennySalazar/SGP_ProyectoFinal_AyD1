package gt.usac.cunoc.sgp.revision.dto;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.inspeccion.dto.IndiceCondicionPreliminarResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Datos y referencias capturados; no incorpora datos técnicos vivos del puente o inspección. */
public record RevisionDetalleResponse(
    UUID revisionId,
    UUID inspeccionId,
    String estadoRevision,
    UUID revisorId,
    int numeroCiclo,
    Instant enviadaEn,
    UUID autorId,
    UUID puenteId,
    LocalDate fechaInspeccion,
    UUID versionFormularioId,
    String versionFormularioCodigo,
    JsonNode esquemaFormulario,
    JsonNode datos,
    JsonNode identificacionCapturada,
    IndiceCondicionPreliminarResponse icPreliminar,
    String fotografiasEndpoint,
    boolean fotografiasCongeladas,
    String observacionesEndpoint) {}
