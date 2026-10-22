package gt.usac.cunoc.sgp.mantenimiento.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "plan_mantenimiento_rutinario_actividad")
public class PlanMantenimientoRutinarioActividad {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_mantenimiento_rutinario_id", nullable = false)
  private PlanMantenimientoRutinario plan;

  @Column(nullable = false)
  private int posicion;

  @Column(nullable = false, columnDefinition = "text")
  private String descripcion;

  protected PlanMantenimientoRutinarioActividad() {}

  public PlanMantenimientoRutinarioActividad(
      UUID id, PlanMantenimientoRutinario plan, int posicion, String descripcion) {
    this.id = id;
    this.plan = plan;
    this.posicion = posicion;
    this.descripcion = descripcion;
  }

  public UUID getId() {
    return id;
  }

  public int getPosicion() {
    return posicion;
  }

  public String getDescripcion() {
    return descripcion;
  }
}
