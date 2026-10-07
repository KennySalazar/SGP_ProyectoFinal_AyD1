package gt.usac.cunoc.sgp.puente.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gt.usac.cunoc.sgp.common.audit.AuditService;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.curso.repository.CursoEstudianteRepository;
import gt.usac.cunoc.sgp.puente.dto.AsignarPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.RevocarAsignacionPuenteRequest;
import gt.usac.cunoc.sgp.puente.entity.AsignacionPuente;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.AsignacionPuenteRepository;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.usuario.entity.Role;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AsignacionPuenteServiceTest {

  private static final Instant AHORA = Instant.parse("2026-10-07T00:00:00Z");

  private final UserAccountRepository usuarios = mock(UserAccountRepository.class);
  private final CursoEstudianteRepository inscripciones = mock(CursoEstudianteRepository.class);
  private final PuenteRepository puentes = mock(PuenteRepository.class);
  private final AsignacionPuenteRepository asignaciones = mock(AsignacionPuenteRepository.class);
  private final AuditService auditoria = mock(AuditService.class);
  private final AsignacionPuenteService service =
      new AsignacionPuenteService(
          usuarios,
          inscripciones,
          puentes,
          asignaciones,
          auditoria,
          Clock.fixed(AHORA, ZoneOffset.UTC));

  @Test
  void asignaPuenteActivoAEstudianteActivoDeCursoVigenteYAudita() {
    UUID inscripcionId = UUID.randomUUID();
    UUID puenteId = UUID.randomUUID();
    UserAccount catedratico = catedratico();
    Puente puente = mock(Puente.class);
    var inscripcion = inscripcion(true, "ACTIVO", true, true);
    when(usuarios.findByEmail("catedratico@ejemplo.com")).thenReturn(Optional.of(catedratico));
    when(asignaciones.findInscripcionParaAsignacion(inscripcionId, catedratico.getId()))
        .thenReturn(Optional.of(inscripcion));
    when(puentes.findById(puenteId)).thenReturn(Optional.of(puente));
    when(puente.isActivo()).thenReturn(true);
    when(puente.getId()).thenReturn(puenteId);
    when(asignaciones.save(any(AsignacionPuente.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.asignar("catedratico@ejemplo.com", new AsignarPuenteRequest(inscripcionId, puenteId));

    verify(asignaciones).save(any(AsignacionPuente.class));
    verify(auditoria)
        .register(
            eq(catedratico.getId()),
            eq("ASIGNAR_PUENTE"),
            eq("asignacion_puente"),
            any(UUID.class),
            anyMap(),
            eq(AHORA));
  }

  @Test
  void rechazaAsignacionEnCursoFinalizado() {
    UserAccount catedratico = catedratico();
    UUID inscripcionId = UUID.randomUUID();
    var inscripcion = inscripcion(false, "ACTIVO", true, true);
    when(usuarios.findByEmail("catedratico@ejemplo.com")).thenReturn(Optional.of(catedratico));
    when(asignaciones.findInscripcionParaAsignacion(inscripcionId, catedratico.getId()))
        .thenReturn(Optional.of(inscripcion));

    assertThatThrownBy(
            () ->
                service.asignar(
                    "catedratico@ejemplo.com",
                    new AsignarPuenteRequest(inscripcionId, UUID.randomUUID())))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("curso_finalizado"));

    verifyNoInteractions(puentes, auditoria);
    verify(asignaciones, never()).save(any(AsignacionPuente.class));
  }

  @Test
  void rechazaPuenteInactivo() {
    UserAccount catedratico = catedratico();
    UUID inscripcionId = UUID.randomUUID();
    UUID puenteId = UUID.randomUUID();
    Puente puente = mock(Puente.class);
    var inscripcion = inscripcion(true, "ACTIVO", true, true);
    when(usuarios.findByEmail("catedratico@ejemplo.com")).thenReturn(Optional.of(catedratico));
    when(asignaciones.findInscripcionParaAsignacion(inscripcionId, catedratico.getId()))
        .thenReturn(Optional.of(inscripcion));
    when(puentes.findById(puenteId)).thenReturn(Optional.of(puente));
    when(puente.isActivo()).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.asignar(
                    "catedratico@ejemplo.com", new AsignarPuenteRequest(inscripcionId, puenteId)))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("puente_inactivo"));

    verify(asignaciones, never()).save(any(AsignacionPuente.class));
    verifyNoInteractions(auditoria);
  }

  @Test
  void revocaAsignacionYAuditaLaAccion() {
    UserAccount catedratico = catedratico();
    UUID asignacionId = UUID.randomUUID();
    AsignacionPuente asignacion = mock(AsignacionPuente.class);
    when(usuarios.findByEmail("catedratico@ejemplo.com")).thenReturn(Optional.of(catedratico));
    when(asignaciones.findByIdAndCursoEstudiante_Curso_Catedratico_IdAndRevocadoEnIsNull(
            asignacionId, catedratico.getId()))
        .thenReturn(Optional.of(asignacion));
    when(asignacion.getId()).thenReturn(asignacionId);

    service.revocar(
        "catedratico@ejemplo.com",
        asignacionId,
        new RevocarAsignacionPuenteRequest("Cambio de planificación"));

    verify(asignacion).revocar("Cambio de planificación", AHORA);
    verify(auditoria)
        .register(
            eq(catedratico.getId()),
            eq("REVOCAR_ASIGNACION_PUENTE"),
            eq("asignacion_puente"),
            eq(asignacionId),
            anyMap(),
            eq(AHORA));
  }

  private UserAccount catedratico() {
    UserAccount usuario = mock(UserAccount.class);
    Role rol = mock(Role.class);
    when(usuario.getId()).thenReturn(UUID.randomUUID());
    when(usuario.isActive()).thenReturn(true);
    when(usuario.getRole()).thenReturn(rol);
    when(rol.getName()).thenReturn(RoleName.CATEDRATICO);
    return usuario;
  }

  private AsignacionPuenteRepository.InscripcionAsignableProjection inscripcion(
      boolean cursoActivo, String estado, boolean estudianteActivo, boolean estudianteActivado) {
    var inscripcion = mock(AsignacionPuenteRepository.InscripcionAsignableProjection.class);
    when(inscripcion.getId()).thenReturn(UUID.randomUUID());
    when(inscripcion.getCursoId()).thenReturn(UUID.randomUUID());
    when(inscripcion.getCursoActivo()).thenReturn(cursoActivo);
    when(inscripcion.getEstado()).thenReturn(estado);
    when(inscripcion.getEstudianteActivo()).thenReturn(estudianteActivo);
    when(inscripcion.getEstudianteActivado()).thenReturn(estudianteActivado);
    return inscripcion;
  }
}
