package gt.usac.cunoc.sgp.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/** Todo proceso programado usa ShedLock y la convención fijada en HU-069 (DT-BE-14). */
class ConvencionProcesosProgramadosTest {

  @Test
  void todoProcesoProgramadoTieneUnBloqueoConNombreUnico() {
    List<Method> procesos = procesosProgramados();
    assertThat(procesos).hasSizeGreaterThanOrEqualTo(7);

    List<String> nombres = new ArrayList<>();
    for (Method proceso : procesos) {
      SchedulerLock bloqueo = proceso.getAnnotation(SchedulerLock.class);
      assertThat(bloqueo).as("%s debe usar @SchedulerLock", proceso).isNotNull();
      assertThat(bloqueo.name()).as("nombre del bloqueo de %s", proceso).isNotBlank();
      // La columna shedlock.name admite 64 caracteres.
      assertThat(bloqueo.name().length()).isLessThanOrEqualTo(64);
      assertThat(bloqueo.lockAtMostFor()).as("duración máxima de %s", proceso).isNotBlank();
      nombres.add(bloqueo.name());
    }
    assertThat(nombres).doesNotHaveDuplicates();
  }

  @Test
  void losProcesosDiariosReutilizanHorarioZonaYDuracionesDeLaConvencion() {
    List<Method> diarios =
        procesosProgramados().stream()
            .filter(m -> !m.getAnnotation(Scheduled.class).cron().isEmpty())
            .toList();

    assertThat(diarios)
        .extracting(m -> m.getAnnotation(SchedulerLock.class).name())
        .containsExactlyInAnyOrder(
            ProcesosProgramados.ALERTA_REVISIONES_SIN_REVISAR,
            ProcesosProgramados.ALERTA_PUENTES_SIN_INSPECCION,
            ProcesosProgramados.ALERTA_ORDENES_PROGRAMADAS,
            ProcesosProgramados.ALERTA_BORRADORES_INACTIVOS,
            ProcesosProgramados.PURGA_ARCHIVOS_BORRADORES,
            ProcesosProgramados.LIMPIEZA_IDEMPOTENCIA);
    for (Method proceso : diarios) {
      Scheduled programacion = proceso.getAnnotation(Scheduled.class);
      SchedulerLock bloqueo = proceso.getAnnotation(SchedulerLock.class);
      assertThat(programacion.cron()).isEqualTo(ProcesosProgramados.CRON_DIARIO);
      assertThat(programacion.zone()).isEqualTo(ProcesosProgramados.ZONA);
      assertThat(bloqueo.lockAtMostFor()).isEqualTo(ProcesosProgramados.BLOQUEO_MAXIMO);
      assertThat(bloqueo.lockAtLeastFor()).isEqualTo(ProcesosProgramados.BLOQUEO_MINIMO);
    }
  }

  private static List<Method> procesosProgramados() {
    var escaner = new ClassPathScanningCandidateComponentProvider(false);
    escaner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
    List<Method> procesos = new ArrayList<>();
    for (var candidato : escaner.findCandidateComponents("gt.usac.cunoc.sgp")) {
      Class<?> clase =
          ClassUtils.resolveClassName(
              candidato.getBeanClassName(), ClassUtils.getDefaultClassLoader());
      Arrays.stream(clase.getDeclaredMethods())
          .filter(m -> m.isAnnotationPresent(Scheduled.class))
          .forEach(procesos::add);
    }
    return procesos;
  }
}
