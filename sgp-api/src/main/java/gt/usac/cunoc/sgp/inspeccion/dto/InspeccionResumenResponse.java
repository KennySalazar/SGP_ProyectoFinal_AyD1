package gt.usac.cunoc.sgp.inspeccion.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InspeccionResumenResponse(
    UUID id,
    UUID puenteId,
    String codigoPuente,
    String nombrePuente,
    String departamentoNombre,
    String municipioNombre,
    UUID versionFormularioId,
    String codigoVersionFormulario,
    UUID autorId,
    String rolAutorCreacion,
    String nombreAutorCapturado,
    String estado,
    String tipo,
    LocalDate fechaInspeccion,
    Double porcentajeAvance,
    Instant ultimaActividadEn,
    Instant creadoEn,
    Instant actualizadoEn) {}
