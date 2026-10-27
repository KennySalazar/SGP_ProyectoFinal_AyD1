package gt.usac.cunoc.sgp.formulario.validation;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class CalibracionIcValidacionTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void plantillaProvisionalCompletaEsValida() throws Exception {
    try (var entrada = getClass().getResourceAsStream("/formulario/sieca-v1/elementos-ic.json")) {
      assertThatCode(() -> ValidadorCalibracionIc.validar(json.readTree(entrada)))
          .doesNotThrowAnyException();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null",
        "[]",
        "{\"reglas_evaluacion_ic\":null}",
        "{\"reglas_evaluacion_ic\":{\"rangos_estado\":{\"MALO\":0,\"REGULAR\":80,\"BUENO\":75}}}",
        "{\"reglas_evaluacion_ic\":{\"rangos_estado\":{\"MALO\":1,\"REGULAR\":50,\"BUENO\":75}}}",
        "{\"reglas_evaluacion_ic\":{\"rangos_estado\":{\"MALO\":0,\"REGULAR\":50,\"BUENO\":101}}}",
        "{\"reglas_evaluacion_ic\":{\"conversion_indicadores\":{\"numericos\":{\"indicador\":{\"umbrales\":{\"LEVE\":1,\"MODERADO\":1,\"SEVERO\":3}}}}}}",
        "{\"reglas_evaluacion_ic\":{\"agregacion\":{\"indicadores_de_un_elemento\":\"OTRO\"}}}"
      })
  void rechazaConfiguracionInvalidaConCampoYMotivo(String contenido) throws Exception {
    var nodo = json.readTree(contenido);
    assertThatThrownBy(() -> ValidadorCalibracionIc.validar(nodo))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getErrores())
                  .isNotEmpty()
                  .allSatisfy(
                      error -> {
                        assertThat(error.get("campo")).isNotBlank();
                        assertThat(error.get("mensaje")).isNotBlank();
                      });
            });
  }

  @Test
  void admiteBorradoresSinConfigurarTodavia() throws Exception {
    ValidadorCalibracionIc.validar(json.readTree("{}"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "27", "\"27\""})
  void aceptaUnaVersionConOSinComillas(String valor) {
    assertThat(VersionCalibracionHttp.exigir(valor)).isGreaterThanOrEqualTo(0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"*", "-1", "W/\"1\"", "1,2", "abc", "9223372036854775808"})
  void rechazaVersionesInvalidas(String valor) {
    assertThatThrownBy(() -> VersionCalibracionHttp.exigir(valor))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void requiereVersionParaGuardar() {
    assertThatThrownBy(() -> VersionCalibracionHttp.exigir(null))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED));
  }
}
