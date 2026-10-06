package gt.usac.cunoc.sgp.puente.service;

import gt.usac.cunoc.sgp.common.audit.aspect.Auditable;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.puente.dto.CrearPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.DarBajaPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.PuenteCatalogoResponse;
import gt.usac.cunoc.sgp.puente.dto.PuenteResponse;
import gt.usac.cunoc.sgp.puente.entity.Departamento;
import gt.usac.cunoc.sgp.puente.entity.Municipio;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.exception.CercaniaPuenteException;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapper;
import gt.usac.cunoc.sgp.puente.repository.DepartamentoRepository;
import gt.usac.cunoc.sgp.puente.repository.MunicipioRepository;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
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

@Service
public class PuenteService {

  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(), 4326);

  private final DepartamentoRepository departamentos;
  private final MunicipioRepository municipios;
  private final PuenteRepository puentes;
  private final PuenteMapper mapper;
  private final Validator validator;
  private final Clock clock;

  public PuenteService(
      DepartamentoRepository departamentos,
      MunicipioRepository municipios,
      PuenteRepository puentes,
      PuenteMapper mapper,
      Validator validator,
      Clock clock) {
    this.departamentos = departamentos;
    this.municipios = municipios;
    this.puentes = puentes;
    this.mapper = mapper;
    this.validator = validator;
    this.clock = clock;
  }

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(accion = AccionAuditoria.CREAR, entidad = "puente")
  public PuenteResponse registrar(CrearPuenteRequest request, UUID administradorId) {
    var errores = validator.validate(request);
    if (!errores.isEmpty()) {
      throw new ConstraintViolationException(errores);
    }

    double latitud = request.latitud().doubleValue();
    double longitud = request.longitud().doubleValue();

    Boolean dentroDeGuatemala = puentes.estaDentroDeGuatemala(latitud, longitud);

    if (dentroDeGuatemala == null) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "limite_territorial_no_disponible",
          "Validación territorial no disponible",
          "No está cargado el límite territorial de Guatemala.");
    }

    if (!dentroDeGuatemala) {
      throw validacion(
          "ubicacion_fuera_de_guatemala",
          "Las coordenadas deben estar dentro del territorio de Guatemala.");
    }

    Departamento departamento =
        departamentos
            .findByIdAndActivoTrue(request.departamentoId())
            .orElseThrow(
                () ->
                    validacion(
                        "departamento_invalido", "El departamento no existe o esta inactivo"));

    Municipio municipio =
        municipios
            .findActivoByIdForUpdate(request.municipioId())
            .orElseThrow(
                () -> validacion("municipio_invalido", "El municipio no existe o esta inactivo"));

    if (!municipio.getDepartamento().getId().equals(departamento.getId())) {
      throw validacion(
          "municipio_departamento_incongruente",
          "El municipio no pertenece al departamento seleccionado");
    }

    Boolean perteneceAlMunicipio =
        municipios.ubicacionPerteneceAlMunicipio(municipio.getId(), latitud, longitud);

    if (perteneceAlMunicipio == null) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "limite_municipal_no_disponible",
          "Validación municipal no disponible",
          "No está cargado el límite geográfico del municipio seleccionado.");
    }

    if (!perteneceAlMunicipio) {
      throw validacion(
          "ubicacion_municipio_incongruente",
          "Las coordenadas no corresponden al municipio seleccionado.");
    }

    var cercanos = puentes.findCercanos(latitud, longitud, PageRequest.of(0, 100));
    if (cercanos.hasContent() && !request.confirmarCercania()) {
      throw new CercaniaPuenteException(cercanos.map(mapper::toPuenteCercanoResponse));
    }

    if (municipio.getUltimoCorrelativoPuente() >= 9999) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "correlativo_agotado",
          "Correlativo agotado",
          "El municipio alcanzo el limite de 9999 puentes");
    }

    int correlativo = municipio.getUltimoCorrelativoPuente() + 1;
    String codigo =
        String.format(
            Locale.ROOT,
            "GT-%s-%s-%04d",
            departamento.getCodigoIne(),
            municipio.getCodigoIne(),
            correlativo);

    var utm = mapper.toCoordenadaUtmResponse(puentes.calcularUtm(latitud, longitud));

    Instant ahora = clock.instant();
    municipio.asignarCorrelativoPuente(correlativo, ahora);

    Puente puente =
        new Puente(
            UuidV7Generator.generate(),
            codigo,
            correlativo,
            municipio,
            request.nombre().strip(),
            request.ruta().strip(),
            request.kilometraje(),
            GEOMETRY_FACTORY.createPoint(new Coordinate(longitud, latitud)),
            administradorId,
            ahora);

    Puente guardado = puentes.saveAndFlush(puente);
    return mapper.toAltaResponse(guardado, utm);
  }

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public PuenteResponse darDeBaja(UUID id, DarBajaPuenteRequest request, UUID administradorId) {
    var errores = validator.validate(request);
    if (!errores.isEmpty()) {
      throw new ConstraintViolationException(errores);
    }

    Puente puente =
        puentes
            .findPuenteConRelacionesById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "puente_no_encontrado",
                        "Puente no encontrado",
                        "No existe un puente con el identificador proporcionado."));

    if (!puente.isActivo()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "puente_ya_inactivo",
          "Puente ya inactivo",
          "El puente ya se encuentra dado de baja.");
    }

    Instant ahora = clock.instant();
    puente.darDeBaja(request.motivo().strip(), administradorId, ahora);
    Puente guardado = puentes.saveAndFlush(puente);

    var utm =
        mapper.toCoordenadaUtmResponse(
            puentes.calcularUtm(guardado.getUbicacion().getY(), guardado.getUbicacion().getX()));
    return mapper.toAltaResponse(guardado, utm);
  }

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public PuenteResponse reactivar(UUID id, UUID administradorId) {
    Puente puente =
        puentes
            .findPuenteConRelacionesById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "puente_no_encontrado",
                        "Puente no encontrado",
                        "No existe un puente con el identificador proporcionado."));

    if (puente.isActivo()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "puente_ya_activo",
          "Puente ya activo",
          "El puente ya se encuentra activo.");
    }

    Instant ahora = clock.instant();
    puente.reactivar(ahora);
    Puente guardado = puentes.saveAndFlush(puente);

    var utm =
        mapper.toCoordenadaUtmResponse(
            puentes.calcularUtm(guardado.getUbicacion().getY(), guardado.getUbicacion().getX()));
    return mapper.toAltaResponse(guardado, utm);
  }

  @Transactional(readOnly = true)
  public Page<PuenteCatalogoResponse> listarCatalogo(
      UUID departamentoId, String estado, int pagina, int tamanio) {
    return listarCatalogo(departamentoId, estado, Boolean.TRUE, pagina, tamanio);
  }

  @Transactional(readOnly = true)
  public Page<PuenteCatalogoResponse> listarCatalogo(
      UUID departamentoId, String estado, Boolean activo, int pagina, int tamanio) {
    if (pagina < 0 || tamanio < 1 || tamanio > 100) {
      throw validacion(
          "paginacion_invalida",
          "La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.");
    }
    if (estado != null && !Set.of("Bueno", "Regular", "Malo", "Sin evaluar").contains(estado)) {
      throw validacion("estado_invalido", "El estado debe ser Bueno, Regular, Malo o Sin evaluar.");
    }

    var pageable =
        PageRequest.of(pagina, tamanio, Sort.by(Sort.Order.asc("nombre"), Sort.Order.asc("id")));

    // Hasta implementar inspecciones publicadas, todos los puentes están sin evaluar.
    if (estado != null && !"Sin evaluar".equals(estado)) {
      return Page.empty(pageable);
    }
    return puentes.findCatalogo(departamentoId, activo, pageable).map(mapper::toCatalogoResponse);
  }

  private ApiException validacion(String codigo, String detalle) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY, codigo, "Datos de puente invalidos", detalle);
  }
}
