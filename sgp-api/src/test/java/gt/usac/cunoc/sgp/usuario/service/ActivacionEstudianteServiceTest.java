package gt.usac.cunoc.sgp.usuario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gt.usac.cunoc.sgp.common.audit.AuditService;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.curso.entity.Asignatura;
import gt.usac.cunoc.sgp.curso.entity.Curso;
import gt.usac.cunoc.sgp.curso.entity.CursoEstudiante;
import gt.usac.cunoc.sgp.curso.repository.CursoEstudianteRepository;
import gt.usac.cunoc.sgp.curso.repository.CursoRepository;
import gt.usac.cunoc.sgp.usuario.dto.ActivarEstudianteRequest;
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

class ActivacionEstudianteServiceTest {

  private final UserAccountRepository usuarios = mock(UserAccountRepository.class);
  private final CursoRepository cursos = mock(CursoRepository.class);
  private final CursoEstudianteRepository inscripciones = mock(CursoEstudianteRepository.class);
  private final AuditService auditoria = mock(AuditService.class);
  private final ActivacionEstudianteService service =
      new ActivacionEstudianteService(
          usuarios,
          cursos,
          inscripciones,
          auditoria,
          Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void activaEstudiantePendienteLoInscribeYAuditaLaAccion() {
    UUID estudianteId = UUID.randomUUID();
    UUID cursoId = UUID.randomUUID();
    UserAccount catedratico = usuario(RoleName.CATEDRATICO, true, true);
    UserAccount estudiante = usuario(RoleName.ESTUDIANTE, true, false);
    Curso curso = curso(catedratico, true);
    ActivarEstudianteRequest request = new ActivarEstudianteRequest(estudianteId, cursoId, "A");
    when(usuarios.findByEmail("catedratico@ejemplo.com")).thenReturn(Optional.of(catedratico));
    when(usuarios.findWithRoleById(estudianteId)).thenReturn(Optional.of(estudiante));
    when(cursos.findByIdAndCatedratico_Id(cursoId, catedratico.getId()))
        .thenReturn(Optional.of(curso));
    when(inscripciones.save(any(CursoEstudiante.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.activar("catedratico@ejemplo.com", request);

    assertThat(response.message()).isEqualTo("Estudiante activado y vinculado al curso correctamente");
    verify(estudiante).activate();
    verify(inscripciones).save(any(CursoEstudiante.class));
    verify(auditoria)
        .register(
            eq(catedratico.getId()),
            eq("ACTIVAR_ESTUDIANTE"),
            eq("curso_estudiante"),
            any(UUID.class),
            anyMap(),
            eq(Instant.parse("2026-10-06T00:00:00Z")));
  }

  @Test
  void rechazaCursoFinalizado() {
    UUID estudianteId = UUID.randomUUID();
    UUID cursoId = UUID.randomUUID();
    UserAccount catedratico = usuario(RoleName.CATEDRATICO, true, true);
    UserAccount estudiante = usuario(RoleName.ESTUDIANTE, true, false);
    Curso curso = curso(catedratico, false);
    when(usuarios.findByEmail("catedratico@ejemplo.com")).thenReturn(Optional.of(catedratico));
    when(usuarios.findWithRoleById(estudianteId)).thenReturn(Optional.of(estudiante));
    when(cursos.findByIdAndCatedratico_Id(cursoId, catedratico.getId()))
        .thenReturn(Optional.of(curso));

    assertThatThrownBy(
            () ->
                service.activar(
                    "catedratico@ejemplo.com",
                    new ActivarEstudianteRequest(estudianteId, cursoId, "A")))
        .isInstanceOfSatisfying(
            ApiException.class, exception -> assertThat(exception.getCode()).isEqualTo("curso_finalizado"));

    verifyNoInteractions(inscripciones, auditoria);
  }

  @Test
  void rechazaEstudianteYaActivoSinCrearInscripcionDuplicada() {
    UUID estudianteId = UUID.randomUUID();
    UUID cursoId = UUID.randomUUID();
    UserAccount catedratico = usuario(RoleName.CATEDRATICO, true, true);
    UserAccount estudiante = usuario(RoleName.ESTUDIANTE, true, true);
    Curso curso = curso(catedratico, true);
    when(usuarios.findByEmail("catedratico@ejemplo.com")).thenReturn(Optional.of(catedratico));
    when(usuarios.findWithRoleById(estudianteId)).thenReturn(Optional.of(estudiante));
    when(cursos.findByIdAndCatedratico_Id(cursoId, catedratico.getId()))
        .thenReturn(Optional.of(curso));
    when(inscripciones.existsByEstudiante_IdAndEstado(any(UUID.class), eq("ACTIVO"))).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.activar(
                    "catedratico@ejemplo.com",
                    new ActivarEstudianteRequest(estudianteId, cursoId, "A")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              assertThat(exception.getStatus().value()).isEqualTo(409);
              assertThat(exception.getCode()).isEqualTo("estudiante_ya_activado");
            });

    verify(inscripciones).existsByEstudiante_IdAndEstado(any(UUID.class), eq("ACTIVO"));
    verifyNoInteractions(auditoria);
  }

  private UserAccount usuario(RoleName roleName, boolean verificado, boolean activado) {
    UserAccount usuario = mock(UserAccount.class);
    Role role = mock(Role.class);
    when(usuario.getId()).thenReturn(UUID.randomUUID());
    when(usuario.isActive()).thenReturn(true);
    when(usuario.isVerified()).thenReturn(verificado);
    when(usuario.isActivated()).thenReturn(activado);
    when(usuario.getRole()).thenReturn(role);
    when(role.getName()).thenReturn(roleName);
    return usuario;
  }

  private Curso curso(UserAccount catedratico, boolean activo) {
    Curso curso =
        new Curso(
            mock(Asignatura.class),
            "2026-2",
            catedratico,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 11, 1),
            Instant.parse("2026-07-01T00:00:00Z"));
    if (!activo) {
      curso.finalizar(Instant.parse("2026-11-02T00:00:00Z"));
    }
    return curso;
  }
}
