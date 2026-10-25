package gt.usac.cunoc.sgp.mantenimiento.entity;

import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "plan_mantenimiento_rutinario")
public class PlanMantenimientoRutinario {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "puente_id", nullable = false, unique = true)
  private Puente puente;

  @Column(name = "periodicidad_meses", nullable = false)
  private short periodicidadMeses;

  @Column(name = "fecha_inicio", nullable = false)
  private LocalDate fechaInicio;

  @Column(name = "responsable_usuario_id")
  private UUID responsableUsuarioId;

  @Column(nullable = false)
  private boolean activo;

  @Column(name = "aprobado_por_id")
  private UUID aprobadoPorId;

  @Column(name = "aprobado_en")
  private Instant aprobadoEn;

  @Column(name = "inactivado_en")
  private Instant inactivadoEn;

  @Column(name = "motivo_inactivacion", columnDefinition = "text")
  private String motivoInactivacion;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version
  @Column(nullable = false)
  private long version;

  @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("posicion ASC")
  private List<PlanMantenimientoRutinarioActividad> actividades = new ArrayList<>();

  protected PlanMantenimientoRutinario() {}

  public PlanMantenimientoRutinario(UUID id, Puente puente, LocalDate fechaInicio, Instant ahora) {
    this.id = id;
    this.puente = puente;
    this.periodicidadMeses = 12;
    this.fechaInicio = fechaInicio;
    this.activo = true;
    this.creadoEn = ahora;
    this.actualizadoEn = ahora;
  }

  public void actualizar(short periodicidadMeses, List<String> descripciones, Instant ahora) {
    this.periodicidadMeses = periodicidadMeses;
    this.actualizadoEn = ahora;
    this.actividades.clear();
    for (int i = 0; i < descripciones.size(); i++) {
      this.actividades.add(
          new PlanMantenimientoRutinarioActividad(
              UuidV7Generator.generate(), this, i + 1, descripciones.get(i).strip()));
    }
  }

  public UUID getId() {
    return id;
  }

  public Puente getPuente() {
    return puente;
  }

  public short getPeriodicidadMeses() {
    return periodicidadMeses;
  }

  public LocalDate getFechaInicio() {
    return fechaInicio;
  }

  public UUID getResponsableUsuarioId() {
    return responsableUsuarioId;
  }

  public boolean isActivo() {
    return activo;
  }

  public UUID getAprobadoPorId() {
    return aprobadoPorId;
  }

  public Instant getAprobadoEn() {
    return aprobadoEn;
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

  public List<PlanMantenimientoRutinarioActividad> getActividades() {
    return actividades;
  }
}
