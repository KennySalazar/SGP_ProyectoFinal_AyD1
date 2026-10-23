package gt.usac.cunoc.sgp.common.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.audit.entity.Auditoria;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.repository.AuditoriaRepository;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditoriaWriter {

  private final AuditoriaRepository repositorio;
  private final Clock clock;

  public AuditoriaWriter(AuditoriaRepository repositorio, Clock clock) {
    this.repositorio = repositorio;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void registrar(
      UUID usuarioId,
      AccionAuditoria accion,
      String entidad,
      UUID entidadId,
      JsonNode valoresAnteriores,
      JsonNode valoresPosteriores,
      String proceso) {
    guardar(usuarioId, accion, entidad, entidadId, valoresAnteriores, valoresPosteriores, proceso);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void registrarEnTransaccion(
      UUID usuarioId,
      AccionAuditoria accion,
      String entidad,
      UUID entidadId,
      JsonNode valoresAnteriores,
      JsonNode valoresPosteriores,
      String proceso) {
    guardar(usuarioId, accion, entidad, entidadId, valoresAnteriores, valoresPosteriores, proceso);
  }

  /**
   * Resumen de una ejecución de un proceso programado: sin usuario y con el nombre del proceso como
   * proceso automático (HU-069, RN-USR-09).
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void registrarProceso(String proceso, JsonNode resumen) {
    guardar(
        null, AccionAuditoria.EJECUTAR_PROCESO, "proceso_programado", null, null, resumen, proceso);
  }

  private void guardar(
      UUID usuarioId,
      AccionAuditoria accion,
      String entidad,
      UUID entidadId,
      JsonNode valoresAnteriores,
      JsonNode valoresPosteriores,
      String proceso) {
    var auditoria =
        new Auditoria(
            UuidV7Generator.generate(),
            usuarioId,
            accion,
            entidad,
            entidadId,
            valoresAnteriores,
            valoresPosteriores,
            procesoNormalizado(proceso),
            clock.instant());

    repositorio.save(auditoria);
  }

  private String procesoNormalizado(String proceso) {
    if (proceso == null || proceso.isBlank()) {
      return null;
    }
    return proceso;
  }
}
