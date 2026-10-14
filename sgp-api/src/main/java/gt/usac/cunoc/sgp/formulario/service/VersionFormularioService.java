package gt.usac.cunoc.sgp.formulario.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.aspect.Auditable;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.dto.AnalisisMapeoResponse;
import gt.usac.cunoc.sgp.formulario.dto.ConfiguracionIcElementoRequest;
import gt.usac.cunoc.sgp.formulario.dto.ConfiguracionIcElementoResponse;
import gt.usac.cunoc.sgp.formulario.dto.CrearVersionRequest;
import gt.usac.cunoc.sgp.formulario.dto.DeclaracionMapeoRequest;
import gt.usac.cunoc.sgp.formulario.dto.FormularioActivoResponse;
import gt.usac.cunoc.sgp.formulario.dto.MapeoCampoResponse;
import gt.usac.cunoc.sgp.formulario.dto.PublicarVersionRequest;
import gt.usac.cunoc.sgp.formulario.dto.VersionFormularioResponse;
import gt.usac.cunoc.sgp.formulario.dto.VersionFormularioResumenResponse;
import gt.usac.cunoc.sgp.formulario.entity.ConfiguracionIcElemento;
import gt.usac.cunoc.sgp.formulario.entity.MapeoCampoFormulario;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.mapper.VersionFormularioMapper;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.model.TipoMapeoCampo;
import gt.usac.cunoc.sgp.formulario.repository.ConfiguracionIcElementoRepository;
import gt.usac.cunoc.sgp.formulario.repository.MapeoCampoFormularioRepository;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.formulario.validation.CampoSchemaExtractor;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VersionFormularioService {

  private final VersionFormularioRepository versiones;
  private final MapeoCampoFormularioRepository mapeos;
  private final ConfiguracionIcElementoRepository configuracionesIc;
  private final VersionFormularioMapper mapper;
  private final JsonSchemaValidatorService schemaValidator;
  private final CampoSchemaExtractor campoExtractor;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public VersionFormularioService(
      VersionFormularioRepository versiones,
      MapeoCampoFormularioRepository mapeos,
      ConfiguracionIcElementoRepository configuracionesIc,
      VersionFormularioMapper mapper,
      JsonSchemaValidatorService schemaValidator,
      CampoSchemaExtractor campoExtractor,
      ObjectMapper objectMapper,
      Clock clock) {
    this.versiones = versiones;
    this.mapeos = mapeos;
    this.configuracionesIc = configuracionesIc;
    this.mapper = mapper;
    this.schemaValidator = schemaValidator;
    this.campoExtractor = campoExtractor;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public List<VersionFormularioResumenResponse> listarVersiones() {
    return versiones.findAllByOrderByCreadoEnDesc().stream()
        .map(mapper::toResumenResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public VersionFormularioResponse obtenerPorId(UUID id) {
    VersionFormulario version =
        versiones
            .findById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "version_no_encontrada",
                        "Versión no encontrada",
                        "No existe una versión de formulario con identificador " + id));

    return construirRespuestaDetallada(version);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("isAuthenticated()")
  public FormularioActivoResponse obtenerActiva() {
    VersionFormulario activa =
        versiones
            .findByActivaTrue()
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "formulario_activo_no_encontrado",
                        "Formulario activo no encontrado",
                        "No existe actualmente una versión de formulario activa en el sistema"));

    return mapper.toActivoResponse(activa);
  }

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(accion = AccionAuditoria.CREAR, entidad = "version_formulario")
  public VersionFormularioResponse crear(CrearVersionRequest request, UUID usuarioId) {
    if (Boolean.TRUE.equals(request.cargarPlantillaInicial())) {
      return cargarPlantillaSiecaInicial(request.codigo(), usuarioId);
    }

    if (versiones.existsByCodigo(request.codigo())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "codigo_duplicado",
          "Código de versión duplicado",
          "Ya existe una versión con el código '" + request.codigo() + "'");
    }

    Instant ahora = clock.instant();
    UUID id = UUID.randomUUID();
    VersionFormulario versionAnterior = null;
    JsonNode esquema;
    JsonNode vinculos;
    JsonNode reglas;
    List<ConfiguracionIcElemento> pesosIniciales = new ArrayList<>();

    if (request.versionBaseId() != null) {
      VersionFormulario base =
          versiones
              .findById(request.versionBaseId())
              .orElseThrow(
                  () ->
                      new ApiException(
                          HttpStatus.NOT_FOUND,
                          "version_base_no_encontrada",
                          "Versión base no encontrada",
                          "No existe la versión base especificada"));
      versionAnterior = base;
      esquema = request.esquema() != null ? request.esquema() : base.getEsquema();
      vinculos =
          request.vinculosAlmacenamiento() != null
              ? request.vinculosAlmacenamiento()
              : base.getVinculosAlmacenamiento();
      reglas =
          request.reglasEvaluacionIc() != null
              ? request.reglasEvaluacionIc()
              : base.getReglasEvaluacionIc();

      // Copiar pesos de la versión base para agilizar la nueva versión
      List<ConfiguracionIcElemento> pesosBase =
          configuracionesIc.findAllByVersionFormularioId(base.getId());
      for (ConfiguracionIcElemento p : pesosBase) {
        pesosIniciales.add(
            new ConfiguracionIcElemento(
                UUID.randomUUID(),
                null,
                p.getElementoRef(),
                p.getPeso(),
                p.getDescripcion(),
                ahora));
      }
    } else {
      esquema = request.esquema();
      vinculos =
          request.vinculosAlmacenamiento() != null
              ? request.vinculosAlmacenamiento()
              : objectMapper.createObjectNode();
      reglas =
          request.reglasEvaluacionIc() != null
              ? request.reglasEvaluacionIc()
              : objectMapper.createObjectNode();
    }

    schemaValidator.validarEsquema(esquema);

    VersionFormulario nuevaVersion =
        new VersionFormulario(
            id,
            request.codigo(),
            EstadoVersionFormulario.BORRADOR,
            false,
            esquema,
            vinculos,
            reglas,
            versionAnterior,
            usuarioId,
            ahora,
            ahora);

    VersionFormulario guardada = versiones.save(nuevaVersion);

    for (ConfiguracionIcElemento p : pesosIniciales) {
      ConfiguracionIcElemento conRelacion =
          new ConfiguracionIcElemento(
              p.getId(), guardada, p.getElementoRef(), p.getPeso(), p.getDescripcion(), ahora);
      configuracionesIc.save(conRelacion);
    }

    return construirRespuestaDetallada(guardada);
  }

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(accion = AccionAuditoria.CREAR, entidad = "version_formulario")
  public VersionFormularioResponse cargarPlantillaSiecaInicial(String codigo, UUID usuarioId) {
    String codigoFinal = (codigo != null && !codigo.isBlank()) ? codigo : "SIECA-v1";

    if (versiones.existsByCodigo(codigoFinal)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "codigo_duplicado",
          "Código de versión duplicado",
          "Ya existe una versión con el código '" + codigoFinal + "'");
    }

    JsonNode esquema;
    JsonNode vinculos;
    JsonNode elementosIcNode;

    try {
      try (InputStream is =
          new ClassPathResource("formulario/sieca-v1/esquema.schema.json").getInputStream()) {
        esquema = objectMapper.readTree(is);
      }
      try (InputStream is =
          new ClassPathResource("formulario/sieca-v1/vinculos-almacenamiento.json")
              .getInputStream()) {
        vinculos = objectMapper.readTree(is);
      }
      try (InputStream is =
          new ClassPathResource("formulario/sieca-v1/elementos-ic.json").getInputStream()) {
        elementosIcNode = objectMapper.readTree(is);
      }
    } catch (Exception e) {
      throw new IllegalStateException(
          "No fue posible cargar los archivos de plantilla SIECA v1", e);
    }

    schemaValidator.validarEsquema(esquema);

    Instant ahora = clock.instant();
    UUID id = UUID.randomUUID();

    VersionFormulario versionInicial =
        new VersionFormulario(
            id,
            codigoFinal,
            EstadoVersionFormulario.BORRADOR,
            false,
            esquema,
            vinculos,
            elementosIcNode,
            null,
            usuarioId,
            ahora,
            ahora);

    VersionFormulario guardada = versiones.save(versionInicial);

    // Precargar configuración de pesos a partir del catálogo elementos-ic.json
    if (elementosIcNode.has("elementos") && elementosIcNode.get("elementos").isArray()) {
      for (JsonNode elem : elementosIcNode.get("elementos")) {
        if (elem.hasNonNull("elemento_ref")) {
          String ref = elem.get("elemento_ref").asText();
          String nombre = elem.hasNonNull("nombre") ? elem.get("nombre").asText() : ref;
          BigDecimal peso =
              (elem.hasNonNull("peso") && elem.get("peso").isNumber())
                  ? BigDecimal.valueOf(elem.get("peso").asDouble())
                  : BigDecimal.ONE;

          ConfiguracionIcElemento cfg =
              new ConfiguracionIcElemento(UUID.randomUUID(), guardada, ref, peso, nombre, ahora);
          configuracionesIc.save(cfg);
        }
      }
    }

    return construirRespuestaDetallada(guardada);
  }

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(accion = AccionAuditoria.MODIFICAR, entidad = "version_formulario")
  public VersionFormularioResponse actualizarBorrador(
      UUID id,
      JsonNode esquema,
      JsonNode vinculosAlmacenamiento,
      JsonNode reglasEvaluacionIc,
      String nuevoCodigo,
      UUID usuarioId) {
    VersionFormulario version =
        versiones
            .findById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "version_no_encontrada",
                        "Versión no encontrada",
                        "No existe una versión con identificador " + id));

    if (version.getEstado() == EstadoVersionFormulario.PUBLICADA) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "version_inmutable",
          "Versión inmutable",
          "Una versión publicada no puede ser modificada. Cree una nueva versión a partir de ella (RN-FRM-03).");
    }

    schemaValidator.validarEsquema(esquema);

    if (nuevoCodigo != null && !nuevoCodigo.isBlank() && !nuevoCodigo.equals(version.getCodigo())) {
      if (versiones.existsByCodigo(nuevoCodigo)) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "codigo_duplicado",
            "Código duplicado",
            "Ya existe otra versión con el código '" + nuevoCodigo + "'");
      }
      version.setCodigo(nuevoCodigo);
    }

    version.setEsquema(esquema);
    if (vinculosAlmacenamiento != null) {
      version.setVinculosAlmacenamiento(vinculosAlmacenamiento);
    }
    if (reglasEvaluacionIc != null) {
      version.setReglasEvaluacionIc(reglasEvaluacionIc);
    }
    version.setActualizadoEn(clock.instant());

    VersionFormulario guardada = versiones.save(version);
    return construirRespuestaDetallada(guardada);
  }

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public List<ConfiguracionIcElementoResponse> calibrarPesos(
      UUID id, List<ConfiguracionIcElementoRequest> nuevosPesos, UUID usuarioId) {
    VersionFormulario version =
        versiones
            .findById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "version_no_encontrada",
                        "Versión no encontrada",
                        "No existe una versión con identificador " + id));

    if (version.getEstado() == EstadoVersionFormulario.PUBLICADA) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "version_inmutable",
          "Versión inmutable",
          "Los pesos de una versión publicada están congelados y no pueden alterarse.");
    }

    Instant ahora = clock.instant();
    configuracionesIc.deleteAllByVersionFormularioId(id);

    List<ConfiguracionIcElemento> lista = new ArrayList<>();
    for (ConfiguracionIcElementoRequest req : nuevosPesos) {
      if (req.peso() == null || req.peso().compareTo(BigDecimal.ZERO) <= 0) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "peso_invalido",
            "Peso inválido",
            "El peso para '" + req.elementoRef() + "' debe ser mayor a cero");
      }
      lista.add(
          new ConfiguracionIcElemento(
              UUID.randomUUID(), version, req.elementoRef(), req.peso(), req.descripcion(), ahora));
    }

    configuracionesIc.saveAll(lista);

    version.setCalibradoPorId(usuarioId);
    version.setCalibradoEn(ahora);
    version.setActualizadoEn(ahora);
    versiones.save(version);

    return lista.stream().map(mapper::toIcElementoResponse).toList();
  }

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public List<MapeoCampoResponse> guardarMapeos(
      UUID versionDestinoId, List<DeclaracionMapeoRequest> declaraciones, UUID usuarioId) {
    VersionFormulario destino =
        versiones
            .findById(versionDestinoId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "version_no_encontrada",
                        "Versión no encontrada",
                        "No existe la versión con identificador " + versionDestinoId));

    if (destino.getVersionAnterior() == null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "sin_version_anterior",
          "Sin versión anterior",
          "La versión no tiene una versión anterior asociada para declarar mapeos.");
    }

    VersionFormulario origen = destino.getVersionAnterior();
    mapeos.deleteAllByVersionOrigenIdAndVersionDestinoId(origen.getId(), destino.getId());

    Instant ahora = clock.instant();
    List<MapeoCampoFormulario> entidadesMapeo = new ArrayList<>();

    for (DeclaracionMapeoRequest d : declaraciones) {
      validarCoherenciaMapeo(d);
      entidadesMapeo.add(
          new MapeoCampoFormulario(
              UUID.randomUUID(),
              origen,
              destino,
              d.elementoRefOrigen(),
              d.elementoRefDestino(),
              d.tipo(),
              d.observacion(),
              usuarioId,
              ahora));
    }

    List<MapeoCampoFormulario> guardados = mapeos.saveAll(entidadesMapeo);
    return guardados.stream().map(mapper::toMapeoResponse).toList();
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public AnalisisMapeoResponse analizarMapeo(UUID versionDestinoId) {
    VersionFormulario destino =
        versiones
            .findById(versionDestinoId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "version_no_encontrada",
                        "Versión no encontrada",
                        "No existe la versión con identificador " + versionDestinoId));

    if (destino.getVersionAnterior() == null) {
      return new AnalisisMapeoResponse(
          null,
          null,
          destino.getId(),
          destino.getCodigo(),
          Collections.emptyList(),
          campoExtractor.extraerCampos(destino.getEsquema()),
          Collections.emptyList(),
          Collections.emptyList(),
          true);
    }

    VersionFormulario origen = destino.getVersionAnterior();
    List<String> camposOrigen = campoExtractor.extraerCampos(origen.getEsquema());
    List<String> camposDestino = campoExtractor.extraerCampos(destino.getEsquema());

    List<MapeoCampoFormulario> mapeosGuardados =
        mapeos.findAllByVersionOrigenIdAndVersionDestinoId(origen.getId(), destino.getId());

    Set<String> mapeadosOrigen = new HashSet<>();
    for (MapeoCampoFormulario m : mapeosGuardados) {
      if (m.getElementoRefOrigen() != null) {
        mapeadosOrigen.add(m.getElementoRefOrigen());
      }
    }

    List<String> pendientes =
        camposOrigen.stream().filter(c -> !mapeadosOrigen.contains(c)).toList();

    return new AnalisisMapeoResponse(
        origen.getId(),
        origen.getCodigo(),
        destino.getId(),
        destino.getCodigo(),
        camposOrigen,
        camposDestino,
        mapeosGuardados.stream().map(mapper::toMapeoResponse).toList(),
        pendientes,
        pendientes.isEmpty());
  }

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(accion = AccionAuditoria.CAMBIAR_ESTADO, entidad = "version_formulario")
  public VersionFormularioResponse publicar(
      UUID id, PublicarVersionRequest request, UUID usuarioId) {
    VersionFormulario version =
        versiones
            .findById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "version_no_encontrada",
                        "Versión no encontrada",
                        "No existe una versión con identificador " + id));

    if (version.getEstado() == EstadoVersionFormulario.PUBLICADA) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "version_ya_publicada",
          "Versión ya publicada",
          "La versión '" + version.getCodigo() + "' ya fue publicada previamente.");
    }

    // 1. Validar esquema
    schemaValidator.validarEsquema(version.getEsquema());

    // 2. Si hay mapeos en el request, guardarlos
    if (request != null && request.mapeos() != null && !request.mapeos().isEmpty()) {
      guardarMapeos(version.getId(), request.mapeos(), usuarioId);
    }

    // 3. Validar mapeo de campos con la versión anterior (RN-FRM-06)
    if (version.getVersionAnterior() != null) {
      AnalisisMapeoResponse analisis = analizarMapeo(version.getId());
      if (!analisis.mapeoCompleto()) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "mapeo_incompleto",
            "Mapeo de campos incompleto",
            "Debe declarar para cada campo de la versión anterior si es equivalente, renombrado, nuevo o eliminado. Faltan por declarar: "
                + analisis.camposOrigenSinMapeo());
      }
    }

    // 4. Validar pesos completos
    List<ConfiguracionIcElemento> pesos =
        configuracionesIc.findAllByVersionFormularioId(version.getId());
    if (pesos.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "pesos_incompletos",
          "Pesos de evaluación incompletos",
          "No se puede publicar la versión sin configurar los pesos de los elementos evaluables.");
    }

    Instant ahora = clock.instant();

    // 5. Desactivar versión activa anterior asegurando que solo exista una activa (RN-FRM-04)
    versiones.desactivarTodas();
    versiones.flush();

    // 6. Publicar y activar
    String hash = calcularSha256(version.getEsquema());
    version.setHashContenido(hash);
    version.setVersionAlgoritmoIc("IC-SIECA-v1");
    version.setEstado(EstadoVersionFormulario.PUBLICADA);
    version.setActiva(true);
    version.setPublicadoPorId(usuarioId);
    version.setPublicadoEn(ahora);
    version.setActualizadoEn(ahora);

    VersionFormulario publicada = versiones.saveAndFlush(version);
    return construirRespuestaDetallada(publicada);
  }

  private void validarCoherenciaMapeo(DeclaracionMapeoRequest d) {
    if (d.tipo() == TipoMapeoCampo.NUEVO) {
      if (d.elementoRefOrigen() != null || d.elementoRefDestino() == null) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "mapeo_incoherente",
            "Mapeo incoherente",
            "Un campo NUEVO no debe tener origen y requiere referencia de destino.");
      }
    } else if (d.tipo() == TipoMapeoCampo.ELIMINADO) {
      if (d.elementoRefOrigen() == null || d.elementoRefDestino() != null) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "mapeo_incoherente",
            "Mapeo incoherente",
            "Un campo ELIMINADO requiere referencia de origen y no debe tener destino.");
      }
    } else {
      if (d.elementoRefOrigen() == null || d.elementoRefDestino() == null) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "mapeo_incoherente",
            "Mapeo incoherente",
            "Un campo "
                + d.tipo()
                + " requiere tanto referencia de origen como referencia de destino.");
      }
    }
  }

  private String calcularSha256(JsonNode jsonNode) {
    try {
      byte[] bytes = objectMapper.writeValueAsBytes(jsonNode);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(bytes);
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (Exception e) {
      throw new IllegalStateException("Error al calcular hash SHA-256", e);
    }
  }

  private VersionFormularioResponse construirRespuestaDetallada(VersionFormulario version) {
    List<ConfiguracionIcElementoResponse> listaPesos =
        configuracionesIc.findAllByVersionFormularioId(version.getId()).stream()
            .map(mapper::toIcElementoResponse)
            .toList();

    List<MapeoCampoResponse> listaMapeos =
        version.getVersionAnterior() != null
            ? mapeos
                .findAllByVersionOrigenIdAndVersionDestinoId(
                    version.getVersionAnterior().getId(), version.getId())
                .stream()
                .map(mapper::toMapeoResponse)
                .toList()
            : Collections.emptyList();

    return mapper.toResponse(version, listaPesos, listaMapeos);
  }
}
