package gt.usac.cunoc.sgp.mantenimiento.service;

import gt.usac.cunoc.sgp.inspeccion.model.InspeccionPublicadaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Genera las propuestas de mantenimiento cuando una publicación ya quedó confirmada (HU-046).
 *
 * <p>Reacciona después de la confirmación y atrapa cualquier fallo: el mantenimiento nunca debe
 * bloquear ni deshacer una publicación. Si algo falla queda registrado y un Administrador puede
 * repetir la generación.
 */
@Component
public class PropuestasAlPublicarListener {

  private static final Logger log = LoggerFactory.getLogger(PropuestasAlPublicarListener.class);

  private final GeneradorPropuestasMantenimientoService generador;

  public PropuestasAlPublicarListener(GeneradorPropuestasMantenimientoService generador) {
    this.generador = generador;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void alPublicarse(InspeccionPublicadaEvent evento) {
    try {
      generador.generarTrasPublicacion(evento.inspeccionId(), evento.usuarioId());
    } catch (RuntimeException e) {
      log.error(
          "No se pudieron generar las propuestas de mantenimiento de la inspección {}; un"
              + " Administrador puede regenerarlas",
          evento.inspeccionId(),
          e);
    }
  }
}
