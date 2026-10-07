package gt.usac.cunoc.sgp.usuario.service;

import gt.usac.cunoc.sgp.common.audit.service.AuditoriaSnapshotProvider;
import gt.usac.cunoc.sgp.usuario.entity.UsuarioProfesional;
import gt.usac.cunoc.sgp.usuario.repository.UsuarioProfesionalRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Campos del colegiado permitidos en auditoría para comparar antes y después de verificar. */
@Component
public class UsuarioProfesionalAuditoriaSnapshotProvider implements AuditoriaSnapshotProvider {

  private final UsuarioProfesionalRepository profesionales;

  public UsuarioProfesionalAuditoriaSnapshotProvider(UsuarioProfesionalRepository profesionales) {
    this.profesionales = profesionales;
  }

  @Override
  public Class<?> tipo() {
    return UsuarioProfesional.class;
  }

  @Override
  public Object cargar(UUID id) {
    return profesionales.findById(id).map(this::proyectar).orElse(null);
  }

  private ProfesionalSnapshot proyectar(UsuarioProfesional profesional) {
    return new ProfesionalSnapshot(
        profesional.getUsuarioId(),
        profesional.getNumeroColegiado(),
        profesional.isColegiadoVerificado(),
        profesional.getColegiadoVerificadoEn(),
        profesional.getColegiadoVerificadoPorId());
  }

  public record ProfesionalSnapshot(
      UUID id,
      String numeroColegiado,
      boolean colegiadoVerificado,
      Instant colegiadoVerificadoEn,
      UUID colegiadoVerificadoPorId) {}
}
