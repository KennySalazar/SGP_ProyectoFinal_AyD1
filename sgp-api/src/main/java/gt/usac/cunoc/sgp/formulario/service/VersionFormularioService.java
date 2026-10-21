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
import gt.usac.cunoc.sgp.formulario.validation.ValidadorReglasCriticasIc;
import java.io.InputStream;
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

  /**
   * Esquema de una versión publicada (activa o anterior) para mostrar una inspección con la
   * estructura con la que se levantó (RN-FRM-05). Los borradores no se exponen.
   */
  @Transactional(readOnly = true)
  @PreAuthorize("isAuthenticated()")
  public FormularioActivoResponse obtenerPublicadaPorId(UUID id) {
    VersionFormulario version =
        versiones
            .findById(id)
            .filter(candidata -> candidata.getEstado() == EstadoVersionFormulario.PUBLICADA)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "version_no_encontrada",
                        "Versión no encontrada",
                        "No existe una versión publicada con identificador " + id));

    return mapper.toActivoResponse(version);
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

    VersionFormulario anterior = versiones.findByActivaTrue().orElse(null);

    VersionFormulario versionInicial =
        new VersionFormulario(
            id,
            codigoFinal,
            EstadoVersionFormulario.BORRADOR,
            false,
            esquema,
            vinculos,
            elementosIcNode,
            anterior,
            usuarioId,
            ahora,
            ahora);

    VersionFormulario guardada = versiones.save(versionInicial);

    if (elementosIcNode.has("elementos") && elementosIcNode.get("elementos").isArray()) {
      for (JsonNode elem : elementosIcNode.get("elementos")) {
        JsonNode pesoConfigurado = elem.get("peso");

        if (pesoConfigurado == null || pesoConfigurado.isNull()) {
          continue;
        }

        if (!pesoConfigurado.isNumber() || pesoConfigurado.decimalValue().signum() <= 0) {
          throw new ApiException(
              HttpStatus.UNPROCESSABLE_ENTITY,
              "peso_invalido",
              "Peso invalido",
              "La plantilla contiene un peso invalido para " + elem.path("elemento_ref").asText());
        }

        String referencia = elem.path("elemento_ref").asText();

        if (referencia.isBlank()) {
          throw new ApiException(
              HttpStatus.UNPROCESSABLE_ENTITY,
              "referencia_elemento_invalida",
              "Referencia invalida",
              "La plantilla contiene un peso sin referencia de elemento.");
        }

        String nombre = elem.hasNonNull("nombre") ? elem.get("nombre").asText() : referencia;

        ConfiguracionIcElemento configuracion =
            new ConfiguracionIcElemento(
                UUID.randomUUID(),
                guardada,
                referencia,
                pesoConfigurado.decimalValue(),
                nombre,
                ahora);

        configuracionesIc.save(configuracion);
      }
    }

    if (anterior != null) {
      Set<String> origen =
          new java.util.LinkedHashSet<>(campoExtractor.extraerCampos(anterior.getEsquema()));
      Set<String> destino = new java.util.LinkedHashSet<>(campoExtractor.extraerCampos(esquema));
      List<MapeoCampoFormulario> declaraciones = new ArrayList<>();
      for (String campo : origen) {
        boolean equivalente = destino.contains(campo);
        declaraciones.add(
            new MapeoCampoFormulario(
                UUID.randomUUID(),
                anterior,
                guardada,
                campo,
                equivalente ? campo : null,
                equivalente ? TipoMapeoCampo.EQUIVALENTE : TipoMapeoCampo.ELIMINADO,
                "Declaracion inicial desde plantilla SIECA; revisar antes de publicar",
                usuarioId,
                ahora));
      }
      for (String campo : destino) {
        if (!origen.contains(campo)) {
          declaraciones.add(
              new MapeoCampoFormulario(
                  UUID.randomUUID(),
                  anterior,
                  guardada,
                  null,
                  campo,
                  TipoMapeoCampo.NUEVO,
                  "Campo nuevo de plantilla SIECA",
                  usuarioId,
                  ahora));
        }
      }
      mapeos.saveAll(declaraciones);
    }

    return construirRespuestaDetallada(guardada);
  }

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(
      accion = AccionAuditoria.MODIFICAR,
      entidad = "version_formulario",
      idArg = "id",
      tipo = VersionFormulario.class,
      recargarPosteriores = true)
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
  @Auditable(
      accion = AccionAuditoria.MODIFICAR,
      entidad = "version_formulario",
      idArg = "id",
      tipo = VersionFormulario.class,
      recargarPosteriores = true)
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
                        "Version no encontrada",
                        "No existe una version con identificador " + id));

    if (version.getEstado() == EstadoVersionFormulario.PUBLICADA) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "version_inmutable",
          "Version inmutable",
          "Los pesos de una version publicada estan congelados. Cree una nueva version.");
    }

    if (nuevosPesos == null || nuevosPesos.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "pesos_incompletos",
          "Pesos incompletos",
          "Debe indicar al menos un elemento con su peso.");
    }

    Set<String> referencias = new HashSet<>();
    List<ConfiguracionIcElemento> lista = new ArrayList<>();
    Instant ahora = clock.instant();

    for (ConfiguracionIcElementoRequest req : nuevosPesos) {
      if (req == null) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "configuracion_ic_invalida",
            "Configuracion invalida",
            "La lista de pesos no puede contener elementos nulos.");
      }

      if (req.peso() == null || req.peso().signum() <= 0) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "peso_invalido",
            "Peso invalido",
            "El peso para '" + req.elementoRef() + "' debe ser mayor a cero.");
      }

      if (req.peso().scale() > 6 || req.peso().precision() - req.peso().scale() > 6) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "peso_invalido",
            "Peso invalido",
            "El peso para '"
                + req.elementoRef()
                + "' admite hasta 6 digitos enteros y 6 decimales.");
      }

      if (req.elementoRef() == null
          || req.elementoRef().isBlank()
          || req.elementoRef().length() > 300
          || !req.elementoRef().startsWith("/")
          || req.elementoRef().length() == 1) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "referencia_elemento_invalida",
            "Referencia invalida",
            "Cada elemento requiere una referencia que comience con / y no supere 300 caracteres.");
      }

      String referencia = req.elementoRef().strip();

      if (!referencias.add(referencia)) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "elemento_ic_duplicado",
            "Elemento duplicado",
            "La referencia aparece mas de una vez: " + referencia);
      }

      if (req.descripcion() == null
          || req.descripcion().isBlank()
          || req.descripcion().length() > 500) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "descripcion_elemento_invalida",
            "Descripcion invalida",
            "El elemento " + referencia + " requiere una descripcion de hasta 500 caracteres.");
      }

      lista.add(
          new ConfiguracionIcElemento(
              UUID.randomUUID(),
              version,
              referencia,
              req.peso(),
              req.descripcion().strip(),
              ahora));
    }

    configuracionesIc.deleteAllByVersionFormularioId(id);
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
    Set<String> mapeadosDestino = new HashSet<>();
    for (MapeoCampoFormulario m : mapeosGuardados) {
      if (m.getElementoRefDestino() != null) {
        mapeadosDestino.add(m.getElementoRefDestino());
      }
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
        pendientes.isEmpty() && camposDestino.stream().allMatch(mapeadosDestino::contains));
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
                + analisis.camposOrigenSinMapeo()
                + "; campos destino sin declarar: "
                + analisis.camposDestinoSinMapeo());
      }
    }

    validarPesosParaPublicacion(version);
    validarEscalaParaPublicacion(version);
    validarReglasParaPublicacion(version);
    validarConversionesParaPublicacion(version);
    ValidadorReglasCriticasIc.validarCatalogo(
        version.getReglasEvaluacionIc().get("reglas_evaluacion_ic"),
        version.getReglasEvaluacionIc());

    Instant ahora = clock.instant();

    JsonNode algoritmoNode =
        version.getReglasEvaluacionIc().at("/reglas_evaluacion_ic/version_algoritmo");

    if (!algoritmoNode.isTextual()
        || algoritmoNode.asText().isBlank()
        || algoritmoNode.asText().length() > 50) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "configuracion_ic_incompleta",
          "Algoritmo IC invalido",
          "Debe configurar version_algoritmo con un identificador de hasta 50 caracteres.");
    }

    String versionAlgoritmo = algoritmoNode.asText();

    // 5. Desactivar versión activa anterior asegurando que solo exista una activa (RN-FRM-04)
    versiones.desactivarTodas();
    versiones.flush();

    // 6. Publicar y activar
    String hash = calcularSha256(version.getEsquema());
    version.setHashContenido(hash);
    version.setVersionAlgoritmoIc(versionAlgoritmo);
    version.setEstado(EstadoVersionFormulario.PUBLICADA);
    version.setActiva(true);
    version.setPublicadoPorId(usuarioId);
    version.setPublicadoEn(ahora);
    version.setActualizadoEn(ahora);

    VersionFormulario publicada = versiones.saveAndFlush(version);
    return construirRespuestaDetallada(publicada);
  }

  private void validarPesosParaPublicacion(VersionFormulario version) {
    JsonNode catalogo = version.getReglasEvaluacionIc();
    JsonNode elementos = catalogo == null ? null : catalogo.get("elementos");

    if (elementos == null || !elementos.isArray() || elementos.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "catalogo_ic_incompleto",
          "Catalogo IC incompleto",
          "La version requiere un catalogo de elementos evaluables para verificar sus pesos.");
    }

    Set<String> esperados = new java.util.TreeSet<>();

    for (JsonNode elemento : elementos) {
      JsonNode referenciaNode = elemento.get("elemento_ref");

      if (referenciaNode == null
          || !referenciaNode.isTextual()
          || referenciaNode.asText().isBlank()
          || !referenciaNode.asText().startsWith("/")
          || referenciaNode.asText().length() == 1
          || referenciaNode.asText().length() > 300) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "referencia_elemento_invalida",
            "Referencia invalida",
            "El catalogo contiene un elemento sin una referencia valida.");
      }

      String referencia = referenciaNode.asText();

      if (!referencia.equals(referencia.strip()) || !esperados.add(referencia)) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "catalogo_ic_invalido",
            "Catalogo IC invalido",
            "La referencia esta duplicada o contiene espacios externos: " + referencia);
      }
    }

    List<ConfiguracionIcElemento> pesos =
        configuracionesIc.findAllByVersionFormularioId(version.getId());

    Set<String> configurados = new HashSet<>();

    for (ConfiguracionIcElemento configuracion : pesos) {
      String referencia = configuracion.getElementoRef();

      if (referencia == null || !esperados.contains(referencia)) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "elemento_ic_desconocido",
            "Elemento IC desconocido",
            "El peso corresponde a un elemento que no pertenece al catalogo: " + referencia);
      }

      if (!configurados.add(referencia)) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "elemento_ic_duplicado",
            "Elemento duplicado",
            "Existe mas de un peso para el elemento " + referencia);
      }

      if (configuracion.getPeso() == null
          || configuracion.getPeso().signum() <= 0
          || configuracion.getPeso().scale() > 6
          || configuracion.getPeso().precision() - configuracion.getPeso().scale() > 6) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "peso_invalido",
            "Peso invalido",
            "El elemento "
                + referencia
                + " requiere un peso positivo con hasta 6 digitos enteros y 6 decimales.");
      }
    }

    Set<String> pendientes = new java.util.TreeSet<>(esperados);
    pendientes.removeAll(configurados);

    if (!pendientes.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "pesos_incompletos",
          "Pesos incompletos",
          "Falta configurar el peso de los siguientes elementos: " + String.join(", ", pendientes));
    }
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

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(
      accion = AccionAuditoria.MODIFICAR,
      entidad = "version_formulario",
      idArg = "id",
      tipo = VersionFormulario.class,
      recargarPosteriores = true)
  public VersionFormularioResponse configurarEscala(
      UUID id, gt.usac.cunoc.sgp.formulario.dto.ConfigurarEscalaIcRequest request, UUID usuarioId) {
    VersionFormulario version =
        versiones
            .findById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "version_no_encontrada",
                        "Version no encontrada",
                        "No existe una version con identificador " + id));

    if (version.getEstado() == EstadoVersionFormulario.PUBLICADA) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "version_inmutable",
          "Version inmutable",
          "La escala de una version publicada no puede modificarse. Cree una nueva version.");
    }

    Set<String> niveles = Set.of("NO_PRESENTA", "LEVE", "MODERADO", "SEVERO");

    if (request == null
        || request.valores() == null
        || !request.valores().keySet().equals(niveles)) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "escala_incompleta",
          "Escala incompleta",
          "Debe configurar exactamente los niveles NO_PRESENTA, LEVE, MODERADO y SEVERO.");
    }

    for (String nivel : niveles) {
      java.math.BigDecimal valor = request.valores().get(nivel);

      if (valor == null
          || valor.signum() < 0
          || valor.compareTo(java.math.BigDecimal.valueOf(4)) > 0) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "severidad_invalida",
            "Severidad invalida",
            "El nivel " + nivel + " requiere un valor entre 0 y 4.");
      }
    }

    if (request.valores().get("NO_PRESENTA").signum() != 0) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "severidad_invalida",
          "Severidad invalida",
          "El nivel NO_PRESENTA debe tener valor 0.");
    }

    JsonNode configuracionActual = version.getReglasEvaluacionIc();

    if (configuracionActual == null || !configuracionActual.isObject()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "configuracion_ic_invalida",
          "Configuracion IC invalida",
          "La version requiere una configuracion IC de tipo objeto.");
    }

    com.fasterxml.jackson.databind.node.ObjectNode configuracion = configuracionActual.deepCopy();

    JsonNode reglasActuales = configuracion.get("reglas_evaluacion_ic");

    if (reglasActuales != null && !reglasActuales.isObject()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "configuracion_ic_invalida",
          "Configuracion IC invalida",
          "Las reglas de evaluacion IC deben ser un objeto.");
    }

    com.fasterxml.jackson.databind.node.ObjectNode reglas =
        reglasActuales == null
            ? objectMapper.createObjectNode()
            : (com.fasterxml.jackson.databind.node.ObjectNode) reglasActuales;

    com.fasterxml.jackson.databind.node.ObjectNode escala = objectMapper.createObjectNode();

    for (String nivel : niveles.stream().sorted().toList()) {
      escala.put(nivel, request.valores().get(nivel));
    }

    reglas.set("escala_severidad", escala);
    configuracion.set("reglas_evaluacion_ic", reglas);

    Instant ahora = clock.instant();
    version.setReglasEvaluacionIc(configuracion);
    version.setCalibradoPorId(usuarioId);
    version.setCalibradoEn(ahora);
    version.setActualizadoEn(ahora);

    return construirRespuestaDetallada(versiones.save(version));
  }

  private void validarEscalaParaPublicacion(VersionFormulario version) {
    JsonNode configuracion = version.getReglasEvaluacionIc();
    JsonNode escala =
        configuracion == null ? null : configuracion.at("/reglas_evaluacion_ic/escala_severidad");

    List<String> niveles = List.of("NO_PRESENTA", "LEVE", "MODERADO", "SEVERO");
    List<String> pendientes = new ArrayList<>();

    for (String nivel : niveles) {
      JsonNode valor = escala == null ? null : escala.get(nivel);

      if (valor == null || valor.isNull()) {
        pendientes.add(nivel);
        continue;
      }

      if (!valor.isNumber()
          || valor.decimalValue().signum() < 0
          || valor.decimalValue().compareTo(java.math.BigDecimal.valueOf(4)) > 0) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "severidad_invalida",
            "Severidad invalida",
            "El nivel " + nivel + " requiere un valor numerico entre 0 y 4.");
      }

      if ("NO_PRESENTA".equals(nivel) && valor.decimalValue().signum() != 0) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "severidad_invalida",
            "Severidad invalida",
            "El nivel NO_PRESENTA debe tener valor 0.");
      }
    }

    if (!pendientes.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "escala_incompleta",
          "Escala incompleta",
          "Falta configurar los siguientes niveles: " + String.join(", ", pendientes));
    }
  }

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(
      accion = AccionAuditoria.MODIFICAR,
      entidad = "version_formulario",
      idArg = "id",
      tipo = VersionFormulario.class,
      recargarPosteriores = true)
  public VersionFormularioResponse configurarReglasCriticas(
      UUID id,
      gt.usac.cunoc.sgp.formulario.dto.ConfigurarReglasCriticasIcRequest request,
      UUID usuarioId) {
    VersionFormulario version =
        versiones
            .findById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "version_no_encontrada",
                        "Version no encontrada",
                        "No existe la version de formulario solicitada."));
    if (version.getEstado() != EstadoVersionFormulario.BORRADOR) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "version_inmutable",
          "Version inmutable",
          "Las reglas de una version publicada no pueden modificarse. Cree una nueva version.");
    }
    if (request == null || request.reglasAnulacion() == null) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "reglas_anulacion_ic_invalidas",
          "Reglas de anulacion del IC invalidas",
          "Debe indicar las reglas criticas completas.");
    }
    JsonNode actual = version.getReglasEvaluacionIc();
    if (actual == null || !actual.isObject() || !actual.path("reglas_evaluacion_ic").isObject()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "configuracion_ic_invalida",
          "Configuracion IC invalida",
          "La version no contiene reglas de evaluacion IC.");
    }
    com.fasterxml.jackson.databind.node.ObjectNode copia = actual.deepCopy();
    com.fasterxml.jackson.databind.node.ObjectNode reglas =
        (com.fasterxml.jackson.databind.node.ObjectNode) copia.get("reglas_evaluacion_ic");
    reglas.set("reglas_anulacion", request.reglasAnulacion().deepCopy());
    ValidadorReglasCriticasIc.validarCatalogo(reglas, copia);
    version.setReglasEvaluacionIc(copia);
    version.setCalibradoPorId(usuarioId);
    version.setCalibradoEn(clock.instant());
    version.setActualizadoEn(clock.instant());
    return construirRespuestaDetallada(versiones.save(version));
  }

  @Transactional(readOnly = true)
  public void validarRespuestas(UUID versionId, JsonNode datos, Boolean esBorrador) {
    VersionFormulario version =
        versiones
            .findById(versionId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "version_no_encontrada",
                        "Version no encontrada",
                        "No existe una version con identificador " + versionId));

    boolean modoBorrador = esBorrador == null || esBorrador;
    schemaValidator.validarRespuestas(version.getEsquema(), datos, modoBorrador);
  }

  private void validarReglasParaPublicacion(VersionFormulario version) {
    JsonNode configuracion = version.getReglasEvaluacionIc();
    JsonNode reglas =
        configuracion == null
            ? com.fasterxml.jackson.databind.node.MissingNode.getInstance()
            : configuracion.path("reglas_evaluacion_ic");

    List<String> pendientes = new ArrayList<>();
    List<String> invalidas = new ArrayList<>();

    for (String campo : List.of("indicadores_de_un_elemento", "instancias_repetibles")) {
      JsonNode valor = reglas.path("agregacion").path(campo);
      String referencia = "agregacion." + campo;

      if (valor.isMissingNode()
          || valor.isNull()
          || (valor.isTextual() && valor.asText().isBlank())) {
        pendientes.add(referencia);
      } else if (!valor.isTextual() || !Set.of("MAXIMO", "PROMEDIO").contains(valor.asText())) {
        invalidas.add(referencia + ": debe ser MAXIMO o PROMEDIO");
      }
    }

    for (String campo :
        List.of("elementos_ausentes_portantes", "elementos_ausentes_no_portantes")) {
      validarCalificacionConfigurada(
          reglas.path("agregacion").path(campo), "agregacion." + campo, pendientes, invalidas);
    }

    JsonNode rangos = reglas.path("rangos_estado");

    for (String estado : List.of("BUENO", "REGULAR", "MALO")) {
      JsonNode valor = rangos.path(estado);

      if (valor.isMissingNode() || valor.isNull()) {
        pendientes.add("rangos_estado." + estado);
      } else if (!valor.isNumber()) {
        invalidas.add("rangos_estado." + estado + ": debe ser numerico");
      }
    }

    if (rangos.path("BUENO").isNumber()
        && rangos.path("REGULAR").isNumber()
        && rangos.path("MALO").isNumber()) {
      java.math.BigDecimal bueno = rangos.path("BUENO").decimalValue();
      java.math.BigDecimal regular = rangos.path("REGULAR").decimalValue();
      java.math.BigDecimal malo = rangos.path("MALO").decimalValue();

      if (malo.signum() != 0
          || regular.compareTo(malo) <= 0
          || bueno.compareTo(regular) <= 0
          || bueno.compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
        invalidas.add("rangos_estado: debe cumplir MALO = 0 < REGULAR < BUENO <= 100");
      }
    }

    if (!pendientes.isEmpty() || !invalidas.isEmpty()) {
      List<String> detalles = new ArrayList<>();

      if (!pendientes.isEmpty()) {
        detalles.add("Falta configurar: " + String.join(", ", pendientes));
      }

      if (!invalidas.isEmpty()) {
        detalles.add("Configuracion invalida: " + String.join("; ", invalidas));
      }

      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "reglas_ic_invalidas",
          "Reglas del IC incompletas o invalidas",
          String.join(". ", detalles));
    }
  }

  private void validarCalificacionConfigurada(
      JsonNode valor, String referencia, List<String> pendientes, List<String> invalidas) {
    if (valor.isMissingNode() || valor.isNull()) {
      pendientes.add(referencia);
      return;
    }

    if (!valor.isNumber()
        || valor.decimalValue().signum() < 0
        || valor.decimalValue().compareTo(java.math.BigDecimal.valueOf(4)) > 0) {
      invalidas.add(referencia + ": requiere una calificacion numerica entre 0 y 4");
    }
  }

  private void validarConversionesParaPublicacion(VersionFormulario version) {
    JsonNode configuracion = version.getReglasEvaluacionIc();
    JsonNode conversiones =
        configuracion.path("reglas_evaluacion_ic").path("conversion_indicadores");

    Set<String> booleanos = new java.util.TreeSet<>();
    Set<String> numericos = new java.util.TreeSet<>();
    Set<String> enumerados = new java.util.TreeSet<>();
    List<String> errores = new ArrayList<>();

    for (JsonNode elemento : configuracion.path("elementos")) {
      String referencia = elemento.path("elemento_ref").asText();

      for (JsonNode indicador : elemento.path("indicadores")) {
        String nombre = indicador.asText();

        if ("falta_elemento".equals(nombre) || "junta_limpia".equals(nombre)) {
          booleanos.add("junta." + nombre);
        } else if ("buen_estado".equals(nombre)) {
          booleanos.add("losa.buen_estado");
        } else if ("existe".equals(nombre)) {
          if (referencia.startsWith("/otros/senalizacion/")) {
            booleanos.add("senal.existe");
          } else if ("/otros/drenajes_aledanos".equals(referencia)) {
            booleanos.add("drenaje_aledano.existe");
          }
        } else if ("golpeado_longitud_m".equals(nombre) || "golpeados_cantidad".equals(nombre)) {
          numericos.add(nombre);
        } else if ("aguas_arriba".equals(nombre) || "aguas_abajo".equals(nombre)) {
          enumerados.add("socavacion.nivel");
        } else if ("condiciones".equals(nombre)) {
          enumerados.add("condicionesCauce");
        } else if ("estado".equals(nombre)) {
          enumerados.add(
              referencia.startsWith("/superestructura/drenaje")
                  ? "drenaje.estado"
                  : "estado_bueno_malo");
        }
      }
    }

    for (String clave : booleanos) {
      for (String valor : List.of("true", "false")) {
        String equivalencia = clave + "=" + valor;
        validarEquivalenciaParaPublicacion(
            conversiones.path("booleanos").path(equivalencia),
            "conversion_indicadores.booleanos." + equivalencia,
            errores);
      }
    }

    for (String familia : enumerados) {
      List<String> categorias =
          switch (familia) {
            case "socavacion.nivel" ->
                List.of("NO_HAY", "SIN_EXPOSICION", "CON_EXPOSICION", "ASENTAMIENTO_ESTRUCTURA");
            case "condicionesCauce" ->
                List.of("LIMPIO", "ASOLVADO_SEDIMENTADO", "EROSIONADO", "OTRO");
            case "drenaje.estado" -> List.of("LIMPIOS", "OBSTRUIDOS");
            case "estado_bueno_malo" -> List.of("BUENO", "MALO");
            default -> List.of();
          };

      for (String categoria : categorias) {
        validarEquivalenciaParaPublicacion(
            conversiones.path("enumerados").path(familia).path(categoria),
            "conversion_indicadores.enumerados." + familia + "." + categoria,
            errores);
      }
    }

    for (String indicador : numericos) {
      String campo = "golpeado_longitud_m".equals(indicador) ? "umbrales_m" : "umbrales";
      String ruta = "conversion_indicadores.numericos." + indicador + "." + campo;
      JsonNode umbrales = conversiones.path("numericos").path(indicador).path(campo);

      List<java.math.BigDecimal> valores = new ArrayList<>();

      for (String nivel : List.of("LEVE", "MODERADO", "SEVERO")) {
        JsonNode valor = umbrales.path(nivel);

        if (!valor.isNumber()) {
          errores.add(ruta + "." + nivel + " requiere un valor numerico");
        } else {
          valores.add(valor.decimalValue());
        }
      }

      if (valores.size() == 3
          && (valores.get(0).signum() <= 0
              || valores.get(1).compareTo(valores.get(0)) <= 0
              || valores.get(2).compareTo(valores.get(1)) <= 0)) {
        errores.add(ruta + " debe cumplir 0 < LEVE < MODERADO < SEVERO");
      }
    }

    if (!errores.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "conversiones_ic_invalidas",
          "Conversiones del IC incompletas o invalidas",
          String.join("; ", errores));
    }
  }

  private void validarEquivalenciaParaPublicacion(
      JsonNode valor, String referencia, List<String> errores) {
    if (!valor.isNumber()
        || valor.decimalValue().signum() < 0
        || valor.decimalValue().compareTo(java.math.BigDecimal.valueOf(4)) > 0) {
      errores.add(referencia + " requiere una calificacion numerica entre 0 y 4");
    }
  }
}
