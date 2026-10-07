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
import java.util.UUID;

@Entity
@Table(name = "curso_estudiante")
public class CursoEstudiante {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "curso_id", nullable = false)
  private Curso curso;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "estudiante_id", nullable = false)
  private UserAccount estudiante;

  @Column(nullable = false, length = 20)
  private String estado;

  @Column(nullable = false, length = 30)
  private String seccion;

  @Column(name = "inscrito_en", nullable = false)
  private Instant inscritoEn;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inscrito_por_id", nullable = false)
  private UserAccount inscritoPor;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version private long version;

  protected CursoEstudiante() {}

  public CursoEstudiante(
      Curso curso, UserAccount estudiante, String seccion, UserAccount inscritoPor, Instant ahora) {
    this.id = UuidV7Generator.generate();
    this.curso = curso;
    this.estudiante = estudiante;
    this.estado = "ACTIVO";
    this.seccion = seccion;
    this.inscritoEn = ahora;
    this.inscritoPor = inscritoPor;
    this.creadoEn = ahora;
    this.actualizadoEn = ahora;
  }

  public UUID getId() {
    return id;
  }
}
