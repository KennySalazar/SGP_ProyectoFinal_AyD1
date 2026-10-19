package gt.usac.cunoc.sgp.mantenimiento.service;

import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class SemillaReglasMantenimientoInitializer {

  private static final Logger log =
      LoggerFactory.getLogger(SemillaReglasMantenimientoInitializer.class);

  private final VersionFormularioRepository versionFormularioRepository;
  private final ReglaMantenimientoService reglaMantenimientoService;

  public SemillaReglasMantenimientoInitializer(
      VersionFormularioRepository versionFormularioRepository,
      ReglaMantenimientoService reglaMantenimientoService) {
    this.versionFormularioRepository = versionFormularioRepository;
    this.reglaMantenimientoService = reglaMantenimientoService;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(20)
  public void inicializarSemillaReglas() {
    versionFormularioRepository
        .findByActivaTrue()
        .ifPresent(
            versionActiva -> {
              try {
                int sembradas =
                    reglaMantenimientoService.sembrarCatalogoProvisionalSiNoExiste(
                        versionActiva.getId(), null);
                if (sembradas > 0) {
                  log.info(
                      "Sembradas {} reglas de mantenimiento provisionales para la versión activa '{}'",
                      sembradas,
                      versionActiva.getCodigo());
                }
              } catch (Exception e) {
                log.warn(
                    "No se pudo sembrar el catálogo de reglas para la versión {}: {}",
                    versionActiva.getCodigo(),
                    e.getMessage());
              }
            });
  }
}
