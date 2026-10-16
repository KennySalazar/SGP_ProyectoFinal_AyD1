package gt.usac.cunoc.sgp.inspeccion.entity;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "inspeccion")
public class Inspeccion {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "puente_id", nullable = false)
  private Puente puente;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "version_formulario_id", nullable = false)
  private VersionFormulario versionFormulario;

  @Column(name = "autor_id", nullable = false)
  private UUID autorId;

  @Column(name = "rol_autor_creacion", nullable = false, length = 40)
  private String rolAutorCreacion;

  @Column(name = "nombre_autor_capturado", nullable = false, length = 200)
  private String nombreAutorCapturado;

  @Column(name = "asignacion_puente_id")
  private UUID asignacionPuenteId;

  @Column(name = "preparacion_salida_id")
  private UUID preparacionSalidaId;

  @Column(name = "inspeccion_base_id")
  private UUID inspeccionBaseId;

  @Column(nullable = false, length = 20)
  private String tipo = "ORIGINAL";

  @Column(name = "supersede_a_id")
  private UUID supersedeAId;

  @Column(name = "motivo_correccion")
  private String motivoCorreccion;

  @Column(nullable = false, length = 30)
  private String estado = "BORRADOR";

  @Column(name = "fecha_inspeccion", nullable = false)
  private LocalDate fechaInspeccion;

  @Column(name = "dispositivo_id", nullable = false, length = 100)
  private String dispositivoId;

  @JdbcTypeCode(SqlTypes.GEOGRAPHY)
  @Column(name = "ubicacion_inicio", columnDefinition = "geography(Point,4326)")
  private Point ubicacionInicio;

  @Column(name = "precision_gps_m", precision = 10, scale = 2)
  private BigDecimal precisionGpsM;

  @Column(name = "distancia_al_puente_m", precision = 12, scale = 2)
  private BigDecimal distanciaAlPuenteM;

  @Column(name = "datos_permanentes_confirmados_en")
  private Instant datosPermanentesConfirmadosEn;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "identificacion_capturada")
  private JsonNode identificacionCapturada;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "datos", nullable = false)
  private JsonNode datos;

  @Column(name = "creado_dispositivo_en")
  private Instant creadoDispositivoEn;

  @Column(name = "enviado_dispositivo_en")
  private Instant enviadoDispositivoEn;

  @Column(name = "sincronizado_en")
  private Instant sincronizadoEn;

  @Column(name = "publicado_en")
  private Instant publicadoEn;

  @Column(name = "ultima_actividad_en", nullable = false)
  private Instant ultimaActividadEn;

  @Column(name = "eliminado_en")
  private Instant eliminadoEn;

  @Column(name = "eliminado_por_id")
  private UUID eliminadoPorId;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version
  @Column(nullable = false)
  private long version;

  @OneToMany(mappedBy = "inspeccion", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<InspeccionAcompanante> acompanantes = new ArrayList<>();

  @OneToOne(mappedBy = "inspeccion", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private InspeccionDatoTecnico datoTecnico;

  protected Inspeccion() {}

  public Inspeccion(
      UUID id,
      Puente puente,
      VersionFormulario versionFormulario,
      UUID autorId,
      String rolAutorCreacion,
      String nombreAutorCapturado,
      LocalDate fechaInspeccion,
      String dispositivoId,
      Point ubicacionInicio,
      BigDecimal precisionGpsM,
      BigDecimal distanciaAlPuenteM,
      Instant datosPermanentesConfirmadosEn,
      JsonNode datos,
      Instant ahora) {
    this.id = id;
    this.puente = puente;
    this.versionFormulario = versionFormulario;
    this.autorId = autorId;
    this.rolAutorCreacion = rolAutorCreacion;
    this.nombreAutorCapturado = nombreAutorCapturado;
    this.fechaInspeccion = fechaInspeccion;
    this.dispositivoId = dispositivoId;
    this.ubicacionInicio = ubicacionInicio;
    this.precisionGpsM = precisionGpsM;
    this.distanciaAlPuenteM = distanciaAlPuenteM;
    this.datosPermanentesConfirmadosEn = datosPermanentesConfirmadosEn;
    this.datos = datos;
    this.tipo = "ORIGINAL";
    this.estado = "BORRADOR";
    this.ultimaActividadEn = ahora;
    this.creadoEn = ahora;
    this.actualizadoEn = ahora;
  }

  public UUID getId() {
    return id;
  }

  public Puente getPuente() {
    return puente;
  }

  public VersionFormulario getVersionFormulario() {
    return versionFormulario;
  }

  public UUID getAutorId() {
    return autorId;
  }

  public String getRolAutorCreacion() {
    return rolAutorCreacion;
  }

  public String getNombreAutorCapturado() {
    return nombreAutorCapturado;
  }

  public UUID getAsignacionPuenteId() {
    return asignacionPuenteId;
  }

  public UUID getPreparacionSalidaId() {
    return preparacionSalidaId;
  }

  public UUID getInspeccionBaseId() {
    return inspeccionBaseId;
  }

  public String getTipo() {
    return tipo;
  }

  public UUID getSupersedeAId() {
    return supersedeAId;
  }

  public String getMotivoCorreccion() {
    return motivoCorreccion;
  }

  public String getEstado() {
    return estado;
  }

  public LocalDate getFechaInspeccion() {
    return fechaInspeccion;
  }

  public String getDispositivoId() {
    return dispositivoId;
  }

  public Point getUbicacionInicio() {
    return ubicacionInicio;
  }

  public BigDecimal getPrecisionGpsM() {
    return precisionGpsM;
  }

  public BigDecimal getDistanciaAlPuenteM() {
    return distanciaAlPuenteM;
  }

  public Instant getDatosPermanentesConfirmadosEn() {
    return datosPermanentesConfirmadosEn;
  }

  public JsonNode getIdentificacionCapturada() {
    return identificacionCapturada;
  }

  public JsonNode getDatos() {
    return datos;
  }

  public Instant getCreadoDispositivoEn() {
    return creadoDispositivoEn;
  }

  public Instant getEnviadoDispositivoEn() {
    return enviadoDispositivoEn;
  }

  public Instant getSincronizadoEn() {
    return sincronizadoEn;
  }

  public Instant getPublicadoEn() {
    return publicadoEn;
  }

  public Instant getUltimaActividadEn() {
    return ultimaActividadEn;
  }

  public Instant getEliminadoEn() {
    return eliminadoEn;
  }

  public UUID getEliminadoPorId() {
    return eliminadoPorId;
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

  public List<InspeccionAcompanante> getAcompanantes() {
    return acompanantes;
  }

  public InspeccionDatoTecnico getDatoTecnico() {
    return datoTecnico;
  }

  public void setDatoTecnico(InspeccionDatoTecnico datoTecnico) {
    this.datoTecnico = datoTecnico;
  }

  public void agregarAcompanante(InspeccionAcompanante acompanante) {
    this.acompanantes.add(acompanante);
  }

  public void actualizarBorrador(
      JsonNode datos,
      JsonNode identificacionCapturada,
      LocalDate fechaInspeccion,
      Instant ahora) {
    if (datos != null) {
      this.datos = datos;
    }
    if (identificacionCapturada != null) {
      this.identificacionCapturada = identificacionCapturada;
    }
    if (fechaInspeccion != null) {
      this.fechaInspeccion = fechaInspeccion;
    }
    this.ultimaActividadEn = ahora;
    this.actualizadoEn = ahora;
  }

  public void reemplazarAcompanantes(List<InspeccionAcompanante> nuevosAcompanantes) {
    this.acompanantes.clear();
    if (nuevosAcompanantes != null) {
      this.acompanantes.addAll(nuevosAcompanantes);
    }
  }
}
