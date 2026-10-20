package gt.usac.cunoc.sgp.mantenimiento.controller;

import static org.mockito.ArgumentMatchers.any;
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
import gt.usac.cunoc.sgp.mantenimiento.dto.GeneracionPropuestasResponse;
import gt.usac.cunoc.sgp.mantenimiento.service.GeneradorPropuestasMantenimientoService;
import gt.usac.cunoc.sgp.mantenimiento.service.OrdenesDeInspeccionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Quién consulta las órdenes de una inspección y quién regenera sus propuestas (HU-046). */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PropuestaMantenimientoControllerSecurityTest.Config.class)
class PropuestaMantenimientoControllerSecurityTest {

  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000004761");
  private static final UUID INSPECCION_ID = UUID.fromString("019a0000-0000-7000-8000-000000004762");
  private static final String ORDENES = "/api/v1/inspecciones/{id}/ordenes-mantenimiento";
  private static final String REGENERAR =
      "/api/v1/inspecciones/{id}/propuestas-mantenimiento/regenerar";

  @Autowired private PropuestaMantenimientoController controller;
  @Autowired private GeneradorPropuestasMantenimientoService generador;
  @Autowired private OrdenesDeInspeccionService ordenesDeInspeccion;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(generador, ordenesDeInspeccion);
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
  @EnumSource(RoleName.class)
  void cualquierUsuarioAutenticadoConsultaLasOrdenesDeUnaInspeccion(RoleName rol) throws Exception {
    when(ordenesDeInspeccion.listar(INSPECCION_ID)).thenReturn(List.of());

    mockMvc
        .perform(get(ORDENES, INSPECCION_ID).principal(autenticar(rol)))
        .andExpect(status().isOk());

    verify(ordenesDeInspeccion).listar(INSPECCION_ID);
  }

  @Test
  void unVisitanteNoConsultaLasOrdenes() throws Exception {
    mockMvc.perform(get(ORDENES, INSPECCION_ID)).andExpect(status().isUnauthorized());

    verifyNoInteractions(ordenesDeInspeccion);
  }

  @Test
  void unaInspeccionNoPublicadaSeResponde404() throws Exception {
    when(ordenesDeInspeccion.listar(INSPECCION_ID))
        .thenThrow(
            new ApiException(
                HttpStatus.NOT_FOUND,
                "inspeccion_no_encontrada",
                "Inspección no encontrada",
                "No existe la inspección publicada solicitada."));

    mockMvc
        .perform(get(ORDENES, INSPECCION_ID).principal(autenticar(RoleName.ESTUDIANTE)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("inspeccion_no_encontrada"));
  }

  @Test
  void soloElAdministradorRegeneraLasPropuestas() throws Exception {
    when(generador.regenerar(INSPECCION_ID, USUARIO_ID))
        .thenReturn(new GeneracionPropuestasResponse(INSPECCION_ID, 18, 2, 0, 0, 0));

    mockMvc
        .perform(post(REGENERAR, INSPECCION_ID).principal(autenticar(RoleName.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.creadas").value(2))
        .andExpect(jsonPath("$.reglasEvaluadas").value(18));

    verify(generador).regenerar(INSPECCION_ID, USUARIO_ID);
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"CATEDRATICO", "PROFESIONAL_EXTERNO", "ESTUDIANTE"})
  void losDemasRolesNoRegeneranYRecibe403(RoleName rol) throws Exception {
    mockMvc
        .perform(post(REGENERAR, INSPECCION_ID).principal(autenticar(rol)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("access_denied"));

    verifyNoInteractions(generador);
  }

  @Test
  void unVisitanteNoRegenera() throws Exception {
    mockMvc.perform(post(REGENERAR, INSPECCION_ID)).andExpect(status().isUnauthorized());

    verifyNoInteractions(generador);
  }

  @Test
  void unaSesionSinDetallesDeUsuarioSeRechazaAlRegenerar() throws Exception {
    var sinDetalles =
        new UsernamePasswordAuthenticationToken(
            "admin@ejemplo.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));
    SecurityContextHolder.getContext().setAuthentication(sinDetalles);

    mockMvc
        .perform(post(REGENERAR, INSPECCION_ID).principal(sinDetalles))
        .andExpect(status().isUnauthorized());

    verify(generador, org.mockito.Mockito.never()).regenerar(any(), any());
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
    GeneradorPropuestasMantenimientoService generador() {
      return mock(GeneradorPropuestasMantenimientoService.class);
    }

    @Bean
    OrdenesDeInspeccionService ordenesDeInspeccion() {
      return mock(OrdenesDeInspeccionService.class);
    }

    @Bean
    PropuestaMantenimientoController controller(
        GeneradorPropuestasMantenimientoService generador,
        OrdenesDeInspeccionService ordenesDeInspeccion) {
      return new PropuestaMantenimientoController(generador, ordenesDeInspeccion);
    }
  }
}
