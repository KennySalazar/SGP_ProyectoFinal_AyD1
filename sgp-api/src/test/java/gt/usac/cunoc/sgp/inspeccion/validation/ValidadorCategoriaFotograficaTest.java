package gt.usac.cunoc.sgp.inspeccion.validation;

import static org.assertj.core.api.Assertions.*;

import gt.usac.cunoc.sgp.archivo.model.CategoriaFoto;
import gt.usac.cunoc.sgp.formulario.validation.AnclajeInvalidoException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ValidadorCategoriaFotograficaTest {
  private final ValidadorCategoriaFotografica validador = new ValidadorCategoriaFotografica();

  @ParameterizedTest
  @EnumSource(CategoriaFoto.class)
  void aceptaExactamenteElCatalogoExistente(CategoriaFoto categoria) {
    assertThat(validador.validar(categoria.name())).isEqualTo(categoria);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"acceso", "/ACCESO", "/fotografias/ACCESO", " ACCESO ", "NUEVA"})
  void rechazaCodigosInvalidosSinInventarRutas(String codigo) {
    assertThatThrownBy(() -> validador.validar(codigo))
        .isInstanceOfSatisfying(
            AnclajeInvalidoException.class,
            error ->
                assertThat(error.getMotivo())
                    .isEqualTo(AnclajeInvalidoException.Motivo.CATEGORIA_FOTOGRAFICA_INVALIDA));
  }
}
