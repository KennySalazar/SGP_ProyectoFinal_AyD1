package gt.usac.cunoc.sgp.inspeccion.entity;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "resultado_ic")
public class ResultadoIc {

  @Id private UUID id;

  @Column(name = "inspeccion_id", nullable = false, updatable = false)
  private UUID inspeccionId;

  @Column(name = "version_configuracion_id", nullable = false, updatable = false)
  private UUID versionConfiguracionId;

  @Column(nullable = false, length = 20, updatable = false)
  private String tipo;

  @Column(name = "indice_calculado", nullable = false, precision = 8, scale = 5, updatable = false)
  private BigDecimal indiceCalculado;

  @Enumerated(EnumType.STRING)
  @Column(name = "estado_por_rango", nullable = false, length = 20, updatable = false)
  private EstadoCondicion estadoPorRango;

  @Enumerated(EnumType.STRING)
  @Column(name = "estado_calculado", nullable = false, length = 20, updatable = false)
  private EstadoCondicion estadoCalculado;

  @Enumerated(EnumType.STRING)
  @Column(name = "estado_confirmado", nullable = false, length = 20, updatable = false)
  private EstadoCondicion estadoConfirmado;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(
      name = "condiciones_anulacion",
      nullable = false,
      columnDefinition = "jsonb",
      updatable = false)
  private JsonNode condicionesAnulacion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
  private JsonNode desglose;

  @Column(name = "hash_entrada", nullable = false, length = 64, updatable = false)
  private String hashEntrada;

  @Column(name = "version_algoritmo", nullable = false, length = 50, updatable = false)
  private String versionAlgoritmo;

  @Column(name = "solicitado_por_id", updatable = false)
  private UUID solicitadoPorId;

  @Column(name = "calculado_en", nullable = false, updatable = false)
  private Instant calculadoEn;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  protected ResultadoIc() {}

  public ResultadoIc(
      UUID id,
      UUID inspeccionId,
      UUID versionConfiguracionId,
      BigDecimal indiceCalculado,
      EstadoCondicion estadoPorRango,
      EstadoCondicion estadoCalculado,
      JsonNode condicionesAnulacion,
      JsonNode desglose,
      String hashEntrada,
      String versionAlgoritmo,
      UUID solicitadoPorId,
      Instant ahora) {
    this.id = id;
    this.inspeccionId = inspeccionId;
    this.versionConfiguracionId = versionConfiguracionId;
    this.tipo = "PUBLICACION";
    this.indiceCalculado = indiceCalculado;
    this.estadoPorRango = estadoPorRango;
    this.estadoCalculado = estadoCalculado;
    this.estadoConfirmado = estadoCalculado;
    this.condicionesAnulacion = condicionesAnulacion.deepCopy();
    this.desglose = desglose.deepCopy();
    this.hashEntrada = hashEntrada;
    this.versionAlgoritmo = versionAlgoritmo;
    this.solicitadoPorId = solicitadoPorId;
    this.calculadoEn = ahora;
    this.creadoEn = ahora;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspeccionId() {
    return inspeccionId;
  }

  public UUID getVersionConfiguracionId() {
    return versionConfiguracionId;
  }

  public String getTipo() {
    return tipo;
  }

  public BigDecimal getIndiceCalculado() {
    return indiceCalculado;
  }

  public EstadoCondicion getEstadoPorRango() {
    return estadoPorRango;
  }

  public EstadoCondicion getEstadoCalculado() {
    return estadoCalculado;
  }

  public EstadoCondicion getEstadoConfirmado() {
    return estadoConfirmado;
  }

  public JsonNode getCondicionesAnulacion() {
    return condicionesAnulacion.deepCopy();
  }

  public JsonNode getDesglose() {
    return desglose.deepCopy();
  }

  public String getHashEntrada() {
    return hashEntrada;
  }

  public String getVersionAlgoritmo() {
    return versionAlgoritmo;
  }

  public UUID getSolicitadoPorId() {
    return solicitadoPorId;
  }

  public Instant getCalculadoEn() {
    return calculadoEn;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
