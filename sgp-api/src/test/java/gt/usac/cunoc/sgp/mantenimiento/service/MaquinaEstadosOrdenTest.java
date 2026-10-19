package gt.usac.cunoc.sgp.mantenimiento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.EventoOrden;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

/** Cubre todas las combinaciones de estado y evento de las órdenes (RN-MTO-04, DT-CAL-01). */
class MaquinaEstadosOrdenTest {

  /** Tabla de transiciones válidas de HU-047: (estado origen, evento) a estado destino. */
  private static final Map<String, EstadoOrden> VALIDAS =
      Map.of(
          "PROPUESTA:ACEPTAR_Y_PROGRAMAR", EstadoOrden.PROGRAMADA,
          "PROPUESTA:DESCARTAR", EstadoOrden.DESCARTADA,
          "PROGRAMADA:INICIAR_EJECUCION", EstadoOrden.EN_EJECUCION,
          "PROGRAMADA:CANCELAR", EstadoOrden.CANCELADA,
          "EN_EJECUCION:CERRAR_COMO_EJECUTADA", EstadoOrden.EJECUTADA);

  static Stream<Arguments> todasLasCombinaciones() {
    return Arrays.stream(EstadoOrden.values())
        .flatMap(
            estado ->
                Arrays.stream(EventoOrden.values()).map(evento -> Arguments.of(estado, evento)));
  }

  @ParameterizedTest(name = "{0} + {1}")
  @MethodSource("todasLasCombinaciones")
  void cadaCombinacionEsValidaSoloSiEstaEnLaTablaDeTransiciones(
      EstadoOrden estado, EventoOrden evento) {
    EstadoOrden esperado = VALIDAS.get(estado.name() + ":" + evento.name());

    if (esperado != null) {
      assertThat(MaquinaEstadosOrden.destino(estado, evento)).isEqualTo(esperado);
      return;
    }
    assertThatThrownBy(() -> MaquinaEstadosOrden.destino(estado, evento))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode())
                  .isEqualTo(estado.esTerminal() ? "estado_terminal" : "transicion_invalida");
            });
  }

  @Test
  void lasTransicionesNombradasEnLaHistoriaSeRechazanCon409() {
    assertThatThrownBy(
            () -> MaquinaEstadosOrden.destino(EstadoOrden.PROPUESTA, EventoOrden.INICIAR_EJECUCION))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () -> MaquinaEstadosOrden.destino(EstadoOrden.PROPUESTA, EventoOrden.CANCELAR))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () -> MaquinaEstadosOrden.destino(EstadoOrden.EN_EJECUCION, EventoOrden.CANCELAR))
        .isInstanceOf(ApiException.class);
    for (EventoOrden evento : EventoOrden.values()) {
      assertThatThrownBy(() -> MaquinaEstadosOrden.destino(EstadoOrden.EJECUTADA, evento))
          .isInstanceOfSatisfying(
              ApiException.class, e -> assertThat(e.getCode()).isEqualTo("estado_terminal"));
    }
  }

  @Test
  void soloEjecutadaDescartadaYCanceladaSonTerminales() {
    EnumSet<EstadoOrden> terminales =
        EnumSet.of(EstadoOrden.EJECUTADA, EstadoOrden.DESCARTADA, EstadoOrden.CANCELADA);
    for (EstadoOrden estado : EstadoOrden.values()) {
      assertThat(estado.esTerminal()).isEqualTo(terminales.contains(estado));
    }
  }

  @Test
  void soloDescartarYCancelarExigenMotivo() {
    for (EventoOrden evento : EventoOrden.values()) {
      assertThat(evento.requiereMotivo())
          .isEqualTo(evento == EventoOrden.DESCARTAR || evento == EventoOrden.CANCELAR);
    }
  }

  @Test
  void elEstadoYElEventoSonObligatorios() {
    assertThatThrownBy(() -> MaquinaEstadosOrden.destino(null, EventoOrden.DESCARTAR))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MaquinaEstadosOrden.destino(EstadoOrden.PROPUESTA, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
