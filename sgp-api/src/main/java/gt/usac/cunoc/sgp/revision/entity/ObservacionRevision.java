package gt.usac.cunoc.sgp.revision.entity;

import gt.usac.cunoc.sgp.archivo.model.CategoriaFoto;
import gt.usac.cunoc.sgp.revision.model.EstadoObservacion;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Observación puntual de V9; no duplica al autor de inspección ni al revisor del ciclo. */
@Entity
@Table(name = "observacion_revision")
public class ObservacionRevision {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "revision_id", nullable = false, updatable = false)
  private Revision revision;

  @Column(name = "elemento_ref", length = 300, updatable = false)
  private String elementoRef;

  @Column(name = "instancia_elemento_id", updatable = false)
  private UUID instanciaElementoId;

  @Enumerated(EnumType.STRING)
  @Column(name = "categoria_fotografica", length = 30, updatable = false)
  private CategoriaFoto categoriaFotografica;

  @Column(nullable = false, columnDefinition = "text")
  private String contenido;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EstadoObservacion estado;

  @Column(name = "creado_por_id", nullable = false, updatable = false)
  private UUID creadoPorId;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "resuelto_por_id")
  private UUID resueltoPorId;

  @Column(name = "resuelto_en")
  private Instant resueltoEn;

  @Column(name = "respuesta_autor", columnDefinition = "text")
  private String respuestaAutor;

  protected ObservacionRevision() {}

  private ObservacionRevision(
      UUID id, Revision revision, String contenido, UUID creadoPorId, Instant creadoEn) {
    this.id = Objects.requireNonNull(id);
    this.revision = Objects.requireNonNull(revision);
    if (contenido == null || contenido.isBlank() || contenido.length() > 2000) {
      throw new IllegalArgumentException("La observación requiere texto de hasta 2000 caracteres");
    }
    this.contenido = contenido;
    this.creadoPorId = Objects.requireNonNull(creadoPorId);
    this.creadoEn = Objects.requireNonNull(creadoEn);
    this.estado = EstadoObservacion.PENDIENTE;
  }

  /**
   * El servicio deberá validar el anclaje contra el esquema y datos congelados antes de persistir.
   */
  public static ObservacionRevision paraElemento(
      UUID id,
      Revision revision,
      String elementoRef,
      UUID instanciaElementoId,
      String texto,
      UUID creadoPorId,
      Instant creadoEn) {
    if (elementoRef == null || elementoRef.isBlank() || elementoRef.length() > 300) {
      throw new IllegalArgumentException("Se requiere una referencia de hasta 300 caracteres");
    }
    var observacion = new ObservacionRevision(id, revision, texto, creadoPorId, creadoEn);
    observacion.elementoRef = elementoRef;
    observacion.instanciaElementoId = instanciaElementoId;
    return observacion;
  }

  public static ObservacionRevision paraCategoriaFotografica(
      UUID id,
      Revision revision,
      CategoriaFoto categoria,
      String texto,
      UUID creadoPorId,
      Instant creadoEn) {
    var observacion = new ObservacionRevision(id, revision, texto, creadoPorId, creadoEn);
    observacion.categoriaFotografica = Objects.requireNonNull(categoria);
    return observacion;
  }

  public UUID getId() {
    return id;
  }

  /**
   * El servicio aplica autorización, bloqueo de ciclo y auditoría antes de confirmar la edición.
   */
  public void editarTexto(String texto) {
    if (texto == null || texto.isBlank() || texto.length() > 2000) {
      throw new IllegalArgumentException("La observación requiere texto de hasta 2000 caracteres");
    }
    contenido = texto;
  }

  public void resolver(UUID actor, String respuesta, Instant ahora) {
    if (estado != EstadoObservacion.PENDIENTE)
      throw new IllegalStateException("La observación ya fue resuelta");
    if (respuesta != null && respuesta.length() > 2000)
      throw new IllegalArgumentException("Respuesta demasiado larga");
    estado = EstadoObservacion.RESUELTA;
    resueltoPorId = Objects.requireNonNull(actor);
    resueltoEn = Objects.requireNonNull(ahora);
    respuestaAutor = respuesta;
  }

  public Revision getRevision() {
    return revision;
  }

  public String getElementoRef() {
    return elementoRef;
  }

  public UUID getInstanciaElementoId() {
    return instanciaElementoId;
  }

  public CategoriaFoto getCategoriaFotografica() {
    return categoriaFotografica;
  }

  public String getContenido() {
    return contenido;
  }

  public EstadoObservacion getEstado() {
    return estado;
  }

  public UUID getCreadoPorId() {
    return creadoPorId;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public UUID getResueltoPorId() {
    return resueltoPorId;
  }

  public Instant getResueltoEn() {
    return resueltoEn;
  }

  public String getRespuestaAutor() {
    return respuestaAutor;
  }
}
