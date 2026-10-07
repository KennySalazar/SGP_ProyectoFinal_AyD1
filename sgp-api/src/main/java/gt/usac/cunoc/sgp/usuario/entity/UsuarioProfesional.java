package gt.usac.cunoc.sgp.usuario.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Datos exclusivos del Profesional Externo: su colegiado y la verificación del Administrador. */
@Entity
@Table(name = "usuario_profesional")
public class UsuarioProfesional {

  @Id
  @Column(name = "usuario_id")
  private UUID usuarioId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "usuario_id")
  private UserAccount usuario;

  @Column(name = "numero_colegiado", nullable = false, unique = true, length = 50)
  private String numeroColegiado;

  @Column(name = "colegiado_verificado_en")
  private Instant colegiadoVerificadoEn;

  @Column(name = "colegiado_verificado_por_id")
  private UUID colegiadoVerificadoPorId;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version
  @Column(nullable = false)
  private long version;

  protected UsuarioProfesional() {}

  public UsuarioProfesional(UserAccount usuario, String numeroColegiado, Instant creadoEn) {
    this.usuario = usuario;
    this.numeroColegiado = numeroColegiado;
    this.creadoEn = creadoEn;
    this.actualizadoEn = creadoEn;
  }

  public boolean isColegiadoVerificado() {
    return colegiadoVerificadoEn != null;
  }

  public void verificarColegiado(UUID administradorId, Instant ahora) {
    if (isColegiadoVerificado()) {
      throw new IllegalStateException("El colegiado ya fue verificado");
    }
    this.colegiadoVerificadoEn = ahora;
    this.colegiadoVerificadoPorId = administradorId;
    this.actualizadoEn = ahora;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public UserAccount getUsuario() {
    return usuario;
  }

  public String getNumeroColegiado() {
    return numeroColegiado;
  }

  public Instant getColegiadoVerificadoEn() {
    return colegiadoVerificadoEn;
  }

  public UUID getColegiadoVerificadoPorId() {
    return colegiadoVerificadoPorId;
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
