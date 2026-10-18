package gt.usac.cunoc.sgp.inspeccion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Registro inmutable de cada cambio de estado de una inspección (RN-INS-13, RN-REV-06). */
@Entity
@Table(name = "historial_estado_inspeccion")
public class HistorialEstadoInspeccion {

  @Id private UUID id;

  @Column(name = "inspeccion_id", nullable = false, updatable = false)
  private UUID inspeccionId;

  @Column(nullable = false, updatable = false)
  private int secuencia;

  @Column(name = "estado_anterior", length = 30, updatable = false)
  private String estadoAnterior;

  @Column(name = "estado_nuevo", nullable = false, length = 30, updatable = false)
  private String estadoNuevo;

  @Column(name = "realizado_por_id", updatable = false)
  private UUID realizadoPorId;

  @Column(name = "revision_id", updatable = false)
  private UUID revisionId;

  @Column(nullable = false, length = 60, updatable = false)
  private String evento;

  @Column(updatable = false, columnDefinition = "text")
  private String motivo;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  protected HistorialEstadoInspeccion() {}

  public HistorialEstadoInspeccion(
      UUID id,
      UUID inspeccionId,
      int secuencia,
      String estadoAnterior,
      String estadoNuevo,
      UUID realizadoPorId,
      String evento,
      String motivo,
      Instant creadoEn) {
    this.id = id;
    this.inspeccionId = inspeccionId;
    this.secuencia = secuencia;
    this.estadoAnterior = estadoAnterior;
    this.estadoNuevo = estadoNuevo;
    this.realizadoPorId = realizadoPorId;
    this.evento = evento;
    this.motivo = motivo;
    this.creadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspeccionId() {
    return inspeccionId;
  }

  public int getSecuencia() {
    return secuencia;
  }

  public String getEstadoAnterior() {
    return estadoAnterior;
  }

  public String getEstadoNuevo() {
    return estadoNuevo;
  }

  public UUID getRealizadoPorId() {
    return realizadoPorId;
  }

  public UUID getRevisionId() {
    return revisionId;
  }

  public String getEvento() {
    return evento;
  }

  public String getMotivo() {
    return motivo;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
