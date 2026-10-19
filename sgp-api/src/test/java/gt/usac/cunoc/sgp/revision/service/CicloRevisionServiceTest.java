package gt.usac.cunoc.sgp.revision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.AsignacionPuenteRepository;
import gt.usac.cunoc.sgp.revision.entity.Revision;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CicloRevisionServiceTest {

  private static final Instant AHORA = Instant.parse("2026-10-18T16:00:00Z");

  private final RevisionRepository revisiones = mock(RevisionRepository.class);
  private final AsignacionPuenteRepository asignaciones = mock(AsignacionPuenteRepository.class);
  private final NotificacionService notificaciones = mock(NotificacionService.class);
  private final CicloRevisionService service =
      new CicloRevisionService(
          revisiones,
          asignaciones,
          notificaciones,
          new ObjectMapper(),
          Clock.fixed(AHORA, ZoneOffset.UTC));

  @Test
  void creaCicloInmutableParaElCatedraticoAsignadoYNotifica() {
    UUID inspeccionId = UUID.randomUUID();
    UUID autorId = UUID.randomUUID();
    UUID asignacionId = UUID.randomUUID();
    UUID puenteId = UUID.randomUUID();
    UUID formularioId = UUID.randomUUID();
    UUID catedraticoId = UUID.randomUUID();
    Inspeccion inspeccion = mock(Inspeccion.class);
    Puente puente = mock(Puente.class);
    VersionFormulario version = mock(VersionFormulario.class);
    var asignacion = mock(AsignacionPuenteRepository.AsignacionInspeccionProjection.class);
    ObjectNode datos = new ObjectMapper().createObjectNode().put("condicion", "buena");
    when(inspeccion.getId()).thenReturn(inspeccionId);
    when(inspeccion.getAutorId()).thenReturn(autorId);
    when(inspeccion.getAsignacionPuenteId()).thenReturn(asignacionId);
    when(inspeccion.getPuente()).thenReturn(puente);
    when(inspeccion.getVersionFormulario()).thenReturn(version);
    when(inspeccion.getFechaInspeccion()).thenReturn(LocalDate.of(2026, 10, 18));
    when(inspeccion.getDatos()).thenReturn(datos);
    when(inspeccion.getVersion()).thenReturn(7L);
    when(puente.getId()).thenReturn(puenteId);
    when(puente.getCodigo()).thenReturn("GT-001");
    when(version.getId()).thenReturn(formularioId);
    when(asignacion.getCatedraticoId()).thenReturn(catedraticoId);
    when(asignaciones.findVigentePorId(
            eq(asignacionId), eq(autorId), eq(LocalDate.of(2026, 10, 18))))
        .thenReturn(Optional.of(asignacion));
    when(revisiones.ultimoCiclo(inspeccionId)).thenReturn(1);
    when(revisiones.save(org.mockito.ArgumentMatchers.any(Revision.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Revision creada = service.crearParaEnvio(inspeccion);

    assertThat(creada.getNumeroCiclo()).isEqualTo(2);
    assertThat(creada.getRevisorId()).isEqualTo(catedraticoId);
    assertThat(creada.getEstado()).isEqualTo("PENDIENTE");
    assertThat(creada.getEnviadaEn()).isEqualTo(AHORA);
    assertThat(creada.getVersionInspeccionEnviada()).isEqualTo(7L);
    assertThat(creada.getContenidoEnviado().path("datos").path("condicion").asText())
        .isEqualTo("buena");
    assertThat(creada.getHashContenido()).hasSize(64);

    ArgumentCaptor<String> clave = ArgumentCaptor.forClass(String.class);
    verify(notificaciones)
        .registrar(
            eq(catedraticoId),
            eq("INSPECCION_ENVIADA"),
            eq("Inspección enviada para revisión"),
            eq("Un estudiante envió la inspección del puente GT-001."),
            eq("inspeccion"),
            eq(inspeccionId),
            clave.capture());
    assertThat(clave.getValue()).isEqualTo("revision-enviada:" + creada.getId());
  }
}
