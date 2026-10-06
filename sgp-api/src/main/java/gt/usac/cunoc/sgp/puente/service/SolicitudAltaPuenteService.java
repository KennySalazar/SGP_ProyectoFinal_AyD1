package gt.usac.cunoc.sgp.puente.service;

import gt.usac.cunoc.sgp.common.audit.aspect.Auditable;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.puente.dto.CrearSolicitudAltaPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.SolicitudAltaPuenteResponse;
import gt.usac.cunoc.sgp.puente.entity.Municipio;
import gt.usac.cunoc.sgp.puente.entity.SolicitudAltaPuente;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapper;
import gt.usac.cunoc.sgp.puente.repository.DepartamentoRepository;
import gt.usac.cunoc.sgp.puente.repository.MunicipioRepository;
import gt.usac.cunoc.sgp.puente.repository.SolicitudAltaPuenteRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.UUID;
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
  private final SolicitudAltaPuenteRepository solicitudes;
  private final PuenteService puenteService;
  private final PuenteMapper mapper;
  private final Validator validator;
  private final Clock clock;

  public SolicitudAltaPuenteService(
      DepartamentoRepository departamentos,
      MunicipioRepository municipios,
      SolicitudAltaPuenteRepository solicitudes,
      PuenteService puenteService,
      PuenteMapper mapper,
      Validator validator,
      Clock clock) {
    this.departamentos = departamentos;
    this.municipios = municipios;
    this.solicitudes = solicitudes;
    this.puenteService = puenteService;
    this.mapper = mapper;
    this.validator = validator;
    this.clock = clock;
  }

  @Transactional
  @PreAuthorize("hasRole('CATEDRATICO')")
  @Auditable(accion = AccionAuditoria.CREAR, entidad = "solicitud_alta_puente")
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
    if (pagina < 0 || tamanio < 1 || tamanio > 100) {
      throw validacion(
          "paginacion_invalida",
          "La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.");
    }

    var pageable =
        PageRequest.of(
            pagina, tamanio, Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id")));

    return solicitudes
        .findBySolicitadoPorId(catedraticoId, pageable)
        .map(mapper::toSolicitudResponse);
  }

  private ApiException validacion(String codigo, String detalle) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY, codigo, "Datos de puente invalidos", detalle);
  }
}
