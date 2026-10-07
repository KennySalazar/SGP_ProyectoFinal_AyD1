package gt.usac.cunoc.sgp.usuario.service;

import gt.usac.cunoc.sgp.common.audit.aspect.Auditable;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.config.InvitacionProperties;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.EmailNormalizer;
import gt.usac.cunoc.sgp.common.util.TokenHasher;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.usuario.dto.AceptarInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.dto.CrearInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.dto.InvitacionPublicaResponse;
import gt.usac.cunoc.sgp.usuario.dto.InvitacionResponse;
import gt.usac.cunoc.sgp.usuario.dto.MessageResponse;
import gt.usac.cunoc.sgp.usuario.dto.TokenInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.entity.InvitacionUsuario;
import gt.usac.cunoc.sgp.usuario.entity.Role;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.mapper.InvitacionMapper;
import gt.usac.cunoc.sgp.usuario.model.EstadoInvitacion;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.InvitacionUsuarioRepository;
import gt.usac.cunoc.sgp.usuario.repository.RoleRepository;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Invitaciones del Administrador para roles que no pueden autorregistrarse (RN-USR-03). */
@Service
public class InvitacionService {

  private static final String ENTIDAD = "invitacion_usuario";

  /** Roles habilitados para invitación; HU-004 amplía esta lista. */
  private static final Set<RoleName> ROLES_INVITABLES =
      EnumSet.of(RoleName.CATEDRATICO, RoleName.PROFESIONAL_EXTERNO);

  private final InvitacionUsuarioRepository invitaciones;
  private final UserAccountRepository users;
  private final RoleRepository roles;
  private final CuentaInvitadaService cuentas;
  private final ProfesionalService profesionales;
  private final InvitacionEmailService invitacionEmailService;
  private final InvitacionMapper mapper;
  private final InvitacionProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public InvitacionService(
      InvitacionUsuarioRepository invitaciones,
      UserAccountRepository users,
      RoleRepository roles,
      CuentaInvitadaService cuentas,
      ProfesionalService profesionales,
      InvitacionEmailService invitacionEmailService,
      InvitacionMapper mapper,
      InvitacionProperties properties,
      Clock clock) {
    this.invitaciones = invitaciones;
    this.users = users;
    this.roles = roles;
    this.cuentas = cuentas;
    this.profesionales = profesionales;
    this.invitacionEmailService = invitacionEmailService;
    this.mapper = mapper;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(accion = AccionAuditoria.CREAR, entidad = ENTIDAD)
  public InvitacionResponse invitar(CrearInvitacionRequest request, UUID administradorId) {
    if (request.rol() == null || !ROLES_INVITABLES.contains(request.rol())) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "rol_no_invitable",
          "Rol no invitable",
          "Solo se pueden enviar invitaciones para los roles Catedratico y Profesional externo.");
    }
    boolean esProfesional = request.rol() == RoleName.PROFESIONAL_EXTERNO;
    String numeroColegiado = validarColegiado(request, esProfesional);

