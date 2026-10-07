package gt.usac.cunoc.sgp.usuario.entity;

import gt.usac.cunoc.sgp.usuario.model.EstadoInvitacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Invitación de un solo uso para crear cuentas que no pueden autorregistrarse (RN-USR-03). */
@Entity
@Table(name = "invitacion_usuario")
public class InvitacionUsuario {

  @Id private UUID id;

  @Column(nullable = false, length = 320, updatable = false)
  private String email;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rol_id", nullable = false, updatable = false)
  private Role rol;

  /** Solo se guarda el hash SHA-256 del token; el token en claro viaja únicamente en el correo. */
  @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
  private String tokenHash;

  @Column(name = "invitado_por_id", nullable = false, updatable = false)
  private UUID invitadoPorId;

  @Column(name = "expira_en", nullable = false, updatable = false)
  private Instant expiraEn;

  @Column(name = "aceptado_en")
  private Instant aceptadoEn;

  @Column(name = "cancelado_en")
  private Instant canceladoEn;

  @Column(name = "usuario_creado_id", updatable = false)
  private UUID usuarioCreadoId;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version
  @Column(nullable = false)
  private long version;

  protected InvitacionUsuario() {}

  public InvitacionUsuario(
      UUID id,
      String email,
      Role rol,
      String tokenHash,
      UUID invitadoPorId,
      UUID usuarioCreadoId,
      Instant creadoEn,
      Instant expiraEn) {
    this.id = id;
    this.email = email;
    this.rol = rol;
    this.tokenHash = tokenHash;
    this.invitadoPorId = invitadoPorId;
    this.usuarioCreadoId = usuarioCreadoId;
    this.creadoEn = creadoEn;
    this.actualizadoEn = creadoEn;
    this.expiraEn = expiraEn;
  }

  public EstadoInvitacion estado(Instant ahora) {
    if (aceptadoEn != null) return EstadoInvitacion.ACEPTADA;
    if (canceladoEn != null) return EstadoInvitacion.CANCELADA;
    return expiraEn.isAfter(ahora) ? EstadoInvitacion.PENDIENTE : EstadoInvitacion.VENCIDA;
  }

  public void aceptar(Instant ahora) {
    if (estado(ahora) != EstadoInvitacion.PENDIENTE) {
      throw new IllegalStateException("Solo una invitacion pendiente puede aceptarse");
    }
    this.aceptadoEn = ahora;
    this.actualizadoEn = ahora;
  }

  public void cancelar(Instant ahora) {
    if (aceptadoEn != null || canceladoEn != null) {
      throw new IllegalStateException("La invitacion ya fue aceptada o cancelada");
    }
    this.canceladoEn = ahora;
    this.actualizadoEn = ahora;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public Role getRol() {
    return rol;
  }

  public UUID getInvitadoPorId() {
    return invitadoPorId;
  }

  public Instant getExpiraEn() {
    return expiraEn;
  }

  public Instant getAceptadoEn() {
    return aceptadoEn;
  }

  public Instant getCanceladoEn() {
    return canceladoEn;
  }

  public UUID getUsuarioCreadoId() {
    return usuarioCreadoId;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }

  public long getVersion() {
    return version;
  }
}
