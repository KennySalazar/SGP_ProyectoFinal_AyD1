package gt.usac.cunoc.sgp.mantenimiento.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Registro inmutable de cada cambio de estado de una orden de mantenimiento (RN-MTO-04). */
@Entity
@Immutable
@Table(name = "historial_estado_orden")
public class HistorialEstadoOrden {

  @Id private UUID id;

  @Column(name = "orden_mantenimiento_id", nullable = false, updatable = false)
  private UUID ordenMantenimientoId;

  @Column(nullable = false, updatable = false)
  private int secuencia;

  @Column(name = "estado_anterior", length = 20, updatable = false)
  private String estadoAnterior;

  @Column(name = "estado_nuevo", nullable = false, length = 20, updatable = false)
  private String estadoNuevo;

  @Column(name = "realizado_por_id", updatable = false)
  private UUID realizadoPorId;

  @Column(updatable = false, columnDefinition = "text")
  private String motivo;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  protected HistorialEstadoOrden() {}

  public HistorialEstadoOrden(
      UUID id,
      UUID ordenMantenimientoId,
      int secuencia,
      String estadoAnterior,
      String estadoNuevo,
      UUID realizadoPorId,
      String motivo,
      Instant creadoEn) {
    this.id = id;
    this.ordenMantenimientoId = ordenMantenimientoId;
    this.secuencia = secuencia;
    this.estadoAnterior = estadoAnterior;
    this.estadoNuevo = estadoNuevo;
    this.realizadoPorId = realizadoPorId;
    this.motivo = motivo;
    this.creadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrdenMantenimientoId() {
    return ordenMantenimientoId;
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

  public String getMotivo() {
    return motivo;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
