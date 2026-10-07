package gt.usac.cunoc.sgp.common.audit.entity;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "auditoria")
public class Auditoria {

  @Id private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 100)
  private AccionAuditoria accion;

  @Column(nullable = false, length = 100)
  private String entidad;

  @Column(name = "entidad_id")
  private UUID entidadId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "valores_anteriores")
  private JsonNode valoresAnteriores;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "valores_posteriores")
  private JsonNode valoresPosteriores;

  @Column(name = "proceso_automatico", length = 100)
  private String procesoAutomatico;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  protected Auditoria() {}

  public Auditoria(
      UUID id,
      UUID usuarioId,
      AccionAuditoria accion,
      String entidad,
      UUID entidadId,
      JsonNode valoresAnteriores,
      JsonNode valoresPosteriores,
      String procesoAutomatico,
      Instant creadoEn) {
    this.id = id;
    this.usuarioId = usuarioId;
    this.accion = accion;
    this.entidad = entidad;
    this.entidadId = entidadId;
    this.valoresAnteriores = valoresAnteriores;
    this.valoresPosteriores = valoresPosteriores;
    this.procesoAutomatico = procesoAutomatico;
    this.creadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public AccionAuditoria getAccion() {
    return accion;
  }

  public String getEntidad() {
    return entidad;
  }

  public UUID getEntidadId() {
    return entidadId;
  }

  public JsonNode getValoresAnteriores() {
    return valoresAnteriores;
  }

  public JsonNode getValoresPosteriores() {
    return valoresPosteriores;
  }

  public String getProcesoAutomatico() {
    return procesoAutomatico;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
