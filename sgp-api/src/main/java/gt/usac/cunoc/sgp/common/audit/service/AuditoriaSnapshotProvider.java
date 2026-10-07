package gt.usac.cunoc.sgp.common.audit.service;

import java.util.UUID;

public interface AuditoriaSnapshotProvider {
  Class<?> tipo();

  Object cargar(UUID id);

  default Object cargarPorEmail(String email) {
    return null;
  }
}
