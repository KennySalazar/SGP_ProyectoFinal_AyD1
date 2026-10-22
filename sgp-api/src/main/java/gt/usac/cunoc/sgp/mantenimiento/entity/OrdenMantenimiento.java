package gt.usac.cunoc.sgp.mantenimiento.entity;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.PrioridadOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Orden de mantenimiento de un puente (RN-MTO-01, RN-MTO-03). Su estado solo cambia mediante el
 * servicio de transiciones (DT-BE-07) y nunca se elimina (RN-MTO-09, DT-BD-11).
 */
@Entity
@Table(name = "orden_mantenimiento")
public class OrdenMantenimiento {

  @Id private UUID id;

  @Column(name = "puente_id", nullable = false, updatable = false)
  private UUID puenteId;

  @Column(name = "inspeccion_origen_id", updatable = false)
  private UUID inspeccionOrigenId;

  @Column(name = "regla_origen_id", updatable = false)
  private UUID reglaOrigenId;

  /**
   * Verdadero si la orden nació de una regla todavía sin validar (HU-049); nulo si no tiene regla.
   * Se calcula al leer la orden y nunca se escribe.
   */
  @Formula(
      "(SELECT r.validado_por_id IS NULL FROM regla_mantenimiento r WHERE r.id = regla_origen_id)")
  private Boolean reglaProvisional;

  @Column(name = "resultado_ic_origen_id", updatable = false)
  private UUID resultadoIcOrigenId;

  @Column(name = "plan_mantenimiento_rutinario_id", updatable = false)
  private UUID planMantenimientoRutinarioId;

  @Column(name = "ciclo_plan", updatable = false)
  private Integer cicloPlan;

  @Column(name = "elemento_ref", length = 300, updatable = false)
  private String elementoRef;

  @Column(name = "instancia_elemento_id", updatable = false)
  private UUID instanciaElementoId;

