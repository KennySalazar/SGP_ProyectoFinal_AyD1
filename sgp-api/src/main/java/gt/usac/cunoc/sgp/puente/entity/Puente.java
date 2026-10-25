package gt.usac.cunoc.sgp.puente.entity;

import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Formula;
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

  @Column(name = "inactivado_en")
  private Instant inactivadoEn;

  @Column(name = "inactivado_por_id")
  private UUID inactivadoPorId;

  @Column(name = "motivo_inactivacion", length = 1000)
  private String motivoInactivacion;

  @Column(name = "estado_actual", nullable = false, length = 20)
  private String estadoActual = "Sin evaluar";

  @Column(name = "indice_condicion_actual", precision = 8, scale = 5)
  private BigDecimal indiceCondicionActual;

  @Column(name = "fecha_ultima_inspeccion")
  private LocalDate fechaUltimaInspeccion;

  @Column(name = "inspeccion_vigente_id")
  private UUID inspeccionVigenteId;

  @Column(name = "resultado_ic_vigente_id")
  private UUID resultadoIcVigenteId;

  @Formula(
      """
          (select r.version_configuracion_id from resultado_ic r
           where r.id = resultado_ic_vigente_id and r.inspeccion_id = inspeccion_vigente_id)
          """)
  private UUID versionFormularioIdIcVigente;

  @Column(name = "inspeccion_vigente_publicada_en")
  private Instant inspeccionVigentePublicadaEn;

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

  public void actualizarEstadoPorPublicacion(
      UUID inspeccionId,
      UUID resultadoId,
      EstadoCondicion estado,
      BigDecimal indice,
      LocalDate fechaInspeccion,
      Instant publicadaEn,
      Instant ahora) {
    Objects.requireNonNull(inspeccionId, "La inspeccion es obligatoria");
    Objects.requireNonNull(resultadoId, "El resultado es obligatorio");
    Objects.requireNonNull(estado, "El estado es obligatorio");
    Objects.requireNonNull(indice, "El indice es obligatorio");
    Objects.requireNonNull(fechaInspeccion, "La fecha de inspeccion es obligatoria");
    Objects.requireNonNull(publicadaEn, "La fecha de publicacion es obligatoria");
    Objects.requireNonNull(ahora, "La fecha de actualizacion es obligatoria");

    if (indice.signum() < 0 || indice.compareTo(BigDecimal.valueOf(100)) > 0) {
      throw new IllegalArgumentException("El indice debe estar entre 0 y 100");
    }

    String estadoFinal =
        switch (estado) {
          case BUENO -> "Bueno";
          case REGULAR -> "Regular";
          case MALO -> "Malo";
        };

    this.estadoActual = estadoFinal;
    this.indiceCondicionActual = indice;
    this.fechaUltimaInspeccion = fechaInspeccion;
    this.inspeccionVigenteId = inspeccionId;
    this.resultadoIcVigenteId = resultadoId;
    this.inspeccionVigentePublicadaEn = publicadaEn;
    this.actualizadoEn = ahora;
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

  public Instant getInactivadoEn() {
    return inactivadoEn;
  }

  public UUID getInactivadoPorId() {
    return inactivadoPorId;
  }

  public String getMotivoInactivacion() {
    return motivoInactivacion;
  }

  public String getEstadoActual() {
    return estadoActual;
  }

  public BigDecimal getIndiceCondicionActual() {
    return indiceCondicionActual;
  }

  public LocalDate getFechaUltimaInspeccion() {
    return fechaUltimaInspeccion;
  }

  public UUID getInspeccionVigenteId() {
    return inspeccionVigenteId;
  }

  public UUID getResultadoIcVigenteId() {
    return resultadoIcVigenteId;
  }

  public UUID getVersionFormularioIdIcVigente() {
    return versionFormularioIdIcVigente;
  }

  public Instant getInspeccionVigentePublicadaEn() {
    return inspeccionVigentePublicadaEn;
  }

  public void darDeBaja(String motivo, UUID usuarioId, Instant ahora) {
    this.activo = false;
    this.motivoInactivacion = motivo;
    this.inactivadoPorId = usuarioId;
    this.inactivadoEn = ahora;
    this.actualizadoEn = ahora;
  }

  public void reactivar(Instant ahora) {
    this.activo = true;
    this.motivoInactivacion = null;
    this.inactivadoPorId = null;
    this.inactivadoEn = null;
    this.actualizadoEn = ahora;
  }

  public void actualizarDatosGenerales(
      String nombre,
      String ruta,
      BigDecimal kilometraje,
      Municipio municipio,
      Point ubicacion,
      Instant ahora) {
    this.nombre = nombre;
    this.ruta = ruta;
    this.kilometraje = kilometraje;
    this.municipio = municipio;
    this.ubicacion = ubicacion;
    this.actualizadoEn = ahora;
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
