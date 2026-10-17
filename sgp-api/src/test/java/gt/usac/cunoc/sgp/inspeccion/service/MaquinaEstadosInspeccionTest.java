package gt.usac.cunoc.sgp.inspeccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoInspeccion;
import gt.usac.cunoc.sgp.inspeccion.model.EventoInspeccion;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

/** Cubre todas las combinaciones de estado y evento de la máquina de estados (DT-CAL-01). */
class MaquinaEstadosInspeccionTest {

  /** Transiciones válidas de RN-INS-04 y RN-REV-02: (estado origen, evento) a estado destino. */
  private static final Map<String, EstadoInspeccion> VALIDAS =
      Map.of(
          "BORRADOR:ENVIAR", EstadoInspeccion.ENVIADA,
          "ENVIADA:INICIAR_REVISION", EstadoInspeccion.EN_REVISION,
          "ENVIADA:PUBLICAR_SIN_REVISION", EstadoInspeccion.PUBLICADA,
          "EN_REVISION:APROBAR", EstadoInspeccion.PUBLICADA,
          "EN_REVISION:SOLICITAR_CAMBIOS", EstadoInspeccion.CAMBIOS_SOLICITADOS,
          "EN_REVISION:RECHAZAR", EstadoInspeccion.RECHAZADA,
          "CAMBIOS_SOLICITADOS:DEVOLVER_A_BORRADOR", EstadoInspeccion.BORRADOR);

  static Stream<Arguments> todasLasCombinaciones() {
    return Arrays.stream(EstadoInspeccion.values())
        .flatMap(
            estado ->
                Arrays.stream(EventoInspeccion.values())
                    .map(evento -> Arguments.of(estado, evento)));
  }

  @ParameterizedTest(name = "{0} + {1}")
  @MethodSource("todasLasCombinaciones")
  void cadaCombinacionEsValidaSoloSiEstaEnLaTablaDeTransiciones(
      EstadoInspeccion estado, EventoInspeccion evento) {
    EstadoInspeccion esperado = VALIDAS.get(estado.name() + ":" + evento.name());

    if (esperado != null) {
      assertThat(MaquinaEstadosInspeccion.destino(estado, evento)).isEqualTo(esperado);
      return;
    }
    assertThatThrownBy(() -> MaquinaEstadosInspeccion.destino(estado, evento))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode())
                  .isEqualTo(estado.esTerminal() ? "estado_terminal" : "transicion_invalida");
            });
  }

  @Test
  void laTablaCoincideConLosEventosDeclarados() {
    assertThat(EventoInspeccion.values()).hasSize(VALIDAS.size());
    for (EventoInspeccion evento : EventoInspeccion.values()) {
      assertThat(VALIDAS.get(evento.origen().name() + ":" + evento.name()))
          .isEqualTo(evento.destino());
    }
  }

  @Test
  void soloPublicadaYRechazadaSonTerminales() {
    assertThat(Arrays.stream(EstadoInspeccion.values()).filter(EstadoInspeccion::esTerminal))
        .containsExactlyInAnyOrder(EstadoInspeccion.PUBLICADA, EstadoInspeccion.RECHAZADA);
  }

  @Test
  void rechazaArgumentosNulos() {
    assertThatThrownBy(() -> MaquinaEstadosInspeccion.destino(null, EventoInspeccion.ENVIAR))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MaquinaEstadosInspeccion.destino(EstadoInspeccion.BORRADOR, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
