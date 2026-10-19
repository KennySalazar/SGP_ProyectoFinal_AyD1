package gt.usac.cunoc.sgp.mantenimiento.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.mantenimiento.dto.CrearOrdenMantenimientoRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.HistorialEstadoOrdenResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.OrdenMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.entity.HistorialEstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.mapper.OrdenMantenimientoMapper;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.PrioridadOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import gt.usac.cunoc.sgp.mantenimiento.repository.HistorialEstadoOrdenRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoRepository;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta manual y consulta de órdenes de mantenimiento (RN-MTO-01, RN-MTO-03). Los cambios de estado
 * pertenecen a {@link TransicionOrdenService} (DT-BE-07).
 */
@Service
public class OrdenMantenimientoService {

  static final int LONGITUD_MAXIMA_DESCRIPCION = 4000;
  static final int LONGITUD_MAXIMA_ELEMENTO_REF = 300;

  private final OrdenMantenimientoRepository ordenes;
  private final HistorialEstadoOrdenRepository historial;
  private final PuenteRepository puentes;
  private final InspeccionRepository inspecciones;
  private final AuditoriaWriter auditoria;
  private final OrdenMantenimientoMapper mapper;
  private final ObjectMapper json;
  private final Clock clock;

  public OrdenMantenimientoService(
      OrdenMantenimientoRepository ordenes,
      HistorialEstadoOrdenRepository historial,
      PuenteRepository puentes,
      InspeccionRepository inspecciones,
      AuditoriaWriter auditoria,
      OrdenMantenimientoMapper mapper,
      ObjectMapper json,
      Clock clock) {
    this.ordenes = ordenes;
    this.historial = historial;
    this.puentes = puentes;
    this.inspecciones = inspecciones;
    this.auditoria = auditoria;
    this.mapper = mapper;
    this.json = json;
    this.clock = clock;
  }

  /**
   * Crea una orden manual en PROPUESTA para un puente activo. Si indica una inspección, debe ser
   * del mismo puente (RN-MTO-01). Valida todos los campos y responde un único 422 con el detalle.
   */
  @Transactional
  public OrdenMantenimientoResponse crear(CrearOrdenMantenimientoRequest request, UUID usuarioId) {
    List<Map<String, String>> errores = new ArrayList<>();
    if (request.puenteId() == null) {
      errores.add(error("puenteId", "El puente es obligatorio."));
    }
    TipoOrden tipo = convertir(TipoOrden.class, request.tipo(), "tipo", errores);
    PrioridadOrden prioridad =
        convertir(PrioridadOrden.class, request.prioridad(), "prioridad", errores);
    String descripcion = texto(request.descripcion());
    if (descripcion == null) {
      errores.add(error("descripcion", "La descripcion es obligatoria."));
    } else if (descripcion.length() > LONGITUD_MAXIMA_DESCRIPCION) {
      errores.add(
          error(
              "descripcion",
              "La descripcion no puede exceder " + LONGITUD_MAXIMA_DESCRIPCION + " caracteres."));
    }
    String elementoRef = texto(request.elementoRef());
    if (elementoRef != null && elementoRef.length() > LONGITUD_MAXIMA_ELEMENTO_REF) {
      errores.add(
          error(
              "elementoRef",
              "El elemento no puede exceder " + LONGITUD_MAXIMA_ELEMENTO_REF + " caracteres."));
    }
    if (request.instanciaElementoId() != null && elementoRef == null) {
      errores.add(error("instanciaElementoId", "La instancia requiere indicar el elemento."));
    }
    if (!errores.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "orden_invalida",
          "Orden invalida",
          "La orden contiene datos invalidos.",
          errores);
    }

