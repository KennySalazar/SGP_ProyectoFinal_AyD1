package gt.usac.cunoc.sgp.inspeccion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inspeccion_dato_tecnico")
public class InspeccionDatoTecnico {

  @Id
  @Column(name = "inspeccion_id")
  private UUID inspeccionId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inspeccion_id")
  private Inspeccion inspeccion;

  @Column(name = "longitud_m", precision = 10, scale = 2)
  private BigDecimal longitudM;

  @Column(name = "numero_tramos")
  private Integer numeroTramos;

  @Column(name = "ancho_rodadura_m", precision = 10, scale = 2)
  private BigDecimal anchoRodaduraM;

  @Column(name = "ancho_acera_derecha_m", precision = 10, scale = 2)
  private BigDecimal anchoAceraDerechaM;

  @Column(name = "ancho_acera_izquierda_m", precision = 10, scale = 2)
  private BigDecimal anchoAceraIzquierdaM;

  @Column(name = "altura_libre_superior_m", precision = 10, scale = 2)
  private BigDecimal alturaLibreSuperiorM;

  @Column(name = "altura_libre_sobre_cauce_m", precision = 10, scale = 2)
  private BigDecimal alturaLibreSobreCauceM;

  @Column(name = "tipologia_puente", length = 100)
  private String tipologiaPuente;

  @Column(name = "tipo_cruce", length = 100)
  private String tipoCruce;

  @Column(name = "numero_vias_por_sentido")
  private Integer numeroViasPorSentido;

  @Column(name = "material_superestructura", length = 100)
  private String materialSuperestructura;

  @Column(name = "material_subestructura", length = 100)
  private String materialSubestructura;

  @Column(name = "carga_diseno", length = 100)
  private String cargaDiseno;

  @Column(name = "anio_construccion")
  private Integer anioConstruccion;

  @Column(name = "trafico_vehiculos_dia")
  private Integer traficoVehiculosDia;

  @Column(name = "porcentaje_camiones_buses", precision = 5, scale = 2)
  private BigDecimal porcentajeCamionesBuses;

  @Column(name = "ruta_pavimentada", length = 10)
  private String rutaPavimentada;

  @Column(name = "alineamiento_horizontal", length = 15)
  private String alineamientoHorizontal;

  @Column(name = "esviaje")
  private Boolean esviaje;

  @Column(name = "poblacion_antes", length = 200)
  private String poblacionAntes;

  @Column(name = "poblacion_despues", length = 200)
  private String poblacionDespues;

  @Column(name = "tipo_cuerpo_agua", length = 50)
  private String tipoCuerpoAgua;

  @Column(name = "nombre_rio", length = 200)
  private String nombreRio;

  @Column(name = "encauzamiento", length = 20)
  private String encauzamiento;

  @Column(name = "antecedente_desbordamiento")
  private Boolean antecedenteDesbordamiento;

  @Column(name = "frecuencia_desbordamiento_anios", precision = 8, scale = 2)
  private BigDecimal frecuenciaDesbordamientoAnios;

  @Column(name = "fecha_ultimo_desbordamiento")
  private LocalDate fechaUltimoDesbordamiento;

  @Column(name = "alumbrado_existe")
  private Boolean alumbradoExiste;

  @Column(name = "drenajes_aledanos_existen")
  private Boolean drenajesAledanosExisten;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version
  @Column(nullable = false)
  private long version;

  public InspeccionDatoTecnico() {}

  public InspeccionDatoTecnico(Inspeccion inspeccion, Instant ahora) {
    this.inspeccion = inspeccion;
    this.creadoEn = ahora;
    this.actualizadoEn = ahora;
  }

  public UUID getInspeccionId() {
    return inspeccionId;
  }

  public Inspeccion getInspeccion() {
    return inspeccion;
  }

  public BigDecimal getLongitudM() {
    return longitudM;
  }

  public void setLongitudM(BigDecimal longitudM) {
    this.longitudM = longitudM;
  }

  public Integer getNumeroTramos() {
    return numeroTramos;
  }

  public void setNumeroTramos(Integer numeroTramos) {
    this.numeroTramos = numeroTramos;
  }

