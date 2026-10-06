package gt.usac.cunoc.sgp.puente.service;

import gt.usac.cunoc.sgp.common.audit.aspect.Auditable;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import gt.usac.cunoc.sgp.puente.dto.AprobarSolicitudAltaPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.CrearPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.CrearSolicitudAltaPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.RechazarSolicitudAltaPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.SolicitudAltaPuenteResponse;
import gt.usac.cunoc.sgp.puente.dto.SolicitudRevisionDetalleResponse;
import gt.usac.cunoc.sgp.puente.dto.SolicitudRevisionResponse;
import gt.usac.cunoc.sgp.puente.entity.Municipio;
import gt.usac.cunoc.sgp.puente.entity.SolicitudAltaPuente;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapper;
import gt.usac.cunoc.sgp.puente.model.EstadoSolicitudAltaPuente;
import gt.usac.cunoc.sgp.puente.repository.DepartamentoRepository;
import gt.usac.cunoc.sgp.puente.repository.MunicipioRepository;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.puente.repository.SolicitudAltaPuenteRepository;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Solicitudes de alta de puente del Catedrático (RN-INV-07). */
@Service
public class SolicitudAltaPuenteService {

  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(), 4326);

  private final DepartamentoRepository departamentos;
  private final MunicipioRepository municipios;
  private static final String ENTIDAD = "solicitud_alta_puente";

  private final SolicitudAltaPuenteRepository solicitudes;
  private final PuenteRepository puentes;
  private final UserAccountRepository usuarios;
  private final PuenteService puenteService;
  private final NotificacionService notificaciones;
  private final PuenteMapper mapper;
  private final Validator validator;
  private final Clock clock;

  public SolicitudAltaPuenteService(
      DepartamentoRepository departamentos,
      MunicipioRepository municipios,
      SolicitudAltaPuenteRepository solicitudes,
      PuenteRepository puentes,
      UserAccountRepository usuarios,
      PuenteService puenteService,
      NotificacionService notificaciones,
      PuenteMapper mapper,
      Validator validator,
      Clock clock) {
    this.departamentos = departamentos;
    this.municipios = municipios;
    this.solicitudes = solicitudes;
    this.puentes = puentes;
    this.usuarios = usuarios;
    this.puenteService = puenteService;
    this.notificaciones = notificaciones;
    this.mapper = mapper;
    this.validator = validator;
    this.clock = clock;
  }

  @Transactional
  @PreAuthorize("hasRole('CATEDRATICO')")
  @Auditable(accion = AccionAuditoria.CREAR, entidad = ENTIDAD)
  public SolicitudAltaPuenteResponse crear(
      CrearSolicitudAltaPuenteRequest request, UUID catedraticoId) {
    var errores = validator.validate(request);
    if (!errores.isEmpty()) {
      throw new ConstraintViolationException(errores);
    }

    double latitud = request.latitud().doubleValue();
    double longitud = request.longitud().doubleValue();

    puenteService.validarDentroDeGuatemala(latitud, longitud);

    var departamento =
        departamentos
            .findByIdAndActivoTrue(request.departamentoId())
            .orElseThrow(
                () ->
                    validacion(
                        "departamento_invalido", "El departamento no existe o esta inactivo"));

    Municipio municipio =
        municipios
            .findByIdAndActivoTrue(request.municipioId())
            .orElseThrow(
                () -> validacion("municipio_invalido", "El municipio no existe o esta inactivo"));

    if (!municipio.getDepartamento().getId().equals(departamento.getId())) {
      throw validacion(
          "municipio_departamento_incongruente",
          "El municipio no pertenece al departamento seleccionado");
    }

    puenteService.validarUbicacionEnMunicipio(municipio.getId(), latitud, longitud);

    String justificacion =
        request.justificacion() == null || request.justificacion().isBlank()
            ? null
            : request.justificacion().strip();

    SolicitudAltaPuente solicitud =
        new SolicitudAltaPuente(
            UuidV7Generator.generate(),
            catedraticoId,
            request.nombre().strip(),
            municipio,
            request.ruta().strip(),
            request.kilometraje(),
            GEOMETRY_FACTORY.createPoint(new Coordinate(longitud, latitud)),
            justificacion,
            clock.instant());

    return mapper.toSolicitudResponse(solicitudes.saveAndFlush(solicitud));
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('CATEDRATICO')")
  public Page<SolicitudAltaPuenteResponse> listarMias(UUID catedraticoId, int pagina, int tamanio) {
    validarPaginacion(pagina, tamanio);

    var pageable =
        PageRequest.of(
            pagina, tamanio, Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id")));

    return solicitudes
        .findBySolicitadoPorId(catedraticoId, pageable)
        .map(mapper::toSolicitudResponse);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public Page<SolicitudRevisionResponse> listarParaRevision(
      EstadoSolicitudAltaPuente estado, int pagina, int tamanio) {
    validarPaginacion(pagina, tamanio);

    EstadoSolicitudAltaPuente filtro =
        estado == null ? EstadoSolicitudAltaPuente.PENDIENTE : estado;
    // Las pendientes se atienden en orden de llegada; el historial, de la más reciente a la más
    // antigua.
    Sort orden =
        filtro == EstadoSolicitudAltaPuente.PENDIENTE
            ? Sort.by(Sort.Order.asc("creadoEn"), Sort.Order.asc("id"))
            : Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id"));

    Page<SolicitudAltaPuente> resultado =
        solicitudes.findByEstado(filtro, PageRequest.of(pagina, tamanio, orden));
    Map<UUID, String> correos =
        correosDe(
            resultado.getContent().stream().map(SolicitudAltaPuente::getSolicitadoPorId).toList());

    return resultado.map(
        solicitud ->
            new SolicitudRevisionResponse(
                mapper.toSolicitudResponse(solicitud),
                correos.get(solicitud.getSolicitadoPorId())));
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public SolicitudRevisionDetalleResponse obtenerParaRevision(UUID id) {
    SolicitudAltaPuente solicitud =
        solicitudes.findConRelacionesById(id).orElseThrow(this::solicitudNoEncontrada);

    // Misma advertencia de posible duplicado (RN-INV-06) que el alta directa.
    var cercanos =
        puentes.findCercanos(
            solicitud.getUbicacion().getY(),
            solicitud.getUbicacion().getX(),
            PageRequest.of(0, 100));

    return new SolicitudRevisionDetalleResponse(
        mapper.toSolicitudResponse(solicitud),
        correosDe(List.of(solicitud.getSolicitadoPorId())).get(solicitud.getSolicitadoPorId()),
        cercanos.map(mapper::toPuenteCercanoResponse).getContent(),
        cercanos.getTotalElements());
  }

  /**
   * Crea el puente reutilizando {@link PuenteService#registrar}: mismas validaciones territoriales,
   * advertencia de cercanía (409 hasta confirmar), código único y auditoría del alta.
   */
  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(
      accion = AccionAuditoria.CAMBIAR_ESTADO,
      entidad = ENTIDAD,
      tipo = SolicitudAltaPuente.class,
      idArg = "id")
  public SolicitudAltaPuenteResponse aprobar(
      UUID id, AprobarSolicitudAltaPuenteRequest request, UUID administradorId) {
    SolicitudAltaPuente solicitud = solicitudPendienteParaDecidir(id);
    Municipio municipio = solicitud.getMunicipio();

    var puenteCreado =
        puenteService.registrar(
            new CrearPuenteRequest(
                solicitud.getNombrePropuesto(),
                municipio.getDepartamento().getId(),
                municipio.getId(),
                solicitud.getRuta(),
                solicitud.getKilometraje(),
                BigDecimal.valueOf(solicitud.getUbicacion().getY()),
                BigDecimal.valueOf(solicitud.getUbicacion().getX()),
                request.confirmarCercania()),
            administradorId);

    solicitud.aprobar(
        administradorId, puentes.getReferenceById(puenteCreado.id()), clock.instant());
    SolicitudAltaPuente guardada = solicitudes.saveAndFlush(solicitud);

    notificaciones.registrar(
        guardada.getSolicitadoPorId(),
        "SOLICITUD_PUENTE_APROBADA",
        "Solicitud de alta aprobada",
        "Tu solicitud del puente «%s» fue aprobada. Código asignado: %s."
            .formatted(guardada.getNombrePropuesto(), puenteCreado.codigo()),
        ENTIDAD,
        guardada.getId(),
        claveEvento(guardada, "aprobada"));

    return mapper.toSolicitudResponse(guardada);
  }

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(
      accion = AccionAuditoria.CAMBIAR_ESTADO,
      entidad = ENTIDAD,
      tipo = SolicitudAltaPuente.class,
      idArg = "id")
  public SolicitudAltaPuenteResponse rechazar(
      UUID id, RechazarSolicitudAltaPuenteRequest request, UUID administradorId) {
    var errores = validator.validate(request);
    if (!errores.isEmpty()) {
      throw new ConstraintViolationException(errores);
    }

    SolicitudAltaPuente solicitud = solicitudPendienteParaDecidir(id);
    String motivo = request.motivo().strip();

    solicitud.rechazar(administradorId, motivo, clock.instant());
    SolicitudAltaPuente guardada = solicitudes.saveAndFlush(solicitud);

    notificaciones.registrar(
        guardada.getSolicitadoPorId(),
        "SOLICITUD_PUENTE_RECHAZADA",
        "Solicitud de alta rechazada",
        "Tu solicitud del puente «%s» fue rechazada. Motivo: %s"
            .formatted(guardada.getNombrePropuesto(), motivo),
        ENTIDAD,
        guardada.getId(),
        claveEvento(guardada, "rechazada"));

    return mapper.toSolicitudResponse(guardada);
  }

  private SolicitudAltaPuente solicitudPendienteParaDecidir(UUID id) {
    SolicitudAltaPuente solicitud =
        solicitudes.findByIdForUpdate(id).orElseThrow(this::solicitudNoEncontrada);

    if (solicitud.getEstado() != EstadoSolicitudAltaPuente.PENDIENTE) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "solicitud_no_pendiente",
          "Solicitud ya resuelta",
          "La solicitud ya fue " + solicitud.getEstado().name().toLowerCase(Locale.ROOT) + ".");
    }

    return solicitud;
  }

  private ApiException solicitudNoEncontrada() {
    return new ApiException(
        HttpStatus.NOT_FOUND,
        "solicitud_no_encontrada",
        "Solicitud no encontrada",
        "No existe una solicitud de alta con el identificador proporcionado.");
  }

  private Map<UUID, String> correosDe(List<UUID> usuarioIds) {
    return usuarios.findAllById(usuarioIds.stream().distinct().toList()).stream()
        .collect(Collectors.toMap(UserAccount::getId, UserAccount::getEmail, (a, b) -> a));
  }

  private String claveEvento(SolicitudAltaPuente solicitud, String resultado) {
    return "solicitud-alta-puente:" + solicitud.getId() + ":" + resultado;
  }

  private void validarPaginacion(int pagina, int tamanio) {
    if (pagina < 0 || tamanio < 1 || tamanio > 100) {
      throw validacion(
          "paginacion_invalida",
          "La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.");
    }
  }

  private ApiException validacion(String codigo, String detalle) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY, codigo, "Datos de puente invalidos", detalle);
  }
}
