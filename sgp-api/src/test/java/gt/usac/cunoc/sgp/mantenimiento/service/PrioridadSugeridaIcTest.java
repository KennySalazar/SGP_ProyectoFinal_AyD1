package gt.usac.cunoc.sgp.mantenimiento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gt.usac.cunoc.sgp.formulario.entity.ConfiguracionIcElemento;
import gt.usac.cunoc.sgp.inspeccion.entity.ResultadoIc;
import gt.usac.cunoc.sgp.mantenimiento.model.PrioridadOrden;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrioridadSugeridaIcTest {

  private static final UUID VERSION = UUID.fromString("019a0000-0000-7000-8000-000000000001");

  @Test
  void aplicaLimitesDeTodasLasPrioridades() {
    for (PrioridadOrden base : PrioridadOrden.values()) {
      assertThat(calcular(base, "0", "1", "1").prioridad()).isEqualTo(PrioridadOrden.CRITICA);
      assertThat(calcular(base, "100", "1", "1").prioridad()).isEqualTo(base);
      assertThat(calcular(base, "0", "0.25", "1").prioridad().ordinal())
          .isGreaterThanOrEqualTo(base.ordinal());
    }
  }

  @Test
  void redondeaLosCasosExactamenteIntermediosHaciaArriba() {
    assertThat(calcular(PrioridadOrden.ALTA, "50", "1", "1").prioridad())
        .isEqualTo(PrioridadOrden.CRITICA);
  }

  @Test
  void utilizaElMaximoConfiguradoDeLaVersionCompleta() {
    var calculo = calcular(PrioridadOrden.BAJA, "0", "0.25", "1");
    assertThat(calculo.prioridad()).isEqualTo(PrioridadOrden.MEDIA);
    assertThat(calculo.pesoMaximo()).isEqualByComparingTo("1");
  }

  @Test
  void datosAusentesOInvalidosConservanLaPrioridadBaseYExplicanElFallback() {
    var sinIc =
        PrioridadSugeridaIc.calcular(
            PrioridadOrden.MEDIA, null, VERSION, "/a", List.of(peso("/a", "1")));
    var sinPeso =
        PrioridadSugeridaIc.calcular(
            PrioridadOrden.ALTA, resultado("80"), VERSION, "/a", List.of());
    var icInvalido = calcular(PrioridadOrden.BAJA, "101", "1", "1");
    var pesoInvalido = calcular(PrioridadOrden.MEDIA, "20", "0", "1");
    var sinPesoMaximo =
        PrioridadSugeridaIc.calcular(
            PrioridadOrden.ALTA, resultado("50"), VERSION, "/a", List.of(peso("/a", "0")));
    var versionInconsistente =
        PrioridadSugeridaIc.calcular(
            PrioridadOrden.ALTA,
            resultado("50", UUID.randomUUID()),
            VERSION,
            "/a",
            List.of(peso("/a", "1")));
    var versionEICInvalidos =
        PrioridadSugeridaIc.calcular(
            PrioridadOrden.MEDIA,
            resultado("101", UUID.randomUUID()),
            VERSION,
            "/a",
            List.of(peso("/a", "1")));

    assertThat(sinIc.prioridad()).isEqualTo(PrioridadOrden.MEDIA);
    assertThat(sinIc.motivoFallback()).contains("IC publicado");
    assertThat(sinPeso.prioridad()).isEqualTo(PrioridadOrden.ALTA);
    assertThat(sinPeso.motivoFallback()).contains("peso configurado");
    assertThat(icInvalido.prioridad()).isEqualTo(PrioridadOrden.BAJA);
    assertThat(icInvalido.motivoFallback()).contains("fuera del rango");
    assertThat(pesoInvalido.prioridad()).isEqualTo(PrioridadOrden.MEDIA);
    assertThat(pesoInvalido.motivoFallback()).contains("no es positivo");
    assertThat(sinPesoMaximo.prioridad()).isEqualTo(PrioridadOrden.ALTA);
    assertThat(sinPesoMaximo.motivoFallback()).contains("peso máximo positivo");
    assertThat(versionInconsistente.prioridad()).isEqualTo(PrioridadOrden.ALTA);
    assertThat(versionInconsistente.motivoFallback()).contains("no coincide");
    assertThat(versionEICInvalidos.motivoFallback()).contains("no coincide", "fuera del rango");
  }

  private static PrioridadSugeridaIc.Calculo calcular(
      PrioridadOrden base, String ic, String peso, String maximo) {
    return PrioridadSugeridaIc.calcular(
        base, resultado(ic), VERSION, "/a", List.of(peso("/a", peso), peso("/otra", maximo)));
  }

  private static ResultadoIc resultado(String ic) {
    return resultado(ic, VERSION);
  }

  private static ResultadoIc resultado(String ic, UUID version) {
    ResultadoIc resultado = mock(ResultadoIc.class);
    when(resultado.getIndiceCalculado()).thenReturn(new BigDecimal(ic));
    when(resultado.getVersionConfiguracionId()).thenReturn(version);
    return resultado;
  }

  private static ConfiguracionIcElemento peso(String referencia, String valor) {
    ConfiguracionIcElemento configuracion = mock(ConfiguracionIcElemento.class);
    when(configuracion.getElementoRef()).thenReturn(referencia);
    when(configuracion.getPeso()).thenReturn(new BigDecimal(valor));
    return configuracion;
  }
}
