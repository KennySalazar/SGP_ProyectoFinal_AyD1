package gt.usac.cunoc.sgp.inspeccion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspeccion_acompanante")
public class InspeccionAcompanante {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inspeccion_id", nullable = false)
  private Inspeccion inspeccion;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(nullable = false, length = 200)
  private String nombre;

  @Column(name = "numero_colegiado", length = 50)
  private String numeroColegiado;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  protected InspeccionAcompanante() {}

  public InspeccionAcompanante(
      UUID id,
      Inspeccion inspeccion,
      UUID usuarioId,
      String nombre,
      String numeroColegiado,
      Instant creadoEn) {
    this.id = id;
    this.inspeccion = inspeccion;
    this.usuarioId = usuarioId;
    this.nombre = nombre;
    this.numeroColegiado = numeroColegiado;
    this.creadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public Inspeccion getInspeccion() {
    return inspeccion;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getNombre() {
    return nombre;
  }

  public String getNumeroColegiado() {
    return numeroColegiado;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
