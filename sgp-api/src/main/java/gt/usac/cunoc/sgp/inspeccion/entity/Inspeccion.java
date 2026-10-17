package gt.usac.cunoc.sgp.inspeccion.entity;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoInspeccion;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
  private Long version;

  @OneToMany(mappedBy = "inspeccion", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<InspeccionAcompanante> acompanantes = new ArrayList<>();

  @OneToOne(mappedBy = "inspeccion", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private InspeccionDatoTecnico datoTecnico;

  public Inspeccion() {}

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

  public Inspeccion(
      Puente puente,
      VersionFormulario versionFormulario,
      UUID autorId,
      String rolAutorCreacion,
      String nombreAutorCapturado,
      LocalDate fechaInspeccion,
      String dispositivoId,
      JsonNode datos) {
    this(
        UuidV7Generator.generate(),
        puente,
        versionFormulario,
        autorId,
        rolAutorCreacion,
        nombreAutorCapturado,
        fechaInspeccion,
        dispositivoId != null ? dispositivoId : "web",
        null,
        null,
        null,
        null,
        datos,
        Instant.now());
  }

  /**
   * Nueva inspección de tipo CORRECCION en BORRADOR, relacionada con la original publicada mediante
   * supersede_a (RN-INS-08). Parte de los datos de la original porque corrige la misma visita; la
   * original no se modifica.
   */
  public static Inspeccion correccionDe(
      Inspeccion original,
      UUID autorId,
      String rolAutor,
      String nombreAutor,
      String motivo,
      Instant ahora) {
    Inspeccion correccion =
        new Inspeccion(
            UuidV7Generator.generate(),
            original.getPuente(),
            original.getVersionFormulario(),
            autorId,
            rolAutor,
            nombreAutor,
            original.getFechaInspeccion(),
            "web",
            null,
            null,
            null,
            null,
            original.getDatos() == null ? null : original.getDatos().deepCopy(),
            ahora);
    correccion.tipo = "CORRECCION";
    correccion.supersedeAId = original.getId();
    correccion.motivoCorreccion = motivo;
    correccion.identificacionCapturada =
        original.getIdentificacionCapturada() == null
            ? null
            : original.getIdentificacionCapturada().deepCopy();
    return correccion;
  }

  @PrePersist
  public void alPersistir() {
    if (this.id == null) {
      this.id = UuidV7Generator.generate();
    }
    Instant ahora = Instant.now();
    if (this.creadoEn == null) {
      this.creadoEn = ahora;
    }
    if (this.actualizadoEn == null) {
      this.actualizadoEn = ahora;
    }
    if (this.ultimaActividadEn == null) {
      this.ultimaActividadEn = ahora;
    }
  }

  @PreUpdate
  public void alActualizar() {
    Instant ahora = Instant.now();
    this.actualizadoEn = ahora;
    this.ultimaActividadEn = ahora;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Puente getPuente() {
    return puente;
  }

  public void setPuente(Puente puente) {
    this.puente = puente;
  }

  public VersionFormulario getVersionFormulario() {
    return versionFormulario;
  }

  public void setVersionFormulario(VersionFormulario versionFormulario) {
    this.versionFormulario = versionFormulario;
  }

  public UUID getAutorId() {
    return autorId;
  }

  public void setAutorId(UUID autorId) {
    this.autorId = autorId;
  }

  public String getRolAutorCreacion() {
    return rolAutorCreacion;
  }

  public void setRolAutorCreacion(String rolAutorCreacion) {
    this.rolAutorCreacion = rolAutorCreacion;
  }

  public String getNombreAutorCapturado() {
    return nombreAutorCapturado;
  }

  public void setNombreAutorCapturado(String nombreAutorCapturado) {
    this.nombreAutorCapturado = nombreAutorCapturado;
  }

  public UUID getAsignacionPuenteId() {
    return asignacionPuenteId;
  }

  public void setAsignacionPuenteId(UUID asignacionPuenteId) {
    this.asignacionPuenteId = asignacionPuenteId;
  }

  public UUID getPreparacionSalidaId() {
    return preparacionSalidaId;
  }

  public void setPreparacionSalidaId(UUID preparacionSalidaId) {
    this.preparacionSalidaId = preparacionSalidaId;
  }

  public UUID getInspeccionBaseId() {
    return inspeccionBaseId;
  }

  public void setInspeccionBaseId(UUID inspeccionBaseId) {
    this.inspeccionBaseId = inspeccionBaseId;
  }

  public String getTipo() {
    return tipo;
  }

  public void setTipo(String tipo) {
    this.tipo = tipo;
  }

  public UUID getSupersedeAId() {
    return supersedeAId;
  }

  public void setSupersedeAId(UUID supersedeAId) {
    this.supersedeAId = supersedeAId;
  }

  public String getMotivoCorreccion() {
    return motivoCorreccion;
  }

  public void setMotivoCorreccion(String motivoCorreccion) {
    this.motivoCorreccion = motivoCorreccion;
  }

  public String getEstado() {
    return estado;
  }

  /**
   * Cambia el estado. Solo debe invocarlo {@code TransicionInspeccionService}, la única puerta de
   * entrada que valida la transición y registra el historial (DT-BE-07).
   */
  public void aplicarEstado(EstadoInspeccion nuevo, Instant ahora) {
    this.estado = nuevo.name();
    if (nuevo == EstadoInspeccion.ENVIADA) {
      this.enviadoDispositivoEn = ahora;
    } else if (nuevo == EstadoInspeccion.PUBLICADA) {
      this.publicadoEn = ahora;
    }
    this.ultimaActividadEn = ahora;
    this.actualizadoEn = ahora;
  }

  public EstadoInspeccion estadoActual() {
    return EstadoInspeccion.valueOf(estado);
  }

  public LocalDate getFechaInspeccion() {
    return fechaInspeccion;
  }

  public void setFechaInspeccion(LocalDate fechaInspeccion) {
    this.fechaInspeccion = fechaInspeccion;
  }

  public String getDispositivoId() {
    return dispositivoId;
  }

  public void setDispositivoId(String dispositivoId) {
    this.dispositivoId = dispositivoId;
  }

  public Point getUbicacionInicio() {
    return ubicacionInicio;
  }

  public void setUbicacionInicio(Point ubicacionInicio) {
    this.ubicacionInicio = ubicacionInicio;
  }

  public BigDecimal getPrecisionGpsM() {
    return precisionGpsM;
  }

  public void setPrecisionGpsM(BigDecimal precisionGpsM) {
    this.precisionGpsM = precisionGpsM;
  }

  public BigDecimal getDistanciaAlPuenteM() {
    return distanciaAlPuenteM;
  }

  public void setDistanciaAlPuenteM(BigDecimal distanciaAlPuenteM) {
    this.distanciaAlPuenteM = distanciaAlPuenteM;
  }

  public Instant getDatosPermanentesConfirmadosEn() {
    return datosPermanentesConfirmadosEn;
  }

  public void setDatosPermanentesConfirmadosEn(Instant datosPermanentesConfirmadosEn) {
    this.datosPermanentesConfirmadosEn = datosPermanentesConfirmadosEn;
  }

  public JsonNode getIdentificacionCapturada() {
    return identificacionCapturada;
  }

  public void setIdentificacionCapturada(JsonNode identificacionCapturada) {
    this.identificacionCapturada = identificacionCapturada;
  }

  public JsonNode getDatos() {
    return datos;
  }

  public void setDatos(JsonNode datos) {
    this.datos = datos;
  }

  public Instant getCreadoDispositivoEn() {
    return creadoDispositivoEn;
  }

  public void setCreadoDispositivoEn(Instant creadoDispositivoEn) {
    this.creadoDispositivoEn = creadoDispositivoEn;
  }

  public Instant getEnviadoDispositivoEn() {
    return enviadoDispositivoEn;
  }

  public void setEnviadoDispositivoEn(Instant enviadoDispositivoEn) {
    this.enviadoDispositivoEn = enviadoDispositivoEn;
  }

  public Instant getSincronizadoEn() {
    return sincronizadoEn;
  }

  public void setSincronizadoEn(Instant sincronizadoEn) {
    this.sincronizadoEn = sincronizadoEn;
  }

  public Instant getPublicadoEn() {
    return publicadoEn;
  }

  public Instant getUltimaActividadEn() {
    return ultimaActividadEn;
  }

  public void setUltimaActividadEn(Instant ultimaActividadEn) {
    this.ultimaActividadEn = ultimaActividadEn;
  }

  public Instant getEliminadoEn() {
    return eliminadoEn;
  }

  public void setEliminadoEn(Instant eliminadoEn) {
    this.eliminadoEn = eliminadoEn;
  }

  public UUID getEliminadoPorId() {
    return eliminadoPorId;
  }

  public void setEliminadoPorId(UUID eliminadoPorId) {
    this.eliminadoPorId = eliminadoPorId;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public void setCreadoEn(Instant creadoEn) {
    this.creadoEn = creadoEn;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
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
      JsonNode datos, JsonNode identificacionCapturada, LocalDate fechaInspeccion, Instant ahora) {
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

  public void marcarEliminado(UUID usuarioId, Instant ahora) {
    this.eliminadoEn = ahora;
    this.eliminadoPorId = usuarioId;
    this.actualizadoEn = ahora;
  }

  public void setActualizadoEn(Instant actualizadoEn) {
    this.actualizadoEn = actualizadoEn;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Inspeccion that)) return false;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
