package gt.usac.cunoc.sgp.archivo.entity;

import gt.usac.cunoc.sgp.archivo.model.TipoUsoArchivo;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "archivo_inspeccion")
public class ArchivoInspeccion implements org.springframework.data.domain.Persistable<UUID> {
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

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inspeccion_id")
  private Inspeccion inspeccion;

  @Column(name = "elemento_ref", length = 300)
  private String elementoRef;

  @Column(name = "instancia_elemento_id")
  private UUID instanciaElementoId;

  @Column(name = "creado_en", nullable = false)
  private Instant creadoEn;

  public UUID getId() {
    return archivoId;
  }

  protected ArchivoInspeccion() {}

  public ArchivoInspeccion(
      Archivo archivo,
      Inspeccion inspeccion,
      String elementoRef,
      UUID instanciaElementoId,
      Instant creadoEn) {
    this.archivo = archivo;
    this.archivoId = archivo.getId();
    this.tipoUso = archivo.getTipoUso();
    this.inspeccion = inspeccion;
    this.elementoRef = elementoRef;
    this.instanciaElementoId = instanciaElementoId;
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

  public Inspeccion getInspeccion() {
    return inspeccion;
  }

  public String getElementoRef() {
    return elementoRef;
  }

  public UUID getInstanciaElementoId() {
    return instanciaElementoId;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
