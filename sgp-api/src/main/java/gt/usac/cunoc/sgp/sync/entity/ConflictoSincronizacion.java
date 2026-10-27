package gt.usac.cunoc.sgp.sync.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "conflicto_sincronizacion")
public class ConflictoSincronizacion {
  @Id private UUID id;

  @Column(name = "inspeccion_id", nullable = false)
  private UUID inspeccionId;

  @Column(name = "operacion_idempotente_id", nullable = false)
  private UUID operacionIdempotenteId;

  @Column(name = "version_servidor", nullable = false)
  private long versionServidor;

  @Column(name = "version_cliente")
  private Long versionCliente;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "copia_servidor", nullable = false)
  private JsonNode copiaServidor;

  @Column(name = "hash_copia_cliente", nullable = false, length = 64)
  private String hashCopiaCliente;

  @Column(nullable = false, length = 30)
  private String resolucion = "CLIENTE_PREVALECE";

  @Column(name = "detectado_en", nullable = false)
  private Instant detectadoEn;

  @Column(name = "resuelto_en", nullable = false)
  private Instant resueltoEn;

  protected ConflictoSincronizacion() {}

  public ConflictoSincronizacion(
      UUID id,
      UUID inspeccionId,
      UUID operacionIdempotenteId,
      long versionServidor,
      Long versionCliente,
      JsonNode copiaServidor,
      String hashCopiaCliente,
      Instant ahora) {
    this.id = id;
    this.inspeccionId = inspeccionId;
    this.operacionIdempotenteId = operacionIdempotenteId;
    this.versionServidor = versionServidor;
    this.versionCliente = versionCliente;
    this.copiaServidor = copiaServidor;
    this.hashCopiaCliente = hashCopiaCliente;
    this.detectadoEn = ahora;
    this.resueltoEn = ahora;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspeccionId() {
    return inspeccionId;
  }

  public long getVersionServidor() {
    return versionServidor;
  }

  public Long getVersionCliente() {
    return versionCliente;
  }

  public JsonNode getCopiaServidor() {
    return copiaServidor;
  }

  public Instant getDetectadoEn() {
    return detectadoEn;
  }
}
