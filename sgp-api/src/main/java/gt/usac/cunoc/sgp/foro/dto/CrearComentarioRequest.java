package gt.usac.cunoc.sgp.foro.dto;

import java.util.UUID;

/**
 * Comentario nuevo. {@code padreId} lo convierte en respuesta a un comentario principal. {@code
 * elementoRef} (y, si el elemento es repetible, {@code instanciaElementoId}) lo ancla a un campo o
 * elemento de una inspección publicada; solo vale en el hilo de una inspección y en un comentario
 * principal.
 */
public record CrearComentarioRequest(
    String contenido, UUID padreId, String elementoRef, UUID instanciaElementoId) {

  /** Comentario o respuesta sin ancla. */
  public CrearComentarioRequest(String contenido, UUID padreId) {
    this(contenido, padreId, null, null);
  }
}
