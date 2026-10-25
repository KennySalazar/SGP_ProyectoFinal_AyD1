package gt.usac.cunoc.sgp.mantenimiento.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.mantenimiento.dto.ActividadPlanMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.ActualizarPlanMantenimientoRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.PlanMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.entity.PlanMantenimientoRutinario;
import gt.usac.cunoc.sgp.mantenimiento.repository.PlanMantenimientoRutinarioRepository;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanMantenimientoService {

  static final String ENTIDAD = "plan_mantenimiento_rutinario";
  static final String PROCESO_BACKFILL = "hu068-backfill-planes-rutinarios";
  private static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");

  private final PlanMantenimientoRutinarioRepository planes;
  private final PuenteRepository puentes;
  private final OrdenCicloPlanService ciclos;
  private final AuditoriaWriter auditoria;
  private final ObjectMapper json;
  private final Clock clock;

  public PlanMantenimientoService(
      PlanMantenimientoRutinarioRepository planes,
      PuenteRepository puentes,
      OrdenCicloPlanService ciclos,
      AuditoriaWriter auditoria,
      ObjectMapper json,
      Clock clock) {
    this.planes = planes;
    this.puentes = puentes;
    this.ciclos = ciclos;
    this.auditoria = auditoria;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public PlanMantenimientoRutinario crearPorDefecto(Puente puente, UUID actorId) {
    if (!puente.isActivo()) return null;
    return planes.findByPuenteId(puente.getId()).orElseGet(() -> crear(puente, actorId, null));
  }

  /** Reconciliación idempotente de puentes activos antiguos, también usada al arrancar el job. */
  @Transactional
  public void crearPlanesFaltantes() {
    for (UUID puenteId : planes.findActiveBridgesWithoutPlan()) {
      Puente puente = puentes.findByIdForUpdate(puenteId).orElse(null);
      if (puente == null || !puente.isActivo() || planes.existsByPuente_Id(puenteId)) continue;
      crear(puente, null, PROCESO_BACKFILL);
    }
  }

  @Transactional(readOnly = true)
  public PlanMantenimientoResponse obtenerPorPuente(UUID puenteId) {
    return planes.findByPuenteId(puenteId).map(this::respuesta).orElseThrow(this::noEncontrado);
  }

  @Transactional(readOnly = true)
  public List<PlanMantenimientoResponse> listar() {
    return planes.findAllForListing().stream().map(this::respuesta).toList();
  }

  @Transactional
  public PlanMantenimientoResponse actualizar(
      UUID puenteId, ActualizarPlanMantenimientoRequest request, UUID actorId) {
    if (request.periodicidadMeses() == null
        || request.periodicidadMeses() < 1
        || request.periodicidadMeses() > 12) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "periodicidad_plan_invalida",
          "Periodicidad inválida",
          "La periodicidad debe estar entre 1 y 12 meses.",
          List.of(Map.of("campo", "periodicidadMeses", "mensaje", "Use un valor entre 1 y 12.")));
    }
    PlanMantenimientoRutinario plan =
        planes.findByPuenteIdForUpdate(puenteId).orElseThrow(this::noEncontrado);
    if (plan.getVersion() != request.version()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "version_plan_desactualizada",
          "El plan cambió",
          "Recargue el plan antes de guardar los cambios.");
    }

    ObjectNode antes = snapshot(plan);
    List<String> actividades = request.actividades().stream().map(String::strip).toList();
    plan.actualizar(request.periodicidadMeses().shortValue(), actividades, clock.instant());
    PlanMantenimientoRutinario guardado = planes.saveAndFlush(plan);
    auditoria.registrarEnTransaccion(
        actorId,
        AccionAuditoria.MODIFICAR,
        ENTIDAD,
        guardado.getId(),
        antes,
        snapshot(guardado),
        null);
    return respuesta(guardado);
  }

  @Transactional
  public void generarCiclosVencidos() {
    crearPlanesFaltantes();
    LocalDate hoy = LocalDate.now(clock.withZone(ZONA_GUATEMALA));
    for (PlanMantenimientoRutinario plan : planes.findDueCandidates()) {
      generarCiclos(plan, hoy);
    }
  }

  private void generarCiclos(PlanMantenimientoRutinario plan, LocalDate hoy) {
    LocalDate inicio = plan.getFechaInicio();
    int periodo = plan.getPeriodicidadMeses();
    long mesesTranscurridos =
        ChronoUnit.MONTHS.between(inicio.withDayOfMonth(1), hoy.withDayOfMonth(1));
    int ultimoMesCicloVencido = (int) (mesesTranscurridos / periodo) * periodo;
    if (ultimoMesCicloVencido < periodo
        || !planes.lockBridgeActive(plan.getPuente().getId()).orElse(false)) {
      return;
    }
    for (int ciclo = periodo; ciclo <= ultimoMesCicloVencido; ciclo += periodo) {
      LocalDate fechaCiclo = inicio.plusMonths(ciclo);
      if (fechaCiclo.isAfter(hoy) || ciclos.existe(plan.getId(), ciclo)) continue;
      ciclos.generar(plan, ciclo, fechaCiclo);
    }
  }

  private PlanMantenimientoRutinario crear(Puente puente, UUID actorId, String proceso) {
    Instant ahora = clock.instant();
    PlanMantenimientoRutinario plan =
        planes.save(
            new PlanMantenimientoRutinario(
                UuidV7Generator.generate(),
                puente,
                LocalDate.now(clock.withZone(ZONA_GUATEMALA)),
                ahora));
    auditoria.registrarEnTransaccion(
        actorId, AccionAuditoria.CREAR, ENTIDAD, plan.getId(), null, snapshot(plan), proceso);
    return plan;
  }

  private PlanMantenimientoResponse respuesta(PlanMantenimientoRutinario plan) {
    var puente = plan.getPuente();
    int periodo = plan.getPeriodicidadMeses();
    if (!puente.isActivo() || !plan.isActivo()) {
      return new PlanMantenimientoResponse(
          plan.getId(),
          puente.getId(),
          puente.getCodigo(),
          puente.getNombre(),
          puente.isActivo(),
          plan.getPeriodicidadMeses(),
          plan.getFechaInicio(),
          null,
          null,
          plan.getResponsableUsuarioId(),
          plan.getAprobadoPorId(),
          plan.getAprobadoEn(),
          plan.getActividades().stream()
              .map(
                  a ->
                      new ActividadPlanMantenimientoResponse(
                          a.getId(), a.getPosicion(), a.getDescripcion()))
              .toList(),
          plan.getVersion());
    }
    long meses =
        Math.max(
            0,
            ChronoUnit.MONTHS.between(
                plan.getFechaInicio().withDayOfMonth(1),
                LocalDate.now(clock.withZone(ZONA_GUATEMALA)).withDayOfMonth(1)));
    int ultimoCicloVencido = Math.toIntExact((meses / periodo) * periodo);
    int ciclo = periodo;
    while (ciclo <= ultimoCicloVencido && ciclos.existe(plan.getId(), ciclo)) {
      ciclo += periodo;
    }
    if (ciclo > ultimoCicloVencido) ciclo = ultimoCicloVencido + periodo;
    return new PlanMantenimientoResponse(
        plan.getId(),
        puente.getId(),
        puente.getCodigo(),
        puente.getNombre(),
        puente.isActivo(),
        periodo,
        plan.getFechaInicio(),
        ciclo,
        plan.getFechaInicio().plusMonths(ciclo),
        plan.getResponsableUsuarioId(),
        plan.getAprobadoPorId(),
        plan.getAprobadoEn(),
        plan.getActividades().stream()
            .map(
                a ->
                    new ActividadPlanMantenimientoResponse(
                        a.getId(), a.getPosicion(), a.getDescripcion()))
            .toList(),
        plan.getVersion());
  }

  private ObjectNode snapshot(PlanMantenimientoRutinario plan) {
    ObjectNode node =
        json.createObjectNode()
            .put("puenteId", plan.getPuente().getId().toString())
            .put("periodicidadMeses", plan.getPeriodicidadMeses())
            .put("fechaInicio", plan.getFechaInicio().toString());
    ArrayNode actividades = node.putArray("actividades");
    plan.getActividades().forEach(a -> actividades.add(a.getDescripcion()));
    return node;
  }

  private ApiException noEncontrado() {
    return new ApiException(
        HttpStatus.NOT_FOUND,
        "plan_mantenimiento_no_encontrado",
        "Plan no encontrado",
        "No existe un plan rutinario para el puente solicitado.");
  }
}
