package gt.usac.cunoc.sgp.inspeccion.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.puente.repository.AsignacionPuenteRepository;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

class FotografiaRevisionResueltaTest {
  private final UUID id = UUID.randomUUID();
  private final UUID autor = UUID.randomUUID();
  private final UUID revisor = UUID.randomUUID();
  private Inspeccion inspeccion;
  private RevisionRepository revisiones;
  private FotografiaService servicio;

  @BeforeEach
  void preparar() {
    var inspecciones = mock(InspeccionRepository.class);
    revisiones = mock(RevisionRepository.class);
    inspeccion = mock(Inspeccion.class);
    when(inspeccion.getAutorId()).thenReturn(autor);
    when(inspecciones.findByIdAndEliminadoEnIsNull(id)).thenReturn(Optional.of(inspeccion));
    servicio =
        new FotografiaService(
            inspecciones,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            mock(AsignacionPuenteRepository.class),
            revisiones,
            mock(PlatformTransactionManager.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"CAMBIOS_SOLICITADOS", "BORRADOR", "RECHAZADA"})
  void revisorDelCicloResueltoConservaLectura(String estado) {
    when(inspeccion.getEstado()).thenReturn(estado);
    when(revisiones.existsByInspeccionIdAndRevisorIdAndEstado(id, revisor, "RESUELTA"))
        .thenReturn(true);
    assertThatCode(() -> servicio.autorizarLectura(id, revisor, RoleName.CATEDRATICO))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"CAMBIOS_SOLICITADOS", "BORRADOR", "RECHAZADA"})
  void catedraticoAjenoNoPuedeConsultar(String estado) {
    when(inspeccion.getEstado()).thenReturn(estado);
    assertThatThrownBy(() -> servicio.autorizarLectura(id, revisor, RoleName.CATEDRATICO))
        .isInstanceOf(ApiException.class)
        .matches(error -> ((ApiException) error).getStatus() == HttpStatus.NOT_FOUND);
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"ADMINISTRADOR", "ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void otrosRolesNoObtienenLecturaDeUnaRevisionAjena(RoleName rol) {
    when(inspeccion.getEstado()).thenReturn("CAMBIOS_SOLICITADOS");
    assertThatThrownBy(() -> servicio.autorizarLectura(id, revisor, rol))
        .isInstanceOf(ApiException.class)
        .matches(error -> ((ApiException) error).getStatus() == HttpStatus.NOT_FOUND);
  }
}
