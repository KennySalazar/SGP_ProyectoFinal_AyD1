package gt.usac.cunoc.sgp.mantenimiento.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.config.ProcesosProgramados;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoRepository;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import gt.usac.cunoc.sgp.puente.repository.AsignacionPuenteRepository;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Alertas diarias de mantenimiento (HU-069): puentes sin inspección publicada en 12 meses
 * (RN-MTO-08) y órdenes programadas próximas o vencidas. Solo avisa; nunca cambia el estado de una
 * orden (RN-MTO-04). Cada aviso usa su clave de evento para no repetirse.
 */
@Service
public class AlertasMantenimientoService {

  static final String TIPO_PUENTE = "PUENTE_SIN_INSPECCION_12_MESES";
  static final String TIPO_PROXIMA = "ORDEN_PROGRAMADA_PROXIMA";
  static final String TIPO_VENCIDA = "ORDEN_PROGRAMADA_VENCIDA";

  private static final Logger LOG = LoggerFactory.getLogger(AlertasMantenimientoService.class);
  private static final ZoneId ZONA = ZoneId.of(ProcesosProgramados.ZONA);
  private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final PuenteRepository puentes;
  private final AsignacionPuenteRepository asignaciones;
  private final OrdenMantenimientoRepository ordenes;
  private final UserAccountRepository usuarios;
  private final NotificacionService notificaciones;
  private final AuditoriaWriter auditoria;
  private final ObjectMapper json;
  private final Clock clock;
  private final int diasAnticipacion;

  public AlertasMantenimientoService(
      PuenteRepository puentes,
      AsignacionPuenteRepository asignaciones,
      OrdenMantenimientoRepository ordenes,
      UserAccountRepository usuarios,
      NotificacionService notificaciones,
      AuditoriaWriter auditoria,
      ObjectMapper json,
      Clock clock,
      @Value("${app.alertas.orden-programada.dias-anticipacion:3}") int diasAnticipacion) {
    this.puentes = puentes;
    this.asignaciones = asignaciones;
    this.ordenes = ordenes;
    this.usuarios = usuarios;
    this.notificaciones = notificaciones;
    this.auditoria = auditoria;
    this.json = json;
    this.clock = clock;
    this.diasAnticipacion = diasAnticipacion;
  }

  /**
   * Avisa a los Administradores y a los Catedráticos que tienen asignado el puente. La clave
   * incluye la fecha de referencia: el aviso se repite solo si, tras una nueva publicación, vuelven
   * a pasar 12 meses.
   */
  @Scheduled(cron = ProcesosProgramados.CRON_DIARIO, zone = ProcesosProgramados.ZONA)
  @SchedulerLock(
      name = ProcesosProgramados.ALERTA_PUENTES_SIN_INSPECCION,
      lockAtMostFor = ProcesosProgramados.BLOQUEO_MAXIMO,
      lockAtLeastFor = ProcesosProgramados.BLOQUEO_MINIMO)
  public void alertarPuentesSinInspeccion() {
    Instant limite = clock.instant().atZone(ZONA).minusMonths(12).toInstant();
    List<Object[]> pendientes = puentes.findActivosSinInspeccionDesde(limite);
    List<UUID> administradores = usuarios.findIdsActivosPorRol(RoleName.ADMINISTRADOR);
    int destinatarios = 0;
    int fallos = 0;
    for (Object[] fila : pendientes) {
      UUID puenteId = (UUID) fila[0];
      try {
        Set<UUID> avisar = new LinkedHashSet<>(administradores);
        List<UUID> catedraticos = asignaciones.findCatedraticosConAsignacionVigente(puenteId);
        if (!catedraticos.isEmpty()) {
          avisar.addAll(usuarios.filtrarActivos(catedraticos));
        }
        String clave =
            "puente-sin-inspeccion-12-meses:"
                + puenteId
                + ":"
                + ProcesosProgramados.instante(fila[3]).toEpochMilli();
        for (UUID usuarioId : avisar) {
          notificaciones.notificar(
              usuarioId,
              TIPO_PUENTE,
              "Puente sin inspección reciente",
              "El puente "
                  + fila[1]
                  + " · "
                  + fila[2]
                  + " no tiene una inspección publicada en"
                  + " los últimos 12 meses.",
              "puente",
              puenteId,
              clave);
          destinatarios++;
        }
      } catch (DataIntegrityViolationException e) {
        // Una ejecución simultánea ya registró el mismo aviso; la clave de evento lo impide.
        LOG.debug("Aviso ya registrado para el puente {}", puenteId);
      } catch (RuntimeException e) {
        fallos++;
        LOG.warn("No se pudo avisar el puente sin inspección {}", puenteId, e);
      }
    }
    auditoria.registrarProceso(
        ProcesosProgramados.ALERTA_PUENTES_SIN_INSPECCION,
        json.createObjectNode()
            .put("puentes", pendientes.size())
            .put("destinatarios", destinatarios)
            .put("fallos", fallos));
  }

