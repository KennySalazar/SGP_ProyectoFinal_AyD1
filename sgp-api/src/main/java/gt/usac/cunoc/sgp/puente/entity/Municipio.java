package gt.usac.cunoc.sgp.puente.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "municipio")
public class Municipio {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "departamento_id", nullable = false)
  private Departamento departamento;

  @Column(name = "codigo_ine", nullable = false, unique = true, length = 10)
  private String codigoIne;

  @Column(nullable = false, length = 120)
  private String nombre;

  @Column(nullable = false)
  private boolean activo;

  @Column(name = "ultimo_correlativo_puente", nullable = false)
  private int ultimoCorrelativoPuente;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version
  @Column(nullable = false)
  private long version;

  protected Municipio() {}

  public UUID getId() {
    return id;
  }

  public Departamento getDepartamento() {
    return departamento;
  }

  public String getCodigoIne() {
    return codigoIne;
  }

  public String getNombre() {
    return nombre;
  }

  public boolean isActivo() {
    return activo;
  }

  public int getUltimoCorrelativoPuente() {
    return ultimoCorrelativoPuente;
  }

  public void asignarCorrelativoPuente(int correlativo, Instant actualizadoEn) {
    if (correlativo != ultimoCorrelativoPuente + 1 || correlativo > 9999) {
      throw new IllegalArgumentException("El correlativo municipal no es valido");
    }
    this.ultimoCorrelativoPuente = correlativo;
    this.actualizadoEn = actualizadoEn;
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
}
