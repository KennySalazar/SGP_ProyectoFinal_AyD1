package gt.usac.cunoc.sgp.usuario.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.usuario.dto.ActivarEstudianteRequest;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.service.ActivacionEstudianteService;
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
@ContextConfiguration(classes = ActivacionEstudianteControllerSecurityTest.Config.class)
class ActivacionEstudianteControllerSecurityTest {

  private static final String REQUEST =
      """
      {"estudianteId":"019a0000-0000-7000-8000-000000000001",
       "cursoId":"019a0000-0000-7000-8000-000000000002","seccion":"A"}
      """;

  @Autowired private ActivacionEstudianteController controller;

  @Autowired private ActivacionEstudianteService service;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.reset(service);
    SecurityContextHolder.clearContext();
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new SecurityAdvice()).build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void permiteCatedratico() throws Exception {
    var authentication = autenticar(RoleName.CATEDRATICO);

    mockMvc
        .perform(
            post("/api/v1/activaciones-estudiantes")
                .principal(authentication)
                .contentType("application/json")
                .content(REQUEST))
        .andExpect(status().isOk());

    verify(service).activar(any(), any(ActivarEstudianteRequest.class));
  }

  @Test
  void rechazaEstudiante() throws Exception {
    rechazaRol(RoleName.ESTUDIANTE);
  }

  @Test
  void rechazaProfesionalExterno() throws Exception {
    rechazaRol(RoleName.PROFESIONAL_EXTERNO);
  }

  private void rechazaRol(RoleName role) throws Exception {
    var authentication = autenticar(role);

    mockMvc
        .perform(
            post("/api/v1/activaciones-estudiantes")
                .principal(authentication)
                .contentType("application/json")
                .content(REQUEST))
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
    ActivacionEstudianteService activacionEstudianteService() {
      return mock(ActivacionEstudianteService.class);
    }

    @Bean
    ActivacionEstudianteController activacionEstudianteController(
        ActivacionEstudianteService activacionEstudianteService) {
      return new ActivacionEstudianteController(activacionEstudianteService);
    }
  }

  @RestControllerAdvice
  static class SecurityAdvice {
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void accessDenied() {}
  }
}