  @Column(name = "clave_generacion", length = 200, updatable = false)
  private String claveGeneracion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false)
  private TipoOrden tipo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EstadoOrden estado;

  @Column(nullable = false, columnDefinition = "text")
  private String descripcion;

  @Enumerated(EnumType.STRING)
  @Column(name = "prioridad_sugerida", nullable = false, length = 20, updatable = false)
  private PrioridadOrden prioridadSugerida;

  @Enumerated(EnumType.STRING)
  @Column(name = "prioridad_confirmada", nullable = false, length = 20)
  private PrioridadOrden prioridadConfirmada;

  @Column(name = "justificacion_prioridad", columnDefinition = "text")
  private String justificacionPrioridad;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "fundamento_generacion", updatable = false)
  private JsonNode fundamentoGeneracion;

  @Column(name = "creado_por_id", updatable = false)
  private UUID creadoPorId;

  @Column(name = "aceptado_por_id")
  private UUID aceptadoPorId;

  @Column(name = "aceptado_en")
  private Instant aceptadoEn;

  @Column(name = "fecha_programada")
  private LocalDate fechaProgramada;

  @Column(name = "motivo_cierre_sin_ejecucion", columnDefinition = "text")
  private String motivoCierreSinEjecucion;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @Column(name = "actualizado_en", nullable = false)
  private Instant actualizadoEn;

  @Version
  @Column(nullable = false)
  private Long version;

  protected OrdenMantenimiento() {}

  /**
   * Orden creada manualmente por un Catedrático o un Administrador. Nace en PROPUESTA y su
   * prioridad confirmada empieza igual a la sugerida (RN-MTO-01).
   */
  public OrdenMantenimiento(
      UUID id,
      UUID puenteId,
      UUID inspeccionOrigenId,
      String elementoRef,
      UUID instanciaElementoId,
      TipoOrden tipo,
      String descripcion,
      PrioridadOrden prioridad,
      UUID creadoPorId,
      Instant ahora) {
    this.id = id;
    this.puenteId = puenteId;
    this.inspeccionOrigenId = inspeccionOrigenId;
    this.elementoRef = elementoRef;
    this.instanciaElementoId = instanciaElementoId;
    this.tipo = tipo;
    this.estado = EstadoOrden.PROPUESTA;
    this.descripcion = descripcion;
    this.prioridadSugerida = prioridad;
    this.prioridadConfirmada = prioridad;
    this.creadoPorId = creadoPorId;
    this.creadoEn = ahora;
    this.actualizadoEn = ahora;
  }

  /**
   * Propuesta generada por el sistema a partir de una inspección publicada (HU-046, RN-MTO-02).
   * Nace en PROPUESTA, sin autor humano, con la regla que la originó y la clave que impide generar
   * dos veces la misma. La prioridad sugerida y la confirmada empiezan iguales.
   */
  public static OrdenMantenimiento propuestaGenerada(
      UUID id,
      UUID puenteId,
      UUID inspeccionOrigenId,
      UUID reglaOrigenId,
      UUID resultadoIcOrigenId,
      String elementoRef,
      UUID instanciaElementoId,
      String claveGeneracion,
      TipoOrden tipo,
      String descripcion,
      PrioridadOrden prioridad,
      JsonNode fundamentoGeneracion,
      Instant ahora) {
    OrdenMantenimiento orden =
        new OrdenMantenimiento(
            id,
            puenteId,
            inspeccionOrigenId,
            elementoRef,
            instanciaElementoId,
            tipo,
            descripcion,
            prioridad,
            null,
            ahora);
    orden.reglaOrigenId = reglaOrigenId;
    orden.resultadoIcOrigenId = resultadoIcOrigenId;
    orden.claveGeneracion = claveGeneracion;
    orden.fundamentoGeneracion = fundamentoGeneracion;
    return orden;
  }

  /**
   * Cambia el estado. Solo lo invoca {@code TransicionOrdenService}, después de validar la
   * transición con la máquina de estados (DT-BE-07).
   */
  public void aplicarTransicion(EstadoOrden destino, Instant ahora) {
    this.estado = destino;
    this.actualizadoEn = ahora;
  }

  /** Datos exigidos para PROGRAMADA: quién aceptó, cuándo y para qué fecha. */
  public void registrarAceptacion(UUID aceptadoPorId, Instant aceptadoEn, LocalDate fecha) {
    this.aceptadoPorId = aceptadoPorId;
    this.aceptadoEn = aceptadoEn;
    this.fechaProgramada = fecha;
  }

  /** Motivo exigido para DESCARTADA y CANCELADA (RN-MTO-09). */
  public void registrarMotivoCierre(String motivo) {
    this.motivoCierreSinEjecucion = motivo;
  }

  /** HU-047: changes only the confirmed priority; the generated suggestion stays immutable. */
  public void cambiarPrioridadConfirmada(
      PrioridadOrden prioridad, String justificacion, Instant ahora) {
    this.prioridadConfirmada = prioridad;
    this.justificacionPrioridad = justificacion;
    this.actualizadoEn = ahora;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPuenteId() {
    return puenteId;
  }

  public UUID getInspeccionOrigenId() {
    return inspeccionOrigenId;
  }

  public UUID getReglaOrigenId() {
    return reglaOrigenId;
  }

  public Boolean getReglaProvisional() {
    return reglaProvisional;
  }

  public UUID getResultadoIcOrigenId() {
    return resultadoIcOrigenId;
  }

  public UUID getPlanMantenimientoRutinarioId() {
    return planMantenimientoRutinarioId;
  }

  public Integer getCicloPlan() {
    return cicloPlan;
  }

  public String getElementoRef() {
    return elementoRef;
  }

  public UUID getInstanciaElementoId() {
    return instanciaElementoId;
  }

  public String getClaveGeneracion() {
    return claveGeneracion;
  }

  public TipoOrden getTipo() {
    return tipo;
  }

  public EstadoOrden getEstado() {
    return estado;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public PrioridadOrden getPrioridadSugerida() {
    return prioridadSugerida;
  }

  public PrioridadOrden getPrioridadConfirmada() {
    return prioridadConfirmada;
  }

  public String getJustificacionPrioridad() {
    return justificacionPrioridad;
  }

  public JsonNode getFundamentoGeneracion() {
    return fundamentoGeneracion;
  }

  public UUID getCreadoPorId() {
    return creadoPorId;
  }

  public UUID getAceptadoPorId() {
    return aceptadoPorId;
  }

  public Instant getAceptadoEn() {
    return aceptadoEn;
  }

  public LocalDate getFechaProgramada() {
    return fechaProgramada;
  }

  public String getMotivoCierreSinEjecucion() {
    return motivoCierreSinEjecucion;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getActualizadoEn() {
    return actualizadoEn;
  }

  public Long getVersion() {
    return version;
  }
}
