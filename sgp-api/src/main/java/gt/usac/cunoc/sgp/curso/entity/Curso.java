package gt.usac.cunoc.sgp.curso.entity;

import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "curso")
public class Curso {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "asignatura_id", nullable = false)
  private Asignatura asignatura;

  @Column(nullable = false, length = 50)
  private String periodo;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "catedratico_id", nullable = false)
  private UserAccount catedratico;

  @Column(name = "fecha_inicio", nullable = false)
  private LocalDate fechaInicio;

  @Column(name = "fecha_fin", nullable = false)
  private LocalDate fechaFin;

  @Column(nullable = false)
  private boolean activo;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version private long version;

  protected Curso() {}

  public Curso(
      Asignatura asignatura,
      String periodo,
      UserAccount catedratico,
      LocalDate fechaInicio,
      LocalDate fechaFin,
      Instant ahora) {
    this.id = UuidV7Generator.generate();
    this.asignatura = asignatura;
    this.periodo = periodo;
    this.catedratico = catedratico;
    this.fechaInicio = fechaInicio;
    this.fechaFin = fechaFin;
    this.activo = true;
    this.creadoEn = ahora;
    this.actualizadoEn = ahora;
  }

  public void finalizar(Instant ahora) {
    this.activo = false;
    this.actualizadoEn = ahora;
  }

  public void actualizar(
      String periodo,
      UserAccount catedratico,
      LocalDate fechaInicio,
      LocalDate fechaFin,
      Instant ahora) {
    this.periodo = periodo;
    this.catedratico = catedratico;
    this.fechaInicio = fechaInicio;
    this.fechaFin = fechaFin;
    this.actualizadoEn = ahora;
  }

  public UUID getId() {
    return id;
  }

  public Asignatura getAsignatura() {
    return asignatura;
  }

  public String getPeriodo() {
    return periodo;
  }

  public UserAccount getCatedratico() {
    return catedratico;
  }

  public LocalDate getFechaInicio() {
    return fechaInicio;
  }

  public LocalDate getFechaFin() {
    return fechaFin;
  }

  public boolean isActivo() {
    return activo;
  }
}
