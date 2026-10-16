package gt.usac.cunoc.sgp.inspeccion.service;

import gt.usac.cunoc.sgp.archivo.model.EstadoArchivo;
import gt.usac.cunoc.sgp.archivo.service.RegistroFotografiasInspeccion;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.config.EvidenciaFotograficaProperties;
import gt.usac.cunoc.sgp.inspeccion.dto.ValidacionEvidenciaResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.exception.EvidenciaFotograficaIncompletaException;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EvidenciaFotograficaService {
  private final InspeccionRepository inspecciones;
  private final RegistroFotografiasInspeccion registro;
  private final AccesoFotografiasInspeccionService acceso;
  private final ReglasEvidenciaFotografica reglas;
  private final EvidenciaFotograficaProperties propiedades;

  public EvidenciaFotograficaService(
      InspeccionRepository inspecciones,
      RegistroFotografiasInspeccion registro,
      AccesoFotografiasInspeccionService acceso,
      ReglasEvidenciaFotografica reglas,
      EvidenciaFotograficaProperties propiedades) {
    this.inspecciones = inspecciones;
    this.registro = registro;
    this.acceso = acceso;
    this.reglas = reglas;
    this.propiedades = propiedades;
  }

  public ValidacionEvidenciaResponse consultar(UUID inspeccionId, UUID actorId) {
    if (actorId == null) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida.");
    }
    var inspeccion = cargar(inspeccionId);
    acceso.validarLectura(inspeccion, actorId);
    return evaluar(inspeccionId);
  }

  /**
   * El llamador autoriza la transición y bloquea la inspección antes de invocar. Al publicar,
   * respeta el orden puente → inspección. La evaluación ocurre antes del cambio de estado.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void exigirParaTransicion(UUID inspeccionId) {
    cargar(inspeccionId);
    var resultado = evaluar(inspeccionId);
    if (resultado.totalDisponibles() > resultado.maximoPermitido()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "limite_fotografias_excedido",
          "Límite de fotografías excedido",
          "La inspección excede el máximo de fotografías disponibles.");
    }
    if (!resultado.valida()) throw new EvidenciaFotograficaIncompletaException(resultado);
  }

  private Inspeccion cargar(UUID id) {
    return inspecciones
        .findByIdAndEliminadoEnIsNull(id)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "inspeccion_no_encontrada",
                    "Inspección no encontrada",
                    "No se encontró la inspección solicitada."));
  }

  private ValidacionEvidenciaResponse evaluar(UUID inspeccionId) {
    var ids = new HashSet<UUID>();
    var disponibles = new HashMap<String, Long>();
    for (var foto : registro.consultar(inspeccionId)) {
      if (foto == null
          || foto.id() == null
          || !inspeccionId.equals(foto.inspeccionId())
          || foto.categoria() == null
          || foto.estado() == null
          || !ids.add(foto.id())) {
        throw new IllegalStateException("El inventario fotográfico es inconsistente.");
      }
      if (foto.estado() == EstadoArchivo.DISPONIBLE) {
        disponibles.merge(foto.categoria().name(), 1L, Long::sum);
      }
    }
    return reglas.evaluar(disponibles, propiedades.minimosPorCategoria());
  }
}
