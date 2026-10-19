package gt.usac.cunoc.sgp.mantenimiento.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.mantenimiento.dto.CerrarSinEjecucionRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.IniciarEjecucionRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.ProgramarOrdenRequest;
import gt.usac.cunoc.sgp.mantenimiento.service.OrdenMantenimientoService;
import gt.usac.cunoc.sgp.mantenimiento.service.TransicionOrdenService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Quién puede cambiar el estado de una orden y quién puede consultarlas (HU-047). */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = OrdenMantenimientoControllerSecurityTest.Config.class)
class OrdenMantenimientoControllerSecurityTest {

  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000004721");
  private static final UUID ORDEN_ID = UUID.fromString("019a0000-0000-7000-8000-000000004722");
  private static final String BASE = "/api/v1/ordenes-mantenimiento";
  private static final String CUERPO =
      "{\"fechaProgramada\":\"2026-11-02\",\"motivo\":\"Motivo\",\"version\":3}";

  @Autowired private OrdenMantenimientoController controller;
  @Autowired private OrdenMantenimientoService ordenes;
  @Autowired private TransicionOrdenService transiciones;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(ordenes, transiciones);
    SecurityContextHolder.clearContext();
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @AfterEach
  void limpiarAutenticacion() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @ValueSource(strings = {"programar", "descartar", "iniciar", "cancelar"})
  void catedraticoYAdministradorPuedenCambiarElEstado(String accion) throws Exception {
    for (RoleName rol : List.of(RoleName.CATEDRATICO, RoleName.ADMINISTRADOR)) {
      reset(transiciones);
      mockMvc.perform(transicion(accion).principal(autenticar(rol))).andExpect(status().isOk());
      verificarDelegacion(accion);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"programar", "descartar", "iniciar", "cancelar"})
  void profesionalExternoYEstudianteReciben403(String accion) throws Exception {
    for (RoleName rol : List.of(RoleName.PROFESIONAL_EXTERNO, RoleName.ESTUDIANTE)) {
      mockMvc
          .perform(transicion(accion).principal(autenticar(rol)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("access_denied"));
    }
    verifyNoInteractions(transiciones);
  }

  @ParameterizedTest
  @ValueSource(strings = {"programar", "descartar", "iniciar", "cancelar"})
  void unVisitanteRecibe401(String accion) throws Exception {
    mockMvc.perform(transicion(accion)).andExpect(status().isUnauthorized());
    verifyNoInteractions(transiciones);
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"CATEDRATICO", "ADMINISTRADOR"})
  void catedraticoYAdministradorCreanOrdenesManuales(RoleName rol) throws Exception {
    mockMvc
        .perform(
            post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"puenteId\":\"" + UUID.randomUUID() + "\",\"tipo\":\"RUTINARIO\"}")
                .principal(autenticar(rol)))
        .andExpect(status().isCreated());
    verify(ordenes).crear(any(), eq(USUARIO_ID));
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"PROFESIONAL_EXTERNO", "ESTUDIANTE"})
  void otrosRolesNoCreanOrdenes(RoleName rol) throws Exception {
    mockMvc
        .perform(
            post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .principal(autenticar(rol)))
        .andExpect(status().isForbidden());
    verifyNoInteractions(ordenes);
  }

  @Test
  void unVisitanteNoCreaOrdenes() throws Exception {
    mockMvc
        .perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(ordenes);
  }

  @ParameterizedTest
  @EnumSource(RoleName.class)
  void cualquierUsuarioAutenticadoConsultaLasOrdenes(RoleName rol) throws Exception {
    mockMvc.perform(get(BASE).principal(autenticar(rol))).andExpect(status().isOk());
    mockMvc
        .perform(get(BASE + "/{id}", ORDEN_ID).principal(autenticar(rol)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get(BASE + "/{id}/historial", ORDEN_ID).principal(autenticar(rol)))
        .andExpect(status().isOk());
  }

  @Test
  void unVisitanteNoConsultaLasOrdenes() throws Exception {
    mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(BASE + "/{id}", ORDEN_ID)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(BASE + "/{id}/historial", ORDEN_ID)).andExpect(status().isUnauthorized());
    verifyNoInteractions(ordenes);
  }

  @Test
  void elMotivoFaltanteSePropagaComo422() throws Exception {
    when(transiciones.descartar(eq(ORDEN_ID), any(), eq(USUARIO_ID)))
        .thenThrow(
            new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "solicitud_invalida",
                "Solicitud invalida",
                "El motivo es obligatorio.",
                List.of(Map.of("campo", "motivo", "mensaje", "El motivo es obligatorio."))));

    mockMvc
        .perform(transicion("descartar").principal(autenticar(RoleName.CATEDRATICO)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("solicitud_invalida"))
        .andExpect(jsonPath("$.errores[0].campo").value("motivo"));
  }

  @Test
  void unaTransicionNoPermitidaSePropagaComo409() throws Exception {
    when(transiciones.cancelar(eq(ORDEN_ID), any(), eq(USUARIO_ID)))
        .thenThrow(
            new ApiException(
                HttpStatus.CONFLICT,
                "transicion_invalida",
                "Transicion no permitida",
                "La accion CANCELAR no se puede aplicar a una orden en estado PROPUESTA."));

    mockMvc
        .perform(transicion("cancelar").principal(autenticar(RoleName.ADMINISTRADOR)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("transicion_invalida"));
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder transicion(
      String accion) {
    return post(BASE + "/{id}/" + accion, ORDEN_ID)
        .contentType(MediaType.APPLICATION_JSON)
        .content(CUERPO);
  }

  private void verificarDelegacion(String accion) {
    switch (accion) {
      case "programar" ->
          verify(transiciones)
              .aceptarYProgramar(
                  ORDEN_ID, new ProgramarOrdenRequest(LocalDate.of(2026, 11, 2), 3L), USUARIO_ID);
      case "descartar" ->
          verify(transiciones)
              .descartar(ORDEN_ID, new CerrarSinEjecucionRequest("Motivo", 3L), USUARIO_ID);
      case "iniciar" ->
          verify(transiciones)
              .iniciarEjecucion(ORDEN_ID, new IniciarEjecucionRequest(3L), USUARIO_ID);
      case "cancelar" ->
          verify(transiciones)
              .cancelar(ORDEN_ID, new CerrarSinEjecucionRequest("Motivo", 3L), USUARIO_ID);
      default -> throw new IllegalArgumentException(accion);
    }
  }

  private UsernamePasswordAuthenticationToken autenticar(RoleName rol) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            "usuario@ejemplo.com", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    authentication.setDetails(new JwtData("usuario@ejemplo.com", USUARIO_ID, rol, 0));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return authentication;
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {

    @Bean
    OrdenMantenimientoService ordenMantenimientoService() {
      return mock(OrdenMantenimientoService.class);
    }

    @Bean
    TransicionOrdenService transicionOrdenService() {
      return mock(TransicionOrdenService.class);
    }

    @Bean
    OrdenMantenimientoController ordenMantenimientoController(
        OrdenMantenimientoService ordenes, TransicionOrdenService transiciones) {
      return new OrdenMantenimientoController(ordenes, transiciones);
    }
  }
}
