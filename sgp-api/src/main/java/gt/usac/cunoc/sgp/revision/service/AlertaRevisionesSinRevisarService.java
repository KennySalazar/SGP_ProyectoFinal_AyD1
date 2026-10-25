package gt.usac.cunoc.sgp.revision.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.config.ProcesosProgramados;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Avisa al Catedrático asignado cuando una inspección enviada lleva 15 días naturales sin revisarse
 * (RN-REV-08, HU-069). Cada revisión se avisa una sola vez.
 */
@Service
public class AlertaRevisionesSinRevisarService {

  static final String TIPO = "REVISION_SIN_REVISAR_15_DIAS";
  static final Duration PLAZO = Duration.ofDays(15);

  private static final Logger LOG =
      LoggerFactory.getLogger(AlertaRevisionesSinRevisarService.class);

  private final RevisionRepository revisiones;
  private final UserAccountRepository usuarios;
  private final NotificacionService notificaciones;
  private final AuditoriaWriter auditoria;
  private final ObjectMapper json;
  private final Clock clock;

  public AlertaRevisionesSinRevisarService(
      RevisionRepository revisiones,
      UserAccountRepository usuarios,
      NotificacionService notificaciones,
      AuditoriaWriter auditoria,
      ObjectMapper json,
      Clock clock) {
    this.revisiones = revisiones;
    this.usuarios = usuarios;
    this.notificaciones = notificaciones;
    this.auditoria = auditoria;
    this.json = json;
    this.clock = clock;
  }

  @Scheduled(cron = ProcesosProgramados.CRON_DIARIO, zone = ProcesosProgramados.ZONA)
  @SchedulerLock(
      name = ProcesosProgramados.ALERTA_REVISIONES_SIN_REVISAR,
      lockAtMostFor = ProcesosProgramados.BLOQUEO_MAXIMO,
      lockAtLeastFor = ProcesosProgramados.BLOQUEO_MINIMO)
  public void ejecutar() {
    int destinatarios = 0;
    int fallos = 0;
    List<Object[]> pendientes = revisiones.findSinRevisarDesde(clock.instant().minus(PLAZO));
    for (Object[] fila : pendientes) {
      UUID revisionId = (UUID) fila[0];
      UUID revisorId = (UUID) fila[2];
      try {
        if (usuarios.filtrarActivos(List.of(revisorId)).isEmpty()) {
          continue;
        }
        notificaciones.notificar(
            revisorId,
            TIPO,
            "Inspección sin revisar",
            "La inspección del puente " + fila[3] + " lleva más de 15 días enviada sin revisarse.",
            "revision",
            revisionId,
            "revision-sin-revisar-15-dias:" + revisionId);
        destinatarios++;
      } catch (DataIntegrityViolationException e) {
        // Una ejecución simultánea ya registró el mismo aviso; la clave de evento lo impide.
        LOG.debug("Aviso ya registrado para la revisión {}", revisionId);
      } catch (RuntimeException e) {
        fallos++;
        LOG.warn("No se pudo avisar la revisión sin revisar {}", revisionId, e);
      }
    }
    auditoria.registrarProceso(
        ProcesosProgramados.ALERTA_REVISIONES_SIN_REVISAR,
        json.createObjectNode()
            .put("pendientes", pendientes.size())
            .put("destinatarios", destinatarios)
            .put("fallos", fallos));
  }
}
