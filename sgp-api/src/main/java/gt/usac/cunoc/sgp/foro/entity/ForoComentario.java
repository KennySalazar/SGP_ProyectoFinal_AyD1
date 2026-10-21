package gt.usac.cunoc.sgp.foro.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Comentario de un hilo. Admite un solo nivel de respuesta: {@code padreId} apunta siempre a un
 * comentario principal (RN-FOR-04). En el hilo de una inspección puede anclarse a un campo o
 * elemento con {@code elementoRef} y, si es repetible, a una {@code instanciaElementoId}
 * (RN-FOR-02); una respuesta hereda el ancla de su comentario principal.
 */
@Entity
@Table(name = "foro_comentario")
public class ForoComentario {

  @Id private UUID id;

  @Column(name = "hilo_id", nullable = false, updatable = false)
  private UUID hiloId;

  @Column(name = "autor_id", nullable = false, updatable = false)
  private UUID autorId;

  @Column(name = "padre_id", updatable = false)
  private UUID padreId;

  @Column(name = "elemento_ref", length = 300, updatable = false)
  private String elementoRef;

  @Column(name = "instancia_elemento_id", updatable = false)
  private UUID instanciaElementoId;

  @Column(nullable = false, columnDefinition = "text", updatable = false)
  private String contenido;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Version private long version;

  protected ForoComentario() {}

  public ForoComentario(
      UUID id,
      UUID hiloId,
      UUID autorId,
      UUID padreId,
      String elementoRef,
      UUID instanciaElementoId,
      String contenido,
      Instant creadoEn) {
    this.id = id;
    this.hiloId = hiloId;
    this.autorId = autorId;
    this.padreId = padreId;
    this.elementoRef = elementoRef;
    this.instanciaElementoId = instanciaElementoId;
    this.contenido = contenido;
    this.creadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getHiloId() {
    return hiloId;
  }

  public UUID getAutorId() {
    return autorId;
  }

  public UUID getPadreId() {
    return padreId;
  }

  public String getElementoRef() {
    return elementoRef;
  }

  public UUID getInstanciaElementoId() {
    return instanciaElementoId;
  }

  public String getContenido() {
    return contenido;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
