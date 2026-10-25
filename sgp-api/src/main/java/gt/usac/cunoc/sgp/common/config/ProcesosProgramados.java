package gt.usac.cunoc.sgp.common.config;

/**
 * Convención de los procesos programados (HU-069, DT-BE-14). Cada proceso se anota con
 * {@code @Scheduled(cron = CRON_DIARIO, zone = ZONA)} y {@code @SchedulerLock} con su nombre y
 * estas duraciones, para que dos réplicas del servidor no lo ejecuten a la vez. Su nombre también
 * identifica al proceso automático en la bitácora.
 */
public final class ProcesosProgramados {

  /** Una vez al día; configurable con PROCESOS_CRON_DIARIO. */
  public static final String CRON_DIARIO = "${app.procesos.cron-diario:0 0 2 * * *}";

  public static final String ZONA = "America/Guatemala";

  /** Libera el bloqueo aunque la réplica que lo tomó se caiga a mitad del proceso. */
  public static final String BLOQUEO_MAXIMO = "PT30M";

  /** Evita que otra réplica con el reloj desfasado vuelva a ejecutarlo enseguida. */
  public static final String BLOQUEO_MINIMO = "PT5M";

  public static final String ALERTA_REVISIONES_SIN_REVISAR = "alerta-revisiones-sin-revisar";
  public static final String ALERTA_PUENTES_SIN_INSPECCION = "alerta-puentes-sin-inspeccion";
  public static final String ALERTA_ORDENES_PROGRAMADAS = "alerta-ordenes-programadas";
  public static final String ALERTA_BORRADORES_INACTIVOS = "alerta-borradores-inactivos";
  public static final String PURGA_ARCHIVOS_BORRADORES = "purga-archivos-borradores";
  public static final String LIMPIEZA_IDEMPOTENCIA = "limpieza-claves-idempotencia";

  /** Entidad de la bitácora para el resumen de cada ejecución. */
  public static final String ENTIDAD_AUDITORIA = "proceso_programado";

  /** Las consultas nativas devuelven las fechas con distinto tipo según el controlador JDBC. */
  public static java.time.Instant instante(Object valor) {
    if (valor instanceof java.time.Instant instante) {
      return instante;
    }
    if (valor instanceof java.time.OffsetDateTime fecha) {
      return fecha.toInstant();
    }
    if (valor instanceof java.sql.Timestamp marca) {
      return marca.toInstant();
    }
    throw new IllegalArgumentException("Fecha no reconocida: " + valor);
  }

  private ProcesosProgramados() {}
}
