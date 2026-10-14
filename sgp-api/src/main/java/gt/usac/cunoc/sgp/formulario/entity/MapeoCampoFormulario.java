package gt.usac.cunoc.sgp.formulario.entity;

import gt.usac.cunoc.sgp.formulario.model.TipoMapeoCampo;
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

@Entity
@Table(name = "mapeo_campo_formulario")
public class MapeoCampoFormulario {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "version_origen_id", nullable = false)
  private VersionFormulario versionOrigen;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "version_destino_id", nullable = false)
  private VersionFormulario versionDestino;

  @Column(name = "elemento_ref_origen", length = 300)
  private String elementoRefOrigen;

  @Column(name = "elemento_ref_destino", length = 300)
  private String elementoRefDestino;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TipoMapeoCampo tipo;

  @Column(columnDefinition = "text")
  private String observacion;

  @Column(name = "creado_por_id", nullable = false)
  private UUID creadoPorId;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  protected MapeoCampoFormulario() {}

  public MapeoCampoFormulario(
      UUID id,
      VersionFormulario versionOrigen,
      VersionFormulario versionDestino,
      String elementoRefOrigen,
      String elementoRefDestino,
      TipoMapeoCampo tipo,
      String observacion,
      UUID creadoPorId,
      Instant creadoEn) {
    this.id = id;
    this.versionOrigen = versionOrigen;
    this.versionDestino = versionDestino;
    this.elementoRefOrigen = elementoRefOrigen;
    this.elementoRefDestino = elementoRefDestino;
    this.tipo = tipo;
    this.observacion = observacion;
    this.creadoPorId = creadoPorId;
    this.creadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public VersionFormulario getVersionOrigen() {
    return versionOrigen;
  }

  public VersionFormulario getVersionDestino() {
    return versionDestino;
  }

  public String getElementoRefOrigen() {
    return elementoRefOrigen;
  }

  public void setElementoRefOrigen(String elementoRefOrigen) {
    this.elementoRefOrigen = elementoRefOrigen;
  }

  public String getElementoRefDestino() {
    return elementoRefDestino;
  }

  public void setElementoRefDestino(String elementoRefDestino) {
    this.elementoRefDestino = elementoRefDestino;
  }

  public TipoMapeoCampo getTipo() {
    return tipo;
  }

  public void setTipo(TipoMapeoCampo tipo) {
    this.tipo = tipo;
  }

  public String getObservacion() {
    return observacion;
  }

  public void setObservacion(String observacion) {
    this.observacion = observacion;
  }

  public UUID getCreadoPorId() {
    return creadoPorId;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MapeoCampoFormulario that = (MapeoCampoFormulario) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
