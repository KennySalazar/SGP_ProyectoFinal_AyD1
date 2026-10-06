package gt.usac.cunoc.sgp.curso.entity;

import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asignatura")
public class Asignatura {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 50)
  private String codigo;

  @Column(nullable = false, length = 150)
  private String nombre;

  @Column(nullable = false)
  private boolean activo;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version private long version;

  protected Asignatura() {}

  public Asignatura(String codigo, String nombre, Instant ahora) {
    this.id = UuidV7Generator.generate();
    this.codigo = codigo;
    this.nombre = nombre;
    this.activo = true;
    this.creadoEn = ahora;
    this.actualizadoEn = ahora;
  }

  public void renombrar(String nombre, Instant ahora) {
    this.nombre = nombre;
    this.actualizadoEn = ahora;
  }

  public UUID getId() {
    return id;
  }

  public String getNombre() {
    return nombre;
  }

  public boolean isActivo() {
    return activo;
  }
}
