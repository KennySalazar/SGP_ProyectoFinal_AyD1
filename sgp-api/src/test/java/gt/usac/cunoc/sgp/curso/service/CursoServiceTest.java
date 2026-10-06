package gt.usac.cunoc.sgp.curso.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.curso.dto.ActualizarCursoRequest;
import gt.usac.cunoc.sgp.curso.dto.CrearCursoRequest;
import gt.usac.cunoc.sgp.curso.dto.CursoResponse;
import gt.usac.cunoc.sgp.curso.entity.Asignatura;
import gt.usac.cunoc.sgp.curso.entity.Curso;
import gt.usac.cunoc.sgp.curso.mapper.CursoMapper;
import gt.usac.cunoc.sgp.curso.repository.AsignaturaRepository;
import gt.usac.cunoc.sgp.curso.repository.CursoRepository;
import gt.usac.cunoc.sgp.usuario.entity.Role;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CursoServiceTest {

  private final AsignaturaRepository asignaturas = mock(AsignaturaRepository.class);
  private final CursoRepository cursos = mock(CursoRepository.class);
  private final UserAccountRepository usuarios = mock(UserAccountRepository.class);
  private final CursoMapper mapper = mock(CursoMapper.class);
  private final CursoService service =
      new CursoService(
          asignaturas,
          cursos,
          usuarios,
          mapper,
          Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void rechazaNombreYPeriodoDuplicados() {
    CrearCursoRequest request = request();
    when(cursos.existsByAsignatura_NombreIgnoreCaseAndPeriodo("Analisis de Sistemas", "2026-2"))
        .thenReturn(true);

    assertThatThrownBy(() -> service.crear(request))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              assertThat(exception.getStatus().value()).isEqualTo(409);
              assertThat(exception.getCode()).isEqualTo("curso_duplicado");
            });
  }

  @Test
  void creaCursoVigente() {
    CrearCursoRequest request = request();
    UserAccount catedratico = mock(UserAccount.class);
    Role role = mock(Role.class);
    CursoResponse response =
        new CursoResponse(
            UUID.randomUUID(),
            request.nombre(),
            request.periodo(),
            null,
            request.fechaInicio(),
            request.fechaFin(),
            "VIGENTE");
    when(cursos.existsByAsignatura_NombreIgnoreCaseAndPeriodo("Analisis de Sistemas", "2026-2"))
        .thenReturn(false);
    when(usuarios.findWithRoleById(request.catedraticoId())).thenReturn(Optional.of(catedratico));
    when(catedratico.isActive()).thenReturn(true);
    when(catedratico.getRole()).thenReturn(role);
    when(role.getName()).thenReturn(RoleName.CATEDRATICO);
    when(cursos.save(any(Curso.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(mapper.toResponse(any(Curso.class))).thenReturn(response);

    CursoResponse resultado = service.crear(request);

    assertThat(resultado.estado()).isEqualTo("VIGENTE");
    verify(asignaturas).save(any());
    verify(cursos).save(any(Curso.class));
  }

  @Test
  void finalizaCursoVigenteSinEliminarlo() {
    UUID id = UUID.randomUUID();
    Curso curso =
        new Curso(
            null,
            "2026-2",
            null,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 11, 1),
            Instant.parse("2026-07-01T00:00:00Z"));
    when(cursos.findById(id)).thenReturn(Optional.of(curso));

    service.finalizar(id);

    assertThat(curso.isActivo()).isFalse();
    verify(mapper).toResponse(curso);
  }

  @Test
  void actualizaCursoUsandoNuevaAsignaturaSinMutarLaOriginal() {
    UUID id = UUID.randomUUID();
    Asignatura asignaturaOriginal = mock(Asignatura.class);
    Asignatura nuevaAsignatura = mock(Asignatura.class);
    UserAccount catedratico = mock(UserAccount.class);
    Role role = mock(Role.class);
    Curso curso =
        new Curso(
            asignaturaOriginal,
            "2026-1",
            null,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 5, 1),
            Instant.parse("2026-01-01T00:00:00Z"));
    ActualizarCursoRequest request =
        new ActualizarCursoRequest(
            "Diseno de Sistemas",
            "2026-2",
            UUID.randomUUID(),
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 11, 1));
    when(cursos.findById(id)).thenReturn(Optional.of(curso));
    when(cursos.existsByAsignatura_NombreIgnoreCaseAndPeriodoAndIdNot(
            "Diseno de Sistemas", "2026-2", id))
        .thenReturn(false);
    when(asignaturas.findByNombreIgnoreCase("Diseno de Sistemas"))
        .thenReturn(Optional.of(nuevaAsignatura));
    when(usuarios.findWithRoleById(request.catedraticoId())).thenReturn(Optional.of(catedratico));
    when(catedratico.isActive()).thenReturn(true);
    when(catedratico.getRole()).thenReturn(role);
    when(role.getName()).thenReturn(RoleName.CATEDRATICO);

    service.actualizar(id, request);

    assertThat(curso.getPeriodo()).isEqualTo("2026-2");
    assertThat(curso.getCatedratico()).isSameAs(catedratico);
    assertThat(curso.getAsignatura()).isSameAs(nuevaAsignatura);
    verifyNoInteractions(asignaturaOriginal);
  }

  @Test
  void creaYAsociaAsignaturaSolicitadaAlActualizarSinMutarLaOriginal() {
    UUID id = UUID.randomUUID();
    Asignatura asignaturaOriginal = mock(Asignatura.class);
    UserAccount catedratico = mock(UserAccount.class);
    Role role = mock(Role.class);
    Curso curso =
        new Curso(
            asignaturaOriginal,
            "2026-1",
            null,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 5, 1),
            Instant.parse("2026-01-01T00:00:00Z"));
    ActualizarCursoRequest request =
        new ActualizarCursoRequest(
            "Arquitectura de Sistemas",
            "2026-2",
            UUID.randomUUID(),
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 11, 1));
    when(cursos.findById(id)).thenReturn(Optional.of(curso));
    when(cursos.existsByAsignatura_NombreIgnoreCaseAndPeriodoAndIdNot(
            "Arquitectura de Sistemas", "2026-2", id))
        .thenReturn(false);
    when(asignaturas.findByNombreIgnoreCase("Arquitectura de Sistemas"))
        .thenReturn(Optional.empty());
    when(asignaturas.save(any(Asignatura.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(usuarios.findWithRoleById(request.catedraticoId())).thenReturn(Optional.of(catedratico));
    when(catedratico.isActive()).thenReturn(true);
    when(catedratico.getRole()).thenReturn(role);
    when(role.getName()).thenReturn(RoleName.CATEDRATICO);

    service.actualizar(id, request);

    assertThat(curso.getAsignatura()).isNotSameAs(asignaturaOriginal);
    assertThat(curso.getAsignatura().getNombre()).isEqualTo("Arquitectura de Sistemas");
    verify(asignaturas).save(any(Asignatura.class));
    verifyNoInteractions(asignaturaOriginal);
  }

  @Test
  void rechazaFinalizarCursoYaFinalizado() {
    UUID id = UUID.randomUUID();
    Curso curso =
        new Curso(
            null,
            "2026-2",
            null,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 11, 1),
            Instant.parse("2026-07-01T00:00:00Z"));
    curso.finalizar(Instant.now());
    when(cursos.findById(id)).thenReturn(Optional.of(curso));

    assertThatThrownBy(() -> service.finalizar(id))
        .isInstanceOfSatisfying(
            ApiException.class, exception -> assertThat(exception.getCode()).isEqualTo("curso_finalizado"));
  }

  private CrearCursoRequest request() {
    return new CrearCursoRequest(
        "Analisis de Sistemas",
        "2026-2",
        UUID.randomUUID(),
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 11, 1));
  }
}
