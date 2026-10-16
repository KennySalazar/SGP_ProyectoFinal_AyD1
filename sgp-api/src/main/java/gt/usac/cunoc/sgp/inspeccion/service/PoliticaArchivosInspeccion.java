package gt.usac.cunoc.sgp.inspeccion.service;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.model.ContextoArchivosInspeccion;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Autorización fotográfica sin I/O. La asignación se resuelve exclusivamente en el servidor. */
@Service
public class PoliticaArchivosInspeccion {

  private final ReglasEvidenciaFotografica reglas;

  public PoliticaArchivosInspeccion(ReglasEvidenciaFotografica reglas) {
    this.reglas = reglas;
  }

  public void validarCarga(ContextoArchivosInspeccion contexto, UUID actorId, long disponibles) {
    validarRetirada(contexto, actorId);
    reglas.validarCapacidadParaNuevaFotografia(disponibles);
  }

  public void validarLectura(
      ContextoArchivosInspeccion contexto, UUID actorId, boolean esCatedraticoAsignado) {
    validarExistencia(contexto, actorId);
    if (!actorId.equals(contexto.autorId())
        && !("BORRADOR".equals(contexto.estado()) && esCatedraticoAsignado)) {
      throw noEncontrada();
    }
  }

  public void validarRetirada(ContextoArchivosInspeccion contexto, UUID actorId) {
    validarExistencia(contexto, actorId);
    if (!actorId.equals(contexto.autorId())) throw noEncontrada();
    if (!"BORRADOR".equals(contexto.estado())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "estado_inspeccion_no_permite_archivos",
          "Estado incompatible",
          "Solo se pueden modificar fotografías de un borrador propio.");
    }
  }

  private void validarExistencia(ContextoArchivosInspeccion contexto, UUID actorId) {
    if (actorId == null) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida.");
    }
    if (contexto == null || contexto.eliminadoEn() != null) throw noEncontrada();
  }

  private ApiException noEncontrada() {
    return new ApiException(
        HttpStatus.NOT_FOUND,
        "inspeccion_no_encontrada",
        "Inspección no encontrada",
        "No se encontró la inspección solicitada.");
  }
}
