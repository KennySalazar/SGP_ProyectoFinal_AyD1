package gt.usac.cunoc.sgp.mantenimiento.entity;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.model.PrioridadMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoMantenimiento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Orden de mantenimiento de un puente (RN-MTO-01). Nace como propuesta, ya sea generada desde una
 * inspección publicada (HU-046) o creada a mano (HU-047), y avanza por el ciclo de vida de
 * RN-MTO-04.
 *
 * <p>Las referencias a otras tablas se guardan como identificadores para mantener el agregado
 * liviano: las restricciones de integridad ya las declara la base (V10). Los cambios de estado los
 * añadirá HU-047 con su máquina de estados; esta clase solo expone cómo se crea una propuesta.
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

  /** Clave única por inspección de origen que impide generar dos veces la misma propuesta. */
  @Column(name = "clave_generacion", length = 200, updatable = false)
  private String claveGeneracion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TipoMantenimiento tipo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EstadoOrdenMantenimiento estado;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String descripcion;

  @Enumerated(EnumType.STRING)
  @Column(name = "prioridad_sugerida", nullable = false, length = 20, updatable = false)
  private PrioridadMantenimiento prioridadSugerida;

  @Enumerated(EnumType.STRING)
  @Column(name = "prioridad_confirmada", nullable = false, length = 20)
  private PrioridadMantenimiento prioridadConfirmada;

  @Column(name = "justificacion_prioridad", columnDefinition = "TEXT")
  private String justificacionPrioridad;

  /** Qué valor se encontró y qué regla lo disparó; es la explicación de por qué se propone. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "fundamento_generacion", updatable = false)
  private JsonNode fundamentoGeneracion;

  @Column(name = "creado_por_id")
  private UUID creadoPorId;

  @Column(name = "aceptado_por_id")
  private UUID aceptadoPorId;

  @Column(name = "aceptado_en")
  private Instant aceptadoEn;

  @Column(name = "fecha_programada")
  private LocalDate fechaProgramada;

  @Column(name = "motivo_cierre_sin_ejecucion", columnDefinition = "TEXT")
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
   * Crea una orden en estado PROPUESTA. La prioridad sugerida y la confirmada empiezan iguales
   * (RN-MTO-06 las separa cuando alguien la modifica).
   *
   * <p>Para una orden manual (HU-047) los datos de origen van en {@code null}; para una propuesta
   * generada (HU-046) llevan la inspección, la regla y la clave de generación. {@code creadoPorId}
   * es nulo cuando la crea el proceso automático.
   */
  public static OrdenMantenimiento nuevaPropuesta(
      UUID puenteId,
      UUID inspeccionOrigenId,
      UUID reglaOrigenId,
      UUID resultadoIcOrigenId,
      String elementoRef,
      UUID instanciaElementoId,
      String claveGeneracion,
      TipoMantenimiento tipo,
      String descripcion,
      PrioridadMantenimiento prioridad,
      JsonNode fundamentoGeneracion,
      UUID creadoPorId) {
    OrdenMantenimiento orden = new OrdenMantenimiento();
    orden.id = UuidV7Generator.generate();
    orden.puenteId = Objects.requireNonNull(puenteId, "puenteId no puede ser nulo");
    orden.inspeccionOrigenId = inspeccionOrigenId;
    orden.reglaOrigenId = reglaOrigenId;
    orden.resultadoIcOrigenId = resultadoIcOrigenId;
    orden.elementoRef = elementoRef;
    orden.instanciaElementoId = instanciaElementoId;
    orden.claveGeneracion = claveGeneracion;
    orden.tipo = Objects.requireNonNull(tipo, "tipo no puede ser nulo");
    orden.estado = EstadoOrdenMantenimiento.PROPUESTA;
    orden.descripcion = Objects.requireNonNull(descripcion, "descripcion no puede ser nula");
    Objects.requireNonNull(prioridad, "prioridad no puede ser nula");
    orden.prioridadSugerida = prioridad;
    orden.prioridadConfirmada = prioridad;
    orden.fundamentoGeneracion = fundamentoGeneracion;
    orden.creadoPorId = creadoPorId;
    return orden;
  }

  @PrePersist
  void alPersistir() {
    Instant ahora = Instant.now();
    if (this.creadoEn == null) {
      this.creadoEn = ahora;
    }
    if (this.actualizadoEn == null) {
      this.actualizadoEn = ahora;
    }
  }

  @PreUpdate
  void alActualizar() {
    this.actualizadoEn = Instant.now();
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

  public TipoMantenimiento getTipo() {
    return tipo;
  }

  public EstadoOrdenMantenimiento getEstado() {
    return estado;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public PrioridadMantenimiento getPrioridadSugerida() {
    return prioridadSugerida;
  }

  public PrioridadMantenimiento getPrioridadConfirmada() {
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