  public BigDecimal getAnchoRodaduraM() {
    return anchoRodaduraM;
  }

  public void setAnchoRodaduraM(BigDecimal anchoRodaduraM) {
    this.anchoRodaduraM = anchoRodaduraM;
  }

  public BigDecimal getAnchoAceraDerechaM() {
    return anchoAceraDerechaM;
  }

  public void setAnchoAceraDerechaM(BigDecimal anchoAceraDerechaM) {
    this.anchoAceraDerechaM = anchoAceraDerechaM;
  }

  public BigDecimal getAnchoAceraIzquierdaM() {
    return anchoAceraIzquierdaM;
  }

  public void setAnchoAceraIzquierdaM(BigDecimal anchoAceraIzquierdaM) {
    this.anchoAceraIzquierdaM = anchoAceraIzquierdaM;
  }

  public BigDecimal getAlturaLibreSuperiorM() {
    return alturaLibreSuperiorM;
  }

  public void setAlturaLibreSuperiorM(BigDecimal alturaLibreSuperiorM) {
    this.alturaLibreSuperiorM = alturaLibreSuperiorM;
  }

  public BigDecimal getAlturaLibreSobreCauceM() {
    return alturaLibreSobreCauceM;
  }

  public void setAlturaLibreSobreCauceM(BigDecimal alturaLibreSobreCauceM) {
    this.alturaLibreSobreCauceM = alturaLibreSobreCauceM;
  }

  public String getTipologiaPuente() {
    return tipologiaPuente;
  }

  public void setTipologiaPuente(String tipologiaPuente) {
    this.tipologiaPuente = tipologiaPuente;
  }

  public String getTipoCruce() {
    return tipoCruce;
  }

  public void setTipoCruce(String tipoCruce) {
    this.tipoCruce = tipoCruce;
  }

  public Integer getNumeroViasPorSentido() {
    return numeroViasPorSentido;
  }

  public void setNumeroViasPorSentido(Integer numeroViasPorSentido) {
    this.numeroViasPorSentido = numeroViasPorSentido;
  }

  public String getMaterialSuperestructura() {
    return materialSuperestructura;
  }

  public void setMaterialSuperestructura(String materialSuperestructura) {
    this.materialSuperestructura = materialSuperestructura;
  }

  public String getMaterialSubestructura() {
    return materialSubestructura;
  }

  public void setMaterialSubestructura(String materialSubestructura) {
    this.materialSubestructura = materialSubestructura;
  }

  public String getCargaDiseno() {
    return cargaDiseno;
  }

  public void setCargaDiseno(String cargaDiseno) {
    this.cargaDiseno = cargaDiseno;
  }

  public Integer getAnioConstruccion() {
    return anioConstruccion;
  }

  public void setAnioConstruccion(Integer anioConstruccion) {
    this.anioConstruccion = anioConstruccion;
  }

  public Integer getTraficoVehiculosDia() {
    return traficoVehiculosDia;
  }

  public void setTraficoVehiculosDia(Integer traficoVehiculosDia) {
    this.traficoVehiculosDia = traficoVehiculosDia;
  }

  public BigDecimal getPorcentajeCamionesBuses() {
    return porcentajeCamionesBuses;
  }

  public void setPorcentajeCamionesBuses(BigDecimal porcentajeCamionesBuses) {
    this.porcentajeCamionesBuses = porcentajeCamionesBuses;
  }

  public String getRutaPavimentada() {
    return rutaPavimentada;
  }

  public void setRutaPavimentada(String rutaPavimentada) {
    this.rutaPavimentada = rutaPavimentada;
  }

  public String getAlineamientoHorizontal() {
    return alineamientoHorizontal;
  }

  public void setAlineamientoHorizontal(String alineamientoHorizontal) {
    this.alineamientoHorizontal = alineamientoHorizontal;
  }

  public Boolean getEsviaje() {
    return esviaje;
  }

  public void setEsviaje(Boolean esviaje) {
    this.esviaje = esviaje;
  }

  public String getPoblacionAntes() {
    return poblacionAntes;
  }

