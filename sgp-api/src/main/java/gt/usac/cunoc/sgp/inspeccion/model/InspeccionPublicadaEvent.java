package gt.usac.cunoc.sgp.inspeccion.model;

import java.util.UUID;

/**
 * Aviso interno de que una inspección acaba de quedar PUBLICADA. Se emite dentro de la transacción
 * de publicación y quienes reaccionan a él (por ejemplo el mantenimiento, HU-046) lo hacen después
 * de la confirmación, para que un fallo suyo nunca revierta ni bloquee una publicación.
 *
 * @param inspeccionId la inspección publicada
 * @param usuarioId quien la publicó: el autor, o el Catedrático que la aprobó
 */
public record InspeccionPublicadaEvent(UUID inspeccionId, UUID usuarioId) {}
