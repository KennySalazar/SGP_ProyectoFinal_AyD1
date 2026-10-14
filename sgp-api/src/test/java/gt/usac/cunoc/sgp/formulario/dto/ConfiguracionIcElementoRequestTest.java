package gt.usac.cunoc.sgp.formulario.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfiguracionIcElementoRequestTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void preparar() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void cerrar() {
    factory.close();
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.000001", "1", "1.5", "999999.999999"})
  void aceptaPesosPositivosRepresentables(String peso) {
    var request = request(new BigDecimal(peso));

    assertThat(validator.validate(request)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "-0.000001", "0.0000001", "1.1234567", "1000000"})
  void rechazaPesosInvalidos(String peso) {
    var errores = validator.validate(request(new BigDecimal(peso)));

    assertThat(errores).isNotEmpty();
    assertThat(errores)
        .allSatisfy(error -> assertThat(error.getPropertyPath().toString()).isEqualTo("peso"));
  }

  @Test
  void rechazaPesoNulo() {
    var errores = validator.validate(request(null));

    assertThat(errores)
        .anySatisfy(
            error -> {
              assertThat(error.getPropertyPath().toString()).isEqualTo("peso");
              assertThat(error.getMessage()).isEqualTo("El peso es obligatorio");
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void rechazaReferenciaVacia(String referencia) {
    var request = new ConfiguracionIcElementoRequest(referencia, BigDecimal.ONE, "Vigas");

    assertThat(validator.validate(request))
        .anySatisfy(
            error -> assertThat(error.getPropertyPath().toString()).isEqualTo("elementoRef"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void rechazaDescripcionVacia(String descripcion) {
    var request = new ConfiguracionIcElementoRequest("/vigas", BigDecimal.ONE, descripcion);

    assertThat(validator.validate(request))
        .anySatisfy(
            error -> assertThat(error.getPropertyPath().toString()).isEqualTo("descripcion"));
  }

  private ConfiguracionIcElementoRequest request(BigDecimal peso) {
    return new ConfiguracionIcElementoRequest("/vigas", peso, "Vigas");
  }
}
