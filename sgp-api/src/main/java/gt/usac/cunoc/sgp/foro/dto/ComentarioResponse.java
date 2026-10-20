package gt.usac.cunoc.sgp.foro.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Comentario o respuesta de un hilo; {@code padreId} es nulo en un comentario principal. */
public record ComentarioResponse(
    UUID id,
    UUID hiloId,
    UUID padreId,
    UUID autorId,
    String autorUsuario,
    String autorRol,
    String contenido,
    OffsetDateTime creadoEn) {}
