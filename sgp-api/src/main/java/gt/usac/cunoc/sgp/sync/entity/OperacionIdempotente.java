package gt.usac.cunoc.sgp.sync.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "operacion_idempotente")
public class OperacionIdempotente {

  @Id private UUID id;

  @Column(name = "usuario_id", nullable = false)
  private UUID usuarioId;

  @Column(nullable = false, length = 100)
  private String clave;

  @Column(nullable = false, length = 10)
  private String metodo;

  @Column(nullable = false, length = 300)
  private String ruta;

  @Column(name = "hash_solicitud", nullable = false, length = 64)
  private String hashSolicitud;

  @Column(nullable = false, length = 20)
  private String estado;

  @Column(name = "codigo_respuesta")
  private Integer codigoRespuesta;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "respuesta")
  private JsonNode respuesta;

  @Column(length = 100)
  private String entidad;

  @Column(name = "entidad_id")
  private UUID entidadId;

  @Column(name = "completado_en")
  private Instant completadoEn;

  @Column(name = "expira_en")
  private Instant expiraEn;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  protected OperacionIdempotente() {}

  public OperacionIdempotente(
      UUID id,
      UUID usuarioId,
      String clave,
      String metodo,
      String ruta,
      String hashSolicitud,
      String estado,
      Integer codigoRespuesta,
      JsonNode respuesta,
      String entidad,
      UUID entidadId,
      Instant completadoEn,
      Instant expiraEn,
      Instant creadoEn) {
    this.id = id;
    this.usuarioId = usuarioId;
    this.clave = clave;
    this.metodo = metodo;
    this.ruta = ruta;
    this.hashSolicitud = hashSolicitud;
    this.estado = estado;
    this.codigoRespuesta = codigoRespuesta;
    this.respuesta = respuesta;
    this.entidad = entidad;
    this.entidadId = entidadId;
    this.completadoEn = completadoEn;
    this.expiraEn = expiraEn;
    this.creadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getClave() {
    return clave;
  }

  public String getMetodo() {
    return metodo;
  }

  public String getRuta() {
    return ruta;
  }

  public String getHashSolicitud() {
    return hashSolicitud;
  }

  public String getEstado() {
    return estado;
  }

  public Integer getCodigoRespuesta() {
    return codigoRespuesta;
  }

  public JsonNode getRespuesta() {
    return respuesta;
  }

  public String getEntidad() {
    return entidad;
  }

  public UUID getEntidadId() {
    return entidadId;
  }

  public Instant getCompletadoEn() {
    return completadoEn;
  }

  public Instant getExpiraEn() {
    return expiraEn;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
