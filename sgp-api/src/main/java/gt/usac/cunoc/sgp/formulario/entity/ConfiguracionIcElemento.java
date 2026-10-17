package gt.usac.cunoc.sgp.formulario.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "configuracion_ic_elemento")
public class ConfiguracionIcElemento {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "version_formulario_id", nullable = false)
  private VersionFormulario versionFormulario;

  @Column(name = "elemento_ref", nullable = false, length = 300)
  private String elementoRef;

  @Column(nullable = false, precision = 12, scale = 6)
  private BigDecimal peso;

  @Column(nullable = false, length = 500)
  private String descripcion;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  protected ConfiguracionIcElemento() {}

  public ConfiguracionIcElemento(
      UUID id,
      VersionFormulario versionFormulario,
      String elementoRef,
      BigDecimal peso,
      String descripcion,
      Instant creadoEn) {
    this.id = id;
    this.versionFormulario = versionFormulario;
    this.elementoRef = elementoRef;
    this.peso = peso;
    this.descripcion = descripcion;
    this.creadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public VersionFormulario getVersionFormulario() {
    return versionFormulario;
  }

  public String getElementoRef() {
    return elementoRef;
  }

  public void setElementoRef(String elementoRef) {
    this.elementoRef = elementoRef;
  }

  public BigDecimal getPeso() {
    return peso;
  }

  public void setPeso(BigDecimal peso) {
    this.peso = peso;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public void setDescripcion(String descripcion) {
    this.descripcion = descripcion;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConfiguracionIcElemento that = (ConfiguracionIcElemento) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
