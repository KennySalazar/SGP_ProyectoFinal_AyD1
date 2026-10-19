package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import gt.usac.cunoc.sgp.inspeccion.dto.AcompananteRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.AcompananteResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.DatosPermanentesResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.GuardarBorradorRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.GuardarRespuestasRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IndiceCondicionPreliminarResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionVersionadaRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionVersionadaResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionBorradorResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionPublicadaDetalleResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionPublicadaResumenResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionResumenResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.ProgresoBorradorResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.entity.InspeccionAcompanante;
import gt.usac.cunoc.sgp.inspeccion.entity.InspeccionDatoTecnico;
import gt.usac.cunoc.sgp.inspeccion.mapper.InspeccionMapper;
import gt.usac.cunoc.sgp.inspeccion.model.EntradaCalculoIc;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionDatoTecnicoRepository;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.repository.ResultadoIcRepository;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.AsignacionPuenteRepository;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.sync.model.SolicitudOperacion;
import gt.usac.cunoc.sgp.sync.service.OperacionIdempotenteService;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import gt.usac.cunoc.sgp.usuario.repository.UsuarioProfesionalRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspeccionService {

  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(), 4326);
  private static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");
  private static final Set<String> ESTADOS_VALIDOS =
      Set.of("BORRADOR", "ENVIADA", "EN_REVISION", "CAMBIOS_SOLICITADOS", "PUBLICADA", "RECHAZADA");

  private final InspeccionRepository inspeccionRepository;
  private final InspeccionDatoTecnicoRepository datoTecnicoRepository;
  private final PuenteRepository puenteRepository;
  private final VersionFormularioRepository versionFormularioRepository;
  private final UserAccountRepository userAccountRepository;
  private final UsuarioProfesionalRepository profesionalRepository;
  private final OperacionIdempotenteService operaciones;
  private final AuditoriaWriter auditoriaWriter;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final CalculadorProgresoBorrador calculadorProgreso;
  private final PreparadorEntradaIcService preparadorEntradaIcService;
  private final CalculoIndiceCondicionService calculoIndiceCondicionService;
  private final JsonSchemaValidatorService jsonSchemaValidatorService;
  private final InspeccionMapper inspeccionMapper;
  private AsignacionPuenteRepository asignaciones;
  private final ResultadoIcRepository resultadoIcRepository;

  public InspeccionService(
      InspeccionRepository inspeccionRepository,
      VersionFormularioRepository versionFormularioRepository,
      PuenteRepository puenteRepository,
      JsonSchemaValidatorService jsonSchemaValidatorService,
      InspeccionMapper inspeccionMapper,
      ObjectMapper objectMapper,
      AsignacionPuenteRepository asignaciones) {
    this(
        inspeccionRepository,
        null,
        puenteRepository,
        versionFormularioRepository,
        null,
        null,
        null,
        null,
        objectMapper,
        Clock.systemUTC(),
        null,
        null,
        null,
        jsonSchemaValidatorService,
        inspeccionMapper,
        asignaciones);
  }

  public InspeccionService(
      InspeccionRepository inspeccionRepository,
      InspeccionDatoTecnicoRepository datoTecnicoRepository,
      PuenteRepository puenteRepository,
      VersionFormularioRepository versionFormularioRepository,
      UserAccountRepository userAccountRepository,
      UsuarioProfesionalRepository profesionalRepository,
      OperacionIdempotenteService operaciones,
      AuditoriaWriter auditoriaWriter,
      ObjectMapper objectMapper,
      Clock clock) {
    this(
        inspeccionRepository,
        datoTecnicoRepository,
        puenteRepository,
        versionFormularioRepository,
        userAccountRepository,
        profesionalRepository,
        operaciones,
        auditoriaWriter,
        objectMapper,
        clock,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public InspeccionService(
      InspeccionRepository inspeccionRepository,
      InspeccionDatoTecnicoRepository datoTecnicoRepository,
      PuenteRepository puenteRepository,
      VersionFormularioRepository versionFormularioRepository,
      UserAccountRepository userAccountRepository,
      UsuarioProfesionalRepository profesionalRepository,
      OperacionIdempotenteService operaciones,
      AuditoriaWriter auditoriaWriter,
      ObjectMapper objectMapper,
      Clock clock,
      CalculadorProgresoBorrador calculadorProgreso,
      PreparadorEntradaIcService preparadorEntradaIcService,
      CalculoIndiceCondicionService calculoIndiceCondicionService) {
    this(
        inspeccionRepository,
        datoTecnicoRepository,
        puenteRepository,
        versionFormularioRepository,
        userAccountRepository,
        profesionalRepository,
        operaciones,
        auditoriaWriter,
        objectMapper,
        clock,
        calculadorProgreso,
        preparadorEntradaIcService,
        calculoIndiceCondicionService,
        null,
        null,
        null);
  }

  public InspeccionService(
      InspeccionRepository inspeccionRepository,
      InspeccionDatoTecnicoRepository datoTecnicoRepository,
      PuenteRepository puenteRepository,
      VersionFormularioRepository versionFormularioRepository,
      UserAccountRepository userAccountRepository,
      UsuarioProfesionalRepository profesionalRepository,
      OperacionIdempotenteService operaciones,
      AuditoriaWriter auditoriaWriter,
      ObjectMapper objectMapper,
      Clock clock,
      CalculadorProgresoBorrador calculadorProgreso,
      PreparadorEntradaIcService preparadorEntradaIcService,
      CalculoIndiceCondicionService calculoIndiceCondicionService,
      JsonSchemaValidatorService jsonSchemaValidatorService,
      InspeccionMapper inspeccionMapper,
      AsignacionPuenteRepository asignaciones) {
    this(
        inspeccionRepository,
        datoTecnicoRepository,
        puenteRepository,
        versionFormularioRepository,
        userAccountRepository,
        profesionalRepository,
        operaciones,
        auditoriaWriter,
        objectMapper,
        clock,
        calculadorProgreso,
        preparadorEntradaIcService,
        calculoIndiceCondicionService,
        jsonSchemaValidatorService,
        inspeccionMapper,
        asignaciones,
        null);
  }

  @Autowired
  public InspeccionService(
      InspeccionRepository inspeccionRepository,
      InspeccionDatoTecnicoRepository datoTecnicoRepository,
      PuenteRepository puenteRepository,
      VersionFormularioRepository versionFormularioRepository,
      UserAccountRepository userAccountRepository,
      UsuarioProfesionalRepository profesionalRepository,
      OperacionIdempotenteService operaciones,
      AuditoriaWriter auditoriaWriter,
      ObjectMapper objectMapper,
      Clock clock,
      CalculadorProgresoBorrador calculadorProgreso,
      PreparadorEntradaIcService preparadorEntradaIcService,
      CalculoIndiceCondicionService calculoIndiceCondicionService,
      JsonSchemaValidatorService jsonSchemaValidatorService,
      InspeccionMapper inspeccionMapper,
      AsignacionPuenteRepository asignaciones,
      ResultadoIcRepository resultadoIcRepository) {
    this.inspeccionRepository = inspeccionRepository;
    this.datoTecnicoRepository = datoTecnicoRepository;
    this.puenteRepository = puenteRepository;
    this.versionFormularioRepository = versionFormularioRepository;
    this.userAccountRepository = userAccountRepository;
    this.profesionalRepository = profesionalRepository;
    this.operaciones = operaciones;
    this.auditoriaWriter = auditoriaWriter;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.calculadorProgreso =
        calculadorProgreso != null ? calculadorProgreso : new CalculadorProgresoBorrador();
    this.preparadorEntradaIcService = preparadorEntradaIcService;
    this.calculoIndiceCondicionService = calculoIndiceCondicionService;
    this.jsonSchemaValidatorService = jsonSchemaValidatorService;
    this.inspeccionMapper = inspeccionMapper;
    this.asignaciones = asignaciones;
    this.resultadoIcRepository = resultadoIcRepository;
  }

  @Autowired(required = false)
  void setAsignacionPuenteRepository(AsignacionPuenteRepository asignaciones) {
    this.asignaciones = asignaciones;
  }

  /** Conserva el contrato de HU-024 utilizado por los consumidores de HU-028. */
  @Transactional
  public IniciarInspeccionResponse iniciarInspeccion(
      IniciarInspeccionRequest request, UUID usuarioId, String idempotencyKey) {
    return iniciarInspeccionHu024(request, usuarioId, idempotencyKey);
  }

  @Transactional
  public IniciarInspeccionResponse iniciarInspeccionHu024(
      IniciarInspeccionRequest request, UUID usuarioId, String idempotencyKey) {
    // 1. Autorización de rol y verificación
    UserAccount user = validarInspector(usuarioId);

    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      var solicitud =
          new SolicitudOperacion(
              usuarioId,
              idempotencyKey,
              "POST",
              "/api/v1/inspecciones",
              calcularHash(request.toString()),
              "inspeccion",
              request.id());
      return operaciones.ejecutar(
          solicitud,
          201,
          () -> crearORecuperar(request, user),
          IniciarInspeccionResponse::id,
          nodo -> {
            if (nodo.has("rolAutor"))
              return objectMapper.convertValue(nodo, IniciarInspeccionResponse.class);
            // Compatibilidad con snapshots de HU-024 guardados antes del coordinador común.
            UUID id = UUID.fromString(nodo.path("id").asText());
            return inspeccionRepository
                .findConDetallesById(id)
                .filter(i -> i.getAutorId().equals(usuarioId) && i.getEliminadoEn() == null)
                .map(this::aResponse)
                .orElseThrow(() -> conflictoIdentidad());
          });
    }
    return crearORecuperar(request, user);
  }

  private IniciarInspeccionResponse crearORecuperar(
      IniciarInspeccionRequest request, UserAccount user) {
    if (request.id() != null) {
      operaciones.bloquearEntidad("inspeccion", request.id());
      var existente = inspeccionRepository.findConDetallesById(request.id());
      if (existente.isPresent()) {
        var i = existente.get();
        if (!i.getAutorId().equals(user.getId())
            || i.getEliminadoEn() != null
            || !i.getPuente().getId().equals(request.puenteId())
            || !i.getFechaInspeccion().equals(request.fechaInspeccion())
            || !i.getDispositivoId().equals(request.dispositivoId())) throw conflictoIdentidad();
        return aResponse(i);
      }
    }
    return crearNuevaInspeccion(request, user);
  }

  private ApiException conflictoIdentidad() {
    return new ApiException(
        HttpStatus.CONFLICT,
        "inspeccion_identidad_conflictiva",
        "Conflicto de inspección",
        "El identificador de inspección ya corresponde a otra solicitud o no está disponible.");
  }

  @Transactional
  public IniciarInspeccionVersionadaResponse iniciarInspeccion(
      IniciarInspeccionVersionadaRequest request, UUID usuarioId, String rolUsuario) {
    if (request == null || request.puenteId() == null || request.versionFormularioId() == null) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "solicitud_invalida",
          "Solicitud inválida",
          "El puente y la versión del formulario son obligatorios.");
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
            .filter(candidata -> candidata.getEstado() == EstadoVersionFormulario.PUBLICADA)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "version_no_utilizable",
                        "Versión de formulario no utilizable",
                        "Solo se pueden iniciar inspecciones con versiones de formulario publicadas"));

    JsonNode datos = request.datos();
    if (datos == null || datos.isNull() || datos.isEmpty()) {
      datos = objectMapper.createObjectNode();
    }
    if (jsonSchemaValidatorService != null) {
      jsonSchemaValidatorService.validarRespuestas(version.getEsquema(), datos, true);
    }

    LocalDate fecha =
        request.fechaInspeccion() != null
            ? request.fechaInspeccion()
            : LocalDate.now(clock.withZone(ZONA_GUATEMALA));
    String dispositivo =
        request.dispositivoId() != null && !request.dispositivoId().isBlank()
            ? request.dispositivoId()
            : "web";
    String nombre =
        request.nombreAutorCapturado() != null && !request.nombreAutorCapturado().isBlank()
            ? request.nombreAutorCapturado()
            : "Inspector";

    if (RoleName.ESTUDIANTE.name().equals(rolUsuario)) {
      if (request.asignacionPuenteId() == null
          || asignaciones == null
          || !asignaciones.existsVigenteParaInspeccion(
              request.asignacionPuenteId(), usuarioId, puente.getId())
          || asignaciones
              .findVigentePorId(request.asignacionPuenteId(), usuarioId, fecha)
              .isEmpty()) {
        throw new ApiException(
            HttpStatus.NOT_FOUND,
            "asignacion_no_encontrada",
            "Asignación no encontrada",
            "No existe una asignación vigente del estudiante para este puente.");
      }
    }

    Inspeccion inspeccion =
        new Inspeccion(puente, version, usuarioId, rolUsuario, nombre, fecha, dispositivo, datos);
    if (request.asignacionPuenteId() != null) {
      if (!RoleName.ESTUDIANTE.name().equals(rolUsuario)
          && !asignaciones.existsVigenteParaInspeccion(
              request.asignacionPuenteId(), usuarioId, puente.getId())) {
        throw new ApiException(
            HttpStatus.NOT_FOUND,
            "asignacion_no_encontrada",
            "Asignación no encontrada",
            "No existe una asignación vigente del autor para este puente.");
      }
      inspeccion.setAsignacionPuenteId(request.asignacionPuenteId());
    }
    InspeccionResumenResponse response =
        inspeccionMapper.toResumenResponse(inspeccionRepository.save(inspeccion));
    return new IniciarInspeccionVersionadaResponse(
        response.id(),
        response.puenteId(),
        response.puenteNombre(),
        response.versionFormularioId(),
        response.versionFormularioCodigo(),
        response.autorId(),
        response.rolAutorCreacion(),
        response.nombreAutorCapturado(),
        response.tipo(),
        response.estado(),
        response.fechaInspeccion(),
        response.dispositivoId(),
        response.datos(),
        response.creadoEn(),
        response.actualizadoEn());
  }

  @Transactional(readOnly = true)
  public Optional<DatosPermanentesResponse> obtenerDatosPermanentesVigentes(UUID puenteId) {
    Puente puente =
        puenteRepository
            .findById(puenteId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "puente_no_encontrado",
                        "Puente no encontrado",
                        "No se encontró el puente especificado."));

    if (puente.getInspeccionVigenteId() == null) {
      return Optional.empty();
    }

    return datoTecnicoRepository
        .findByInspeccionId(puente.getInspeccionVigenteId())
        .map(this::aDatosPermanentesResponse);
  }

  @Transactional
  public InspeccionBorradorResponse guardarBorrador(
      UUID id, GuardarBorradorRequest request, UUID usuarioId) {
    Inspeccion inspeccion =
        inspeccionRepository
            .findConDetallesById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "inspeccion_no_encontrada",
                        "Inspección no encontrada",
                        "No se encontró la inspección especificada."));

    if (inspeccion.getEliminadoEn() != null) {
      throw new ApiException(
          HttpStatus.NOT_FOUND,
          "inspeccion_no_encontrada",
          "Inspección no encontrada",
          "No se encontró la inspección especificada.");
    }

    // 7. Solo el autor puede editar
    if (!inspeccion.getAutorId().equals(usuarioId)) {
      throw new ApiException(
          HttpStatus.NOT_FOUND,
          "inspeccion_no_encontrada",
          "Inspección no encontrada",
          "No se encontró la inspección especificada o no tiene acceso a ella.");
    }

    // 6. Solo BORRADOR puede editarse
    if (!"BORRADOR".equals(inspeccion.getEstado())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "inspeccion_no_editable",
          "Inspección no editable",
          "Solo las inspecciones en estado BORRADOR pueden ser modificadas.");
    }

    // 8. Ediciones obsoletas / concurrencia optimista
    if (request != null
        && request.version() != null
        && !Objects.equals(request.version(), inspeccion.getVersion())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "concurrencia_conflicto",
          "Conflicto de concurrencia",
          "La inspección fue modificada concurrentemente por otra sesión o dispositivo.");
    }

    // 9. Fechas futuras o anteriores a la construcción
    if (request != null && request.fechaInspeccion() != null) {
      LocalDate hoy = LocalDate.now(clock);
      if (request.fechaInspeccion().isAfter(hoy)) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "fecha_futura",
            "Fecha de inspección inválida",
            "La fecha de inspección no puede ser posterior a la fecha actual.");
      }

      Short anioConstruccion = null;
      if (inspeccion.getDatoTecnico() != null
          && inspeccion.getDatoTecnico().getAnioConstruccion() != null) {
        anioConstruccion = inspeccion.getDatoTecnico().getAnioConstruccion();
      } else if (inspeccion.getPuente().getInspeccionVigenteId() != null) {
        var tv =
            datoTecnicoRepository
                .findByInspeccionId(inspeccion.getPuente().getInspeccionVigenteId())
                .orElse(null);
        if (tv != null) {
          anioConstruccion = tv.getAnioConstruccion();
        }
      }

      if (anioConstruccion != null && request.fechaInspeccion().getYear() < anioConstruccion) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "fecha_anterior_construccion",
            "Fecha de inspección inválida",
            "La fecha de inspección ("
                + request.fechaInspeccion()
                + ") no puede ser anterior al año de construcción registrado ("
                + anioConstruccion
                + ").");
      }
    }

    Instant ahora = clock.instant();

    if (request != null) {
      inspeccion.actualizarBorrador(
          request.datos(), request.identificacionCapturada(), request.fechaInspeccion(), ahora);

      if (request.acompanantes() != null) {
        List<InspeccionAcompanante> nuevos = new ArrayList<>();
        for (AcompananteRequest a : request.acompanantes()) {
          nuevos.add(
              new InspeccionAcompanante(
                  UuidV7Generator.generate(),
                  inspeccion,
                  a.usuarioId(),
                  a.nombre().trim(),
                  a.numeroColegiado() != null ? a.numeroColegiado().trim() : null,
                  ahora));
        }
        inspeccion.reemplazarAcompanantes(nuevos);
      }
    } else {
      inspeccion.actualizarBorrador(null, null, null, ahora);
    }

    inspeccionRepository.saveAndFlush(inspeccion);

    return aBorradorResponse(inspeccion);
  }

  @Transactional(readOnly = true)
  public InspeccionBorradorResponse obtenerBorrador(UUID id, UUID usuarioId) {
    Inspeccion inspeccion =
        inspeccionRepository
            .findConDetallesById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "inspeccion_no_encontrada",
                        "Inspección no encontrada",
                        "No se encontró la inspección especificada o no tiene acceso a ella."));

    boolean autor = inspeccion.getAutorId().equals(usuarioId);
    boolean catedraticoAsignado = esCatedraticoAsignado(inspeccion, usuarioId);
    // El autor edita; el Catedrático asignado puede consultar el borrador en solo lectura.
    if (inspeccion.getEliminadoEn() != null
        || (!autor && !catedraticoAsignado)
        || !"BORRADOR".equals(inspeccion.getEstado())) {
      throw new ApiException(
          HttpStatus.NOT_FOUND,
          "inspeccion_no_encontrada",
          "Inspección no encontrada",
          "No se encontró la inspección especificada o no tiene acceso a ella.");
    }

    return aBorradorResponse(inspeccion);
  }

  @Transactional(readOnly = true)
  public Page<InspeccionResumenResponse> listarMisInspecciones(
      UUID usuarioId, String estado, int pagina, int tamanio) {
    validarInspector(usuarioId);

    if (pagina < 0 || tamanio < 1 || tamanio > 100) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "paginacion_invalida",
          "Paginación inválida",
          "La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.");
    }

    String estadoFiltro = null;
    if (estado != null && !estado.isBlank()) {
      estadoFiltro = estado.trim();
      if (!ESTADOS_VALIDOS.contains(estadoFiltro)) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "estado_invalido",
            "Estado inválido",
            "El estado debe ser uno de: " + ESTADOS_VALIDOS);
      }
    }

    Pageable pageable =
        PageRequest.of(
            pagina,
            tamanio,
            Sort.by(Sort.Order.desc("ultimaActividadEn"), Sort.Order.desc("creadoEn")));

    Page<Inspeccion> resultado =
        inspeccionRepository.findMisInspecciones(usuarioId, estadoFiltro, pageable);
    Map<UUID, UUID> superadas = correccionesPublicadasDe(resultado.getContent());
    return resultado.map(
        i -> {
          InspeccionResumenResponse resumen = aResumenResponse(i);
          UUID correccion = superadas.get(i.getId());
          return correccion == null ? resumen : resumen.conSuperadaPor(correccion);
        });
  }

  /** Correcciones publicadas que superan a las inspecciones indicadas, en una sola consulta. */
  private Map<UUID, UUID> correccionesPublicadasDe(List<Inspeccion> inspecciones) {
    List<UUID> publicadas =
        inspecciones.stream()
            .filter(i -> "PUBLICADA".equals(i.getEstado()))
            .map(Inspeccion::getId)
            .toList();
    Map<UUID, UUID> superadas = new HashMap<>();
    if (!publicadas.isEmpty()) {
      for (Object[] fila : inspeccionRepository.findCorreccionesPublicadas(publicadas)) {
        superadas.put((UUID) fila[0], (UUID) fila[1]);
      }
    }
    return superadas;
  }

  @Transactional
  public void eliminarBorrador(UUID id, UUID usuarioId) {
    Inspeccion inspeccion =
        inspeccionRepository
            .findConDetallesById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "inspeccion_no_encontrada",
                        "Inspección no encontrada",
                        "No se encontró la inspección especificada o no tiene acceso a ella."));

    if (inspeccion.getEliminadoEn() != null || !inspeccion.getAutorId().equals(usuarioId)) {
      throw new ApiException(
          HttpStatus.NOT_FOUND,
          "inspeccion_no_encontrada",
          "Inspección no encontrada",
          "No se encontró la inspección especificada o no tiene acceso a ella.");
    }

    validarInspector(usuarioId);

    if ("PUBLICADA".equals(inspeccion.getEstado())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "inspeccion_publicada",
          "Inspección publicada",
          "No se puede eliminar una inspección publicada.");
    }

    if (!"BORRADOR".equals(inspeccion.getEstado())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "inspeccion_no_eliminable",
          "Inspección no eliminable",
          "Solo las inspecciones en estado BORRADOR pueden ser eliminadas.");
    }

    Instant ahora = clock.instant();
    inspeccion.marcarEliminado(usuarioId, ahora);
    inspeccionRepository.save(inspeccion);

    ObjectNode snapshotAnterior = objectMapper.createObjectNode();
    snapshotAnterior.put("id", inspeccion.getId().toString());
    snapshotAnterior.put("puenteId", inspeccion.getPuente().getId().toString());
    snapshotAnterior.put("codigoPuente", inspeccion.getPuente().getCodigo());
    snapshotAnterior.put("estado", inspeccion.getEstado());
    snapshotAnterior.put("autorId", inspeccion.getAutorId().toString());

    ObjectNode snapshotNuevo = objectMapper.createObjectNode();
    snapshotNuevo.put("eliminadoEn", ahora.toString());
    snapshotNuevo.put("eliminadoPorId", usuarioId.toString());

    auditoriaWriter.registrarEnTransaccion(
        usuarioId,
        AccionAuditoria.CAMBIAR_ESTADO,
        "inspeccion",
        inspeccion.getId(),
        snapshotAnterior,
        snapshotNuevo,
        "HU-031");
  }

  private InspeccionResumenResponse aResumenResponse(Inspeccion i) {
    String depto = null;
    String muni = null;
    if (i.getPuente() != null && i.getPuente().getMunicipio() != null) {
      muni = i.getPuente().getMunicipio().getNombre();
      if (i.getPuente().getMunicipio().getDepartamento() != null) {
        depto = i.getPuente().getMunicipio().getDepartamento().getNombre();
      }
    }

    Double avance = null;
    if ("BORRADOR".equals(i.getEstado())) {
      ProgresoBorradorResponse progreso = calculadorProgreso.calcularProgreso(i);
      avance = progreso != null ? progreso.porcentajeTotal() : 0.0;
    } else if ("PUBLICADA".equals(i.getEstado())) {
      avance = 100.0;
    }

    return new InspeccionResumenResponse(
        i.getId(),
        i.getPuente().getId(),
        i.getPuente().getCodigo(),
        i.getPuente().getNombre(),
        depto,
        muni,
        i.getVersionFormulario().getId(),
        i.getVersionFormulario().getCodigo(),
        i.getAutorId(),
        i.getRolAutorCreacion(),
        i.getNombreAutorCapturado(),
        i.getEstado(),
        i.getTipo(),
        i.getFechaInspeccion(),
        avance,
        i.getUltimaActividadEn(),
        i.getCreadoEn(),
        i.getActualizadoEn());
  }

  private IniciarInspeccionResponse crearNuevaInspeccion(
      IniciarInspeccionRequest request, UserAccount user) {
    // 3. Validar puente
    Puente puente =
        puenteRepository
            .findById(request.puenteId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "puente_no_encontrado",
                        "Puente no encontrado",
                        "El puente especificado no existe."));

    if (!puente.isActivo()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "puente_inactivo",
          "Puente inactivo",
          "El puente está inactivo y no admite nuevas inspecciones.");
    }

    LocalDate hoyGuatemala = LocalDate.now(clock.withZone(ZONA_GUATEMALA));
    UUID asignacionPuenteId = null;
    if (user.getRole().getName() == RoleName.ESTUDIANTE) {
      if (asignaciones == null) {
        throw new ApiException(
            HttpStatus.NOT_FOUND,
            "asignacion_no_encontrada",
            "Asignación no encontrada",
            "No existe una asignación vigente del estudiante para este puente.");
      }
      asignacionPuenteId =
          asignaciones
              .findVigenteParaInspeccion(user.getId(), puente.getId(), hoyGuatemala)
              .map(AsignacionPuenteRepository.AsignacionInspeccionProjection::getId)
              .orElseThrow(
                  () ->
                      new ApiException(
                          HttpStatus.NOT_FOUND,
                          "asignacion_no_encontrada",
                          "Asignación no encontrada",
                          "No existe una asignación vigente del estudiante para este puente."));
    }

    // 4. Versión activa de formulario (fijada para la inspección)
    VersionFormulario versionActiva =
        versionFormularioRepository
            .findByActivaTrue()
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.CONFLICT,
                        "sin_formulario_activo",
                        "Formulario no disponible",
                        "No existe una versión de formulario activa para iniciar inspecciones."));
    if (versionActiva.getEstado() != EstadoVersionFormulario.PUBLICADA) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "version_no_utilizable",
          "Versión de formulario no utilizable",
          "Solo se pueden iniciar inspecciones con versiones de formulario publicadas");
    }

    JsonNode datosIniciales = objectMapper.createObjectNode();
    if (jsonSchemaValidatorService != null) {
      jsonSchemaValidatorService.validarRespuestas(
          versionActiva.getEsquema(), datosIniciales, true);
    }

    // 5. Validar fechas de inspección
    LocalDate fechaInspeccion =
        request.fechaInspeccion() != null ? request.fechaInspeccion() : hoyGuatemala;
    if (fechaInspeccion.isAfter(hoyGuatemala)) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "fecha_inspeccion_futura",
          "Fecha de inspección inválida",
          "La fecha de inspección no puede ser posterior a la fecha actual.");
    }

    // Comprobar año de construcción del puente si existe inspección previa
    InspeccionDatoTecnico tecnicoVigente = null;
    if (puente.getInspeccionVigenteId() != null) {
      tecnicoVigente =
          datoTecnicoRepository.findByInspeccionId(puente.getInspeccionVigenteId()).orElse(null);
      if (tecnicoVigente != null
          && tecnicoVigente.getAnioConstruccion() != null
          && fechaInspeccion.getYear() < tecnicoVigente.getAnioConstruccion()) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "fecha_anterior_construccion",
            "Fecha de inspección inválida",
            "La fecha de inspección ("
                + fechaInspeccion
                + ") no puede ser anterior al año de construcción registrado ("
                + tecnicoVigente.getAnioConstruccion()
                + ").");
      }
    }

    // 6. Validación y cálculo GPS
    Point ubicacionInicio = null;
    BigDecimal precisionGps = null;
    BigDecimal distanciaAlPuente = null;
    boolean advertenciaGps = false;
    String mensajeAdvertencia = null;

    if (request.latitud() != null && request.longitud() != null) {
      ubicacionInicio =
          GEOMETRY_FACTORY.createPoint(new Coordinate(request.longitud(), request.latitud()));
      precisionGps = request.precisionGpsM();

      double distancia =
          calcularDistanciaMetros(
              puente.getUbicacion().getY(),
              puente.getUbicacion().getX(),
              request.latitud(),
              request.longitud());
      distanciaAlPuente = BigDecimal.valueOf(distancia).setScale(2, RoundingMode.HALF_UP);

      if (distancia > 500.0) {
        advertenciaGps = true;
        mensajeAdvertencia =
            "El dispositivo se encuentra a más de 500 metros del puente ("
                + Math.round(distancia)
                + " m).";
      }
    }

    // 7. Precarga de datos permanentes con confirmación explícita (nunca daños)
    Instant ahora = clock.instant();
    Instant datosPermanentesConfirmadosEn = null;
    InspeccionDatoTecnico datoTecnico = null;

    if (request.confirmarDatosPermanentes() && tecnicoVigente != null) {
      datosPermanentesConfirmadosEn = ahora;
    }

    // 8. Crear agregada Inspeccion en BORRADOR
    UUID inspeccionId = request.id() != null ? request.id() : UuidV7Generator.generate();
    String nombreAutorCapturado =
        request.nombreAutor() != null && !request.nombreAutor().isBlank()
            ? request.nombreAutor().trim()
            : user.getEmail();
    String dispositivoId =
        request.dispositivoId() != null && !request.dispositivoId().isBlank()
            ? request.dispositivoId()
            : "web";

    Inspeccion inspeccion =
        new Inspeccion(
            inspeccionId,
            puente,
            versionActiva,
            user.getId(),
            user.getRole().getName().name(),
            nombreAutorCapturado,
            fechaInspeccion,
            dispositivoId,
            ubicacionInicio,
            precisionGps,
            distanciaAlPuente,
            datosPermanentesConfirmadosEn,
            datosIniciales,
            ahora);
    inspeccion.setAsignacionPuenteId(asignacionPuenteId);

    if (request.confirmarDatosPermanentes() && tecnicoVigente != null) {
      datoTecnico = new InspeccionDatoTecnico(inspeccion, ahora);
      datoTecnico.copiarDatosPermanentesDe(tecnicoVigente);
      inspeccion.setDatoTecnico(datoTecnico);
    }

    // Acompañantes
    if (request.acompanantes() != null) {
      for (AcompananteRequest a : request.acompanantes()) {
        InspeccionAcompanante acompanante =
            new InspeccionAcompanante(
                UuidV7Generator.generate(),
                inspeccion,
                a.usuarioId(),
                a.nombre().trim(),
                a.numeroColegiado() != null ? a.numeroColegiado().trim() : null,
                ahora);
        inspeccion.agregarAcompanante(acompanante);
      }
    }

    inspeccionRepository.save(inspeccion);

    // 9. Registro de auditoría
    ObjectNode snapshot = objectMapper.createObjectNode();
    snapshot.put("id", inspeccion.getId().toString());
    snapshot.put("puenteId", puente.getId().toString());
    snapshot.put("codigoPuente", puente.getCodigo());
    snapshot.put("versionFormularioId", versionActiva.getId().toString());
    snapshot.put("autorId", user.getId().toString());
    snapshot.put("rolAutorCreacion", user.getRole().getName().name());
    snapshot.put("nombreAutorCapturado", nombreAutorCapturado);
    snapshot.put("estado", "BORRADOR");
    snapshot.put("fechaInspeccion", fechaInspeccion.toString());
    snapshot.put("dispositivoId", dispositivoId);
    snapshot.put("datosPermanentesConfirmados", datosPermanentesConfirmadosEn != null);
    if (distanciaAlPuente != null) {
      snapshot.put("distanciaAlPuenteM", distanciaAlPuente);
    }

    auditoriaWriter.registrarEnTransaccion(
        user.getId(),
        AccionAuditoria.CREAR,
        "inspeccion",
        inspeccion.getId(),
        null,
        snapshot,
        user.getRole().getName() == RoleName.ESTUDIANTE ? "HU-035" : "HU-024");

    IniciarInspeccionResponse response = aResponse(inspeccion);
    if (advertenciaGps) {
      return new IniciarInspeccionResponse(
          response.id(),
          response.puenteId(),
          response.codigoPuente(),
          response.nombrePuente(),
          response.versionFormularioId(),
          response.codigoVersionFormulario(),
          response.autorId(),
          response.rolAutor(),
          response.nombreAutor(),
          response.estado(),
          response.fechaInspeccion(),
          response.dispositivoId(),
          response.latitudInicio(),
          response.longitudInicio(),
          response.precisionGpsM(),
          response.distanciaAlPuenteM(),
          true,
          mensajeAdvertencia,
          response.datosPermanentesConfirmados(),
          response.datosPermanentes(),
          response.acompanantes(),
          response.creadoEn());
    }
    return response;
  }

  private UserAccount validarInspector(UUID usuarioId) {
    if (usuarioId == null) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida para iniciar una inspección.");
    }

    UserAccount user =
        userAccountRepository
            .findWithRoleById(usuarioId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "usuario_no_encontrado",
                        "Usuario no encontrado",
                        "No se encontró el usuario autenticado."));

    if (!user.isActive() || !user.isActivated() || !user.isVerified()) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "cuenta_inactiva",
          "Cuenta no disponible",
          "La cuenta del usuario se encuentra inactiva o no verificada.");
    }

    RoleName role = user.getRole().getName();
    if (role == RoleName.PROFESIONAL_EXTERNO) {
      boolean verificado =
          profesionalRepository
              .findById(user.getId())
              .map(p -> p.isColegiadoVerificado())
              .orElse(false);
      if (!verificado) {
        throw new ApiException(
            HttpStatus.FORBIDDEN,
            "profesional_no_verificado",
            "Colegiado no verificado",
            "Tu número de colegiado aún no ha sido verificado por el Administrador.");
      }
    } else if (role != RoleName.CATEDRATICO && role != RoleName.ESTUDIANTE) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "rol_no_autorizado",
          "Acceso denegado",
          "Solo Estudiantes asignados, Catedráticos y Profesionales externos verificados pueden iniciar inspecciones.");
    }

    return user;
  }

  private IniciarInspeccionResponse aResponse(Inspeccion i) {
    List<AcompananteResponse> acs =
        i.getAcompanantes().stream()
            .map(
                a ->
                    new AcompananteResponse(
                        a.getId(), a.getUsuarioId(), a.getNombre(), a.getNumeroColegiado()))
            .toList();

    DatosPermanentesResponse dp =
        i.getDatoTecnico() != null ? aDatosPermanentesResponse(i.getDatoTecnico()) : null;

    Double lat = i.getUbicacionInicio() != null ? i.getUbicacionInicio().getY() : null;
    Double lon = i.getUbicacionInicio() != null ? i.getUbicacionInicio().getX() : null;

    boolean advertencia =
        i.getDistanciaAlPuenteM() != null
            && i.getDistanciaAlPuenteM().compareTo(BigDecimal.valueOf(500.0)) > 0;
    String msjAdvertencia =
        advertencia
            ? "El dispositivo se encuentra a más de 500 metros del puente ("
                + i.getDistanciaAlPuenteM()
                + " m)."
            : null;

    return new IniciarInspeccionResponse(
        i.getId(),
        i.getPuente().getId(),
        i.getPuente().getCodigo(),
        i.getPuente().getNombre(),
        i.getVersionFormulario().getId(),
        i.getVersionFormulario().getCodigo(),
        i.getAutorId(),
        i.getRolAutorCreacion(),
        i.getNombreAutorCapturado(),
        i.getEstado(),
        i.getFechaInspeccion(),
        i.getDispositivoId(),
        lat,
        lon,
        i.getPrecisionGpsM(),
        i.getDistanciaAlPuenteM(),
        advertencia,
        msjAdvertencia,
        i.getDatosPermanentesConfirmadosEn() != null,
        dp,
        acs,
        i.getCreadoEn());
  }

  private InspeccionBorradorResponse aBorradorResponse(Inspeccion inspeccion) {
    List<AcompananteResponse> acompanantes =
        inspeccion.getAcompanantes().stream()
            .map(
                a ->
                    new AcompananteResponse(
                        a.getId(), a.getUsuarioId(), a.getNombre(), a.getNumeroColegiado()))
            .toList();

    DatosPermanentesResponse dp =
        inspeccion.getDatoTecnico() != null
            ? aDatosPermanentesResponse(inspeccion.getDatoTecnico())
            : null;

    Double latitud =
        inspeccion.getUbicacionInicio() != null ? inspeccion.getUbicacionInicio().getY() : null;
    Double longitud =
        inspeccion.getUbicacionInicio() != null ? inspeccion.getUbicacionInicio().getX() : null;

    ProgresoBorradorResponse progreso = calculadorProgreso.calcularProgreso(inspeccion);
    IndiceCondicionPreliminarResponse icPreliminar = calcularIcPreliminar(inspeccion);

    return new InspeccionBorradorResponse(
        inspeccion.getId(),
        inspeccion.getPuente().getId(),
        inspeccion.getPuente().getCodigo(),
        inspeccion.getPuente().getNombre(),
        inspeccion.getVersionFormulario().getId(),
        inspeccion.getVersionFormulario().getCodigo(),
        inspeccion.getAutorId(),
        inspeccion.getRolAutorCreacion(),
        inspeccion.getNombreAutorCapturado(),
        inspeccion.getEstado(),
        inspeccion.getFechaInspeccion(),
        inspeccion.getDispositivoId(),
        latitud,
        longitud,
        inspeccion.getPrecisionGpsM(),
        inspeccion.getDistanciaAlPuenteM(),
        false,
        null,
        inspeccion.getDatosPermanentesConfirmadosEn() != null,
        dp,
        inspeccion.getIdentificacionCapturada(),
        inspeccion.getDatos(),
        acompanantes,
        inspeccion.getVersion(),
        inspeccion.getUltimaActividadEn(),
        inspeccion.getCreadoEn(),
        inspeccion.getActualizadoEn(),
        progreso,
        icPreliminar);
  }

  private IndiceCondicionPreliminarResponse calcularIcPreliminar(Inspeccion inspeccion) {
    if (preparadorEntradaIcService == null || calculoIndiceCondicionService == null) {
      return null;
    }
    try {
      if (inspeccion.getVersionFormulario() == null
          || inspeccion.getDatos() == null
          || !inspeccion.getDatos().isObject()
          || inspeccion.getDatos().isEmpty()) {
        return null;
      }
      EntradaCalculoIc entrada =
          preparadorEntradaIcService.preparar(
              inspeccion.getVersionFormulario().getId(), inspeccion.getDatos());
      ResultadoCalculoIc resultado = calculoIndiceCondicionService.calcular(entrada);
      if (resultado != null && resultado.indiceCalculado() != null) {
        String estado =
            resultado.estadoCalculado() != null ? resultado.estadoCalculado().name() : null;
        return new IndiceCondicionPreliminarResponse(
            resultado.indiceCalculado(), estado, "Preliminar");
      }
    } catch (Exception ignored) {
      // Incomplete data for IC calculation is expected during partial draft saves
    }
    return null;
  }

  private DatosPermanentesResponse aDatosPermanentesResponse(InspeccionDatoTecnico d) {
    return new DatosPermanentesResponse(
        d.getLongitudM(),
        d.getNumeroTramos(),
        d.getAnchoRodaduraM(),
        d.getAnchoAceraDerechaM(),
        d.getAnchoAceraIzquierdaM(),
        d.getAlturaLibreSuperiorM(),
        d.getAlturaLibreSobreCauceM(),
        d.getTipologiaPuente(),
        d.getTipoCruce(),
        d.getNumeroViasPorSentido(),
        d.getMaterialSuperestructura(),
        d.getMaterialSubestructura(),
        d.getCargaDiseno(),
        d.getAnioConstruccion() != null ? d.getAnioConstruccion().intValue() : null,
        d.getRutaPavimentada(),
        d.getAlineamientoHorizontal(),
        d.getEsviaje(),
        d.getTipoCuerpoAgua(),
        d.getNombreRio(),
        d.getEncauzamiento(),
        d.getAlumbradoExiste(),
        d.getDrenajesAledanosExisten());
  }

  public static double calcularDistanciaMetros(double lat1, double lon1, double lat2, double lon2) {
    final double R = 6371000.0;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }

  private String calcularHash(String contenido) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(contenido.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Error al calcular hash SHA-256", e);
    }
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

    if (!inspeccion.getAutorId().equals(usuarioId)) {
      throw new ApiException(
          HttpStatus.NOT_FOUND,
          "inspeccion_no_encontrada",
          "Inspección no encontrada",
          "No existe la inspección solicitada.");
    }
    if (!"BORRADOR".equalsIgnoreCase(inspeccion.getEstado())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "inspeccion_no_editable",
          "Inspección no editable",
          "Solo las inspecciones en estado BORRADOR pueden modificarse.");
    }

    if (request.version() != null && !Objects.equals(request.version(), inspeccion.getVersion())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "concurrencia_conflicto",
          "Conflicto de concurrencia",
          "La inspección fue modificada concurrentemente por otra sesión o dispositivo.");
    }

    // Se valida contra la versión de la inspección, NO contra la activa (RN-FRM-02)
    VersionFormulario version = inspeccion.getVersionFormulario();
    if (jsonSchemaValidatorService != null) {
      jsonSchemaValidatorService.validarRespuestas(version.getEsquema(), request.datos(), true);
    }
    inspeccion.setDatos(request.datos());
    Instant ahora = clock.instant();
    inspeccion.setActualizadoEn(ahora);
    inspeccion.setUltimaActividadEn(ahora);

    inspeccion = inspeccionRepository.saveAndFlush(inspeccion);
    return inspeccionMapper.toResumenResponse(inspeccion);
  }

  @Transactional(readOnly = true)
  public Page<InspeccionPublicadaResumenResponse> listarPublicadasPorPuente(
      UUID puenteId, int pagina, int tamanio) {
    if (pagina < 0 || tamanio < 1 || tamanio > 100) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "paginacion_invalida",
          "Paginación inválida",
          "La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.");
    }
    return inspeccionRepository.findPublicadasByPuenteId(
        puenteId,
        PageRequest.of(
            pagina, tamanio, Sort.by(Sort.Order.desc("publicadoEn"), Sort.Order.desc("id"))));
  }

  @Transactional(readOnly = true)
  public InspeccionPublicadaDetalleResponse consultarPublicada(UUID id) {
    Inspeccion inspeccion =
        inspeccionRepository
            .findPublicadaConDetallesById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "inspeccion_no_encontrada",
                        "Inspección no encontrada",
                        "No existe la inspección publicada solicitada."));
    // Leer el resultado de ESTA publicación, nunca el vigente del puente ni un cálculo nuevo.
    var resultado = resultadoIcRepository.findPublicacionByInspeccionId(id).orElse(null);
    UUID correccionId = inspeccionRepository.findCorreccionPublicadaId(id).orElse(null);
    DatosPermanentesResponse permanentes =
        inspeccion.getDatoTecnico() == null
            ? null
            : aDatosPermanentesResponse(inspeccion.getDatoTecnico());
    return inspeccionMapper.toPublicadaDetalle(inspeccion, resultado, permanentes, correccionId);
  }

  @Transactional(readOnly = true)
  public InspeccionResumenResponse consultarPorId(UUID inspeccionId, UUID usuarioId) {
    Inspeccion inspeccion = obtenerInspeccionActiva(inspeccionId);
    boolean academica = RoleName.ESTUDIANTE.name().equals(inspeccion.getRolAutorCreacion());
    boolean autorizado =
        inspeccion.getAutorId().equals(usuarioId) || esCatedraticoAsignado(inspeccion, usuarioId);
    boolean borradorPrivadoNoAcademico =
        !academica
            && "BORRADOR".equalsIgnoreCase(inspeccion.getEstado())
            && !inspeccion.getAutorId().equals(usuarioId);
    boolean inspeccionAcademicaPrivada =
        academica && !"PUBLICADA".equalsIgnoreCase(inspeccion.getEstado()) && !autorizado;
    if (borradorPrivadoNoAcademico || inspeccionAcademicaPrivada) {
      throw new ApiException(
          HttpStatus.NOT_FOUND,
          "inspeccion_no_encontrada",
          "Inspección no encontrada",
          "No existe la inspección solicitada.");
    }
    InspeccionResumenResponse resumen = inspeccionMapper.toResumenResponse(inspeccion);
    // Una inspección publicada superada por una corrección enlaza hacia ella (RN-INS-08).
    return "PUBLICADA".equals(inspeccion.getEstado())
        ? inspeccionRepository
            .findCorreccionPublicadaId(inspeccion.getId())
            .map(resumen::conSuperadaPor)
            .orElse(resumen)
        : resumen;
  }

  private boolean esCatedraticoAsignado(Inspeccion inspeccion, UUID usuarioId) {
    return inspeccion.getAsignacionPuenteId() != null
        && asignaciones != null
        && asignaciones.perteneceACatedratico(
            inspeccion.getAsignacionPuenteId(), usuarioId);
  }

  @Transactional(readOnly = true)
  public void validarRespuestasInspeccion(
      UUID inspeccionId, JsonNode datos, Boolean esBorrador, UUID usuarioId) {
    Inspeccion inspeccion = obtenerInspeccionActiva(inspeccionId);
    if ("BORRADOR".equalsIgnoreCase(inspeccion.getEstado())
        && !inspeccion.getAutorId().equals(usuarioId)) {
      throw new ApiException(
          HttpStatus.NOT_FOUND,
          "inspeccion_no_encontrada",
          "Inspección no encontrada",
          "No existe la inspección solicitada.");
    }
    boolean modoBorrador = esBorrador == null || esBorrador;
    if (jsonSchemaValidatorService != null) {
      jsonSchemaValidatorService.validarRespuestas(
          inspeccion.getVersionFormulario().getEsquema(), datos, modoBorrador);
    }
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
    if (jsonSchemaValidatorService != null) {
      jsonSchemaValidatorService.validarRespuestas(version.getEsquema(), datos, modoBorrador);
    }
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