  /**
   * Recuerda las órdenes PROGRAMADA cuya fecha llega en los próximos días y avisa de las vencidas
   * sin iniciar, a quien las aceptó y a los Administradores. La orden no cambia de estado.
   */
  @Scheduled(cron = ProcesosProgramados.CRON_DIARIO, zone = ProcesosProgramados.ZONA)
  @SchedulerLock(
      name = ProcesosProgramados.ALERTA_ORDENES_PROGRAMADAS,
      lockAtMostFor = ProcesosProgramados.BLOQUEO_MAXIMO,
      lockAtLeastFor = ProcesosProgramados.BLOQUEO_MINIMO)
  public void alertarOrdenesProgramadas() {
    LocalDate hoy = LocalDate.now(clock.withZone(ZONA));
    List<UUID> administradores = usuarios.findIdsActivosPorRol(RoleName.ADMINISTRADOR);
    List<OrdenMantenimiento> proximas =
        ordenes.findByEstadoAndFechaProgramadaBetween(
            EstadoOrden.PROGRAMADA, hoy, hoy.plusDays(diasAnticipacion));
    List<OrdenMantenimiento> vencidas =
        ordenes.findByEstadoAndFechaProgramadaBefore(EstadoOrden.PROGRAMADA, hoy);

    int[] resultado = new int[2];
    for (OrdenMantenimiento orden : proximas) {
      avisarOrden(
          orden,
          administradores,
          TIPO_PROXIMA,
          "Orden de mantenimiento próxima",
          "La orden «"
              + orden.getDescripcion()
              + "» está programada para el "
              + FORMATO_FECHA.format(orden.getFechaProgramada())
              + ".",
          "orden-programada-proxima:",
          resultado);
    }
    for (OrdenMantenimiento orden : vencidas) {
      avisarOrden(
          orden,
          administradores,
          TIPO_VENCIDA,
          "Orden de mantenimiento vencida",
          "La orden «"
              + orden.getDescripcion()
              + "» estaba programada para el "
              + FORMATO_FECHA.format(orden.getFechaProgramada())
              + " y no se ha iniciado.",
          "orden-programada-vencida:",
          resultado);
    }
    auditoria.registrarProceso(
        ProcesosProgramados.ALERTA_ORDENES_PROGRAMADAS,
        json.createObjectNode()
            .put("proximas", proximas.size())
            .put("vencidas", vencidas.size())
            .put("diasAnticipacion", diasAnticipacion)
            .put("destinatarios", resultado[0])
            .put("fallos", resultado[1]));
  }

  private void avisarOrden(
      OrdenMantenimiento orden,
      List<UUID> administradores,
      String tipo,
      String titulo,
      String mensaje,
      String prefijoClave,
      int[] resultado) {
    try {
      Set<UUID> avisar = new LinkedHashSet<>(administradores);
      if (orden.getAceptadoPorId() != null) {
        avisar.addAll(usuarios.filtrarActivos(List.of(orden.getAceptadoPorId())));
      }
      String clave = prefijoClave + orden.getId() + ":" + orden.getFechaProgramada();
      for (UUID usuarioId : avisar) {
        notificaciones.notificar(
            usuarioId, tipo, titulo, mensaje, "orden_mantenimiento", orden.getId(), clave);
        resultado[0]++;
      }
    } catch (DataIntegrityViolationException e) {
      // Una ejecución simultánea ya registró el mismo aviso; la clave de evento lo impide.
      LOG.debug("Aviso ya registrado para la orden {}", orden.getId());
    } catch (RuntimeException e) {
      resultado[1]++;
      LOG.warn("No se pudo avisar la orden programada {}", orden.getId(), e);
    }
  }
}
