package gt.usac.cunoc.sgp.common.audit;

import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "auditoria")
public class AuditLog {

  @Id private UUID id;

  @Column(name = "usuario_id")
  private UUID userId;

  @Column(name = "accion", nullable = false, length = 100)
  private String action;

  @Column(name = "entidad", nullable = false, length = 100)
  private String entity;

  @Column(name = "entidad_id")
  private UUID entityId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "valores_posteriores", columnDefinition = "jsonb")
  private Map<String, Object> valuesAfter;

  @Column(name = "creado_en", nullable = false)
  private Instant createdAt;

  protected AuditLog() {}

  public AuditLog(
      UUID userId,
      String action,
      String entity,
      UUID entityId,
      Map<String, Object> valuesAfter,
      Instant now) {
    this.id = UuidV7Generator.generate();
    this.userId = userId;
    this.action = action;
    this.entity = entity;
    this.entityId = entityId;
    this.valuesAfter = valuesAfter;
    this.createdAt = now;
  }
}
