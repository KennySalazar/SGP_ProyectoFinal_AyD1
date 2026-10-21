package gt.usac.cunoc.sgp.foro.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Comentario o respuesta de un hilo; {@code padreId} es nulo en un comentario principal. {@code
 * elementoRef} e {@code instanciaElementoId} son nulos cuando el comentario no está anclado.
 */
public record ComentarioResponse(
    UUID id,
    UUID hiloId,
    UUID padreId,
    String elementoRef,
    UUID instanciaElementoId,
    UUID autorId,
    String autorUsuario,
    String autorRol,
    String contenido,
    OffsetDateTime creadoEn) {}
