package gt.usac.cunoc.sgp.archivo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.archivo.entity.Archivo;
import gt.usac.cunoc.sgp.archivo.model.EstadoArchivo;
import gt.usac.cunoc.sgp.archivo.repository.ArchivoInspeccionRepository;
import gt.usac.cunoc.sgp.archivo.repository.ArchivoRepository;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purga el contenido multimedia de un borrador inactivo (RN-ARC-06, HU-069), un archivo por
 * transacción. Conserva el registro del archivo y de la inspección; solo elimina los objetos.
 */
@Service
public class PurgaArchivosBorradorService {

  private final ArchivoInspeccionRepository vinculos;
  private final ArchivoRepository archivos;
  private final AlmacenamientoArchivos almacenamiento;
  private final AuditoriaWriter auditoria;
  private final ObjectMapper json;
  private final Clock clock;

  public PurgaArchivosBorradorService(
      ArchivoInspeccionRepository vinculos,
      ArchivoRepository archivos,
      AlmacenamientoArchivos almacenamiento,
      AuditoriaWriter auditoria,
      ObjectMapper json,
      Clock clock) {
    this.vinculos = vinculos;
    this.archivos = archivos;
    this.almacenamiento = almacenamiento;
    this.auditoria = auditoria;
    this.json = json;
    this.clock = clock;
  }

  /** Archivos por purgar de borradores sin actividad desde el límite: [archivoId, inspeccionId]. */
  @Transactional(readOnly = true)
  public List<Object[]> candidatos(Instant limite) {
    return vinculos.findPurgablesDeBorradoresInactivos(limite);
  }

  /**
   * Revalida bajo bloqueo que la inspección siga siendo un borrador inactivo, elimina sus objetos y
   * marca el archivo como PURGADO con auditoría. Si el almacenamiento falla, nada cambia y se
   * reintenta en la siguiente ejecución.
   *
   * @return verdadero si el archivo se purgó
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean purgar(UUID archivoId, UUID inspeccionId, Instant limite, String proceso) {
    if (!vinculos.bloquearBorradorInactivo(inspeccionId, limite).orElse(false)) {
      return false;
    }
    var vinculo = vinculos.findById(archivoId).orElse(null);
    if (vinculo == null || !vinculo.getInspeccion().getId().equals(inspeccionId)) {
      return false;
    }
    Archivo archivo = vinculo.getArchivo();
    EstadoArchivo anterior = archivo.getEstado();
    if (anterior == EstadoArchivo.PURGADO) {
      return false;
    }
    try {
      almacenamiento.eliminar(archivo.getClaveObjetoOriginal());
      if (archivo.getClaveObjetoMiniatura() != null) {
        almacenamiento.eliminar(archivo.getClaveObjetoMiniatura());
      }
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo eliminar el archivo " + archivoId, e);
    }
    archivo.purgar(clock.instant());
    archivos.saveAndFlush(archivo);
    auditoria.registrarEnTransaccion(
        null,
        AccionAuditoria.CAMBIAR_ESTADO,
        "archivo",
        archivoId,
        json.createObjectNode().put("estado", anterior.name()),
        json.createObjectNode()
            .put("estado", EstadoArchivo.PURGADO.name())
            .put("inspeccionId", inspeccionId.toString()),
        proceso);
    return true;
  }
}
