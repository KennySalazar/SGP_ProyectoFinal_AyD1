package gt.usac.cunoc.sgp.mantenimiento.service;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.EventoOrden;
import org.springframework.http.HttpStatus;

/**
 * Reglas de transición de las órdenes de mantenimiento (RN-MTO-04, DT-BE-07). Solo decide si una
 * transición es válida; quien la aplica es {@link TransicionOrdenService}.
 */
public final class MaquinaEstadosOrden {

  private MaquinaEstadosOrden() {}

  /**
   * Devuelve el estado destino del evento o responde 409 si no es válido desde el estado actual.
   * Los estados terminales (EJECUTADA, DESCARTADA, CANCELADA) rechazan cualquier evento.
   */
  public static EstadoOrden destino(EstadoOrden actual, EventoOrden evento) {
    if (actual == null || evento == null) {
      throw new IllegalArgumentException("El estado y el evento son obligatorios");
    }
    if (actual.esTerminal()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "estado_terminal",
          "Transicion no permitida",
          "La orden esta "
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
              + " no se puede aplicar a una orden en estado "
              + actual.name()
              + ".");
    }
    return evento.destino();
  }
}
