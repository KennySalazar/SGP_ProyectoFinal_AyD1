package gt.usac.cunoc.sgp.usuario.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.usuario.exception.InvitacionExceptionHandler;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.service.ProfesionalService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProfesionalControllerSecurityTest.Config.class)
class ProfesionalControllerSecurityTest {

  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000000050");
  private static final UUID PROFESIONAL_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000051");

  @Autowired private ProfesionalController controller;

  @Autowired private ProfesionalService service;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(service);
    SecurityContextHolder.clearContext();
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new InvitacionExceptionHandler(), new ApiExceptionHandler())
            .build();
  }

  @AfterEach
  void limpiarAutenticacion() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @EnumSource(value = RoleName.class, names = "ADMINISTRADOR", mode = EnumSource.Mode.EXCLUDE)
  void soloElAdministradorConsultaYVerificaColegiados(RoleName rol) throws Exception {
    var authentication = autenticar(rol);

    mockMvc
        .perform(get("/api/v1/profesionales").principal(authentication))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/profesionales/{id}/verificacion-colegiado", PROFESIONAL_ID)
                .principal(authentication))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("access_denied"));

    verifyNoInteractions(service);
  }

  @Test
  void administradorVerificaConSuIdDeJwtData() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);

    mockMvc
        .perform(
            post("/api/v1/profesionales/{id}/verificacion-colegiado", PROFESIONAL_ID)
                .principal(authentication))
        .andExpect(status().isOk());

    verify(service).verificarColegiado(PROFESIONAL_ID, USUARIO_ID);
  }

  @Test
  void administradorFiltraPorVerificacion() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);

    mockMvc
        .perform(get("/api/v1/profesionales?verificado=false").principal(authentication))
        .andExpect(status().isOk());

    verify(service).listar(false, 0, 20);
  }

  @Test
  void rechazaIdentificadorInvalidoCon400() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);

    mockMvc
        .perform(
            post("/api/v1/profesionales/no-es-uuid/verificacion-colegiado")
                .principal(authentication))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores[0].campo").value("usuarioId"));

    verifyNoInteractions(service);
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
    ProfesionalService profesionalService() {
      return mock(ProfesionalService.class);
    }

    @Bean
    ProfesionalController profesionalController(ProfesionalService profesionalService) {
      return new ProfesionalController(profesionalService);
    }
  }
}
