package gt.usac.cunoc.sgp.usuario.service;

import gt.usac.cunoc.sgp.common.audit.aspect.Auditable;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.usuario.dto.CambiarRolRequest;
import gt.usac.cunoc.sgp.usuario.dto.DesactivarUsuarioRequest;
import gt.usac.cunoc.sgp.usuario.dto.UsuarioResponse;
import gt.usac.cunoc.sgp.usuario.entity.Role;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.entity.UsuarioProfesional;
import gt.usac.cunoc.sgp.usuario.mapper.UsuarioMapper;
import gt.usac.cunoc.sgp.usuario.model.EstadoUsuario;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.RoleRepository;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestión de cuentas por el Administrador: listado, baja lógica, reactivación y cambio de rol. */
@Service
public class UsuarioService {

  private static final String ENTIDAD = "usuario";

  private final UserAccountRepository users;
  private final RoleRepository roles;
  private final ProfesionalService profesionales;
  private final RefreshTokenService refreshTokens;
  private final UsuarioMapper mapper;
  private final Clock clock;

  public UsuarioService(
      UserAccountRepository users,
      RoleRepository roles,
      ProfesionalService profesionales,
      RefreshTokenService refreshTokens,
      UsuarioMapper mapper,
      Clock clock) {
    this.users = users;
    this.roles = roles;
    this.profesionales = profesionales;
    this.refreshTokens = refreshTokens;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public Page<UsuarioResponse> listar(RoleName rol, EstadoUsuario estado, int pagina, int tamanio) {
    if (pagina < 0 || tamanio < 1 || tamanio > 100) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "paginacion_invalida",
          "Paginación inválida",
          "La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.");
    }
    var pageable =
        PageRequest.of(pagina, tamanio, Sort.by(Sort.Order.asc("email"), Sort.Order.asc("id")));
    Page<UserAccount> resultado =
        users.findByRolYEstado(rol, estado == null ? null : estado.name(), pageable);
    Map<UUID, UsuarioProfesional> colegiados =
        profesionales.profesionalesDe(
            resultado.getContent().stream()
                .filter(usuario -> usuario.getRole().getName() == RoleName.PROFESIONAL_EXTERNO)
                .map(UserAccount::getId)
                .toList());

    return resultado.map(usuario -> respuesta(usuario, colegiados.get(usuario.getId())));
  }

  /**
   * Baja lógica (RN-USR-08): la cuenta nunca se elimina, así sus inspecciones y comentarios siguen
   * atribuidos. Se cierran sus sesiones abiertas.
   */
  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(
      accion = AccionAuditoria.CAMBIAR_ESTADO,
      entidad = ENTIDAD,
      tipo = UserAccount.class,
      idArg = "usuarioId")
  public void desactivar(UUID usuarioId, DesactivarUsuarioRequest request, UUID administradorId) {
    if (usuarioId.equals(administradorId)) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "autodesactivacion_no_permitida",
          "Operacion no permitida",
          "Un administrador no puede desactivar su propia cuenta.");
    }
    UserAccount usuario = usuarioParaModificar(usuarioId);
    if (!usuario.isActive()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "usuario_ya_inactivo",
          "Usuario ya inactivo",
          "La cuenta ya se encuentra desactivada.");
    }

    String motivo =
        request == null || request.motivo() == null || request.motivo().isBlank()
            ? null
            : request.motivo().strip();
    usuario.desactivar(administradorId, motivo, clock.instant());
    refreshTokens.revokeAll(usuario);
    users.saveAndFlush(usuario);
  }

  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(
      accion = AccionAuditoria.CAMBIAR_ESTADO,
      entidad = ENTIDAD,
      tipo = UserAccount.class,
      idArg = "usuarioId")
  public void reactivar(UUID usuarioId) {
    UserAccount usuario = usuarioParaModificar(usuarioId);
    if (usuario.isActive()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "usuario_ya_activo",
          "Usuario ya activo",
          "La cuenta no se encuentra desactivada.");
    }
    usuario.reactivar();
    users.saveAndFlush(usuario);
  }

  /**
   * Cambia el rol global (RN-USR-05). Pasar a Profesional Externo exige el número de colegiado, que
   * queda pendiente de verificación (RN-USR-04). Se cierran las sesiones para que el usuario vuelva
   * a ingresar con los permisos del nuevo rol.
   */
  @Transactional
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Auditable(
      accion = AccionAuditoria.MODIFICAR,
      entidad = ENTIDAD,
      tipo = UserAccount.class,
      idArg = "usuarioId")
  public void cambiarRol(UUID usuarioId, CambiarRolRequest request, UUID administradorId) {
    if (usuarioId.equals(administradorId)) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "cambio_rol_propio_no_permitido",
          "Operacion no permitida",
          "Un administrador no puede cambiar su propio rol.");
    }
    String numeroColegiado =
        ProfesionalService.colegiadoParaRol(request.rol(), request.numeroColegiado());

    UserAccount usuario = usuarioParaModificar(usuarioId);
    if (usuario.getRole().getName() == request.rol()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "rol_sin_cambios",
          "Rol sin cambios",
          "El usuario ya tiene el rol indicado.");
    }

    Role rol =
        roles
            .findByName(request.rol())
            .orElseThrow(() -> new IllegalStateException("Falta el rol " + request.rol()));
    usuario.setRole(rol);
    usuario.incrementTokenVersion();
    refreshTokens.revokeAll(usuario);
    users.saveAndFlush(usuario);

    if (numeroColegiado != null) {
      profesionales.asignarColegiado(usuarioId, numeroColegiado);
    }
  }

  /**
   * Estado actual de una cuenta. Las operaciones auditadas no devuelven datos para que la bitácora
   * compare el mismo formato antes y después; el controlador consulta aquí la respuesta.
   */
  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  public UsuarioResponse obtener(UUID usuarioId) {
    return respuesta(
        users
            .findWithRoleById(usuarioId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "usuario_no_encontrado",
                        "Usuario no encontrado",
                        "No existe un usuario con el identificador proporcionado.")));
  }

  private UserAccount usuarioParaModificar(UUID usuarioId) {
    return users
        .findByIdForUpdate(usuarioId)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "usuario_no_encontrado",
                    "Usuario no encontrado",
                    "No existe un usuario con el identificador proporcionado."));
  }

  private UsuarioResponse respuesta(UserAccount usuario) {
    UsuarioProfesional profesional =
        usuario.getRole().getName() == RoleName.PROFESIONAL_EXTERNO
            ? profesionales.profesionalesDe(List.of(usuario.getId())).get(usuario.getId())
            : null;
    return respuesta(usuario, profesional);
  }

  private UsuarioResponse respuesta(UserAccount usuario, UsuarioProfesional profesional) {
    return mapper.toResponse(usuario, estadoDe(usuario), profesional);
  }

  static EstadoUsuario estadoDe(UserAccount usuario) {
    if (!usuario.isActive()) return EstadoUsuario.INACTIVO;
    return usuario.isActivated() && usuario.isVerified()
        ? EstadoUsuario.ACTIVO
        : EstadoUsuario.PENDIENTE;
  }
}
