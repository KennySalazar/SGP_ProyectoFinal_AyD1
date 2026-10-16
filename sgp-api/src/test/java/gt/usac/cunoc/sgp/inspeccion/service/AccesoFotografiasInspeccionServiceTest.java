package gt.usac.cunoc.sgp.inspeccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.puente.entity.AsignacionPuente;
import gt.usac.cunoc.sgp.puente.repository.AsignacionPuenteRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccesoFotografiasInspeccionServiceTest {
  private final AsignacionPuenteRepository asignaciones = mock(AsignacionPuenteRepository.class);
  private final Inspeccion inspeccion = mock(Inspeccion.class);
  private final UUID autor = UUID.randomUUID();
  private final UUID lector = UUID.randomUUID();
  private final UUID asignacionId = UUID.randomUUID();
  private final AccesoFotografiasInspeccionService acceso =
      new AccesoFotografiasInspeccionService(
          asignaciones, new PoliticaArchivosInspeccion(new ReglasEvidenciaFotografica()));

  @BeforeEach
  void preparar() {
    when(inspeccion.getId()).thenReturn(UUID.randomUUID());
    when(inspeccion.getAutorId()).thenReturn(autor);
    when(inspeccion.getEstado()).thenReturn("BORRADOR");
  }

  @Test
  void autorNoNecesitaAsignacionNiConsultaAcademica() {
    acceso.validarLectura(inspeccion, autor);
    verifyNoInteractions(asignaciones);
  }

  @Test
  void relacionPersistidaNoRevocadaPermiteLectura() {
    when(inspeccion.getAsignacionPuenteId()).thenReturn(asignacionId);
    when(asignaciones.findByIdAndCursoEstudiante_Curso_Catedratico_IdAndRevocadoEnIsNull(
            asignacionId, lector))
        .thenReturn(Optional.of(mock(AsignacionPuente.class)));

    assertThatCode(() -> acceso.validarLectura(inspeccion, lector)).doesNotThrowAnyException();
    verify(asignaciones)
        .findByIdAndCursoEstudiante_Curso_Catedratico_IdAndRevocadoEnIsNull(asignacionId, lector);
  }

  @Test
  void vinculoNuloNoSeDeduceDelPuenteNiDelRol() {
    rechaza(() -> acceso.validarLectura(inspeccion, lector), 404);
    verifyNoInteractions(asignaciones);
  }

  @Test
  void relacionAusenteORevocadaSeConsultaCadaVez() {
    when(inspeccion.getAsignacionPuenteId()).thenReturn(asignacionId);
    when(asignaciones.findByIdAndCursoEstudiante_Curso_Catedratico_IdAndRevocadoEnIsNull(
            asignacionId, lector))
        .thenReturn(Optional.of(mock(AsignacionPuente.class)), Optional.empty());

    acceso.validarLectura(inspeccion, lector);
    rechaza(() -> acceso.validarLectura(inspeccion, lector), 404);

    verify(asignaciones, times(2))
        .findByIdAndCursoEstudiante_Curso_Catedratico_IdAndRevocadoEnIsNull(asignacionId, lector);
  }

  @Test
  void fueraDeBorradorNoConsultaNiAmpliaPermisosDelLector() {
    when(inspeccion.getEstado()).thenReturn("PUBLICADA");
    rechaza(() -> acceso.validarLectura(inspeccion, lector), 404);
    acceso.validarLectura(inspeccion, autor);
    verifyNoInteractions(asignaciones);
  }

  @Test
  void inspeccionEliminadaOSesionAusenteNoConsultaAsignaciones() {
    when(inspeccion.getEliminadoEn()).thenReturn(Instant.now());
    rechaza(() -> acceso.validarLectura(inspeccion, lector), 404);
    rechaza(() -> acceso.validarLectura(inspeccion, autor), 404);
    rechaza(() -> acceso.validarLectura(inspeccion, null), 401);
    rechaza(() -> acceso.validarLectura(null, lector), 404);
    verifyNoInteractions(asignaciones);
  }

  private void rechaza(Runnable accion, int status) {
    assertThatThrownBy(accion::run)
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.getStatus().value()).isEqualTo(status));
  }
}
