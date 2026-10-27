package gt.usac.cunoc.sgp.notificacion.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class AvisosAcademicosInitializer {
  private final AvisosAcademicosService avisos;

  public AvisosAcademicosInitializer(AvisosAcademicosService avisos) {
    this.avisos = avisos;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(30)
  public void revisar() {
    avisos.revisarPendientes();
  }
}
