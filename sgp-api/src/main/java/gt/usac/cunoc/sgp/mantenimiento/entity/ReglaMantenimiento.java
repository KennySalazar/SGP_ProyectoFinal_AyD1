package gt.usac.cunoc.sgp.mantenimiento.entity;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.mantenimiento.model.PrioridadOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entidad que representa una regla versionada del catálogo daño -> intervención (HU-045, RN-MTO-02,
 * RN-MTO-03).
 */
@Entity
@Table(name = "regla_mantenimiento")
public class ReglaMantenimiento {

  @Id private UUID id;

  @Column(nullable = false, length = 80)
  private String codigo;

  @Column(name = "numero_revision", nullable = false)
  private Integer numeroRevision;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "version_formulario_id", nullable = false)
  private VersionFormulario versionFormulario;

  @Column(name = "elemento_ref", nullable = false, length = 300)
  private String elementoRef;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "condicion", nullable = false)
  private JsonNode condicion;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo_mantenimiento", nullable = false, length = 20)
  private TipoOrden tipoMantenimiento;

  @Column(name = "intervencion_sugerida", nullable = false, columnDefinition = "TEXT")
  private String intervencionSugerida;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "regla_prioridad", nullable = false)
  private JsonNode reglaPrioridad;

  @Column(nullable = false)
  private boolean activa;

  @Column(name = "creado_por_id", nullable = false)
  private UUID creadoPorId;

  @Column(name = "validado_por_id")
  private UUID validadoPorId;

  @Formula(
      "(SELECT COALESCE(NULLIF(TRIM(u.nombre_completo), ''), u.email) FROM usuario u WHERE u.id = validado_por_id)")
  private String validadoPorNombre;

  @Column(name = "validado_en")
  private Instant validadoEn;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  protected ReglaMantenimiento() {}

  public ReglaMantenimiento(
      UUID id,
      String codigo,
      Integer numeroRevision,
      VersionFormulario versionFormulario,
      String elementoRef,
      JsonNode condicion,
      TipoOrden tipoMantenimiento,
      String intervencionSugerida,
      JsonNode reglaPrioridad,
      boolean activa,
      UUID creadoPorId,
      UUID validadoPorId,
      Instant validadoEn,
      Instant creadoEn) {
    this.id = id;
    this.codigo = codigo;
    this.numeroRevision = numeroRevision;
    this.versionFormulario = versionFormulario;
    this.elementoRef = elementoRef;
    this.condicion = condicion;
    this.tipoMantenimiento = tipoMantenimiento;
    this.intervencionSugerida = intervencionSugerida;
    this.reglaPrioridad = reglaPrioridad;
    this.activa = activa;
    this.creadoPorId = creadoPorId;
    this.validadoPorId = validadoPorId;
    this.validadoEn = validadoEn;
    this.creadoEn = creadoEn;
  }

  public boolean isProvisional() {
    return validadoPorId == null || validadoEn == null;
  }

  public void validar(UUID usuarioId, Instant ahora) {
    if (!isProvisional()) return;
    this.validadoPorId = Objects.requireNonNull(usuarioId, "usuarioId no puede ser nulo");
    this.validadoEn = Objects.requireNonNull(ahora, "fecha de validación no puede ser nula");
  }

  public void activar() {
    this.activa = true;
  }

  public void desactivar() {
    this.activa = false;
  }

  public UUID getId() {
    return id;
  }

  public String getCodigo() {
    return codigo;
  }

  public Integer getNumeroRevision() {
    return numeroRevision;
  }

  public VersionFormulario getVersionFormulario() {
    return versionFormulario;
  }

  public String getElementoRef() {
    return elementoRef;
  }

  public JsonNode getCondicion() {
    return condicion;
  }

  public TipoOrden getTipoMantenimiento() {
    return tipoMantenimiento;
  }

  public String getIntervencionSugerida() {
    return intervencionSugerida;
  }

  public JsonNode getReglaPrioridad() {
    return reglaPrioridad;
  }

  public PrioridadOrden getPrioridadBase() {
    if (reglaPrioridad != null && reglaPrioridad.hasNonNull("prioridad_base")) {
      try {
        return PrioridadOrden.valueOf(reglaPrioridad.get("prioridad_base").asText());
      } catch (IllegalArgumentException e) {
        return PrioridadOrden.MEDIA;
      }
    }
    return PrioridadOrden.MEDIA;
  }

  public boolean isActiva() {
    return activa;
  }

  public void setActiva(boolean activa) {
    this.activa = activa;
  }

  public UUID getCreadoPorId() {
    return creadoPorId;
  }

  public UUID getValidadoPorId() {
    return validadoPorId;
  }

  public String getValidadoPorNombre() {
    return validadoPorNombre;
  }

  public Instant getValidadoEn() {
    return validadoEn;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
