package gt.usac.cunoc.sgp.mantenimiento.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Registro de ejecución de una orden. Se crea al iniciar la ejecución (RN-MTO-04); la fecha de
 * ejecución y el responsable definitivo se completan al cerrarla (HU-048, RN-MTO-05).
 */
@Entity
@Table(name = "orden_mantenimiento_ejecucion")
public class OrdenMantenimientoEjecucion {

  @Id
  @Column(name = "orden_mantenimiento_id")
  private UUID ordenMantenimientoId;

  @Column(name = "iniciado_en", nullable = false, updatable = false)
  private Instant iniciadoEn;

  @Column(name = "fecha_ejecucion")
  private LocalDate fechaEjecucion;

  @Column(name = "responsable_usuario_id")
  private UUID responsableUsuarioId;

  @Column(name = "responsable_nombre", length = 200)
  private String responsableNombre;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version
  @Column(nullable = false)
  private Long version;

  protected OrdenMantenimientoEjecucion() {}

  /** Inicia la ejecución; quien la inicia queda como responsable hasta el cierre. */
  public OrdenMantenimientoEjecucion(
      UUID ordenMantenimientoId, Instant iniciadoEn, UUID responsableUsuarioId) {
    this.ordenMantenimientoId = ordenMantenimientoId;
    this.iniciadoEn = iniciadoEn;
    this.responsableUsuarioId = responsableUsuarioId;
    this.creadoEn = iniciadoEn;
    this.actualizadoEn = iniciadoEn;
  }

  /**
   * Registra la fecha de ejecución y el responsable: un usuario del sistema, el nombre de una
   * persona o empresa externa, o ambos (RN-MTO-05). El servicio valida antes de llamar.
   */
  public void registrar(
      LocalDate fechaEjecucion,
      UUID responsableUsuarioId,
      String responsableNombre,
      Instant ahora) {
    this.fechaEjecucion = fechaEjecucion;
    this.responsableUsuarioId = responsableUsuarioId;
    this.responsableNombre = responsableNombre;
    this.actualizadoEn = ahora;
  }

  public UUID getOrdenMantenimientoId() {
    return ordenMantenimientoId;
  }

  public Instant getIniciadoEn() {
    return iniciadoEn;
  }

  public LocalDate getFechaEjecucion() {
    return fechaEjecucion;
  }

  public UUID getResponsableUsuarioId() {
    return responsableUsuarioId;
  }

  public String getResponsableNombre() {
    return responsableNombre;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }

  public Long getVersion() {
    return version;
  }
}
