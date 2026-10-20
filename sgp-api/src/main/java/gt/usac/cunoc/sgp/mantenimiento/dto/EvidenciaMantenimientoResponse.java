package gt.usac.cunoc.sgp.mantenimiento.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Fotografía de evidencia disponible; su contenido se sirve por un extremo autorizado. */
public record EvidenciaMantenimientoResponse(
    UUID id,
    UUID ordenId,
    String elementoRef,
    String mimeReal,
    long tamanoBytes,
    Integer anchoPx,
    Integer altoPx,
    UUID subidoPorId,
    OffsetDateTime subidoEn) {}