    Puente puente =
        puentes
            .findById(request.puenteId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "puente_no_encontrado",
                        "Puente no encontrado",
                        "No existe el puente " + request.puenteId()));
    if (!puente.isActivo()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "puente_inactivo",
          "Puente inactivo",
          "No se pueden crear ordenes para un puente dado de baja.");
    }
    if (request.inspeccionOrigenId() != null) {
      exigirInspeccionDelPuente(request.inspeccionOrigenId(), puente.getId());
    }

    Instant ahora = clock.instant();
    OrdenMantenimiento orden =
        ordenes.saveAndFlush(
            new OrdenMantenimiento(
                UuidV7Generator.generate(),
                puente.getId(),
                request.inspeccionOrigenId(),
                elementoRef,
                request.instanciaElementoId(),
                tipo,
                descripcion,
                prioridad,
                usuarioId,
                ahora));
    historial.save(
        new HistorialEstadoOrden(
            UuidV7Generator.generate(),
            orden.getId(),
            1,
            null,
            EstadoOrden.PROPUESTA.name(),
            usuarioId,
            null,
            ahora));

    ObjectNode despues =
        json.createObjectNode()
            .put("estado", orden.getEstado().name())
            .put("tipo", tipo.name())
            .put("prioridad", prioridad.name())
            .put("puenteId", puente.getId().toString())
            .put("descripcion", descripcion);
    if (orden.getInspeccionOrigenId() != null) {
      despues.put("inspeccionOrigenId", orden.getInspeccionOrigenId().toString());
    }
    if (elementoRef != null) {
      despues.put("elementoRef", elementoRef);
    }
    auditoria.registrarEnTransaccion(
        usuarioId,
        AccionAuditoria.CREAR,
        TransicionOrdenService.ENTIDAD,
        orden.getId(),
        null,
        despues,
        null);
    return mapper.toResponse(orden);
  }

  /** Detalle de una orden; también se consultan las de puentes dados de baja. */
  @Transactional(readOnly = true)
  public OrdenMantenimientoResponse obtener(UUID ordenId) {
    return mapper.toResponse(buscar(ordenId));
  }

  /** Órdenes filtradas por puente y estado, de la más reciente a la más antigua. */
  @Transactional(readOnly = true)
  public Page<OrdenMantenimientoResponse> listar(
      UUID puenteId, String estado, int pagina, int tamanio) {
    if (pagina < 0 || tamanio < 1 || tamanio > 100) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "paginacion_invalida",
          "Paginación inválida",
          "La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.");
    }
    List<Map<String, String>> errores = new ArrayList<>();
    EstadoOrden filtroEstado =
        estado == null || estado.isBlank()
            ? null
            : convertir(EstadoOrden.class, estado, "estado", errores);
    if (!errores.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "filtro_invalido",
          "Filtro invalido",
          "El estado indicado no existe.",
          errores);
    }
    PageRequest orden =
        PageRequest.of(
            pagina, tamanio, Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id")));
    return ordenes.buscar(puenteId, filtroEstado, orden).map(mapper::toResponse);
  }

  /** Historial de estados ordenado por secuencia; nunca se modifica (RN-MTO-04). */
  @Transactional(readOnly = true)
  public List<HistorialEstadoOrdenResponse> historial(UUID ordenId) {
    buscar(ordenId);
    return historial.findByOrdenMantenimientoIdOrderBySecuenciaAsc(ordenId).stream()
        .map(mapper::toHistorialResponse)
        .toList();
  }

  private void exigirInspeccionDelPuente(UUID inspeccionId, UUID puenteId) {
    UUID puenteDeInspeccion =
        inspecciones
            .findPuenteIdByInspeccionId(inspeccionId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "inspeccion_no_encontrada",
                        "Inspeccion no encontrada",
                        "No existe la inspeccion " + inspeccionId));
    if (!puenteDeInspeccion.equals(puenteId)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "inspeccion_de_otro_puente",
          "Inspeccion de otro puente",
          "La inspeccion indicada no pertenece al puente de la orden.");
    }
  }

  private OrdenMantenimiento buscar(UUID ordenId) {
    return ordenes
        .findById(ordenId)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "orden_no_encontrada",
                    "Orden no encontrada",
                    "No existe la orden de mantenimiento " + ordenId));
  }

  /** Convierte un valor textual al enum o agrega el error con los valores permitidos. */
  private static <E extends Enum<E>> E convertir(
      Class<E> tipo, String valor, String campo, List<Map<String, String>> errores) {
    String limpio = texto(valor);
    if (limpio == null) {
      errores.add(error(campo, "El campo es obligatorio."));
      return null;
    }
    try {
      return Enum.valueOf(tipo, limpio);
    } catch (IllegalArgumentException e) {
      String permitidos =
          Arrays.stream(tipo.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
      errores.add(error(campo, "Valor no permitido. Use uno de: " + permitidos + "."));
      return null;
    }
  }

  private static String texto(String valor) {
    return valor == null || valor.isBlank() ? null : valor.strip();
  }

  private static Map<String, String> error(String campo, String mensaje) {
    return Map.of("campo", campo, "mensaje", mensaje);
  }
}
