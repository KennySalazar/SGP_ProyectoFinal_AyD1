package gt.usac.cunoc.sgp.mantenimiento.service;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaSnapshotProvider;
import gt.usac.cunoc.sgp.mantenimiento.entity.ReglaMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.model.PrioridadOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import gt.usac.cunoc.sgp.mantenimiento.repository.ReglaMantenimientoRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReglaMantenimientoAuditoriaSnapshotProvider implements AuditoriaSnapshotProvider {

  private final ReglaMantenimientoRepository repository;

  public ReglaMantenimientoAuditoriaSnapshotProvider(ReglaMantenimientoRepository repository) {
    this.repository = repository;
  }

  @Override
  public Class<?> tipo() {
    return ReglaMantenimiento.class;
  }

  @Override
  public Object cargar(UUID id) {
    return repository.findById(id).map(this::proyectar).orElse(null);
  }

  private ReglaMantenimientoSnapshot proyectar(ReglaMantenimiento r) {
    return new ReglaMantenimientoSnapshot(
        r.getId(),
        r.getCodigo(),
        r.getNumeroRevision(),
        r.getVersionFormulario() != null ? r.getVersionFormulario().getId() : null,
        r.getElementoRef(),
        r.getCondicion(),
        r.getTipoMantenimiento(),
        r.getIntervencionSugerida(),
        r.getReglaPrioridad(),
        r.getPrioridadBase(),
        r.isActiva(),
        r.getValidadoPorId(),
        r.getValidadoEn());
  }

  public record ReglaMantenimientoSnapshot(
      UUID id,
      String codigo,
      int numeroRevision,
      UUID versionFormularioId,
      String elementoRef,
      JsonNode condicion,
      TipoOrden tipoMantenimiento,
      String intervencionSugerida,
      JsonNode reglaPrioridad,
      PrioridadOrden prioridadBase,
      boolean activa,
      UUID validadoPorId,
      Instant validadoEn) {}
}
