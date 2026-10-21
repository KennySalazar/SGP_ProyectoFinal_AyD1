package gt.usac.cunoc.sgp.foro.service;

import gt.usac.cunoc.sgp.inspeccion.model.InspeccionPublicadaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Abre el hilo del foro de una inspección cuando su publicación ya quedó confirmada (HU-064,
 * RN-FOR-03).
 *
 * <p>Reacciona después de la confirmación y atrapa cualquier fallo: el foro nunca debe bloquear ni
 * deshacer una publicación. Si algo falla queda en el log y el hilo se abre la primera vez que un
 * usuario abra el foro de esa inspección.
 */
@Component
public class HiloInspeccionAlPublicarListener {

  private static final Logger log = LoggerFactory.getLogger(HiloInspeccionAlPublicarListener.class);

  private final ForoInspeccionService foroInspeccion;

  public HiloInspeccionAlPublicarListener(ForoInspeccionService foroInspeccion) {
    this.foroInspeccion = foroInspeccion;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void alPublicarse(InspeccionPublicadaEvent evento) {
    try {
      foroInspeccion.abrirTrasPublicacion(evento.inspeccionId());
    } catch (RuntimeException e) {
      log.error(
          "No se pudo abrir el hilo del foro de la inspección {}; se abrirá cuando alguien abra"
              + " su foro",
          evento.inspeccionId(),
          e);
    }
  }
}
