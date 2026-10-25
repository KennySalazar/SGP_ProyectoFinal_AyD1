package gt.usac.cunoc.sgp.foro.entity;

import gt.usac.cunoc.sgp.foro.model.CategoriaHilo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Hilo de discusión de un puente. Es permanente: cruza inspecciones y ciclos académicos
 * (RN-FOR-01). Si {@code inspeccionId} no es nulo, es el hilo de una inspección publicada
 * (RN-FOR-03).
 */
@Entity
@Table(name = "foro_hilo")
public class ForoHilo {

  @Id private UUID id;

  @Column(name = "puente_id", nullable = false, updatable = false)
  private UUID puenteId;

  @Column(name = "inspeccion_id", updatable = false)
  private UUID inspeccionId;

  @Column(name = "autor_id", nullable = false, updatable = false)
  private UUID autorId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30, updatable = false)
  private CategoriaHilo categoria;

  @Column(nullable = false, length = 250, updatable = false)
  private String titulo;

  /**
   * Un moderador cierra el hilo con un motivo (HU-065, RN-FOR-08) mediante {@code
   * ForoHiloRepository#cerrar}; aquí solo se lee. Cerrado sigue visible para lectura.
   */
  @Column(name = "cerrado_en", insertable = false, updatable = false)
  private Instant cerradoEn;

  @Column(name = "cerrado_por_id", insertable = false, updatable = false)
  private UUID cerradoPorId;

  @Column(name = "motivo_cierre", insertable = false, updatable = false, columnDefinition = "text")
  private String motivoCierre;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version private long version;

  protected ForoHilo() {}

  public ForoHilo(
      UUID id,
      UUID puenteId,
      UUID inspeccionId,
      UUID autorId,
      CategoriaHilo categoria,
      String titulo,
      Instant creadoEn) {
    this.id = id;
    this.puenteId = puenteId;
    this.inspeccionId = inspeccionId;
    this.autorId = autorId;
    this.categoria = categoria;
    this.titulo = titulo;
    this.creadoEn = creadoEn;
    this.actualizadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPuenteId() {
    return puenteId;
  }

  public UUID getInspeccionId() {
    return inspeccionId;
  }

  public UUID getAutorId() {
    return autorId;
  }

  public CategoriaHilo getCategoria() {
    return categoria;
  }

  public String getTitulo() {
    return titulo;
  }

  public Instant getCerradoEn() {
    return cerradoEn;
  }

  public UUID getCerradoPorId() {
    return cerradoPorId;
  }

  public String getMotivoCierre() {
    return motivoCierre;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }
}
