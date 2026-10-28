package gt.usac.cunoc.sgp.revision.dto;

import java.time.Instant;
import java.util.UUID;

/** Resumen privado de un ciclo resuelto; las calificaciones se consultan en su resultado. */
public record RevisionResueltaResponse(
    UUID revisionId,
    UUID inspeccionId,
    String puenteCodigo,
    String puenteNombre,
    String estudianteNombre,
    int numeroCiclo,
    Instant enviadaEn,
    Instant resueltaEn,
    String veredicto) {}
