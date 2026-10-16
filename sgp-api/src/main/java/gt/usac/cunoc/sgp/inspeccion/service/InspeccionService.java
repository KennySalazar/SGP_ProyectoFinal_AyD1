package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import gt.usac.cunoc.sgp.inspeccion.dto.GuardarRespuestasRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionResumenResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.mapper.InspeccionMapper;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspeccionService {

  private final InspeccionRepository inspeccionRepository;
  private final VersionFormularioRepository versionFormularioRepository;
  private final PuenteRepository puenteRepository;
  private final JsonSchemaValidatorService jsonSchemaValidatorService;
  private final InspeccionMapper inspeccionMapper;
  private final ObjectMapper objectMapper;

  public InspeccionService(
      InspeccionRepository inspeccionRepository,
      VersionFormularioRepository versionFormularioRepository,
      PuenteRepository puenteRepository,
      JsonSchemaValidatorService jsonSchemaValidatorService,
      InspeccionMapper inspeccionMapper,
      ObjectMapper objectMapper) {
    this.inspeccionRepository = inspeccionRepository;
    this.versionFormularioRepository = versionFormularioRepository;
    this.puenteRepository = puenteRepository;
    this.jsonSchemaValidatorService = jsonSchemaValidatorService;
    this.inspeccionMapper = inspeccionMapper;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public InspeccionResumenResponse iniciarInspeccion(
      IniciarInspeccionRequest request, UUID usuarioId, String rolUsuario) {
    if (request == null) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "solicitud_invalida",
          "Solicitud inválida",
          "La solicitud de inicio de inspección es obligatoria");
    }

    Puente puente =
        puenteRepository
            .findById(request.puenteId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "puente_no_encontrado",
                        "Puente no encontrado",
                        "El puente especificado no existe"));

    if (!puente.isActivo()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "puente_inactivo",
          "Puente inactivo",
          "No se puede iniciar una inspección en un puente inactivo");
    }

    VersionFormulario version =
        versionFormularioRepository
            .findById(request.versionFormularioId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "version_no_utilizable",
                        "Versión de formulario no utilizable",
                        "La versión de formulario especificada no existe"));

    if (version.getEstado() != EstadoVersionFormulario.PUBLICADA) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "version_no_utilizable",
          "Versión de formulario no utilizable",
          "Solo se pueden iniciar inspecciones con versiones de formulario publicadas");
    }

    JsonNode datos = request.datos();
    if (datos != null && !datos.isNull() && !datos.isEmpty()) {
      jsonSchemaValidatorService.validarRespuestas(version.getEsquema(), datos, true);
    } else {
      datos = objectMapper.createObjectNode();
    }

    String rol = rolUsuario != null ? rolUsuario : "INSPECTOR";
    String nombre =
        request.nombreAutorCapturado() != null && !request.nombreAutorCapturado().isBlank()
            ? request.nombreAutorCapturado()
            : "Inspector";
    LocalDate fecha =
        request.fechaInspeccion() != null ? request.fechaInspeccion() : LocalDate.now();
    String dispositivo =
        request.dispositivoId() != null && !request.dispositivoId().isBlank()
            ? request.dispositivoId()
            : "web";

    Inspeccion inspeccion =
        new Inspeccion(puente, version, usuarioId, rol, nombre, fecha, dispositivo, datos);
    inspeccion = inspeccionRepository.save(inspeccion);

    return inspeccionMapper.toResumenResponse(inspeccion);
  }

  @Transactional
  public InspeccionResumenResponse guardarRespuestas(
      UUID inspeccionId, GuardarRespuestasRequest request, UUID usuarioId) {
    if (request == null || request.datos() == null) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "datos_obligatorios",
          "Datos obligatorios",
          "Los datos de la inspección son obligatorios");
    }

    Inspeccion inspeccion = obtenerInspeccionActiva(inspeccionId);

    if ("PUBLICADA".equalsIgnoreCase(inspeccion.getEstado())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "inspeccion_publicada_inmutable",
          "Inspección inmutable",
          "No se pueden modificar las respuestas de una inspección publicada");
    }

    if ("RECHAZADA".equalsIgnoreCase(inspeccion.getEstado())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "inspeccion_rechazada",
          "Inspección rechazada",
          "No se pueden modificar las respuestas de una inspección rechazada");
    }

    // Se valida contra la versión de la inspección, NO contra la activa (RN-FRM-02)
    VersionFormulario version = inspeccion.getVersionFormulario();
    jsonSchemaValidatorService.validarRespuestas(version.getEsquema(), request.datos(), true);

    inspeccion.setDatos(request.datos());
    Instant ahora = Instant.now();
    inspeccion.setActualizadoEn(ahora);
    inspeccion.setUltimaActividadEn(ahora);

    inspeccion = inspeccionRepository.save(inspeccion);
    return inspeccionMapper.toResumenResponse(inspeccion);
  }

  @Transactional
  public InspeccionResumenResponse enviarInspeccion(UUID inspeccionId, UUID usuarioId) {
    Inspeccion inspeccion = obtenerInspeccionActiva(inspeccionId);

    if (!"BORRADOR".equalsIgnoreCase(inspeccion.getEstado())
        && !"CAMBIOS_SOLICITADOS".equalsIgnoreCase(inspeccion.getEstado())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "estado_invalido",
          "Estado inválido",
          "Solo se pueden enviar inspecciones en borrador o con cambios solicitados");
    }

    // Enviar exige todos los campos obligatorios del esquema de su versión
    VersionFormulario version = inspeccion.getVersionFormulario();
    jsonSchemaValidatorService.validarRespuestas(
        version.getEsquema(), inspeccion.getDatos(), false);

    inspeccion.setEstado("ENVIADA");
    Instant ahora = Instant.now();
    inspeccion.setEnviadoDispositivoEn(ahora);
    inspeccion.setUltimaActividadEn(ahora);
    inspeccion.setActualizadoEn(ahora);

    inspeccion = inspeccionRepository.save(inspeccion);
    return inspeccionMapper.toResumenResponse(inspeccion);
  }

  @Transactional(readOnly = true)
  public InspeccionResumenResponse consultarPorId(UUID inspeccionId) {
    Inspeccion inspeccion = obtenerInspeccionActiva(inspeccionId);
    return inspeccionMapper.toResumenResponse(inspeccion);
  }

  @Transactional(readOnly = true)
  public void validarRespuestasInspeccion(UUID inspeccionId, JsonNode datos, Boolean esBorrador) {
    Inspeccion inspeccion = obtenerInspeccionActiva(inspeccionId);
    boolean modoBorrador = esBorrador == null || esBorrador;
    jsonSchemaValidatorService.validarRespuestas(
        inspeccion.getVersionFormulario().getEsquema(), datos, modoBorrador);
  }

  @Transactional(readOnly = true)
  public void validarRespuestasVersion(UUID versionId, JsonNode datos, Boolean esBorrador) {
    VersionFormulario version =
        versionFormularioRepository
            .findById(versionId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "version_no_encontrada",
                        "Versión no encontrada",
                        "La versión de formulario especificada no existe"));

    boolean modoBorrador = esBorrador == null || esBorrador;
    jsonSchemaValidatorService.validarRespuestas(version.getEsquema(), datos, modoBorrador);
  }

  private Inspeccion obtenerInspeccionActiva(UUID id) {
    if (id == null) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "id_obligatorio",
          "ID obligatorio",
          "El identificador de la inspección es obligatorio");
    }
    return inspeccionRepository
        .findActiveById(id)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "inspeccion_no_encontrada",
                    "Inspección no encontrada",
                    "No existe la inspección solicitada"));
  }
}
