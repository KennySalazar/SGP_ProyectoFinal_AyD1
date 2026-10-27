package gt.usac.cunoc.sgp.revision.dto;

import java.util.UUID;

/** GENERAL no tiene identidad ni estado de observación puntual; texto puede ser null. */
public record ObservacionGeneralResponse(UUID revisionId, String texto) {}
