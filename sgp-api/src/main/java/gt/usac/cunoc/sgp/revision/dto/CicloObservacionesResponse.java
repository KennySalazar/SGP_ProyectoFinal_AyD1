package gt.usac.cunoc.sgp.revision.dto;

import java.time.Instant;
import java.util.UUID;

/** Identifica el ciclo que se abrirá siempre con su contenido histórico. */
public record CicloObservacionesResponse(
    UUID revisionId,
    UUID inspeccionId,
    int numeroCiclo,
    String estadoRevision,
    Instant enviadaEn,
    String fechaInspeccion,
    String puenteNombre,
    String puenteCodigo) {}
