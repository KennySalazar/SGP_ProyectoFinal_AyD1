package gt.usac.cunoc.sgp.formulario.entity;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "version_formulario")
public class VersionFormulario {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 50)
  private String codigo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EstadoVersionFormulario estado;

  @Column(nullable = false)
  private boolean activa;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private JsonNode esquema;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "vinculos_almacenamiento", nullable = false)
  private JsonNode vinculosAlmacenamiento;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "reglas_evaluacion_ic", nullable = false)
  private JsonNode reglasEvaluacionIc;

  @Column(name = "hash_contenido", length = 64)
  private String hashContenido;

  @Column(name = "version_algoritmo_ic", length = 50)
  private String versionAlgoritmoIc;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "version_anterior_id")
  private VersionFormulario versionAnterior;

  @Column(name = "creado_por_id", nullable = false)
  private UUID creadoPorId;

  @Column(name = "publicado_por_id")
  private UUID publicadoPorId;

  @Column(name = "publicado_en")
  private Instant publicadoEn;

  @Column(name = "calibrado_por_id")
  private UUID calibradoPorId;

  @Column(name = "calibrado_en")
  private Instant calibradoEn;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version
  @Column(nullable = false)
  private long version;

  protected VersionFormulario() {}

  public VersionFormulario(
      UUID id,
      String codigo,
      EstadoVersionFormulario estado,
      boolean activa,
      JsonNode esquema,
      JsonNode vinculosAlmacenamiento,
      JsonNode reglasEvaluacionIc,
      VersionFormulario versionAnterior,
      UUID creadoPorId,
      Instant creadoEn,
      Instant actualizadoEn) {
    this.id = id;
    this.codigo = codigo;
    this.estado = estado;
    this.activa = activa;
    this.esquema = esquema;
    this.vinculosAlmacenamiento = vinculosAlmacenamiento;
    this.reglasEvaluacionIc = reglasEvaluacionIc;
    this.versionAnterior = versionAnterior;
    this.creadoPorId = creadoPorId;
    this.creadoEn = creadoEn;
    this.actualizadoEn = actualizadoEn;
  }

  public UUID getId() {
    return id;
  }

  public String getCodigo() {
    return codigo;
  }

  public void setCodigo(String codigo) {
    this.codigo = codigo;
  }

  public EstadoVersionFormulario getEstado() {
    return estado;
  }

  public void setEstado(EstadoVersionFormulario estado) {
    this.estado = estado;
  }

  public boolean isActiva() {
    return activa;
  }

  public void setActiva(boolean activa) {
    this.activa = activa;
  }

  public JsonNode getEsquema() {
    return esquema;
  }

  public void setEsquema(JsonNode esquema) {
    this.esquema = esquema;
  }

  public JsonNode getVinculosAlmacenamiento() {
    return vinculosAlmacenamiento;
  }

  public void setVinculosAlmacenamiento(JsonNode vinculosAlmacenamiento) {
    this.vinculosAlmacenamiento = vinculosAlmacenamiento;
  }

  public JsonNode getReglasEvaluacionIc() {
    return reglasEvaluacionIc;
  }

  public void setReglasEvaluacionIc(JsonNode reglasEvaluacionIc) {
    this.reglasEvaluacionIc = reglasEvaluacionIc;
  }

  public String getHashContenido() {
    return hashContenido;
  }

  public void setHashContenido(String hashContenido) {
    this.hashContenido = hashContenido;
  }

  public String getVersionAlgoritmoIc() {
    return versionAlgoritmoIc;
  }

  public void setVersionAlgoritmoIc(String versionAlgoritmoIc) {
    this.versionAlgoritmoIc = versionAlgoritmoIc;
  }

  public VersionFormulario getVersionAnterior() {
    return versionAnterior;
  }

  public void setVersionAnterior(VersionFormulario versionAnterior) {
    this.versionAnterior = versionAnterior;
  }

  public UUID getCreadoPorId() {
    return creadoPorId;
  }

  public UUID getPublicadoPorId() {
    return publicadoPorId;
  }

  public void setPublicadoPorId(UUID publicadoPorId) {
    this.publicadoPorId = publicadoPorId;
  }

  public Instant getPublicadoEn() {
    return publicadoEn;
  }

  public void setPublicadoEn(Instant publicadoEn) {
    this.publicadoEn = publicadoEn;
  }

  public UUID getCalibradoPorId() {
    return calibradoPorId;
  }

  public void setCalibradoPorId(UUID calibradoPorId) {
    this.calibradoPorId = calibradoPorId;
  }

  public Instant getCalibradoEn() {
    return calibradoEn;
  }

  public void setCalibradoEn(Instant calibradoEn) {
    this.calibradoEn = calibradoEn;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }

  public void setActualizadoEn(Instant actualizadoEn) {
    this.actualizadoEn = actualizadoEn;
  }

  public long getVersion() {
    return version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VersionFormulario that = (VersionFormulario) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
