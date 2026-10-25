package gt.usac.cunoc.sgp.foro.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Comentario de un hilo. Admite un solo nivel de respuesta: {@code padreId} apunta siempre a un
 * comentario principal (RN-FOR-04). En el hilo de una inspección puede anclarse a un campo o
 * elemento con {@code elementoRef} y, si es repetible, a una {@code instanciaElementoId}
 * (RN-FOR-02); una respuesta hereda el ancla de su comentario principal.
 *
 * <p>Su autor puede editarlo dentro del plazo que fija el servidor y eliminarlo, lo que deja la
 * marca sin borrar la fila para conservar las respuestas (RN-FOR-05). Un moderador puede ocultarlo
 * con un motivo (RN-FOR-08).
 */
@Entity
@Table(name = "foro_comentario")
public class ForoComentario {

  /** Plazo para que el autor edite su comentario, contado con la hora del servidor (RN-FOR-05). */
  public static final Duration PLAZO_EDICION = Duration.ofMinutes(15);

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

  @Column(nullable = false, columnDefinition = "text")
  private String contenido;

  @Column(name = "editado_en")
  private Instant editadoEn;

  @Column(name = "eliminado_en")
  private Instant eliminadoEn;

  @Column(name = "ocultado_en")
  private Instant ocultadoEn;

  @Column(name = "ocultado_por_id")
  private UUID ocultadoPorId;

  @Column(name = "motivo_ocultamiento", columnDefinition = "text")
  private String motivoOcultamiento;

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

  public Instant getEditadoEn() {
    return editadoEn;
  }

  public Instant getEliminadoEn() {
    return eliminadoEn;
  }

  public Instant getOcultadoEn() {
    return ocultadoEn;
  }

  public UUID getOcultadoPorId() {
    return ocultadoPorId;
  }

  public String getMotivoOcultamiento() {
    return motivoOcultamiento;
  }

  /** Último instante en que su autor todavía puede editarlo. */
  public Instant getEditableHasta() {
    return creadoEn.plus(PLAZO_EDICION);
  }

  public boolean isEliminado() {
    return eliminadoEn != null;
  }

  public boolean isOculto() {
    return ocultadoEn != null;
  }

  public void editar(String contenido, Instant ahora) {
    this.contenido = contenido;
    this.editadoEn = ahora;
  }

  /** Deja la marca de eliminado; el contenido se conserva para la bitácora y nunca se expone. */
  public void eliminar(Instant ahora) {
    this.eliminadoEn = ahora;
  }

  public void ocultar(UUID moderadorId, String motivo, Instant ahora) {
    this.ocultadoEn = ahora;
    this.ocultadoPorId = moderadorId;
    this.motivoOcultamiento = motivo;
  }
}
