package gt.usac.cunoc.sgp.foro.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Comentario o respuesta de un hilo; {@code padreId} es nulo en un comentario principal. {@code
 * elementoRef} e {@code instanciaElementoId} son nulos cuando el comentario no está anclado.
 *
 * <p>{@code contenido} es nulo si el comentario se eliminó (queda la marca) o si está oculto y
 * quien lo lee no modera el foro; {@code motivoOcultamiento} solo llega a los moderadores
 * (RN-FOR-05, RN-FOR-08). {@code editableHasta} es el límite que decide el servidor para que el
 * autor lo edite.
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
    OffsetDateTime creadoEn,
    OffsetDateTime editadoEn,
    OffsetDateTime editableHasta,
    boolean eliminado,
    boolean oculto,
    String motivoOcultamiento) {}
