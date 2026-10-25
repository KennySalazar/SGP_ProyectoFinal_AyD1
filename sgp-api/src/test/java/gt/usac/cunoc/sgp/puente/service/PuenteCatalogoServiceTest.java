package gt.usac.cunoc.sgp.puente.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.mantenimiento.service.PlanMantenimientoService;
import gt.usac.cunoc.sgp.puente.dto.PuenteCatalogoResponse;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapper;
import gt.usac.cunoc.sgp.puente.repository.DepartamentoRepository;
import gt.usac.cunoc.sgp.puente.repository.MunicipioRepository;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class PuenteCatalogoServiceTest {

  private final PuenteRepository repository = mock(PuenteRepository.class);
  private final PuenteMapper mapper = mock(PuenteMapper.class);
  private final PuenteService service =
      new PuenteService(
          mock(DepartamentoRepository.class),
          mock(MunicipioRepository.class),
          repository,
          mapper,
          mock(Validator.class),
          Clock.systemUTC(),
          mock(PlanMantenimientoService.class));

  @ParameterizedTest
  @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
  void rechazaPaginacionFueraDeRango(int pagina, int tamanio) {
    assertThatThrownBy(() -> service.listarCatalogo(null, null, pagina, tamanio))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("paginacion_invalida"));
    verifyNoInteractions(repository, mapper);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Desconocido", "BUENO"})
  void rechazaEstadoInvalido(String estado) {
    assertThatThrownBy(() -> service.listarCatalogo(null, estado, 0, 20))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("estado_invalido"));
    verifyNoInteractions(repository, mapper);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Bueno", "Regular", "Malo", "Sin evaluar"})
  void consultaElEstadoSolicitadoYConservaPaginacionYTotal(String estado) {
    UUID departamentoId = UUID.randomUUID();
    Puente puente = mock(Puente.class);
    PuenteCatalogoResponse respuesta = mock(PuenteCatalogoResponse.class);
    var pageable = pagina(1, 20);

    when(repository.findCatalogoPorEstado(departamentoId, true, estado, pageable))
        .thenReturn(new PageImpl<>(List.of(puente), pageable, 41));
    when(mapper.toCatalogoResponse(puente)).thenReturn(respuesta);

    var resultado = service.listarCatalogo(departamentoId, estado, 1, 20);

    assertThat(resultado.getContent()).containsExactly(respuesta);
    assertThat(resultado.getNumber()).isEqualTo(1);
    assertThat(resultado.getSize()).isEqualTo(20);
    assertThat(resultado.getTotalElements()).isEqualTo(41);
    assertThat(resultado.getTotalPages()).isEqualTo(3);
    verify(repository).findCatalogoPorEstado(departamentoId, true, estado, pageable);
    verify(mapper).toCatalogoResponse(puente);
  }

  @Test
  void sinFiltroDeEstadoConsultaLosPuentesActivos() {
    var pageable = pagina(0, 20);
    when(repository.findCatalogo(null, true, pageable)).thenReturn(Page.empty(pageable));

    var resultado = service.listarCatalogo(null, null, 0, 20);

    assertThat(resultado.getContent()).isEmpty();
    verify(repository).findCatalogo(null, true, pageable);
    verifyNoInteractions(mapper);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void conservaElFiltroDeActividadAlConsultarEstado(boolean activo) {
    var pageable = pagina(0, 20);
    when(repository.findCatalogoPorEstado(null, activo, "Regular", pageable))
        .thenReturn(Page.empty(pageable));

    service.listarCatalogo(null, "Regular", activo, 0, 20);

    verify(repository).findCatalogoPorEstado(null, activo, "Regular", pageable);
  }

  @Test
  void permiteConsultarEstadoSinRestringirActividad() {
    var pageable = pagina(0, 20);
    when(repository.findCatalogoPorEstado(null, null, "Malo", pageable))
        .thenReturn(Page.empty(pageable));

    service.listarCatalogo(null, "Malo", null, 0, 20);

    verify(repository).findCatalogoPorEstado(null, null, "Malo", pageable);
  }

  private PageRequest pagina(int numero, int tamanio) {
    return PageRequest.of(numero, tamanio, Sort.by(Sort.Order.asc("nombre"), Sort.Order.asc("id")));
  }
}
