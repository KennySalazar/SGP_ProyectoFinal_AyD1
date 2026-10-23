package gt.usac.cunoc.sgp.formulario.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "aprobacion_calibracion_ic")
public class AprobacionCalibracion {
  @Id private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "version_formulario_id", nullable = false, unique = true, updatable = false)
  private VersionFormulario versionFormulario;

  @Column(name = "aprobado_por_id", nullable = false, updatable = false)
  private UUID aprobadoPorId;

  @Column(name = "aprobado_por_nombre", nullable = false, length = 320, updatable = false)
  private String aprobadoPorNombre;

  @Formula(
      """
          (case when position('@' in aprobado_por_nombre) > 0 then
            coalesce((select nullif(trim(u.nombre_completo), '') from usuario u
                      where u.id = aprobado_por_id), aprobado_por_nombre)
            else aprobado_por_nombre end)
          """)
  private String nombreAprobadorVisible;

  @Column(name = "aprobado_en", nullable = false, updatable = false)
  private Instant aprobadoEn;

  @Column(length = 2000, updatable = false)
  private String observaciones;

  protected AprobacionCalibracion() {}

  public AprobacionCalibracion(
      UUID id,
      VersionFormulario versionFormulario,
      UUID aprobadoPorId,
      String aprobadoPorNombre,
      Instant aprobadoEn,
      String observaciones) {
    this.id = id;
    this.versionFormulario = versionFormulario;
    this.aprobadoPorId = aprobadoPorId;
    this.aprobadoPorNombre = aprobadoPorNombre;
    this.aprobadoEn = aprobadoEn;
    this.observaciones = observaciones;
  }

  public UUID getId() {
    return id;
  }

  public VersionFormulario getVersionFormulario() {
    return versionFormulario;
  }

  public UUID getAprobadoPorId() {
    return aprobadoPorId;
  }

  public String getAprobadoPorNombre() {
    return aprobadoPorNombre;
  }

  public String getNombreAprobadorVisible() {
    return nombreAprobadorVisible == null || nombreAprobadorVisible.isBlank()
        ? aprobadoPorNombre
        : nombreAprobadorVisible;
  }

  public Instant getAprobadoEn() {
    return aprobadoEn;
  }

  public String getObservaciones() {
    return observaciones;
  }
}
