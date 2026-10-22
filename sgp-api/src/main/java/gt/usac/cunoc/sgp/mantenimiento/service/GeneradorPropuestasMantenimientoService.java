package gt.usac.cunoc.sgp.mantenimiento.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.formulario.entity.ConfiguracionIcElemento;
import gt.usac.cunoc.sgp.formulario.repository.ConfiguracionIcElementoRepository;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.entity.ResultadoIc;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoInspeccion;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.repository.ResultadoIcRepository;
import gt.usac.cunoc.sgp.inspeccion.service.ResolverElementosIc;
import gt.usac.cunoc.sgp.inspeccion.service.ResolverElementosIc.ElementoResuelto;
import gt.usac.cunoc.sgp.mantenimiento.dto.GeneracionPropuestasResponse;
import gt.usac.cunoc.sgp.mantenimiento.entity.HistorialEstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.entity.ReglaMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.PrioridadOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import gt.usac.cunoc.sgp.mantenimiento.repository.HistorialEstadoOrdenRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.ReglaMantenimientoRepository;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Propone órdenes de mantenimiento a partir de los daños de una inspección publicada (HU-046,
 * RN-MTO-01, RN-MTO-02, RN-MTO-03).
 *
 * <p>Evalúa las reglas activas de la versión del formulario con que se levantó la inspección. Cada
 * condición que se cumple genera una orden en PROPUESTA: nunca aceptada ni programada. Es
 * idempotente: la clave de generación impide duplicar y una orden aún en curso sobre el mismo
 * elemento y regla no se vuelve a proponer.
 */
@Service
public class GeneradorPropuestasMantenimientoService {

  private static final Logger log =
      LoggerFactory.getLogger(GeneradorPropuestasMantenimientoService.class);

  /** Marca el proceso automático en la bitácora (RN-USR-09). */
  static final String PROCESO = "generar-propuestas-mantenimiento";

  static final String TIPO_AVISO = "MANTENIMIENTO_PROPUESTAS";

  private static final Set<EstadoOrden> ESTADOS_EN_CURSO =
      EnumSet.of(EstadoOrden.PROPUESTA, EstadoOrden.PROGRAMADA, EstadoOrden.EN_EJECUCION);

  private final InspeccionRepository inspecciones;
  private final ReglaMantenimientoRepository reglas;
  private final OrdenMantenimientoRepository ordenes;
  private final HistorialEstadoOrdenRepository historial;
  private final ResultadoIcRepository resultadosIc;
  private final ConfiguracionIcElementoRepository configuracionesIc;
  private final UserAccountRepository usuarios;
  private final ResolverElementosIc resolverElementos;
  private final EvaluadorCondicionRegla evaluador;
  private final AuditoriaWriter auditoria;
  private final NotificacionService notificaciones;
  private final ObjectMapper json;
  private final Clock clock;

  public GeneradorPropuestasMantenimientoService(
      InspeccionRepository inspecciones,
      ReglaMantenimientoRepository reglas,
      OrdenMantenimientoRepository ordenes,
      HistorialEstadoOrdenRepository historial,
      ResultadoIcRepository resultadosIc,
      ConfiguracionIcElementoRepository configuracionesIc,
      UserAccountRepository usuarios,
      ResolverElementosIc resolverElementos,
      EvaluadorCondicionRegla evaluador,
      AuditoriaWriter auditoria,
      NotificacionService notificaciones,
      ObjectMapper json,
      Clock clock) {
    this.inspecciones = inspecciones;
    this.reglas = reglas;
    this.ordenes = ordenes;
    this.historial = historial;
    this.resultadosIc = resultadosIc;
    this.configuracionesIc = configuracionesIc;
    this.usuarios = usuarios;
    this.resolverElementos = resolverElementos;
    this.evaluador = evaluador;
    this.auditoria = auditoria;
    this.notificaciones = notificaciones;
    this.json = json;
    this.clock = clock;
  }

  /**
   * Generación automática, disparada después de confirmar una publicación. Corre en su propia
   * transacción: si falla, la publicación ya confirmada no se revierte (HU-046).
   *
   * @param publicadorId quien publicó o aprobó; recibe el aviso junto con los Administradores
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public GeneracionPropuestasResponse generarTrasPublicacion(UUID inspeccionId, UUID publicadorId) {
    return ejecutar(inspeccionId, null, PROCESO, publicadorId);
  }

  /**
   * Un Administrador vuelve a evaluar una inspección, por ejemplo si la generación falló. Solo se
   * crean las propuestas que faltan.
   */
  @Transactional
  public GeneracionPropuestasResponse regenerar(UUID inspeccionId, UUID administradorId) {
    return ejecutar(inspeccionId, administradorId, null, administradorId);
  }

