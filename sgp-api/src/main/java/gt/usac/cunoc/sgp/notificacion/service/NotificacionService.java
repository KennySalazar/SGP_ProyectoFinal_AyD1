package gt.usac.cunoc.sgp.notificacion.service;

import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.notificacion.entity.Notificacion;
import gt.usac.cunoc.sgp.notificacion.repository.NotificacionRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registra eventos mostrados una sola vez por usuario y clave de negocio (V11). */
@Service
public class NotificacionService {

  private final NotificacionRepository notificaciones;
  private final Clock clock;

  public NotificacionService(NotificacionRepository notificaciones, Clock clock) {
    this.notificaciones = notificaciones;
    this.clock = clock;
  }

  /** Participa en la transacción del negocio que la origina; es idempotente por clave de evento. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void registrar(
      UUID usuarioId,
      String tipo,
      String titulo,
      String mensaje,
      String entidad,
      UUID entidadId,
      String claveEvento) {
    if (notificaciones.existsByUsuarioIdAndClaveEvento(usuarioId, claveEvento)) {
      return;
    }

    notificaciones.save(
        new Notificacion(
            UuidV7Generator.generate(),
            usuarioId,
            tipo,
            titulo,
            mensaje,
            entidad,
            entidadId,
            claveEvento,
            clock.instant()));
  }
}
