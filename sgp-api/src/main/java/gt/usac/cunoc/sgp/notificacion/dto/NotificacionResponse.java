package gt.usac.cunoc.sgp.notificacion.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificacionResponse(
    UUID id,
    String tipo,
    String titulo,
    String mensaje,
    String entidad,
    UUID entidadId,
    String claveEvento,
    Instant leidoEn,
    Instant creadoEn,
    boolean leido) {}