  private GeneracionPropuestasResponse ejecutar(
      UUID inspeccionId, UUID actor, String proceso, UUID destinatarioAdicional) {
    Inspeccion inspeccion =
        inspecciones
            .findByIdAndEliminadoEnIsNullForUpdate(inspeccionId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "inspeccion_no_encontrada",
                        "Inspección no encontrada",
                        "No existe la inspección " + inspeccionId));
    if (inspeccion.estadoActual() != EstadoInspeccion.PUBLICADA) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "inspeccion_no_publicada",
          "Inspección no publicada",
          "Solo una inspección publicada genera propuestas de mantenimiento.");
    }

    UUID puenteId = inspeccion.getPuente().getId();
    UUID versionId = inspeccion.getVersionFormulario().getId();
    ResultadoIc resultadoIc = resultadosIc.findPublicacionByInspeccionId(inspeccionId).orElse(null);
    List<ConfiguracionIcElemento> pesosConfigurados =
        configuracionesIc.findAllByVersionFormularioId(versionId);
    List<ReglaMantenimiento> activas =
        reglas.findByVersionFormularioIdAndActivaTrue(versionId).stream()
            .sorted(Comparator.comparing(ReglaMantenimiento::getCodigo))
            .toList();

    Contadores contadores = new Contadores(activas.size());
    for (ReglaMantenimiento regla : activas) {
      try {
        evaluarRegla(
            regla,
            inspeccion,
            puenteId,
            resultadoIc,
            pesosConfigurados,
            versionId,
            actor,
            proceso,
            contadores);
      } catch (RuntimeException e) {
        // Una regla o un dato defectuoso no debe impedir evaluar las demás.
        contadores.conError++;
        log.warn(
            "No se pudo evaluar la regla {} sobre la inspección {}: {}",
            regla.getCodigo(),
            inspeccionId,
            e.getMessage());
      }
    }

    if (contadores.creadas > 0) {
      avisar(inspeccionId, contadores.creadas, destinatarioAdicional);
    }
    return new GeneracionPropuestasResponse(
        inspeccionId,
        contadores.evaluadas,
        contadores.creadas,
        contadores.yaGeneradas,
        contadores.omitidas,
        contadores.conError);
  }

  private void evaluarRegla(
      ReglaMantenimiento regla,
      Inspeccion inspeccion,
      UUID puenteId,
      ResultadoIc resultadoIc,
      List<ConfiguracionIcElemento> pesosConfigurados,
      UUID versionId,
      UUID actor,
      String proceso,
      Contadores contadores) {
    List<ElementoResuelto> elementos =
        resolverElementos.resolver(inspeccion.getDatos(), regla.getElementoRef());
    for (ElementoResuelto elemento : elementos) {
      Optional<JsonNode> hallado = evaluador.evaluar(regla.getCondicion(), elemento.datos());
      if (hallado.isEmpty()) {
        continue;
      }
      String clave = claveGeneracion(regla, elemento);
      if (ordenes.existsByInspeccionOrigenIdAndClaveGeneracion(inspeccion.getId(), clave)) {
        contadores.yaGeneradas++;
        continue;
      }
      if (hayOrdenEnCurso(puenteId, regla, elemento, versionId)) {
        contadores.omitidas++;
        continue;
      }
      crear(
          regla,
          inspeccion,
          puenteId,
          resultadoIc,
          pesosConfigurados,
          elemento,
          clave,
          hallado.get(),
          actor,
          proceso);
      contadores.creadas++;
    }
  }

  private static String claveGeneracion(ReglaMantenimiento regla, ElementoResuelto elemento) {
    return regla.getCodigo()
        + ":"
        + (elemento.instanciaId() != null ? elemento.instanciaId() : "-");
  }

  /**
   * Hay una orden en curso del mismo puente y elemento que viene de cualquier revisión de la regla.
   */
  private boolean hayOrdenEnCurso(
      UUID puenteId, ReglaMantenimiento regla, ElementoResuelto elemento, UUID versionId) {
    Set<UUID> revisiones =
        reglas
            .findByCodigoAndVersionFormularioIdOrderByNumeroRevisionDesc(
                regla.getCodigo(), versionId)
            .stream()
            .map(ReglaMantenimiento::getId)
            .collect(Collectors.toSet());
    return ordenes
        .findPorElementoYEstados(puenteId, regla.getElementoRef(), ESTADOS_EN_CURSO)
        .stream()
        .anyMatch(
            orden ->
                revisiones.contains(orden.getReglaOrigenId())
                    && Objects.equals(orden.getInstanciaElementoId(), elemento.instanciaId()));
  }

  private void crear(
      ReglaMantenimiento regla,
      Inspeccion inspeccion,
      UUID puenteId,
      ResultadoIc resultadoIc,
      List<ConfiguracionIcElemento> pesosConfigurados,
      ElementoResuelto elemento,
      String clave,
      JsonNode valorEncontrado,
      UUID actor,
      String proceso) {
    Instant ahora = clock.instant();
    TipoOrden tipo = regla.getTipoMantenimiento();
    PrioridadOrden prioridadBase = regla.getPrioridadBase();
    PrioridadSugeridaIc.Calculo calculo =
        PrioridadSugeridaIc.calcular(
            prioridadBase,
            resultadoIc,
            inspeccion.getVersionFormulario().getId(),
            regla.getElementoRef(),
            pesosConfigurados);
    PrioridadOrden prioridad = calculo.prioridad();
    OrdenMantenimiento orden =
        ordenes.saveAndFlush(
            OrdenMantenimiento.propuestaGenerada(
                UuidV7Generator.generate(),
                puenteId,
                inspeccion.getId(),
                regla.getId(),
                resultadoIc == null ? null : resultadoIc.getId(),
                regla.getElementoRef(),
                elemento.instanciaId(),
                clave,
                tipo,
                regla.getIntervencionSugerida(),
                prioridad,
                fundamento(regla, elemento, valorEncontrado, calculo),
                ahora));
    historial.save(
        new HistorialEstadoOrden(
            UuidV7Generator.generate(),
            orden.getId(),
            1,
            null,
            EstadoOrden.PROPUESTA.name(),
            actor,
            null,
            ahora));

    ObjectNode despues =
        json.createObjectNode()
            .put("estado", orden.getEstado().name())
            .put("tipo", tipo.name())
            .put("prioridad", prioridad.name())
            .put("puenteId", puenteId.toString())
            .put("inspeccionOrigenId", inspeccion.getId().toString())
            .put("reglaCodigo", regla.getCodigo())
            .put("elementoRef", regla.getElementoRef());
    despues.put("prioridadSugerida", prioridad.name());
    auditoria.registrarEnTransaccion(
        actor,
        AccionAuditoria.CREAR,
        TransicionOrdenService.ENTIDAD,
        orden.getId(),
        null,
        despues,
        proceso);
  }

  /** Por qué se propone: qué valor se encontró y qué regla lo disparó. */
  private ObjectNode fundamento(
      ReglaMantenimiento regla,
      ElementoResuelto elemento,
      JsonNode valorEncontrado,
      PrioridadSugeridaIc.Calculo calculo) {
    JsonNode condicion = regla.getCondicion();
    ObjectNode fundamento =
        json.createObjectNode()
            .put("reglaCodigo", regla.getCodigo())
            .put("reglaRevision", regla.getNumeroRevision())
            .put("elementoRef", regla.getElementoRef())
            .put("indicador", condicion.path("indicador").asText())
            .put("operador", condicion.path("operador").asText());
    fundamento.set("umbral", condicion.get("umbral"));
    fundamento.set("valorEncontrado", valorEncontrado);
    fundamento.put("prioridadBase", regla.getPrioridadBase().name());
    fundamento.put("prioridadSugerida", calculo.prioridad().name());
    if (calculo.ic() != null) fundamento.put("indiceCondicion", calculo.ic().toPlainString());
    if (calculo.pesoElemento() != null) fundamento.put("pesoElemento", calculo.pesoElemento().toPlainString());
    if (calculo.pesoMaximo() != null) fundamento.put("pesoMaximoVersion", calculo.pesoMaximo().toPlainString());
    if (calculo.pesoRelativo() != null) fundamento.put("pesoRelativo", calculo.pesoRelativo().toPlainString());
    fundamento.put("formulaPrioridad", "HU-067-OPCION-3");
    if (calculo.motivoFallback() != null) fundamento.put("motivoFallbackPrioridad", calculo.motivoFallback());
    if (elemento.instanciaId() != null) {
      fundamento.put("instanciaElementoId", elemento.instanciaId().toString());
    }
    return fundamento;
  }

  /** Un solo aviso por inspección y destinatario: los Administradores y quien publicó. */
  private void avisar(UUID inspeccionId, int creadas, UUID destinatarioAdicional) {
    Set<UUID> destinatarios = new LinkedHashSet<>();
    usuarios.findByRole_NameAndActiveTrueOrderByEmailAsc(RoleName.ADMINISTRADOR).stream()
        .map(UserAccount::getId)
        .forEach(destinatarios::add);
    if (destinatarioAdicional != null) {
      destinatarios.add(destinatarioAdicional);
    }
    String mensaje =
        creadas == 1
            ? "Se generó 1 propuesta de mantenimiento a partir de una inspección publicada."
            : "Se generaron "
                + creadas
                + " propuestas de mantenimiento a partir de una inspección publicada.";
    for (UUID destinatario : destinatarios) {
      notificaciones.notificar(
          destinatario,
          TIPO_AVISO,
          "Nuevas propuestas de mantenimiento",
          mensaje,
          "inspeccion",
          inspeccionId,
          "mantenimiento-propuestas:" + inspeccionId);
    }
  }

  private static final class Contadores {
    final int evaluadas;
    int creadas;
    int yaGeneradas;
    int omitidas;
    int conError;

    Contadores(int evaluadas) {
      this.evaluadas = evaluadas;
    }
  }
}
