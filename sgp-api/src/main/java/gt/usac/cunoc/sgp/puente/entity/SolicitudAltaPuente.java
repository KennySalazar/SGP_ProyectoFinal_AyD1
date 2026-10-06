package gt.usac.cunoc.sgp.puente.entity;

import gt.usac.cunoc.sgp.puente.model.EstadoSolicitudAltaPuente;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "solicitud_alta_puente")
public class SolicitudAltaPuente {

  @Id private UUID id;

  @Column(name = "solicitado_por_id", nullable = false, updatable = false)
  private UUID solicitadoPorId;

  @Column(name = "nombre_propuesto", nullable = false, length = 200)
  private String nombrePropuesto;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "municipio_id", nullable = false)
  private Municipio municipio;

  @Column(nullable = false, length = 100)
  private String ruta;

  @Column(precision = 10, scale = 3)
  private BigDecimal kilometraje;

  @JdbcTypeCode(SqlTypes.GEOGRAPHY)
  @Column(nullable = false, columnDefinition = "geography(Point,4326)")
  private Point ubicacion;

  @Column(columnDefinition = "text")
  private String justificacion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EstadoSolicitudAltaPuente estado;

  @Column(name = "revisado_en")
  private Instant revisadoEn;

  @Column(name = "motivo_decision", columnDefinition = "text")
  private String motivoDecision;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version
  @Column(nullable = false)
  private long version;

  protected SolicitudAltaPuente() {}

  public SolicitudAltaPuente(
      UUID id,
      UUID solicitadoPorId,
      String nombrePropuesto,
      Municipio municipio,
      String ruta,
      BigDecimal kilometraje,
      Point ubicacion,
      String justificacion,
      Instant creadoEn) {
    this.id = id;
    this.solicitadoPorId = solicitadoPorId;
    this.nombrePropuesto = nombrePropuesto;
    this.municipio = municipio;
    this.ruta = ruta;
    this.kilometraje = kilometraje;
    this.ubicacion = ubicacion;
    this.justificacion = justificacion;
    this.estado = EstadoSolicitudAltaPuente.PENDIENTE;
    this.creadoEn = creadoEn;
    this.actualizadoEn = creadoEn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSolicitadoPorId() {
    return solicitadoPorId;
  }

  public String getNombrePropuesto() {
    return nombrePropuesto;
  }

  public Municipio getMunicipio() {
    return municipio;
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

  public String getJustificacion() {
    return justificacion;
  }

  public EstadoSolicitudAltaPuente getEstado() {
    return estado;
  }

  public Instant getRevisadoEn() {
    return revisadoEn;
  }

  public String getMotivoDecision() {
    return motivoDecision;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
