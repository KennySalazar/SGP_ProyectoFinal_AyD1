package gt.usac.cunoc.sgp.puente.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapper;
import gt.usac.cunoc.sgp.puente.repository.DepartamentoRepository;
import gt.usac.cunoc.sgp.puente.repository.MunicipioRepository;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import jakarta.validation.Validator;
import java.time.Clock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PuenteCatalogoServiceTest {

  private final PuenteRepository repository = mock(PuenteRepository.class);
  private final PuenteService service =
      new PuenteService(
          mock(DepartamentoRepository.class),
          mock(MunicipioRepository.class),
          repository,
          mock(PuenteMapper.class),
          mock(Validator.class),
          Clock.systemUTC());

  @ParameterizedTest
  @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
  void rechazaPaginacionFueraDeRango(int pagina, int tamanio) {
    assertThatThrownBy(() -> service.listarCatalogo(null, null, pagina, tamanio))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("paginacion_invalida"));
    verifyNoInteractions(repository);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Desconocido", "BUENO"})
  void rechazaEstadoInvalido(String estado) {
    assertThatThrownBy(() -> service.listarCatalogo(null, estado, 0, 20))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("estado_invalido"));
    verifyNoInteractions(repository);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Bueno", "Regular", "Malo"})
  void estadosEvaluadosNoInventanResultados(String estado) {
    var resultado = service.listarCatalogo(null, estado, 0, 20);
    assertThat(resultado.getContent()).isEmpty();
    assertThat(resultado.getTotalElements()).isZero();
    verifyNoInteractions(repository);
  }
}
