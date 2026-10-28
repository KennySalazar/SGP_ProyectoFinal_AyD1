package gt.usac.cunoc.sgp.mantenimiento.service;

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
import gt.usac.cunoc.sgp.mantenimiento.entity.HistorialEstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.entity.PlanMantenimientoRutinario;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import gt.usac.cunoc.sgp.mantenimiento.repository.HistorialEstadoOrdenRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenCicloPlanRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoRepository;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrdenCicloPlanServiceTest {

  private final OrdenCicloPlanRepository ciclos = mock(OrdenCicloPlanRepository.class);
  private final OrdenMantenimientoRepository ordenes = mock(OrdenMantenimientoRepository.class);
  private final HistorialEstadoOrdenRepository historial =
      mock(HistorialEstadoOrdenRepository.class);
  private final AuditoriaWriter auditoria = mock(AuditoriaWriter.class);
  private final Instant ahora = Instant.parse("2026-10-22T12:00:00Z");
  private final OrdenCicloPlanService service =
      new OrdenCicloPlanService(
          ciclos,
          ordenes,
          historial,
          auditoria,
          new ObjectMapper(),
          Clock.fixed(ahora, ZoneOffset.UTC));

  @Test
  void noDuplicaElCicloSiYaTieneOrdenEnCualquierEstado() {
    UUID planId = UUID.randomUUID();
    PlanMantenimientoRutinario plan = plan(planId);
    when(ciclos.existsByPlanMantenimientoRutinarioIdAndCicloPlan(planId, 12)).thenReturn(true);

    service.generar(plan, 12, LocalDate.parse("2026-10-01"));

    verify(ordenes, never()).saveAndFlush(any());
    verify(historial, never()).save(any());
    verifyNoInteractions(auditoria);
  }

  @Test
  void creaPropuestaRutinariaConCicloHistorialYAudiAutomatica() {
    UUID planId = UUID.randomUUID();
    UUID puenteId = UUID.randomUUID();
    Puente puente = mock(Puente.class);
    when(puente.getId()).thenReturn(puenteId);
    PlanMantenimientoRutinario plan =
        new PlanMantenimientoRutinario(planId, puente, LocalDate.parse("2025-10-01"), ahora);
    when(ciclos.existsByPlanMantenimientoRutinarioIdAndCicloPlan(planId, 12)).thenReturn(false);
    when(ordenes.saveAndFlush(any(OrdenMantenimiento.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.generar(plan, 12, LocalDate.parse("2026-10-01"));

    ArgumentCaptor<OrdenMantenimiento> captor = ArgumentCaptor.forClass(OrdenMantenimiento.class);
    verify(ordenes).saveAndFlush(captor.capture());
    OrdenMantenimiento orden = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(orden.getTipo()).isEqualTo(TipoOrden.RUTINARIO);
    org.assertj.core.api.Assertions.assertThat(orden.getEstado()).isEqualTo(EstadoOrden.PROPUESTA);
    org.assertj.core.api.Assertions.assertThat(orden.getPlanMantenimientoRutinarioId())
        .isEqualTo(planId);
    org.assertj.core.api.Assertions.assertThat(orden.getCicloPlan()).isEqualTo(12);
    verify(historial).save(any(HistorialEstadoOrden.class));
    verify(auditoria)
        .registrarEnTransaccion(
            eq(null),
            eq(AccionAuditoria.CREAR),
            eq(TransicionOrdenService.ENTIDAD),
            eq(orden.getId()),
            eq(null),
            any(),
            eq(OrdenCicloPlanService.PROCESO));
  }

  private PlanMantenimientoRutinario plan(UUID id) {
    Puente puente = mock(Puente.class);
    when(puente.getId()).thenReturn(UUID.randomUUID());
    return new PlanMantenimientoRutinario(id, puente, LocalDate.parse("2025-10-01"), ahora);
  }
}
