package gt.usac.cunoc.sgp.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.config.ProcesosProgramados;
import gt.usac.cunoc.sgp.sync.repository.OperacionIdempotenteRepository;
import java.time.Clock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Elimina cada día las claves de idempotencia vencidas (HU-069). */
@Service
public class LimpiezaIdempotenciaService {

  private final OperacionIdempotenteRepository operaciones;
  private final AuditoriaWriter auditoria;
  private final ObjectMapper json;
  private final Clock clock;
  private final TransactionTemplate tx;

  public LimpiezaIdempotenciaService(
      OperacionIdempotenteRepository operaciones,
      AuditoriaWriter auditoria,
      ObjectMapper json,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.operaciones = operaciones;
    this.auditoria = auditoria;
    this.json = json;
    this.clock = clock;
    this.tx = new TransactionTemplate(transactionManager);
  }

  @Scheduled(cron = ProcesosProgramados.CRON_DIARIO, zone = ProcesosProgramados.ZONA)
  @SchedulerLock(
      name = ProcesosProgramados.LIMPIEZA_IDEMPOTENCIA,
      lockAtMostFor = ProcesosProgramados.BLOQUEO_MAXIMO,
      lockAtLeastFor = ProcesosProgramados.BLOQUEO_MINIMO)
  public void limpiar() {
    Integer eliminadas = tx.execute(t -> operaciones.eliminarVencidas(clock.instant()));
    auditoria.registrarProceso(
        ProcesosProgramados.LIMPIEZA_IDEMPOTENCIA,
        json.createObjectNode().put("eliminadas", eliminadas == null ? 0 : eliminadas));
  }
}
