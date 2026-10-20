package gt.usac.cunoc.sgp.sync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.inspeccion.dto.DatosPermanentesResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.entity.InspeccionDatoTecnico;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapper;
import gt.usac.cunoc.sgp.puente.repository.AsignacionPuenteRepository;
import gt.usac.cunoc.sgp.sync.dto.*;
import gt.usac.cunoc.sgp.sync.dto.PaqueteSalidaResponse.*;
import gt.usac.cunoc.sgp.sync.repository.ConsultaSalidaRepository;
import gt.usac.cunoc.sgp.sync.repository.PreparacionSalidaRepository;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import gt.usac.cunoc.sgp.usuario.repository.UsuarioProfesionalRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreparacionSalidaService {
  private final UserAccountRepository usuarios;
  private final UsuarioProfesionalRepository profesionales;
  private final AsignacionPuenteRepository asignaciones;
  private final VersionFormularioRepository formularios;
  private final ConsultaSalidaRepository consulta;
  private final PreparacionSalidaRepository preparaciones;
  private final PuenteMapper puentes;
  private final ObjectMapper json;
  private final AuditoriaWriter auditoria;
  private final Clock clock;

  public PreparacionSalidaService(
      UserAccountRepository usuarios,
      UsuarioProfesionalRepository profesionales,
      AsignacionPuenteRepository asignaciones,
      VersionFormularioRepository formularios,
      ConsultaSalidaRepository consulta,
      PreparacionSalidaRepository preparaciones,
      PuenteMapper puentes,
      ObjectMapper json,
      AuditoriaWriter auditoria,
      Clock clock) {
    this.usuarios = usuarios;
    this.profesionales = profesionales;
    this.asignaciones = asignaciones;
    this.formularios = formularios;
    this.consulta = consulta;
    this.preparaciones = preparaciones;
    this.puentes = puentes;
    this.json = json;
    this.auditoria = auditoria;
    this.clock = clock;
  }

  @Transactional
  public PreparacionSalidaResponse preparar(UUID usuarioId, PrepararSalidaRequest request) {
    UserAccount usuario = autorizado(usuarioId);
    PaqueteSalidaResponse paquete = paquete(usuario, request);
    long bytes = tamano(paquete);
    UUID id = UuidV7Generator.generate();
    var ahora = clock.instant();
    var contexto = json.createObjectNode();
    contexto.put("rol", usuario.getRole().getName().name());
    contexto.set(
        "puentesIds", json.valueToTree(paquete.puentes().stream().map(PuenteSalida::id).toList()));
    contexto.set("asignaciones", json.valueToTree(paquete.asignaciones()));
    contexto.put("versionFormularioId", paquete.formulario().id().toString());
    contexto.put("hashFormulario", paquete.formulario().hashContenido());
    preparaciones.registrar(
        id, usuarioId, request.dispositivoId(), paquete.formulario().id(), contexto, ahora);
    auditoria.registrarEnTransaccion(
        usuarioId,
        AccionAuditoria.CREAR,
        "preparacion_salida",
        id,
        null,
        json.createObjectNode()
            .put("dispositivoId", request.dispositivoId())
            .put("versionFormularioId", paquete.formulario().id().toString())
            .put("numeroPuentes", paquete.puentes().size())
            .put("preparadoEn", ahora.toString()),
        "HU-041");
    return new PreparacionSalidaResponse(
        id, usuarioId, request.dispositivoId(), ahora, paquete.puentes().size(), bytes, paquete);
  }

  @Transactional(readOnly = true)
  public EstimacionSalidaResponse estimar(UUID usuarioId, PrepararSalidaRequest request) {
    var paquete = paquete(autorizado(usuarioId), request);
    return new EstimacionSalidaResponse(
        paquete.formulario().id(),
        paquete.formulario().codigo(),
        paquete.puentes().size(),
        paquete.inspecciones().size(),
        tamano(paquete));
  }

  @Transactional(readOnly = true)
  public Optional<UltimaPreparacionResponse> ultima(UUID usuarioId, String dispositivo) {
    autorizado(usuarioId);
    return preparaciones.ultima(usuarioId, dispositivo);
  }

  @Transactional(readOnly = true)
  public Page<PuenteSalida> disponibles(
      UUID usuarioId, UUID departamento, String busqueda, int pagina, int tamanio) {
    var usuario = autorizado(usuarioId);
    if (pagina < 0 || tamanio < 1 || tamanio > 100) {
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "paginacion_invalida",
          "Pagina o tamanio invalido (1 a 100).");
    }
    boolean estudiante = usuario.getRole().getName() == RoleName.ESTUDIANTE;
    var ids = vigentes(usuario).stream().map(AsignacionSalida::puenteId).distinct().toList();
    var paginacion =
        PageRequest.of(pagina, tamanio, Sort.by("codigo").ascending().and(Sort.by("id")));
    if (estudiante && ids.isEmpty()) return Page.empty(paginacion);
    return consulta
        .disponibles(
            estudiante,
            estudiante ? ids : List.of(new UUID(0, 0)),
            departamento,
            busqueda == null ? "" : busqueda.trim(),
            paginacion)
        .map(this::puente);
  }

  private PaqueteSalidaResponse paquete(UserAccount usuario, PrepararSalidaRequest request) {
    var vigentes = vigentes(usuario);
    Set<UUID> permitidos = new HashSet<>();
    vigentes.forEach(a -> permitidos.add(a.puenteId()));
    var solicitados = request.puentesIds() == null ? List.<UUID>of() : request.puentesIds();
    if (solicitados.stream().anyMatch(Objects::isNull)) {
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "seleccion_invalida",
          "La seleccion contiene identificadores nulos.");
    }
    Set<UUID> ids = new LinkedHashSet<>(solicitados);
    boolean estudiante = usuario.getRole().getName() == RoleName.ESTUDIANTE;
    if (estudiante) {
      if (ids.isEmpty()) ids.addAll(permitidos);
      if (!permitidos.containsAll(ids)) {
        throw error(
            HttpStatus.FORBIDDEN,
            "puente_no_autorizado",
            "La seleccion contiene puentes sin asignacion vigente.");
      }
    } else if (ids.isEmpty() || ids.size() > 200) {
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "seleccion_invalida",
          "Selecciona entre 1 y 200 puentes activos por salida.");
    }
    var seleccion = ids.isEmpty() ? List.<Puente>of() : consulta.seleccionados(ids);
    if (seleccion.size() != ids.size()) {
      throw error(
          HttpStatus.CONFLICT,
          "puente_no_disponible",
          "Algun puente seleccionado ya no esta activo o disponible. Actualiza la seleccion.");
    }
    var formulario =
        formularios
            .findByActivaTrue()
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.CONFLICT,
                        "sin_formulario_activo",
                        "No existe una version activa del formulario."));
    var anteriores = ids.isEmpty() ? List.<Inspeccion>of() : consulta.ultimasPublicadas(ids);
    return new PaqueteSalidaResponse(
        new FormularioSalida(
            formulario.getId(),
            formulario.getCodigo(),
            formulario.getHashContenido(),
            formulario.getEsquema()),
        seleccion.stream().map(this::puente).toList(),
        anteriores.stream().map(this::inspeccion).toList(),
        vigentes.stream().filter(a -> ids.contains(a.puenteId())).toList());
  }

  private List<AsignacionSalida> vigentes(UserAccount usuario) {
    if (usuario.getRole().getName() != RoleName.ESTUDIANTE) return List.of();
    return asignaciones
        .findVigentesByEstudianteId(
            usuario.getId(), LocalDate.now(clock.withZone(ZoneId.of("America/Guatemala"))))
        .stream()
        .map(a -> new AsignacionSalida(a.getId(), a.getPuenteId(), a.getCursoEstudianteId()))
        .toList();
  }

  private UserAccount autorizado(UUID id) {
    if (id == null)
      throw error(
          HttpStatus.UNAUTHORIZED, "autenticacion_requerida", "Se requiere una sesion valida.");
    var usuario =
        usuarios
            .findWithRoleById(id)
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.UNAUTHORIZED,
                        "autenticacion_requerida",
                        "Se requiere una sesion valida."));
    var rol = usuario.getRole().getName();
    if (!usuario.isActive()
        || !usuario.isVerified()
        || !usuario.isActivated()
        || (rol != RoleName.ESTUDIANTE
            && rol != RoleName.CATEDRATICO
            && rol != RoleName.PROFESIONAL_EXTERNO)) {
      throw error(
          HttpStatus.FORBIDDEN,
          "salida_no_autorizada",
          "La cuenta no esta autorizada para preparar una salida.");
    }
    if (rol == RoleName.PROFESIONAL_EXTERNO
        && !profesionales.findById(id).map(p -> p.isColegiadoVerificado()).orElse(false)) {
      throw error(
          HttpStatus.FORBIDDEN,
          "profesional_no_verificado",
          "El colegiado debe estar verificado para preparar una salida.");
    }
    return usuario;
  }

  private PuenteSalida puente(Puente p) {
    var catalogo = puentes.toCatalogoResponse(p);
    return new PuenteSalida(
        p.getId(),
        p.getCodigo(),
        p.getNombre(),
        p.getRuta(),
        p.getKilometraje(),
        catalogo.departamento(),
        catalogo.municipio(),
        catalogo.latitud(),
        catalogo.longitud(),
        p.isActivo(),
        p.getEstadoActual(),
        p.getIndiceCondicionActual(),
        p.getFechaUltimaInspeccion(),
        p.getVersion());
  }

  private InspeccionSalida inspeccion(Inspeccion i) {
    return new InspeccionSalida(
        i.getId(),
        i.getPuente().getId(),
        i.getVersionFormulario().getId(),
        i.getVersionFormulario().getCodigo(),
        i.getFechaInspeccion(),
        i.getPublicadoEn(),
        i.getIdentificacionCapturada(),
        i.getDatos(),
        permanentes(i.getDatoTecnico()));
  }

  private DatosPermanentesResponse permanentes(InspeccionDatoTecnico d) {
    if (d == null) return null;
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
        d.getAnioConstruccion() == null ? null : d.getAnioConstruccion().intValue(),
        d.getRutaPavimentada(),
        d.getAlineamientoHorizontal(),
        d.getEsviaje(),
        d.getTipoCuerpoAgua(),
        d.getNombreRio(),
        d.getEncauzamiento(),
        d.getAlumbradoExiste(),
        d.getDrenajesAledanosExisten());
  }

  private long tamano(PaqueteSalidaResponse paquete) {
    try {
      return json.writeValueAsBytes(paquete).length;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("No se pudo serializar la salida", e);
    }
  }

  private ApiException error(HttpStatus estado, String codigo, String detalle) {
    return new ApiException(estado, codigo, "Preparacion de salida", detalle);
  }
}
