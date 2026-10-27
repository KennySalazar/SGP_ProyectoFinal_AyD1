package gt.usac.cunoc.sgp.revision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionResumenResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.mapper.InspeccionMapper;
import gt.usac.cunoc.sgp.inspeccion.service.TransicionInspeccionService;
import gt.usac.cunoc.sgp.revision.entity.Revision;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;

class RevisionServiceTest {

  private static final Instant AHORA = Instant.parse("2026-10-18T16:00:00Z");

  private final RevisionRepository revisiones = mock(RevisionRepository.class);
  private final TransicionInspeccionService transiciones = mock(TransicionInspeccionService.class);
  private final InspeccionMapper mapper = mock(InspeccionMapper.class);
  private final RevisionService service =
      new RevisionService(revisiones, transiciones, mapper, Clock.fixed(AHORA, ZoneOffset.UTC));

  @Test
  void iniciaCicloPendienteYRegistraLaFecha() {
    UUID revisionId = UUID.randomUUID();
    UUID inspeccionId = UUID.randomUUID();
    UUID revisorId = UUID.randomUUID();
    Revision revision = mock(Revision.class);
    Inspeccion inspeccion = mock(Inspeccion.class);
    InspeccionResumenResponse respuesta = mock(InspeccionResumenResponse.class);
    when(revisiones.findByIdForUpdate(revisionId)).thenReturn(Optional.of(revision));
    when(revision.getId()).thenReturn(revisionId);
    when(revision.getRevisorId()).thenReturn(revisorId);
    when(revision.getEstado()).thenReturn("PENDIENTE");
    when(revision.getInspeccion()).thenReturn(inspeccion);
    when(inspeccion.getId()).thenReturn(inspeccionId);
    when(transiciones.iniciarRevision(inspeccionId, revisorId, revisionId)).thenReturn(respuesta);

    assertThat(service.iniciar(revisionId, revisorId)).isSameAs(respuesta);

    verify(revision).iniciar(AHORA);
    verify(revisiones).saveAndFlush(revision);
  }

  @Test
  void reanudarCicloEnCursoNoCambiaEstadoNiFecha() {
    UUID revisionId = UUID.randomUUID();
    UUID revisorId = UUID.randomUUID();
    Revision revision = mock(Revision.class);
    Inspeccion inspeccion = mock(Inspeccion.class);
    InspeccionResumenResponse respuesta = mock(InspeccionResumenResponse.class);
    when(revisiones.findByIdForUpdate(revisionId)).thenReturn(Optional.of(revision));
    when(revision.getRevisorId()).thenReturn(revisorId);
    when(revision.getEstado()).thenReturn("EN_CURSO");
    when(revision.getInspeccion()).thenReturn(inspeccion);
    when(inspeccion.getEstado()).thenReturn("EN_REVISION");
    when(mapper.toResumenResponse(inspeccion)).thenReturn(respuesta);

    assertThat(service.iniciar(revisionId, revisorId)).isSameAs(respuesta);

    verify(revision, never()).iniciar(AHORA);
    verify(revisiones, never()).saveAndFlush(revision);
  }

  @Test
  void ocultaRevisionQuePerteneceAOtroCatedratico() {
    UUID revisionId = UUID.randomUUID();
    Revision revision = mock(Revision.class);
    when(revisiones.findByIdForUpdate(revisionId)).thenReturn(Optional.of(revision));
    when(revision.getRevisorId()).thenReturn(UUID.randomUUID());

    assertThatThrownBy(() -> service.iniciar(revisionId, UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("revision_no_encontrada"));

    verify(revisiones, never()).saveAndFlush(revision);
  }

  @Test
  void limitaElTamanioDePaginaACien() {
    assertThatThrownBy(
            () ->
                service.listarBandeja(
                    UUID.randomUUID(), null, null, null, null, null, null, 0, 101))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("paginacion_invalida"));
  }

  @Test
  void segundaPeticionConcurrenteRecibeConflicto() {
    UUID revisionId = UUID.randomUUID();
    when(revisiones.findByIdForUpdate(revisionId))
        .thenThrow(new PessimisticLockingFailureException("bloqueada"));

    assertThatThrownBy(() -> service.iniciar(revisionId, UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("revision_en_proceso"));
  }
}
