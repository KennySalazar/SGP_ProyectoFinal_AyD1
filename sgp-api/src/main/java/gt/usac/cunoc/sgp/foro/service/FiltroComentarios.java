package gt.usac.cunoc.sgp.foro.service;

import java.util.UUID;

/**
 * Qué comentarios de un hilo se piden. Se usa a lo sumo un filtro a la vez:
 *
 * <ul>
 *   <li>{@code elementoRef} (con su {@code instanciaElementoId} si es repetible): los de ese campo
 *       o elemento;
 *   <li>{@code seccion}: los de una sección del formulario, es decir, los anclados a ella o a
 *       cualquier cosa que contenga; se indica con su clave, sin barras («subestructura»);
 *   <li>{@code soloGenerales}: los que no están anclados a nada.
 * </ul>
 *
 * Una respuesta lleva el ancla de su comentario principal, así que entra en el mismo filtro.
 */
public record FiltroComentarios(
    String elementoRef, UUID instanciaElementoId, String seccion, boolean soloGenerales) {

  /** Sin filtro: todos los comentarios del hilo. */
  public static final FiltroComentarios NINGUNO = new FiltroComentarios(null, null, null, false);

  public static FiltroComentarios porElemento(String elementoRef, UUID instanciaElementoId) {
    return new FiltroComentarios(elementoRef, instanciaElementoId, null, false);
  }
}
