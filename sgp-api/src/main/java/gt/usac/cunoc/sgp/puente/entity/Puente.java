package gt.usac.cunoc.sgp.puente.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "puente")
public class Puente {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 40, updatable = false)
  private String codigo;

  @Column(name = "correlativo_municipal", nullable = false, updatable = false)
  private int correlativoMunicipal;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "municipio_id", nullable = false)
  private Municipio municipio;

  @Column(nullable = false, length = 200)
  private String nombre;

  @Column(nullable = false, length = 100)
  private String ruta;

  @Column(precision = 10, scale = 3)
  private BigDecimal kilometraje;

  @JdbcTypeCode(SqlTypes.GEOGRAPHY)
  @Column(nullable = false, columnDefinition = "geography(Point,4326)")
  private Point ubicacion;

  @Column(nullable = false)
  private boolean activo;

  @Column(name = "creado_por_id", nullable = false, updatable = false)
  private UUID creadoPorId;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version
  @Column(nullable = false)
  private long version;

  protected Puente() {}

  public Puente(
      UUID id,
      String codigo,
      int correlativoMunicipal,
      Municipio municipio,
      String nombre,
      String ruta,
      BigDecimal kilometraje,
      Point ubicacion,
      UUID creadoPorId,
      Instant creadoEn) {
    this.id = id;
    this.codigo = codigo;
    this.correlativoMunicipal = correlativoMunicipal;
    this.municipio = municipio;
    this.nombre = nombre;
    this.ruta = ruta;
    this.kilometraje = kilometraje;
    this.ubicacion = ubicacion;
    this.activo = true;
    this.creadoPorId = creadoPorId;
    this.creadoEn = creadoEn;
    this.actualizadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public String getCodigo() {
    return codigo;
  }

  public int getCorrelativoMunicipal() {
    return correlativoMunicipal;
  }

  public Municipio getMunicipio() {
    return municipio;
  }

  public String getNombre() {
    return nombre;
  }

  public String getRuta() {
    return ruta;
  }

  public BigDecimal getKilometraje() {
    return kilometraje;
  }

  public Point getUbicacion() {
    return ubicacion;
  }

  public boolean isActivo() {
    return activo;
  }

  public UUID getCreadoPorId() {
    return creadoPorId;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }

  public long getVersion() {
    return version;
  }
}
