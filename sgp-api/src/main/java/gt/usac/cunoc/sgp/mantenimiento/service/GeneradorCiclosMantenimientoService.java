package gt.usac.cunoc.sgp.mantenimiento.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GeneradorCiclosMantenimientoService {

  private final PlanMantenimientoService planes;

  public GeneradorCiclosMantenimientoService(PlanMantenimientoService planes) {
    this.planes = planes;
  }

  @Scheduled(fixedDelayString = "${app.mantenimiento.ciclos-intervalo-ms:86400000}")
  @SchedulerLock(name = "generar-ciclos-mantenimiento-rutinario", lockAtMostFor = "PT10M")
  public void generarCiclosVencidos() {
    planes.generarCiclosVencidos();
  }
}
