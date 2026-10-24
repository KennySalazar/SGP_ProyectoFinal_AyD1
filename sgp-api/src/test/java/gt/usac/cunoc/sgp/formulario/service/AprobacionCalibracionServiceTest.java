package gt.usac.cunoc.sgp.formulario.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.entity.*;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.*;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import gt.usac.cunoc.sgp.usuario.entity.*;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AprobacionCalibracionServiceTest {
  @Mock VersionFormularioRepository versiones;
  @Mock AprobacionCalibracionRepository aprobaciones;
  @Mock UserAccountRepository usuarios;
  @Mock AuditoriaWriter auditoria;
  @Mock NotificacionService notificaciones;
  private final UUID id = UUID.randomUUID();
  private final UUID autor = UUID.randomUUID();
  private final Instant ahora = Instant.parse("2026-10-23T02:30:00Z");
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  private AprobacionCalibracionService servicio;
  private VersionFormulario version;

  @BeforeEach
  void preparar() throws Exception {
    servicio =
        new AprobacionCalibracionService(
            versiones,
            aprobaciones,
            usuarios,
            auditoria,
            notificaciones,
            json,
            Clock.fixed(ahora, ZoneOffset.UTC));
    version =
        new VersionFormulario(
            id,
            "v1",
            EstadoVersionFormulario.PUBLICADA,
            true,
            json.readTree("{}"),
            json.readTree("{}"),
            json.readTree("{}"),
            null,
            autor,
            ahora,
            ahora);
  }

  private void prepararAprobacion() {
    when(versiones.findByIdForUpdate(id)).thenReturn(Optional.of(version));
    var usuario = mock(UserAccount.class);
    var rol = mock(Role.class);
    when(usuario.isActive()).thenReturn(true);
    when(usuario.getRole()).thenReturn(rol);
    when(rol.getName()).thenReturn(RoleName.CATEDRATICO);
    when(usuario.getNombreCompleto()).thenReturn("Kenny");
    when(usuarios.findById(autor)).thenReturn(Optional.of(usuario));
    when(aprobaciones.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
  }

  @Test
  void apruebaSinPdfSinGuardarVersionYAvisaAdministradores() {
    prepararAprobacion();
    UUID adminId = UUID.randomUUID();
    when(usuarios.findIdsActivosPorRol(RoleName.ADMINISTRADOR)).thenReturn(List.of(adminId));
    var respuesta = servicio.aprobar(id, autor, "  Conforme  ");
    assertThat(respuesta.observaciones()).isEqualTo("Conforme");
    assertThat(respuesta.aprobadoPorId()).isEqualTo(autor);
    assertThat(respuesta.rotulo()).isEqualTo("Aprobada por Kenny el 22/10/2026");
    verify(versiones, never()).save(any());
    verify(versiones, never()).saveAndFlush(any());
    verify(auditoria)
        .registrarEnTransaccion(
            eq(autor),
            any(),
            eq("aprobacion_calibracion_ic"),
            eq(respuesta.id()),
            isNull(),
            argThat(nodo -> id.toString().equals(nodo.path("versionFormularioId").asText())),
            anyString());
    verify(notificaciones)
        .notificar(
            eq(adminId),
            eq("CALIBRACION_APROBADA"),
            anyString(),
            anyString(),
            eq("version_formulario"),
            eq(id),
            eq("calibracion-ic-aprobada:" + id));
  }

  @Test
  void conflictoEnBorradorNoProduceEfectos() {
    version.setEstado(EstadoVersionFormulario.BORRADOR);
    when(versiones.findByIdForUpdate(id)).thenReturn(Optional.of(version));
    assertThatThrownBy(() -> servicio.aprobar(id, autor, null))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("calibracion_version_no_publicada");
    verifyNoInteractions(aprobaciones, usuarios, auditoria, notificaciones);
  }

  @Test
  void duplicadoNoReemplazaAprobacionNiAvisaOtraVez() {
    when(versiones.findByIdForUpdate(id)).thenReturn(Optional.of(version));
    when(aprobaciones.existsByVersionFormularioId(id)).thenReturn(true);
    assertThatThrownBy(() -> servicio.aprobar(id, autor, null))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("calibracion_ya_aprobada");
    verify(aprobaciones, never()).saveAndFlush(any());
    verifyNoInteractions(usuarios, auditoria, notificaciones);
  }

  @Test
  void versionInexistenteResponde404() {
    assertThatThrownBy(() -> servicio.aprobar(id, autor, null))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("version_no_encontrada");
  }

  @Test
  void consultaInicialSinAprobacion() {
    when(versiones.findById(id)).thenReturn(Optional.of(version));
    assertThat(servicio.consultar(id)).isNull();
    assertThat(servicio.estado(id).estado()).isEqualTo("SIN_APROBAR");
    verifyNoInteractions(auditoria, notificaciones);
  }

  @Test
  void resumenNoIncluyeObservacionesPrivadas() {
    when(versiones.findById(id)).thenReturn(Optional.of(version));
    when(aprobaciones.findByVersionFormularioId(id))
        .thenReturn(
            Optional.of(
                new AprobacionCalibracion(
                    UUID.randomUUID(), version, autor, "Kenny", ahora, "Observacion reservada")));
    var estado = json.valueToTree(servicio.estado(id));
    assertThat(estado.path("estado").asText()).isEqualTo("APROBADA");
    assertThat(estado.has("observaciones")).isFalse();
    assertThat(estado.toString()).doesNotContain("Observacion reservada");
  }
}
