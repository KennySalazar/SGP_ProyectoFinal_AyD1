package gt.usac.cunoc.sgp.revision.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Respuesta privada exclusiva del estudiante autor y del revisor de este ciclo. */
public record RevisionResultadoResponse(
    UUID revisionId,
    UUID inspeccionId,
    UUID autorId,
    UUID revisorId,
    int numeroCiclo,
    String estadoRevision,
    String estadoInspeccion,
    String veredicto,
    String observacionGeneral,
    BigDecimal calificacion,
    Instant enviadaEn,
    Instant resueltaEn) {}
