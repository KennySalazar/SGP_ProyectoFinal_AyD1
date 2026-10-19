package gt.usac.cunoc.sgp.revision.dto;

import java.util.UUID;

/** Cuenta observaciones puntuales, sin incluir la general ni los ciclos de la bandeja. */
public record ContadorObservacionesResponse(UUID revisionId, long total, long pendientes) {}
