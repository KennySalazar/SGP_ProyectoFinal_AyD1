package gt.usac.cunoc.sgp.mantenimiento.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.mantenimiento.entity.HistorialEstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.entity.PlanMantenimientoRutinario;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.PrioridadOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import gt.usac.cunoc.sgp.mantenimiento.repository.HistorialEstadoOrdenRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenCicloPlanRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrdenCicloPlanService {

  static final String PROCESO = "generar-ciclos-mantenimiento-rutinario";

  private final OrdenCicloPlanRepository ciclos;
  private final OrdenMantenimientoRepository ordenes;
  private final HistorialEstadoOrdenRepository historial;
  private final AuditoriaWriter auditoria;
  private final ObjectMapper json;
  private final Clock clock;

  public OrdenCicloPlanService(
      OrdenCicloPlanRepository ciclos,
      OrdenMantenimientoRepository ordenes,
      HistorialEstadoOrdenRepository historial,
      AuditoriaWriter auditoria,
      ObjectMapper json,
      Clock clock) {
    this.ciclos = ciclos;
    this.ordenes = ordenes;
    this.historial = historial;
    this.auditoria = auditoria;
    this.json = json;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public boolean existe(UUID planId, int ciclo) {
    return ciclos.existsByPlanMantenimientoRutinarioIdAndCicloPlan(planId, ciclo);
  }

  @Transactional
  public void generar(PlanMantenimientoRutinario plan, int ciclo, LocalDate fechaCiclo) {
    if (existe(plan.getId(), ciclo)) return;
    var puente = plan.getPuente();
    String descripcion =
        plan.getActividades().isEmpty()
            ? "Mantenimiento rutinario según el plan del puente."
            : String.join(
                "; ", plan.getActividades().stream().map(a -> a.getDescripcion()).toList());
    ObjectNode fundamento =
        json.createObjectNode()
            .put("planId", plan.getId().toString())
            .put("cicloPlan", ciclo)
            .put("fechaCiclo", fechaCiclo.toString())
            .put("periodicidadMeses", plan.getPeriodicidadMeses());
    ArrayNode actividades = fundamento.putArray("actividades");
    plan.getActividades().forEach(a -> actividades.add(a.getDescripcion()));
    var orden =
        ordenes.saveAndFlush(
            OrdenMantenimiento.propuestaCicloRutinario(
                UuidV7Generator.generate(),
                puente.getId(),
                plan.getId(),
                ciclo,
                descripcion,
                PrioridadOrden.MEDIA,
                fundamento,
                clock.instant()));
    historial.save(
        new HistorialEstadoOrden(
            UuidV7Generator.generate(),
            orden.getId(),
            1,
            null,
            EstadoOrden.PROPUESTA.name(),
            null,
            null,
            orden.getCreadoEn()));
    ObjectNode posterior =
        json.createObjectNode()
            .put("tipo", TipoOrden.RUTINARIO.name())
            .put("estado", EstadoOrden.PROPUESTA.name())
            .put("puenteId", puente.getId().toString())
            .put("planId", plan.getId().toString())
            .put("cicloPlan", ciclo)
            .put("prioridad", PrioridadOrden.MEDIA.name());
    auditoria.registrarEnTransaccion(
        null,
        AccionAuditoria.CREAR,
        TransicionOrdenService.ENTIDAD,
        orden.getId(),
        null,
        posterior,
        PROCESO);
  }
}
