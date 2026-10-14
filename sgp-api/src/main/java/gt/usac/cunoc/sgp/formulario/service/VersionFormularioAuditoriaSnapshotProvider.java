package gt.usac.cunoc.sgp.formulario.service;

import gt.usac.cunoc.sgp.common.audit.service.AuditoriaSnapshotProvider;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class VersionFormularioAuditoriaSnapshotProvider implements AuditoriaSnapshotProvider {

  private final VersionFormularioRepository versiones;

  public VersionFormularioAuditoriaSnapshotProvider(VersionFormularioRepository versiones) {
    this.versiones = versiones;
  }

  @Override
  public Class<?> tipo() {
    return VersionFormulario.class;
  }

  @Override
  public Object cargar(UUID id) {
    return versiones.findById(id).map(this::proyectar).orElse(null);
  }

  private VersionFormularioSnapshot proyectar(VersionFormulario version) {
    return new VersionFormularioSnapshot(
        version.getId(),
        version.getCodigo(),
        version.getEstado(),
        version.isActiva(),
        version.getHashContenido(),
        version.getVersionAlgoritmoIc(),
        version.getVersionAnterior() != null ? version.getVersionAnterior().getId() : null,
        version.getPublicadoPorId(),
        version.getPublicadoEn());
  }

  public record VersionFormularioSnapshot(
      UUID id,
      String codigo,
      EstadoVersionFormulario estado,
      boolean activa,
      String hashContenido,
      String versionAlgoritmoIc,
      UUID versionAnteriorId,
      UUID publicadoPorId,
      Instant publicadoEn) {}
}
