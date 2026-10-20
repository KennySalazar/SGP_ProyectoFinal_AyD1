package gt.usac.cunoc.sgp.mantenimiento.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Registro de ejecución de una orden; el correo identifica al responsable del sistema. */
public record EjecucionOrdenResponse(
    UUID ordenId,
    OffsetDateTime iniciadoEn,
    LocalDate fechaEjecucion,
    UUID responsableUsuarioId,
    String responsableUsuarioEmail,
    String responsableNombre,
    OffsetDateTime actualizadoEn) {}
