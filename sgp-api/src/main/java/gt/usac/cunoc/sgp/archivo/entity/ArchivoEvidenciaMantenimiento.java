package gt.usac.cunoc.sgp.archivo.entity;

import gt.usac.cunoc.sgp.archivo.model.TipoUsoArchivo;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Vínculo de una fotografía de evidencia con una orden de mantenimiento (HU-048). */
@Entity
@Table(name = "archivo_evidencia_mantenimiento")
public class ArchivoEvidenciaMantenimiento
    implements org.springframework.data.domain.Persistable<UUID> {
  @Transient private boolean nuevo = true;

  @PostLoad
  @PostPersist
  void persistido() {
    nuevo = false;
  }

  public boolean isNew() {
    return nuevo;
  }

  @Id
  @Column(name = "archivo_id")
  private UUID archivoId;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "archivo_id", insertable = false, updatable = false)
  private Archivo archivo;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo_uso", nullable = false, length = 30)
  private TipoUsoArchivo tipoUso;

  @Column(name = "orden_mantenimiento_id", nullable = false)
  private UUID ordenMantenimientoId;

  @Column(name = "elemento_ref", length = 300)
  private String elementoRef;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  public UUID getId() {
    return archivoId;
  }

  protected ArchivoEvidenciaMantenimiento() {}

  public ArchivoEvidenciaMantenimiento(
      Archivo archivo, UUID ordenMantenimientoId, String elementoRef, Instant creadoEn) {
    this.archivo = archivo;
    this.archivoId = archivo.getId();
    this.tipoUso = archivo.getTipoUso();
    this.ordenMantenimientoId = ordenMantenimientoId;
    this.elementoRef = elementoRef;
    this.creadoEn = creadoEn;
  }

  public UUID getArchivoId() {
    return archivoId;
  }

  public Archivo getArchivo() {
    return archivo;
  }

  public TipoUsoArchivo getTipoUso() {
    return tipoUso;
  }

  public UUID getOrdenMantenimientoId() {
    return ordenMantenimientoId;
  }

  public String getElementoRef() {
    return elementoRef;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
