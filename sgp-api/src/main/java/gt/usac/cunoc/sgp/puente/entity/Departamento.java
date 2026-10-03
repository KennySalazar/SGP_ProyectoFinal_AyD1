package gt.usac.cunoc.sgp.puente.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "departamento")
public class Departamento {

  @Id private UUID id;

  @Column(name = "codigo_ine", nullable = false, unique = true, length = 10)
  private String codigoIne;

  @Column(nullable = false, length = 100)
  private String nombre;

  @Column(nullable = false)
  private boolean activo;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version
  @Column(nullable = false)
  private long version;

  protected Departamento() {}

  public UUID getId() {
    return id;
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
