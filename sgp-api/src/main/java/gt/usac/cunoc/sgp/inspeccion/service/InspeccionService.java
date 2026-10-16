package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.inspeccion.dto.AcompananteRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.AcompananteResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.DatosPermanentesResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.entity.InspeccionAcompanante;
import gt.usac.cunoc.sgp.inspeccion.entity.InspeccionDatoTecnico;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionDatoTecnicoRepository;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.sync.entity.OperacionIdempotente;
import gt.usac.cunoc.sgp.sync.repository.OperacionIdempotenteRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspeccionService {

  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(), 4326);
  private static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");

  private final InspeccionRepository inspeccionRepository;
  private final InspeccionDatoTecnicoRepository datoTecnicoRepository;
  private final PuenteRepository puenteRepository;
  private final VersionFormularioRepository versionFormularioRepository;
  private final UserAccountRepository userAccountRepository;
  private final UsuarioProfesionalRepository profesionalRepository;
  private final OperacionIdempotenteRepository operacionIdempotenteRepository;
  private final AuditoriaWriter auditoriaWriter;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public InspeccionService(
      InspeccionRepository inspeccionRepository,
      InspeccionDatoTecnicoRepository datoTecnicoRepository,
      PuenteRepository puenteRepository,
      VersionFormularioRepository versionFormularioRepository,
      UserAccountRepository userAccountRepository,
      UsuarioProfesionalRepository profesionalRepository,
      OperacionIdempotenteRepository operacionIdempotenteRepository,
      AuditoriaWriter auditoriaWriter,
      ObjectMapper objectMapper,
      Clock clock) {
    this.inspeccionRepository = inspeccionRepository;
    this.datoTecnicoRepository = datoTecnicoRepository;
    this.puenteRepository = puenteRepository;
    this.versionFormularioRepository = versionFormularioRepository;
    this.userAccountRepository = userAccountRepository;
    this.profesionalRepository = profesionalRepository;
    this.operacionIdempotenteRepository = operacionIdempotenteRepository;
    this.auditoriaWriter = auditoriaWriter;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional
  public IniciarInspeccionResponse iniciarInspeccion(
      IniciarInspeccionRequest request, UUID usuarioId, String idempotencyKey) {
    // 1. Autorización de rol y verificación
    UserAccount user = validarInspector(usuarioId);

    // 2. Idempotencia
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      Optional<OperacionIdempotente> opExistente =
          operacionIdempotenteRepository.findByUsuarioIdAndClave(usuarioId, idempotencyKey);
      if (opExistente.isPresent()
          && "COMPLETADA".equals(opExistente.get().getEstado())
          && opExistente.get().getEntidadId() != null) {
        return inspeccionRepository
            .findConDetallesById(opExistente.get().getEntidadId())
            .map(this::aResponse)
            .orElseGet(
                () ->
                    crearNuevaInspeccion(
                        request, user, idempotencyKey));
      }
    }

    if (request.id() != null) {
      Optional<Inspeccion> existente = inspeccionRepository.findConDetallesById(request.id());
      if (existente.isPresent() && existente.get().getAutorId().equals(usuarioId)) {
        return aResponse(existente.get());
      }
    }

    return crearNuevaInspeccion(request, user, idempotencyKey);
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

  private IniciarInspeccionResponse crearNuevaInspeccion(
      IniciarInspeccionRequest request, UserAccount user, String idempotencyKey) {
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

    // 5. Validar fechas de inspección
    LocalDate hoyGuatemala = LocalDate.now(clock.withZone(ZONA_GUATEMALA));
    if (request.fechaInspeccion().isAfter(hoyGuatemala)) {
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
          && request.fechaInspeccion().getYear() < tecnicoVigente.getAnioConstruccion()) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "fecha_anterior_construccion",
            "Fecha de inspección inválida",
            "La fecha de inspección ("
                + request.fechaInspeccion()
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
    String nombreAutor =
        (request.nombreAutor() != null && !request.nombreAutor().isBlank())
            ? request.nombreAutor().trim()
            : user.getEmail();

    Inspeccion inspeccion =
        new Inspeccion(
            inspeccionId,
            puente,
            versionActiva,
            user.getId(),
            user.getRole().getName().name(),
            nombreAutor,
            request.fechaInspeccion(),
            request.dispositivoId(),
            ubicacionInicio,
            precisionGps,
            distanciaAlPuente,
            datosPermanentesConfirmadosEn,
            objectMapper.createObjectNode(),
            ahora);

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
    snapshot.put("nombreAutorCapturado", nombreAutor);
    snapshot.put("estado", "BORRADOR");
    snapshot.put("fechaInspeccion", request.fechaInspeccion().toString());
    snapshot.put("dispositivoId", request.dispositivoId());
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
        "HU-024");

    // 10. Registro de idempotencia si aplica
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      String hashSolicitud = calcularHash(request.toString());
      OperacionIdempotente op =
          new OperacionIdempotente(
              UuidV7Generator.generate(),
              user.getId(),
              idempotencyKey,
              "POST",
              "/api/v1/inspecciones",
              hashSolicitud,
              "COMPLETADA",
              201,
              snapshot,
              "inspeccion",
              inspeccion.getId(),
              ahora,
              ahora.plus(Duration.ofDays(7)),
              ahora);
      operacionIdempotenteRepository.save(op);
    }

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
    } else if (role != RoleName.CATEDRATICO) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "rol_no_autorizado",
          "Acceso denegado",
          "Solo Catedráticos y Profesionales externos verificados pueden iniciar inspecciones.");
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
        d.getAnioConstruccion(),
        d.getRutaPavimentada(),
        d.getAlineamientoHorizontal(),
        d.getEsviaje(),
        d.getTipoCuerpoAgua(),
        d.getNombreRio(),
        d.getEncauzamiento(),
        d.getAlumbradoExiste(),
        d.getDrenajesAledanosExisten());
  }

  public static double calcularDistanciaMetros(
      double lat1, double lon1, double lat2, double lon2) {
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
}