    String email = EmailNormalizer.normalize(request.email());
    UserAccount existente = users.findByEmail(email).orElse(null);
    if (existente != null) {
      throw correoRegistrado(existente);
    }
    if (esProfesional && profesionales.colegiadoRegistrado(numeroColegiado)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "colegiado_duplicado",
          "Colegiado ya registrado",
          "El numero de colegiado ya esta asociado a otra cuenta.");
    }

    Role rol =
        roles
            .findByName(request.rol())
            .orElseThrow(() -> new IllegalStateException("Falta el rol " + request.rol()));

    UserAccount cuenta = cuentas.crearPendiente(email, rol);
    if (esProfesional) {
      // La cuenta queda pendiente de verificación de colegiado hasta que el Administrador la
      // apruebe.
      profesionales.registrar(cuenta.getId(), numeroColegiado);
    }
    return emitir(email, rol, cuenta.getId(), administradorId, numeroColegiado);
  }

  /**
   * Reemplaza una invitación pendiente o vencida por una nueva con otro enlace y nueva vigencia. El
   * enlace anterior queda cancelado.
   */
  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(accion = AccionAuditoria.CREAR, entidad = ENTIDAD)
  public InvitacionResponse reenviar(UUID id, UUID administradorId) {
    InvitacionUsuario anterior =
        invitaciones.findByIdForUpdate(id).orElseThrow(this::invitacionNoEncontrada);

    Instant ahora = clock.instant();
    EstadoInvitacion estado = anterior.estado(ahora);
    if (estado == EstadoInvitacion.ACEPTADA || estado == EstadoInvitacion.CANCELADA) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "invitacion_no_reenviable",
          "Invitacion no reenviable",
          estado == EstadoInvitacion.ACEPTADA
              ? "La invitacion ya fue aceptada."
              : "La invitacion fue reemplazada por otra mas reciente.");
    }

    anterior.cancelar(ahora);
    // Se libera la restricción de una invitación pendiente por correo y rol antes de crear la
    // nueva.
    invitaciones.saveAndFlush(anterior);

    UUID usuarioId = anterior.getUsuarioCreadoId();
    return emitir(
        anterior.getEmail(),
        anterior.getRol(),
        usuarioId,
        administradorId,
        usuarioId == null ? null : profesionales.colegiadosDe(List.of(usuarioId)).get(usuarioId));
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public Page<InvitacionResponse> listar(EstadoInvitacion estado, int pagina, int tamanio) {
    if (pagina < 0 || tamanio < 1 || tamanio > 100) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "paginacion_invalida",
          "Paginación inválida",
          "La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.");
    }

    Instant ahora = clock.instant();
    var pageable =
        PageRequest.of(
            pagina, tamanio, Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id")));

    Page<InvitacionUsuario> resultado =
        invitaciones.findByEstado(estado == null ? null : estado.name(), ahora, pageable);
    Map<UUID, String> colegiados =
        profesionales.colegiadosDe(
            resultado.getContent().stream()
                .map(InvitacionUsuario::getUsuarioCreadoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

    return resultado.map(
        invitacion ->
            mapper.toResponse(
                invitacion,
                invitacion.estado(ahora),
                colegiados.get(invitacion.getUsuarioCreadoId())));
  }

  /** Permite al invitado comprobar su enlace antes de definir la contraseña. */
  @Transactional(readOnly = true)
  public InvitacionPublicaResponse validar(TokenInvitacionRequest request) {
    InvitacionUsuario invitacion =
        invitaciones
            .findByTokenHash(TokenHasher.sha256(request.token()))
            .orElseThrow(this::invitacionInvalida);

    exigirPendiente(invitacion, clock.instant());
    return mapper.toPublicaResponse(invitacion);
  }

  @Transactional
  public MessageResponse aceptar(AceptarInvitacionRequest request) {
    if (!PasswordPolicy.isValid(request.password())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "contrasena_invalida",
          "Contraseña invalida",
          "La contraseña debe tener entre 10 y 72 caracteres e incluir al menos una letra y un"
              + " digito");
    }

    InvitacionUsuario invitacion =
        invitaciones
            .findByTokenHashForUpdate(TokenHasher.sha256(request.token()))
            .orElseThrow(this::invitacionInvalida);

    Instant ahora = clock.instant();
    exigirPendiente(invitacion, ahora);

    invitacion.aceptar(ahora);
    cuentas.activar(invitacion.getUsuarioCreadoId(), request.password());

    return new MessageResponse("La cuenta fue activada. Ya puede iniciar sesion.");
  }

  private InvitacionResponse emitir(
      String email, Role rol, UUID usuarioId, UUID administradorId, String numeroColegiado) {
    Instant ahora = clock.instant();
    String token = generarToken();

    InvitacionUsuario guardada =
        invitaciones.saveAndFlush(
            new InvitacionUsuario(
                UuidV7Generator.generate(),
                email,
                rol,
                TokenHasher.sha256(token),
                administradorId,
                usuarioId,
                ahora,
                ahora.plus(Duration.ofHours(properties.getVigenciaHoras()))));

    // Si el correo falla, la transacción se revierte y no queda una cuenta sin invitación.
    invitacionEmailService.enviarInvitacion(email, rol.getName(), token, guardada.getExpiraEn());

    return mapper.toResponse(guardada, guardada.estado(ahora), numeroColegiado);
  }

  /** El colegiado es obligatorio para el Profesional Externo y no aplica a los demás roles. */
  private String validarColegiado(CrearInvitacionRequest request, boolean esProfesional) {
    String numeroColegiado = ProfesionalService.normalizarColegiado(request.numeroColegiado());
    boolean informado = numeroColegiado != null && !numeroColegiado.isEmpty();
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
    return esProfesional ? numeroColegiado : null;
  }

  private void exigirPendiente(InvitacionUsuario invitacion, Instant ahora) {
    EstadoInvitacion estado = invitacion.estado(ahora);
    if (estado == EstadoInvitacion.VENCIDA) {
      throw new ApiException(
          HttpStatus.GONE,
          "invitacion_vencida",
          "Invitacion vencida",
          "El enlace de invitacion vencio. Solicite al Administrador que reenvie la invitacion.");
    }
    if (estado != EstadoInvitacion.PENDIENTE) {
      throw invitacionInvalida();
    }
  }

  private ApiException correoRegistrado(UserAccount existente) {
    if (invitaciones.existsByUsuarioCreadoIdAndAceptadoEnIsNullAndCanceladoEnIsNull(
        existente.getId())) {
      return new ApiException(
          HttpStatus.CONFLICT,
          "invitacion_pendiente",
          "Invitacion pendiente",
          "El correo ya tiene una invitacion sin aceptar. Utilice el reenvio de la invitacion.");
    }
    return new ApiException(
        HttpStatus.CONFLICT,
        "email_already_registered",
        "Correo ya registrado",
        "El correo electronico ya esta registrado");
  }

  private ApiException invitacionInvalida() {
    return new ApiException(
        HttpStatus.BAD_REQUEST,
        "invitacion_invalida",
        "Invitacion invalida",
        "El enlace de invitacion no es valido o ya fue utilizado.");
  }

  private ApiException invitacionNoEncontrada() {
    return new ApiException(
        HttpStatus.NOT_FOUND,
        "invitacion_no_encontrada",
        "Invitacion no encontrada",
        "No existe una invitacion con el identificador proporcionado.");
  }

  private String generarToken() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
