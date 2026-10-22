package gt.usac.cunoc.sgp.mantenimiento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.mantenimiento.dto.ActualizarPlanMantenimientoRequest;
import gt.usac.cunoc.sgp.mantenimiento.entity.PlanMantenimientoRutinario;
import gt.usac.cunoc.sgp.mantenimiento.repository.PlanMantenimientoRutinarioRepository;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanMantenimientoServiceTest {

  private final PlanMantenimientoRutinarioRepository planes =
      mock(PlanMantenimientoRutinarioRepository.class);
  private final PuenteRepository puentes = mock(PuenteRepository.class);
  private final OrdenCicloPlanService ciclos = mock(OrdenCicloPlanService.class);
  private final AuditoriaWriter auditoria = mock(AuditoriaWriter.class);
  private final Instant ahora = Instant.parse("2026-10-22T12:00:00Z");
  private final PlanMantenimientoService service =
      new PlanMantenimientoService(
          planes,
          puentes,
          ciclos,
          auditoria,
          new ObjectMapper(),
          Clock.fixed(ahora, ZoneOffset.UTC));

  @Test
  void backfillCreaPlanActivoIdempotenteYLoAuditaComoProceso() {
    UUID puenteId = UUID.randomUUID();
    Puente puente = mock(Puente.class);
    when(puente.getId()).thenReturn(puenteId);
    when(puente.isActivo()).thenReturn(true);
    when(planes.findActiveBridgesWithoutPlan()).thenReturn(List.of(puenteId));
    when(puentes.findByIdForUpdate(puenteId)).thenReturn(Optional.of(puente));
    when(planes.existsByPuente_Id(puenteId)).thenReturn(false);
    when(planes.save(any(PlanMantenimientoRutinario.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.crearPlanesFaltantes();

    verify(planes).save(any(PlanMantenimientoRutinario.class));
    verify(auditoria)
        .registrarEnTransaccion(
            eq(null),
            eq(AccionAuditoria.CREAR),
            eq(PlanMantenimientoService.ENTIDAD),
            any(),
            eq(null),
            any(),
            eq(PlanMantenimientoService.PROCESO_BACKFILL));
  }

  @Test
  void creaPlanPorDefectoParaPuenteActivoConAuditoriaDelAdministrador() {
    UUID puenteId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    Puente puente = mock(Puente.class);
    when(puente.getId()).thenReturn(puenteId);
    when(puente.isActivo()).thenReturn(true);
    when(planes.findByPuenteId(puenteId)).thenReturn(Optional.empty());
    when(planes.save(any(PlanMantenimientoRutinario.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PlanMantenimientoRutinario plan = service.crearPorDefecto(puente, adminId);

    assertThat(plan.getPeriodicidadMeses()).isEqualTo(12);
    verify(auditoria)
        .registrarEnTransaccion(
            eq(adminId),
            eq(AccionAuditoria.CREAR),
            eq(PlanMantenimientoService.ENTIDAD),
            eq(plan.getId()),
            eq(null),
            any(),
            eq(null));
  }

  @Test
  void puenteInactivoNoRecibePlan() {
    Puente puente = mock(Puente.class);
    when(puente.isActivo()).thenReturn(false);

    assertThat(service.crearPorDefecto(puente, UUID.randomUUID())).isNull();

    verify(planes, never()).save(any(PlanMantenimientoRutinario.class));
    verifyNoInteractions(auditoria);
  }

  @Test
  void periodicidadInvalidaResponde422AntesDeModificarElPlan() {
    for (int periodicidad : List.of(0, 13)) {
      assertThatThrownBy(
              () ->
                  service.actualizar(
                      UUID.randomUUID(),
                      new ActualizarPlanMantenimientoRequest(periodicidad, List.of(), 0L),
                      UUID.randomUUID()))
          .isInstanceOfSatisfying(
              ApiException.class,
              exception ->
                  assertThat(exception.getStatus())
                      .isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY));
    }
    verifyNoInteractions(planes);
  }
}
