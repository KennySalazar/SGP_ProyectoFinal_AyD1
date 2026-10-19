package gt.usac.cunoc.sgp.revision.entity;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Ciclo inmutable de contenido enviado y su avance de revisión académica. */
@Entity
@Table(name = "revision")
public class Revision {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inspeccion_id", nullable = false)
  private Inspeccion inspeccion;

  @Column(name = "numero_ciclo", nullable = false)
  private int numeroCiclo;

  @Column(name = "revisor_id", nullable = false)
  private UUID revisorId;

  @Column(nullable = false, length = 20)
  private String estado;

  @Column(length = 30)
  private String veredicto;

  @Column(name = "observacion_general", columnDefinition = "text")
  private String observacionGeneral;

  @Column(precision = 6, scale = 2)
  private BigDecimal calificacion;

  @Column(name = "enviada_en", nullable = false)
  private Instant enviadaEn;

  @Column(name = "iniciada_en")
  private Instant iniciadaEn;

  @Column(name = "resuelta_en")
  private Instant resueltaEn;

  @Column(name = "version_inspeccion_enviada", nullable = false)
  private long versionInspeccionEnviada;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "contenido_enviado", nullable = false)
  private JsonNode contenidoEnviado;

  @Column(name = "hash_contenido", nullable = false, length = 64)
  private String hashContenido;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  protected Revision() {}

  public Revision(
      UUID id,
      Inspeccion inspeccion,
      int numeroCiclo,
      UUID revisorId,
      Instant enviadaEn,
      long versionInspeccionEnviada,
      JsonNode contenidoEnviado,
      String hashContenido) {
    this.id = id;
    this.inspeccion = inspeccion;
    this.numeroCiclo = numeroCiclo;
    this.revisorId = revisorId;
    this.estado = "PENDIENTE";
    this.enviadaEn = enviadaEn;
    this.versionInspeccionEnviada = versionInspeccionEnviada;
    this.contenidoEnviado = contenidoEnviado;
    this.hashContenido = hashContenido;
    this.creadoEn = enviadaEn;
  }

  public void iniciar(Instant ahora) {
    if ("PENDIENTE".equals(estado)) {
      estado = "EN_CURSO";
      iniciadaEn = ahora;
    }
  }

  public UUID getId() {
    return id;
  }

  public Inspeccion getInspeccion() {
    return inspeccion;
  }

  public int getNumeroCiclo() {
    return numeroCiclo;
  }

  public UUID getRevisorId() {
    return revisorId;
  }

  public String getEstado() {
    return estado;
  }

  public Instant getEnviadaEn() {
    return enviadaEn;
  }

  public Instant getIniciadaEn() {
    return iniciadaEn;
  }

  public long getVersionInspeccionEnviada() {
    return versionInspeccionEnviada;
  }

  public JsonNode getContenidoEnviado() {
    return contenidoEnviado;
  }

  public String getHashContenido() {
    return hashContenido;
  }
}
