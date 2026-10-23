package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.archivo.service.PurgaArchivosBorradorService;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.config.ProcesosProgramados;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Borradores inactivos (RN-ARC-06, HU-069): avisa al autor a los 180 días sin actividad y purga los
 * archivos multimedia a los 210, conservando el registro de datos. El fallo de un borrador o de un
 * archivo no detiene a los demás.
 */
@Service
public class BorradoresInactivosService {

  static final String TIPO = "BORRADOR_180_DIAS";
  static final Duration AVISO = Duration.ofDays(180);
  static final Duration PURGA = Duration.ofDays(210);

  private static final Logger LOG = LoggerFactory.getLogger(BorradoresInactivosService.class);

  private final InspeccionRepository inspecciones;
  private final PurgaArchivosBorradorService purga;
  private final UserAccountRepository usuarios;
  private final NotificacionService notificaciones;
  private final AuditoriaWriter auditoria;
  private final ObjectMapper json;
  private final Clock clock;

  public BorradoresInactivosService(
      InspeccionRepository inspecciones,
      PurgaArchivosBorradorService purga,
      UserAccountRepository usuarios,
      NotificacionService notificaciones,
      AuditoriaWriter auditoria,
      ObjectMapper json,
      Clock clock) {
    this.inspecciones = inspecciones;
    this.purga = purga;
    this.usuarios = usuarios;
    this.notificaciones = notificaciones;
    this.auditoria = auditoria;
    this.json = json;
    this.clock = clock;
  }

  /**
   * La clave incluye la última actividad: si el autor retoma el borrador y vuelve a dejarlo 180
   * días, recibe un aviso nuevo.
   */
  @Scheduled(cron = ProcesosProgramados.CRON_DIARIO, zone = ProcesosProgramados.ZONA)
  @SchedulerLock(
      name = ProcesosProgramados.ALERTA_BORRADORES_INACTIVOS,
      lockAtMostFor = ProcesosProgramados.BLOQUEO_MAXIMO,
      lockAtLeastFor = ProcesosProgramados.BLOQUEO_MINIMO)
  public void avisarInactivos() {
    List<Object[]> borradores =
        inspecciones.findBorradoresInactivosDesde(clock.instant().minus(AVISO));
    int destinatarios = 0;
    int fallos = 0;
    for (Object[] fila : borradores) {
      UUID inspeccionId = (UUID) fila[0];
      UUID autorId = (UUID) fila[1];
      try {
        if (usuarios.filtrarActivos(List.of(autorId)).isEmpty()) {
          continue;
        }
        notificaciones.notificar(
            autorId,
            TIPO,
            "Borrador sin actividad",
            "Tu borrador de inspección del puente "
                + fila[2]
                + " lleva 180 días sin actividad. A los 210 días se eliminarán sus fotografías"
                + " y documentos; los datos capturados se conservan.",
            "inspeccion",
            inspeccionId,
            "borrador-180-dias:"
                + inspeccionId
                + ":"
                + ProcesosProgramados.instante(fila[3]).toEpochMilli());
        destinatarios++;
      } catch (DataIntegrityViolationException e) {
        // Una ejecución simultánea ya registró el mismo aviso; la clave de evento lo impide.
        LOG.debug("Aviso ya registrado para el borrador {}", inspeccionId);
      } catch (RuntimeException e) {
        fallos++;
        LOG.warn("No se pudo avisar el borrador inactivo {}", inspeccionId, e);
      }
    }
    auditoria.registrarProceso(
        ProcesosProgramados.ALERTA_BORRADORES_INACTIVOS,
        json.createObjectNode()
            .put("borradores", borradores.size())
            .put("destinatarios", destinatarios)
            .put("fallos", fallos));
  }

  @Scheduled(cron = ProcesosProgramados.CRON_DIARIO, zone = ProcesosProgramados.ZONA)
  @SchedulerLock(
      name = ProcesosProgramados.PURGA_ARCHIVOS_BORRADORES,
      lockAtMostFor = ProcesosProgramados.BLOQUEO_MAXIMO,
      lockAtLeastFor = ProcesosProgramados.BLOQUEO_MINIMO)
  public void purgarArchivos() {
    Instant limite = clock.instant().minus(PURGA);
    List<Object[]> candidatos = purga.candidatos(limite);
    int purgados = 0;
    int fallos = 0;
    for (Object[] fila : candidatos) {
      UUID archivoId = (UUID) fila[0];
      try {
        if (purga.purgar(
            archivoId, (UUID) fila[1], limite, ProcesosProgramados.PURGA_ARCHIVOS_BORRADORES)) {
          purgados++;
        }
      } catch (RuntimeException e) {
        fallos++;
        LOG.warn("No se pudo purgar el archivo {}", archivoId, e);
      }
    }
    auditoria.registrarProceso(
        ProcesosProgramados.PURGA_ARCHIVOS_BORRADORES,
        json.createObjectNode()
            .put("candidatos", candidatos.size())
            .put("purgados", purgados)
            .put("fallos", fallos));
  }
}
