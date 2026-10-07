package gt.usac.cunoc.sgp.puente.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.puente.controller.AsignacionPuenteController;
import gt.usac.cunoc.sgp.puente.dto.AsignarPuenteRequest;
import gt.usac.cunoc.sgp.puente.service.AsignacionPuenteService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AsignacionPuenteControllerSecurityTest.Config.class)
class AsignacionPuenteControllerSecurityTest {

  private static final String REQUEST =
      """
      {"cursoEstudianteId":"019a0000-0000-7000-8000-000000000001",
       "puenteId":"019a0000-0000-7000-8000-000000000002"}
      """;

  @Autowired private AsignacionPuenteController controller;

  @Autowired private AsignacionPuenteService service;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(service);
    SecurityContextHolder.clearContext();
    mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new SecurityAdvice()).build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void permiteAsignarSoloACatedratico() throws Exception {
    var authentication = autenticar(RoleName.CATEDRATICO);

    mockMvc
        .perform(
            post("/api/v1/asignaciones-puentes")
                .principal(authentication)
                .contentType("application/json")
                .content(REQUEST))
        .andExpect(status().isCreated());

    verify(service).asignar(any(), any(AsignarPuenteRequest.class));
  }

  @Test
  void rechazaAsignacionDeEstudiante() throws Exception {
    var authentication = autenticar(RoleName.ESTUDIANTE);

    mockMvc
        .perform(
            post("/api/v1/asignaciones-puentes")
                .principal(authentication)
                .contentType("application/json")
                .content(REQUEST))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void permiteConsultarPuentesPropiosSoloAEstudiante() throws Exception {
    var authentication = autenticar(RoleName.ESTUDIANTE);

    mockMvc
        .perform(get("/api/v1/asignaciones-puentes/mis-puentes").principal(authentication))
        .andExpect(status().isOk());

    verify(service).listarMisPuentes(any());
  }

  @Test
  void rechazaConsultaDePuentesPropiosPorCatedratico() throws Exception {
    var authentication = autenticar(RoleName.CATEDRATICO);

    mockMvc
        .perform(get("/api/v1/asignaciones-puentes/mis-puentes").principal(authentication))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  private UsernamePasswordAuthenticationToken autenticar(RoleName role) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            "usuario@ejemplo.com", null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    authentication.setDetails(new JwtData("usuario@ejemplo.com", UUID.randomUUID(), role, 0));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return authentication;
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {

    @Bean
    AsignacionPuenteService asignacionPuenteService() {
      return mock(AsignacionPuenteService.class);
    }

    @Bean
    AsignacionPuenteController asignacionPuenteController(
        AsignacionPuenteService asignacionPuenteService) {
      return new AsignacionPuenteController(asignacionPuenteService);
    }
  }

  @RestControllerAdvice
  static class SecurityAdvice {
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void accessDenied() {}
  }
}
