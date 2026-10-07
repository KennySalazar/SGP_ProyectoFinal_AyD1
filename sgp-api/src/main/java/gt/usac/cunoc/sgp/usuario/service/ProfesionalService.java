package gt.usac.cunoc.sgp.usuario.service;

import gt.usac.cunoc.sgp.common.audit.aspect.Auditable;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.usuario.dto.ProfesionalResponse;
import gt.usac.cunoc.sgp.usuario.entity.UsuarioProfesional;
import gt.usac.cunoc.sgp.usuario.mapper.InvitacionMapper;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import gt.usac.cunoc.sgp.usuario.repository.UsuarioProfesionalRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Colegiado del Profesional Externo y su verificación por el Administrador (RN-USR-04). */
@Service
public class ProfesionalService {

  private static final String ENTIDAD = "usuario_profesional";

  private final UsuarioProfesionalRepository profesionales;
  private final UserAccountRepository users;
  private final InvitacionMapper mapper;
  private final Clock clock;

  public ProfesionalService(
      UsuarioProfesionalRepository profesionales,
      UserAccountRepository users,
      InvitacionMapper mapper,
      Clock clock) {
    this.profesionales = profesionales;
    this.users = users;
    this.mapper = mapper;
    this.clock = clock;
  }

  /** Forma canónica para comparar colegiados sin depender de espacios ni mayúsculas. */
  public static String normalizarColegiado(String numeroColegiado) {
    return numeroColegiado == null ? null : numeroColegiado.strip().toUpperCase(Locale.ROOT);
  }

