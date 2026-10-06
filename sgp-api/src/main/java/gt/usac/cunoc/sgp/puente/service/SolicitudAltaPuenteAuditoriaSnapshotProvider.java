package gt.usac.cunoc.sgp.puente.service;

import gt.usac.cunoc.sgp.common.audit.service.AuditoriaSnapshotProvider;
import gt.usac.cunoc.sgp.puente.entity.SolicitudAltaPuente;
import gt.usac.cunoc.sgp.puente.model.EstadoSolicitudAltaPuente;
import gt.usac.cunoc.sgp.puente.repository.SolicitudAltaPuenteRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SolicitudAltaPuenteAuditoriaSnapshotProvider implements AuditoriaSnapshotProvider {
  private final SolicitudAltaPuenteRepository solicitudes;

  public SolicitudAltaPuenteAuditoriaSnapshotProvider(SolicitudAltaPuenteRepository solicitudes) {
    this.solicitudes = solicitudes;
  }

  @Override
  public Class<?> tipo() {
    return SolicitudAltaPuente.class;
  }

  @Override
  public Object cargar(UUID id) {
    return solicitudes.findById(id).map(this::proyectar).orElse(null);
  }

  private SolicitudSnapshot proyectar(SolicitudAltaPuente solicitud) {
    return new SolicitudSnapshot(
        solicitud.getId(),
        solicitud.getNombrePropuesto(),
        solicitud.getRuta(),
        solicitud.getEstado(),
        solicitud.getMotivoDecision());
  }

  public record SolicitudSnapshot(
      UUID id,
      String nombre,
      String ruta,
      EstadoSolicitudAltaPuente estado,
      String motivoDecision) {}
}
