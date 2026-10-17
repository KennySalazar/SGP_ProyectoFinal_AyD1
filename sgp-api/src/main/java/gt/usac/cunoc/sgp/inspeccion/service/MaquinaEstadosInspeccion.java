package gt.usac.cunoc.sgp.inspeccion.service;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoInspeccion;
import gt.usac.cunoc.sgp.inspeccion.model.EventoInspeccion;
import org.springframework.http.HttpStatus;

/**
 * Reglas de transición de la inspección (RN-INS-04, DT-BE-07). Solo decide si una transición es
 * válida; quien la aplica es {@link TransicionInspeccionService}.
 */
public final class MaquinaEstadosInspeccion {

  private MaquinaEstadosInspeccion() {}

  /**
   * Devuelve el estado destino del evento o responde 409 si no es válido desde el estado actual.
   * Los estados terminales (PUBLICADA, RECHAZADA) rechazan cualquier evento.
   */
  public static EstadoInspeccion destino(EstadoInspeccion actual, EventoInspeccion evento) {
    if (actual == null || evento == null) {
      throw new IllegalArgumentException("El estado y el evento son obligatorios");
    }
    if (actual.esTerminal()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "estado_terminal",
          "Transicion no permitida",
          "La inspeccion esta "
              + actual.name()
              + " y no admite cambios de estado (accion "
              + evento.name()
              + ").");
    }
    if (evento.origen() != actual) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "transicion_invalida",
          "Transicion no permitida",
          "La accion "
              + evento.name()
              + " no se puede aplicar a una inspeccion en estado "
              + actual.name()
              + ".");
    }
    return evento.destino();
  }
}