  public void setPoblacionAntes(String poblacionAntes) {
    this.poblacionAntes = poblacionAntes;
  }

  public String getPoblacionDespues() {
    return poblacionDespues;
  }

  public void setPoblacionDespues(String poblacionDespues) {
    this.poblacionDespues = poblacionDespues;
  }

  public String getTipoCuerpoAgua() {
    return tipoCuerpoAgua;
  }

  public void setTipoCuerpoAgua(String tipoCuerpoAgua) {
    this.tipoCuerpoAgua = tipoCuerpoAgua;
  }

  public String getNombreRio() {
    return nombreRio;
  }

  public void setNombreRio(String nombreRio) {
    this.nombreRio = nombreRio;
  }

  public String getEncauzamiento() {
    return encauzamiento;
  }

  public void setEncauzamiento(String encauzamiento) {
    this.encauzamiento = encauzamiento;
  }

  public Boolean getAntecedenteDesbordamiento() {
    return antecedenteDesbordamiento;
  }

  public void setAntecedenteDesbordamiento(Boolean antecedenteDesbordamiento) {
    this.antecedenteDesbordamiento = antecedenteDesbordamiento;
  }

  public BigDecimal getFrecuenciaDesbordamientoAnios() {
    return frecuenciaDesbordamientoAnios;
  }

  public void setFrecuenciaDesbordamientoAnios(BigDecimal frecuenciaDesbordamientoAnios) {
    this.frecuenciaDesbordamientoAnios = frecuenciaDesbordamientoAnios;
  }

  public LocalDate getFechaUltimoDesbordamiento() {
    return fechaUltimoDesbordamiento;
  }

  public void setFechaUltimoDesbordamiento(LocalDate fechaUltimoDesbordamiento) {
    this.fechaUltimoDesbordamiento = fechaUltimoDesbordamiento;
  }

  public Boolean getAlumbradoExiste() {
    return alumbradoExiste;
  }

  public void setAlumbradoExiste(Boolean alumbradoExiste) {
    this.alumbradoExiste = alumbradoExiste;
  }

  public Boolean getDrenajesAledanosExisten() {
    return drenajesAledanosExisten;
  }

  public void setDrenajesAledanosExisten(Boolean drenajesAledanosExisten) {
    this.drenajesAledanosExisten = drenajesAledanosExisten;
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

  public void copiarDatosPermanentesDe(InspeccionDatoTecnico fuente) {
    if (fuente == null) return;
    this.longitudM = fuente.longitudM;
    this.numeroTramos = fuente.numeroTramos;
    this.anchoRodaduraM = fuente.anchoRodaduraM;
    this.anchoAceraDerechaM = fuente.anchoAceraDerechaM;
    this.anchoAceraIzquierdaM = fuente.anchoAceraIzquierdaM;
    this.alturaLibreSuperiorM = fuente.alturaLibreSuperiorM;
    this.alturaLibreSobreCauceM = fuente.alturaLibreSobreCauceM;
    this.tipologiaPuente = fuente.tipologiaPuente;
    this.tipoCruce = fuente.tipoCruce;
    this.numeroViasPorSentido = fuente.numeroViasPorSentido;
    this.materialSuperestructura = fuente.materialSuperestructura;
    this.materialSubestructura = fuente.materialSubestructura;
    this.cargaDiseno = fuente.cargaDiseno;
    this.anioConstruccion = fuente.anioConstruccion;
    this.rutaPavimentada = fuente.rutaPavimentada;
    this.alineamientoHorizontal = fuente.alineamientoHorizontal;
    this.esviaje = fuente.esviaje;
    this.poblacionAntes = fuente.poblacionAntes;
    this.poblacionDespues = fuente.poblacionDespues;
    this.tipoCuerpoAgua = fuente.tipoCuerpoAgua;
    this.nombreRio = fuente.nombreRio;
    this.encauzamiento = fuente.encauzamiento;
    this.alumbradoExiste = fuente.alumbradoExiste;
    this.drenajesAledanosExisten = fuente.drenajesAledanosExisten;
  }
}
