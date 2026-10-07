package gt.usac.cunoc.sgp.puente.entity;

import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.curso.entity.CursoEstudiante;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
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
@Table(name = "asignacion_puente")
public class AsignacionPuente {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "curso_estudiante_id", nullable = false)
  private CursoEstudiante cursoEstudiante;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "puente_id", nullable = false)
  private Puente puente;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "asignado_por_id", nullable = false)
  private UserAccount asignadoPor;

  @Column(name = "asignado_en", nullable = false, updatable = false)
  private Instant asignadoEn;

  @Column(name = "revocado_en")
  private Instant revocadoEn;

  @Column(name = "motivo_revocacion", length = 1000)
  private String motivoRevocacion;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  protected AsignacionPuente() {}

  public AsignacionPuente(
      CursoEstudiante cursoEstudiante, Puente puente, UserAccount asignadoPor, Instant ahora) {
    this.id = UuidV7Generator.generate();
    this.cursoEstudiante = cursoEstudiante;
    this.puente = puente;
    this.asignadoPor = asignadoPor;
    this.asignadoEn = ahora;
    this.creadoEn = ahora;
  }

  public void revocar(String motivo, Instant ahora) {
    this.revocadoEn = ahora;
    this.motivoRevocacion = motivo;
  }

  public UUID getId() {
    return id;
  }
}
