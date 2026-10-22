package gt.usac.cunoc.sgp.formulario.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.formulario.dto.AprobacionCalibracionResponse;
import gt.usac.cunoc.sgp.formulario.service.AprobacionCalibracionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AprobacionCalibracionControllerSecurityTest.Config.class)
class AprobacionCalibracionControllerSecurityTest {
  @Autowired private AprobacionCalibracionService servicio;
  @Autowired private AprobacionCalibracionController controlador;
  private final UUID version = UUID.randomUUID();
  private final UUID usuario = UUID.randomUUID();
  private MockMvc mvc;

  @BeforeEach
  void preparar() {
    reset(servicio);
    mvc =
        MockMvcBuilders.standaloneSetup(controlador)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
  }

  private UsernamePasswordAuthenticationToken autenticar(String rol) {
    var autenticacion =
        new UsernamePasswordAuthenticationToken(
            "usuario", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    autenticacion.setDetails(new JwtData("usuario@ejemplo.com", usuario, RoleName.valueOf(rol), 0));
    SecurityContextHolder.getContext().setAuthentication(autenticacion);
    return autenticacion;
  }

  @ParameterizedTest
  @ValueSource(strings = {"ADMINISTRADOR", "ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void soloCatedraticoAprueba(String rol) throws Exception {
    mvc.perform(
            post(ruta())
                .principal(autenticar(rol))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(servicio);
  }

  @Test
  void catedraticoApruebaConIdentidadDeSesion() throws Exception {
    var auth = autenticar("CATEDRATICO");
    when(servicio.aprobar(version, usuario, "Revisada"))
        .thenReturn(
            new AprobacionCalibracionResponse(
                UUID.randomUUID(),
                version,
                usuario,
                "Kenny",
                Instant.parse("2026-10-22T18:00:00Z"),
                "Revisada"));
    mvc.perform(
            post(ruta())
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"observaciones\":\"Revisada\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.aprobadoPorId").value(usuario.toString()));
    verify(servicio).aprobar(version, usuario, "Revisada");
  }

  @Test
  void observacionesSonOpcionales() throws Exception {
    mvc.perform(post(ruta()).principal(autenticar("CATEDRATICO"))).andExpect(status().isCreated());
    verify(servicio).aprobar(version, usuario, null);
  }

  @Test
  void limitaObservacionesAntesDeInvocarServicio() throws Exception {
    mvc.perform(
            post(ruta())
                .principal(autenticar("CATEDRATICO"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"observaciones\":\"" + "x".repeat(2001) + "\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(servicio);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void otrosRolesNoConsultanObservaciones(String rol) throws Exception {
    mvc.perform(get(ruta()).principal(autenticar(rol))).andExpect(status().isForbidden());
    verifyNoInteractions(servicio);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ADMINISTRADOR", "CATEDRATICO"})
  void responsablesConsultanHistorial(String rol) throws Exception {
    mvc.perform(get(ruta()).principal(autenticar(rol))).andExpect(status().isOk());
    verify(servicio).consultar(version);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ADMINISTRADOR", "CATEDRATICO", "ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void usuariosDelIcConsultanResumenSinObservaciones(String rol) throws Exception {
    mvc.perform(
            get("/api/v1/formularios/" + version + "/estado-calibracion")
                .principal(autenticar(rol)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    verify(servicio).estado(version);
  }

  private String ruta() {
    return "/api/v1/formularios/" + version + "/aprobacion-calibracion";
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {
    @Bean
    AprobacionCalibracionService servicio() {
      return mock(AprobacionCalibracionService.class);
    }

    @Bean
    AprobacionCalibracionController controlador(AprobacionCalibracionService servicio) {
      return new AprobacionCalibracionController(servicio);
    }
  }
}
