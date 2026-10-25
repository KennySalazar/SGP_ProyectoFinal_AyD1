package gt.usac.cunoc.sgp.foro.service;

import gt.usac.cunoc.sgp.common.exception.DemasiadasSolicitudesException;
import gt.usac.cunoc.sgp.foro.repository.ForoComentarioRepository;
import gt.usac.cunoc.sgp.foro.repository.ForoHiloRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Límite de tasa del foro por usuario (HU-066, DT-SEC-07): unos pocos hilos y comentarios por
 * minuto. Se cuenta lo publicado en la base de datos, así un usuario que lo supera no afecta a los
 * demás y el límite vale igual en todas las instancias. Quien lo supera recibe 429 con el tiempo de
 * espera en {@code Retry-After}.
 *
 * <p>Es un límite blando: dos publicaciones exactamente simultáneas del mismo usuario pueden pasar
 * a la vez; lo que importa es frenar la ráfaga.
 */
@Component
public class LimiteTasaForo {

  /** Ventana deslizante en que se cuentan las publicaciones. */
  static final Duration VENTANA = Duration.ofMinutes(1);

  /** Hilos que un usuario puede abrir por minuto. */
  static final int HILOS_POR_MINUTO = 3;

  /** Comentarios que un usuario puede publicar por minuto, incluido el primero de cada hilo. */
  static final int COMENTARIOS_POR_MINUTO = 10;

  private final ForoHiloRepository hilos;
  private final ForoComentarioRepository comentarios;
  private final Clock clock;

  LimiteTasaForo(ForoHiloRepository hilos, ForoComentarioRepository comentarios, Clock clock) {
    this.hilos = hilos;
    this.comentarios = comentarios;
    this.clock = clock;
  }

  /** Abrir un hilo también publica su primer comentario: se exigen los dos límites. */
  void exigirParaHilo(UUID usuarioId) {
    Instant ahora = clock.instant();
    exigir(
        hilos.abiertosDesde(usuarioId, ahora.minus(VENTANA), PageRequest.of(0, HILOS_POR_MINUTO)),
        HILOS_POR_MINUTO,
        ahora,
        "Abriste demasiados hilos en poco tiempo. Espera un momento antes de abrir otro.");
    exigirParaComentario(usuarioId);
  }

  void exigirParaComentario(UUID usuarioId) {
    Instant ahora = clock.instant();
    exigir(
        comentarios.publicadosDesde(
            usuarioId, ahora.minus(VENTANA), PageRequest.of(0, COMENTARIOS_POR_MINUTO)),
        COMENTARIOS_POR_MINUTO,
        ahora,
        "Publicaste demasiados comentarios en poco tiempo. Espera un momento antes de comentar"
            + " de nuevo.");
  }

  /**
   * {@code recientes} son las publicaciones de la ventana, de la más nueva a la más vieja, a lo
   * sumo {@code limite}. Si ya está lleno, se podrá publicar cuando la más vieja salga de la
   * ventana.
   */
  private void exigir(List<Instant> recientes, int limite, Instant ahora, String mensaje) {
    if (recientes.size() < limite) return;
    Instant libre = recientes.get(limite - 1).plus(VENTANA);
    long segundos = (Duration.between(ahora, libre).toMillis() + 999) / 1000;
    throw new DemasiadasSolicitudesException("limite_foro_excedido", mensaje, segundos);
  }
}
