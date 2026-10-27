package gt.usac.cunoc.sgp.inspeccion.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Proyección del historial público para usuarios autenticados; no incluye respuestas privadas. */
public record InspeccionPublicadaResumenResponse(
    UUID id,
    UUID puenteId,
    UUID autorId,
    String nombreAutor,
    LocalDate fechaInspeccion,
    Instant publicadoEn,
    String estado) {}
