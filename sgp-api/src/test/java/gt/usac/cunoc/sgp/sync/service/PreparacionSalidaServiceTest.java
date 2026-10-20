package gt.usac.cunoc.sgp.sync.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.puente.dto.PuenteCatalogoResponse;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapper;
import gt.usac.cunoc.sgp.puente.repository.AsignacionPuenteRepository;
import gt.usac.cunoc.sgp.sync.dto.PrepararSalidaRequest;
import gt.usac.cunoc.sgp.sync.repository.*;
import gt.usac.cunoc.sgp.usuario.entity.*;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PreparacionSalidaServiceTest {
  UserAccountRepository usuarios = mock(UserAccountRepository.class);
  UsuarioProfesionalRepository profesionales = mock(UsuarioProfesionalRepository.class);
  AsignacionPuenteRepository asignaciones = mock(AsignacionPuenteRepository.class);
  VersionFormularioRepository formularios = mock(VersionFormularioRepository.class);
  ConsultaSalidaRepository consulta = mock(ConsultaSalidaRepository.class);
  PreparacionSalidaRepository registro = mock(PreparacionSalidaRepository.class);
  PuenteMapper mapper = mock(PuenteMapper.class);
  AuditoriaWriter auditoria = mock(AuditoriaWriter.class);
  ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  Clock clock = Clock.fixed(Instant.parse("2026-10-20T02:00:00Z"), ZoneOffset.UTC);
  PreparacionSalidaService servicio;
  UUID usuarioId = UUID.randomUUID();
  UUID puenteId = UUID.randomUUID();
  UUID versionId = UUID.randomUUID();
  UserAccount usuario = mock(UserAccount.class);

  @BeforeEach
  void configurar() {
    servicio =
        new PreparacionSalidaService(
            usuarios,
            profesionales,
            asignaciones,
            formularios,
            consulta,
            registro,
            mapper,
            json,
            auditoria,
            clock);
    when(usuario.getId()).thenReturn(usuarioId);
    when(usuario.isActive()).thenReturn(true);
    when(usuario.isActivated()).thenReturn(true);
    when(usuario.isVerified()).thenReturn(true);
    var rol = mock(Role.class);
    when(rol.getName()).thenReturn(RoleName.CATEDRATICO);
    when(usuario.getRole()).thenReturn(rol);
    when(usuarios.findWithRoleById(usuarioId)).thenReturn(Optional.of(usuario));
  }

  PrepararSalidaRequest solicitud() {
    return new PrepararSalidaRequest("kenny-hu041", List.of(puenteId));
  }

  void rol(RoleName nombre) {
    when(usuario.getRole().getName()).thenReturn(nombre);
  }

  void datos() {
    var p = mock(Puente.class);
    when(p.getId()).thenReturn(puenteId);
    when(p.getCodigo()).thenReturn("GT-01-0114-0001");
    when(p.isActivo()).thenReturn(true);
    when(consulta.seleccionados(anyCollection())).thenReturn(List.of(p));
    when(mapper.toCatalogoResponse(p))
        .thenReturn(
            new PuenteCatalogoResponse(
                puenteId,
                "GT-01-0114-0001",
                "Puente",
                null,
                null,
                14.0,
                -90.0,
                true,
                "Sin evaluar",
                null,
                null));
    var v = mock(VersionFormulario.class);
    when(v.getId()).thenReturn(versionId);
    when(v.getCodigo()).thenReturn("v3");
    when(v.getEsquema()).thenReturn(json.createObjectNode().put("type", "object"));
    when(formularios.findByActivaTrue()).thenReturn(Optional.of(v));
  }

  void rechaza(HttpStatus estado, Runnable accion) {
    assertThatThrownBy(accion::run)
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(estado));
    verifyNoInteractions(registro, auditoria);
  }

  @Test
  void preparaPaqueteCompletoYAuditaDispositivoSinReglasIc() throws Exception {
    datos();
    var r = servicio.preparar(usuarioId, solicitud());
    assertThat(r.numeroPuentes()).isEqualTo(1);
    assertThat(r.usuarioId()).isEqualTo(usuarioId);
    assertThat(r.preparadoEn()).isEqualTo(clock.instant());
    assertThat(r.paquete().formulario().id()).isEqualTo(versionId);
    assertThat(r.tamanoEstimadoBytes()).isEqualTo(json.writeValueAsBytes(r.paquete()).length);
    verify(registro)
        .registrar(
            eq(r.id()),
            eq(usuarioId),
            eq("kenny-hu041"),
            eq(versionId),
            argThat(
                c ->
                    c.path("rol").asText().equals("CATEDRATICO")
                        && c.path("puentesIds").size() == 1),
            eq(clock.instant()));
    verify(auditoria)
        .registrarEnTransaccion(
            eq(usuarioId),
            any(),
            eq("preparacion_salida"),
            eq(r.id()),
            isNull(),
            argThat(c -> c.path("dispositivoId").asText().equals("kenny-hu041")),
            eq("HU-041"));
    assertThat(json.writeValueAsString(r)).doesNotContain("reglasEvaluacionIc", "password");
  }

  @Test
  void estimacionNoRegistraNiAudita() {
    datos();
    assertThat(servicio.estimar(usuarioId, solicitud()).numeroPuentes()).isEqualTo(1);
    verifyNoInteractions(registro, auditoria);
  }

  @Test
  void administradorProhibido() {
    rol(RoleName.ADMINISTRADOR);
    rechaza(HttpStatus.FORBIDDEN, () -> servicio.preparar(usuarioId, solicitud()));
  }

  @Test
  void visitanteSinSesion() {
    rechaza(HttpStatus.UNAUTHORIZED, () -> servicio.preparar(null, solicitud()));
  }

  @Test
  void cuentaInactiva() {
    when(usuario.isActive()).thenReturn(false);
    rechaza(HttpStatus.FORBIDDEN, () -> servicio.preparar(usuarioId, solicitud()));
  }

  @Test
  void externoSinColegiadoVerificado() {
    rol(RoleName.PROFESIONAL_EXTERNO);
    rechaza(HttpStatus.FORBIDDEN, () -> servicio.preparar(usuarioId, solicitud()));
  }

  @Test
  void externoVerificadoPrepara() {
    rol(RoleName.PROFESIONAL_EXTERNO);
    var p = mock(UsuarioProfesional.class);
    when(p.isColegiadoVerificado()).thenReturn(true);
    when(profesionales.findById(usuarioId)).thenReturn(Optional.of(p));
    datos();
    assertThat(servicio.preparar(usuarioId, solicitud()).numeroPuentes()).isEqualTo(1);
  }

  @Test
  void limiteDoscientosYSeleccionObligatoria() {
    rechaza(
        HttpStatus.UNPROCESSABLE_ENTITY,
        () ->
            servicio.preparar(
                usuarioId,
                new PrepararSalidaRequest(
                    "equipo",
                    java.util.stream.IntStream.range(0, 201)
                        .mapToObj(n -> UUID.randomUUID())
                        .toList())));
    rechaza(
        HttpStatus.UNPROCESSABLE_ENTITY,
        () -> servicio.preparar(usuarioId, new PrepararSalidaRequest("equipo", null)));
  }

  @Test
  void estudianteRecibeSoloVigentesYConservaAsignacion() {
    rol(RoleName.ESTUDIANTE);
    var a = mock(AsignacionPuenteRepository.AsignacionPuenteProjection.class);
    UUID asignacionId = UUID.randomUUID();
    when(a.getId()).thenReturn(asignacionId);
    when(a.getPuenteId()).thenReturn(puenteId);
    when(asignaciones.findVigentesByEstudianteId(eq(usuarioId), any())).thenReturn(List.of(a));
    datos();
    var r = servicio.preparar(usuarioId, new PrepararSalidaRequest("equipo", null));
    assertThat(r.paquete().asignaciones())
        .singleElement()
        .satisfies(as -> assertThat(as.id()).isEqualTo(asignacionId));
    verify(asignaciones).findVigentesByEstudianteId(usuarioId, LocalDate.parse("2026-10-19"));
  }

  @Test
  void estudianteNoPuedeInyectarPuenteAjeno() {
    rol(RoleName.ESTUDIANTE);
    rechaza(HttpStatus.FORBIDDEN, () -> servicio.preparar(usuarioId, solicitud()));
  }

  @Test
  void seleccionNoDisponibleSeRechazaCompleta() {
    rechaza(HttpStatus.CONFLICT, () -> servicio.preparar(usuarioId, solicitud()));
  }

  @Test
  void sinVersionActivaNoRegistraNada() {
    datos();
    when(formularios.findByActivaTrue()).thenReturn(Optional.empty());
    rechaza(HttpStatus.CONFLICT, () -> servicio.preparar(usuarioId, solicitud()));
  }

  @Test
  void fallaLecturaDePublicadasNoRegistraDatosParciales() {
    datos();
    when(consulta.ultimasPublicadas(any())).thenThrow(new IllegalStateException("fallo"));
    assertThatThrownBy(() -> servicio.preparar(usuarioId, solicitud()))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(registro, auditoria);
  }

  @Test
  void estudianteSinAsignacionesObtieneListaVaciaSinConsultaGeneral() {
    rol(RoleName.ESTUDIANTE);
    assertThat(servicio.disponibles(usuarioId, null, "", 0, 20)).isEmpty();
    verifyNoInteractions(consulta);
  }

  @Test
  void ultimaPreparacionEstaAcotadaPorUsuarioYDispositivo() {
    when(registro.ultima(usuarioId, "equipo")).thenReturn(Optional.empty());
    assertThat(servicio.ultima(usuarioId, "equipo")).isEmpty();
    verify(registro).ultima(usuarioId, "equipo");
  }
}
