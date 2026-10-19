package gt.usac.cunoc.sgp.revision.dto;

import java.time.Instant;
import java.util.UUID;

public record RevisionBandejaResponse(
    UUID revisionId,
    UUID inspeccionId,
    UUID puenteId,
    String puenteCodigo,
    String puenteNombre,
    UUID estudianteId,
    String estudianteEmail,
    String estudianteNombre,
    UUID cursoId,
    String cursoNombre,
    String periodo,
    String seccion,
    int numeroCiclo,
    Instant enviadaEn,
    Instant iniciadaEn,
    long diasEnEspera,
    boolean esperaProlongada,
    String estadoInspeccion,
    String estadoRevision) {}
