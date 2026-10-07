package gt.usac.cunoc.sgp.puente.service;

import gt.usac.cunoc.sgp.common.audit.service.AuditoriaSnapshotProvider;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PuenteAuditoriaSnapshotProvider implements AuditoriaSnapshotProvider {
  private final PuenteRepository puentes;

  public PuenteAuditoriaSnapshotProvider(PuenteRepository puentes) {
    this.puentes = puentes;
  }

  @Override
  public Class<?> tipo() {
    return Puente.class;
  }

  @Override
  public Object cargar(UUID id) {
    return puentes.findById(id).map(this::proyectar).orElse(null);
  }

  private PuenteSnapshot proyectar(Puente puente) {
    return new PuenteSnapshot(
        puente.getId(),
        puente.getCodigo(),
        puente.getNombre(),
        puente.getRuta(),
        puente.getUbicacion().getY(),
        puente.getUbicacion().getX(),
        puente.isActivo());
  }

  public record PuenteSnapshot(
      UUID id,
      String codigo,
      String nombre,
      String ruta,
      double latitud,
      double longitud,
      boolean activo) {}
}
