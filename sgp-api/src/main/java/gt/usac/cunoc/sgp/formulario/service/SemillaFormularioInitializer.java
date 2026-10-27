package gt.usac.cunoc.sgp.formulario.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "app.formulario.siembra",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SemillaFormularioInitializer {
  private static final Logger LOGGER = LoggerFactory.getLogger(SemillaFormularioInitializer.class);
  private final SemillaFormularioService servicio;

  public SemillaFormularioInitializer(SemillaFormularioService servicio) {
    this.servicio = servicio;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(10)
  public void inicializar() {
    try {
      servicio
          .sembrarSiNoExisteActiva()
          .ifPresent(id -> LOGGER.info("Formulario SIECA provisional sembrado: {}", id));
    } catch (Exception error) {
      LOGGER.error(
          "No se pudo sembrar el formulario inicial. El administrador puede cargar la plantilla desde el editor.",
          error);
    }
  }
}
