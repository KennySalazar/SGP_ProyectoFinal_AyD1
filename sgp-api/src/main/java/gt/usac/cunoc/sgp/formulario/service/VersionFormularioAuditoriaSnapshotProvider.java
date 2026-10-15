package gt.usac.cunoc.sgp.formulario.service;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaSnapshotProvider;
import gt.usac.cunoc.sgp.formulario.entity.ConfiguracionIcElemento;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.ConfiguracionIcElementoRepository;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class VersionFormularioAuditoriaSnapshotProvider implements AuditoriaSnapshotProvider {

  private final VersionFormularioRepository versiones;
  private final ConfiguracionIcElementoRepository configuracionesIc;

  public VersionFormularioAuditoriaSnapshotProvider(
      VersionFormularioRepository versiones, ConfiguracionIcElementoRepository configuracionesIc) {
    this.versiones = versiones;
    this.configuracionesIc = configuracionesIc;
  }

  @Override
  public Class<?> tipo() {
    return VersionFormulario.class;
  }

  @Override
  @Transactional(readOnly = true)
  public Object cargar(UUID id) {
    if (id == null) {
      return null;
    }

    return versiones.findById(id).map(this::proyectar).orElse(null);
  }

  private VersionFormularioSnapshot proyectar(VersionFormulario version) {
    List<PesoSnapshot> pesos =
        configuracionesIc.findAllByVersionFormularioId(version.getId()).stream()
            .sorted(Comparator.comparing(ConfiguracionIcElemento::getElementoRef))
            .map(
                elemento ->
                    new PesoSnapshot(
                        elemento.getElementoRef(), elemento.getPeso(), elemento.getDescripcion()))
            .toList();

    JsonNode reglas = version.getReglasEvaluacionIc();

    return new VersionFormularioSnapshot(
        version.getId(),
        version.getCodigo(),
        version.getEstado(),
        version.isActiva(),
        version.getHashContenido(),
        version.getVersionAlgoritmoIc(),
        version.getVersionAnterior() != null ? version.getVersionAnterior().getId() : null,
        version.getPublicadoPorId(),
        version.getPublicadoEn(),
        version.getCalibradoPorId(),
        version.getCalibradoEn(),
        reglas == null ? null : reglas.deepCopy(),
        pesos);
  }

  public record PesoSnapshot(String elementoRef, BigDecimal peso, String descripcion) {}

  public record VersionFormularioSnapshot(
      UUID id,
      String codigo,
      EstadoVersionFormulario estado,
      boolean activa,
      String hashContenido,
      String versionAlgoritmoIc,
      UUID versionAnteriorId,
      UUID publicadoPorId,
      Instant publicadoEn,
      UUID calibradoPorId,
      Instant calibradoEn,
      JsonNode reglasEvaluacionIc,
      List<PesoSnapshot> pesos) {}
}
