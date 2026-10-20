package gt.usac.cunoc.sgp.foro.service;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Límites y limpieza de lo que se escribe en el foro. */
final class ContenidoForo {

  /** Longitud máxima del título de un hilo. Es una decisión de HU-063, no de la base de datos. */
  static final int LONGITUD_MAXIMA_TITULO = 120;

  /** Longitud máxima de un comentario. */
  static final int LONGITUD_MAXIMA_COMENTARIO = 2000;

  private ContenidoForo() {}

  /**
   * Prepara un comentario: unifica los saltos de línea, quita los caracteres de control (PostgreSQL
   * rechaza NUL) y los espacios de los extremos. Devuelve {@code null} si queda vacío. El HTML no
   * se altera: se guarda como texto y la interfaz lo muestra escapado.
   */
  static String limpiarComentario(String texto) {
    if (texto == null) return null;
    String limpio =
        texto.replace("\r\n", "\n").replace('\r', '\n').replaceAll("[\\p{Cc}&&[^\\n\\t]]", "");
    limpio = limpio.strip();
    return limpio.isEmpty() ? null : limpio;
  }

  /** Prepara un título: una sola línea, sin caracteres de control ni espacios repetidos. */
  static String limpiarTitulo(String texto) {
    if (texto == null) return null;
    String limpio = texto.replaceAll("\\p{Cc}", " ").replaceAll("\\s+", " ").strip();
    return limpio.isEmpty() ? null : limpio;
  }

  static Map<String, String> error(String campo, String mensaje) {
    return Map.of("campo", campo, "mensaje", mensaje);
  }

  /** Página desde 0 y tamaño entre 1 y 100 (DT-BE-08). */
  static void validarPaginacion(int pagina, int tamanio) {
    if (pagina < 0 || tamanio < 1 || tamanio > 100) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "paginacion_invalida",
          "Paginación inválida",
          "La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.");
    }
  }
}