  /**
   * Valida y normaliza el colegiado según el rol: obligatorio para el Profesional Externo y no
   * aplicable a los demás roles. Devuelve nulo cuando el rol no lo usa.
   */
  public static String colegiadoParaRol(RoleName rol, String numeroColegiado) {
    String numero = normalizarColegiado(numeroColegiado);
    boolean informado = numero != null && !numero.isEmpty();
    boolean esProfesional = rol == RoleName.PROFESIONAL_EXTERNO;
    if (esProfesional && !informado) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "colegiado_requerido",
          "Colegiado requerido",
          "El numero de colegiado es obligatorio para el rol Profesional externo.");
    }
    if (!esProfesional && informado) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "colegiado_no_aplica",
          "Colegiado no aplica",
          "El numero de colegiado solo aplica al rol Profesional externo.");
    }
    return esProfesional ? numero : null;
  }

  public boolean colegiadoRegistrado(String numeroColegiado) {
    return profesionales.existsByNumeroColegiado(normalizarColegiado(numeroColegiado));
  }

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(
      accion = AccionAuditoria.CREAR,
      entidad = ENTIDAD,
      tipo = UsuarioProfesional.class,
      idArg = "usuarioId")
  public ProfesionalResponse registrar(UUID usuarioId, String numeroColegiado) {
    UsuarioProfesional profesional =
        new UsuarioProfesional(
            users.getReferenceById(usuarioId),
            normalizarColegiado(numeroColegiado),
            clock.instant());
    return mapper.toProfesionalResponse(profesionales.saveAndFlush(profesional));
  }

  /**
   * Asigna el colegiado a una cuenta que pasa a ser Profesional Externo. Si ya tuvo un registro se
   * reutiliza; un número distinto queda pendiente de verificación (RN-USR-04).
   */
  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(
      accion = AccionAuditoria.CREAR,
      entidad = ENTIDAD,
      tipo = UsuarioProfesional.class,
      idArg = "usuarioId")
  public void asignarColegiado(UUID usuarioId, String numeroColegiado) {
    String numero = normalizarColegiado(numeroColegiado);
    if (profesionales.existsByNumeroColegiadoAndUsuarioIdNot(numero, usuarioId)) {
      throw colegiadoDuplicado();
    }
    UsuarioProfesional profesional =
        profesionales
            .findById(usuarioId)
            .map(
                existente -> {
                  existente.reasignarColegiado(numero, clock.instant());
                  return existente;
                })
            .orElseGet(
                () ->
                    new UsuarioProfesional(
                        users.getReferenceById(usuarioId), numero, clock.instant()));
    profesionales.saveAndFlush(profesional);
  }

  public static ApiException colegiadoDuplicado() {
    return new ApiException(
        HttpStatus.CONFLICT,
        "colegiado_duplicado",
        "Colegiado ya registrado",
        "El numero de colegiado ya esta asociado a otra cuenta.");
  }

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(
      accion = AccionAuditoria.CAMBIAR_ESTADO,
      entidad = ENTIDAD,
      tipo = UsuarioProfesional.class,
      idArg = "usuarioId")
  public void verificarColegiado(UUID usuarioId, UUID administradorId) {
    UsuarioProfesional profesional =
        profesionales
            .findByUsuarioIdForUpdate(usuarioId)
            .orElseThrow(this::profesionalNoEncontrado);

    if (profesional.isColegiadoVerificado()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "colegiado_ya_verificado",
          "Colegiado ya verificado",
          "El numero de colegiado de esta cuenta ya fue verificado.");
    }

    profesional.verificarColegiado(administradorId, clock.instant());
    profesionales.saveAndFlush(profesional);
  }

  /** Datos actuales del profesional; las operaciones auditadas no devuelven datos. */
  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public ProfesionalResponse obtener(UUID usuarioId) {
    return profesionales
        .findById(usuarioId)
        .map(mapper::toProfesionalResponse)
        .orElseThrow(this::profesionalNoEncontrado);
  }

  private ApiException profesionalNoEncontrado() {
    return new ApiException(
        HttpStatus.NOT_FOUND,
        "profesional_no_encontrado",
        "Profesional no encontrado",
        "No existe un Profesional Externo con el identificador proporcionado.");
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public Page<ProfesionalResponse> listar(Boolean verificado, int pagina, int tamanio) {
    if (pagina < 0 || tamanio < 1 || tamanio > 100) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "paginacion_invalida",
          "Paginación inválida",
          "La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.");
    }
    var pageable =
        PageRequest.of(
            pagina, tamanio, Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("usuarioId")));
    return profesionales
        .findByVerificacion(verificado, pageable)
        .map(mapper::toProfesionalResponse);
  }

  /** Datos de colegiado de varios usuarios en una sola consulta, para los listados. */
  @Transactional(readOnly = true)
  public Map<UUID, UsuarioProfesional> profesionalesDe(Collection<UUID> usuarioIds) {
    return profesionales.findAllById(usuarioIds).stream()
        .collect(Collectors.toMap(UsuarioProfesional::getUsuarioId, Function.identity()));
  }

  /** Colegiados de varios usuarios en una sola consulta, para evitar N+1 en los listados. */
  @Transactional(readOnly = true)
  public Map<UUID, String> colegiadosDe(Collection<UUID> usuarioIds) {
    return profesionales.findAllById(usuarioIds).stream()
        .collect(
            Collectors.toMap(
                UsuarioProfesional::getUsuarioId, UsuarioProfesional::getNumeroColegiado));
  }

  /**
   * Regla de autorización para las acciones de inspección (RN-USR-04). Solo restringe al
   * Profesional Externo sin colegiado verificado; los demás requisitos de cada rol se validan en su
   * endpoint. Uso: {@code @PreAuthorize("...
   * and @profesionalService.colegiadoHabilitado(authentication)")}.
   */
  @Transactional(readOnly = true)
  public boolean colegiadoHabilitado(Authentication authentication) {
    if (authentication == null || !(authentication.getDetails() instanceof JwtData jwt)) {
      return false;
    }
    if (jwt.role() != RoleName.PROFESIONAL_EXTERNO) {
      return true;
    }
    return profesionales
        .findById(jwt.userId())
        .map(UsuarioProfesional::isColegiadoVerificado)
        .orElse(false);
  }
}
