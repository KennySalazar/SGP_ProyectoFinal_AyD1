package gt.usac.cunoc.sgp.inspeccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.inspeccion.dto.ProgresoSeccionResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import org.junit.jupiter.api.Test;

class CalculadorProgresoBorradorTest {

  private final CalculadorProgresoBorrador calculador = new CalculadorProgresoBorrador();
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void recomendacionesVaciasCompletanLaSeccionOtros() throws Exception {
    ProgresoSeccionResponse otros = calcularOtros("{}");
    assertThat(otros.camposRequeridos()).isEqualTo(4);
    assertThat(otros.camposCompletados()).isEqualTo(4);
    assertThat(otros.porcentaje()).isEqualTo(100.0);
    assertThat(otros.completada()).isTrue();
  }

  @Test
  void recomendacionesConTextoTambienCompletanLaSeccion() throws Exception {
    assertThat(calcularOtros("{\"mantenimiento\":\"Revisar drenajes\"}").completada()).isTrue();
  }

  @Test
  void recomendacionesAusentesPermanecenPendientes() throws Exception {
    ProgresoSeccionResponse otros = calcularOtros(null);
    assertThat(otros.camposCompletados()).isEqualTo(3);
    assertThat(otros.porcentaje()).isEqualTo(75.0);
    assertThat(otros.completada()).isFalse();
  }

  @Test
  void recomendacionesNulasPermanecenPendientes() throws Exception {
    assertThat(calcularOtros("null").completada()).isFalse();
  }

  @Test
  void recomendacionesConTipoIncorrectoNoSeCuentanComoCompletas() throws Exception {
    for (String valor : new String[] {"\"texto\"", "[]", "false", "1"}) {
      assertThat(calcularOtros(valor).completada()).as(valor).isFalse();
    }
  }

  @Test
  void datosAusentesNoCompletanLaSeccionOtros() {
    Inspeccion inspeccion = mock(Inspeccion.class);
    ProgresoSeccionResponse otros =
        calculador.calcularProgreso(inspeccion).secciones().stream()
            .filter(seccion -> seccion.seccion().equals("otros"))
            .findFirst()
            .orElseThrow();
    assertThat(otros.camposCompletados()).isZero();
    assertThat(otros.completada()).isFalse();
  }

  @Test
  void inspeccionNulaConservaElResultadoVacio() {
    var progreso = calculador.calcularProgreso(null);
    assertThat(progreso.totalCamposRequeridos()).isZero();
    assertThat(progreso.totalCamposCompletados()).isZero();
    assertThat(progreso.secciones()).isEmpty();
  }

  private ProgresoSeccionResponse calcularOtros(String recomendaciones) throws Exception {
    var datos =
        json.readTree(
            """
        {
          "otros": {
            "senalizacion": {"informativa": {"existe": false}},
            "alumbrado_artificial_existe": false,
            "drenajes_aledanos": {"existe": false}
          }
        }
        """);
    if (recomendaciones != null) {
      ((com.fasterxml.jackson.databind.node.ObjectNode) datos.get("otros"))
          .set("recomendaciones", json.readTree(recomendaciones));
    }
    Inspeccion inspeccion = mock(Inspeccion.class);
    when(inspeccion.getDatos()).thenReturn(datos);
    return calculador.calcularProgreso(inspeccion).secciones().stream()
        .filter(seccion -> seccion.seccion().equals("otros"))
        .findFirst()
        .orElseThrow();
  }
}
