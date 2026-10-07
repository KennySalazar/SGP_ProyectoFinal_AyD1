package gt.usac.cunoc.sgp.notificacion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notificacion")
public class Notificacion {

  @Id private UUID id;

  @Column(name = "usuario_id", nullable = false, updatable = false)
  private UUID usuarioId;

  @Column(nullable = false, length = 50, updatable = false)
  private String tipo;

  @Column(nullable = false, length = 200, updatable = false)
  private String titulo;

  @Column(nullable = false, columnDefinition = "text", updatable = false)
  private String mensaje;

  @Column(length = 100, updatable = false)
  private String entidad;

  @Column(name = "entidad_id", updatable = false)
  private UUID entidadId;

  @Column(name = "clave_evento", nullable = false, length = 250, updatable = false)
  private String claveEvento;

  @Column(name = "leido_en")
  private Instant leidoEn;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  protected Notificacion() {}

  public Notificacion(
      UUID id,
      UUID usuarioId,
      String tipo,
      String titulo,
      String mensaje,
      String entidad,
      UUID entidadId,
      String claveEvento,
      Instant creadoEn) {
    this.id = id;
    this.usuarioId = usuarioId;
    this.tipo = tipo;
    this.titulo = titulo;
    this.mensaje = mensaje;
    this.entidad = entidad;
    this.entidadId = entidadId;
    this.claveEvento = claveEvento;
    this.creadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getTipo() {
    return tipo;
  }

  public String getTitulo() {
    return titulo;
  }

  public String getMensaje() {
    return mensaje;
  }

  public String getEntidad() {
    return entidad;
  }

  public UUID getEntidadId() {
    return entidadId;
  }

  public String getClaveEvento() {
    return claveEvento;
  }

  public Instant getLeidoEn() {
    return leidoEn;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
