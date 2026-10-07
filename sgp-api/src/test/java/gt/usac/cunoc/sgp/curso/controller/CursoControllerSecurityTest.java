package gt.usac.cunoc.sgp.curso.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.curso.dto.CrearCursoRequest;
import gt.usac.cunoc.sgp.curso.service.CursoService;
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
@ContextConfiguration(classes = CursoControllerSecurityTest.Config.class)
class CursoControllerSecurityTest {

  private static final String REQUEST =
      """
      {"nombre":"Analisis de Sistemas","periodo":"2026-2",
       "catedraticoId":"019a0000-0000-7000-8000-000000000001",
       "fechaInicio":"2026-07-01","fechaFin":"2026-11-01"}
      """;

  @Autowired private CursoController cursoController;

  @Autowired private CursoService cursoService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.reset(cursoService);
    SecurityContextHolder.clearContext();
    mockMvc =
        MockMvcBuilders.standaloneSetup(cursoController)
            .setControllerAdvice(new SecurityAdvice())
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void rechazaRolNoAdministrador() throws Exception {
    var authentication = autenticar(RoleName.ESTUDIANTE);

    mockMvc
        .perform(
            post("/api/v1/cursos")
                .principal(authentication)
                .contentType("application/json")
                .content(REQUEST))
        .andExpect(status().isForbidden());

    verifyNoInteractions(cursoService);
  }

  @Test
  void permiteAdministrador() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);

    mockMvc
        .perform(
            post("/api/v1/cursos")
                .principal(authentication)
                .contentType("application/json")
                .content(REQUEST))
        .andExpect(status().isCreated());

    verify(cursoService).crear(any(CrearCursoRequest.class));
  }

  private UsernamePasswordAuthenticationToken autenticar(RoleName rol) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            "admin@ejemplo.com", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    authentication.setDetails(new JwtData("admin@ejemplo.com", UUID.randomUUID(), rol, 0));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return authentication;
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {
    @Bean
    CursoService cursoService() {
      return mock(CursoService.class);
    }

    @Bean
    CursoController cursoController(CursoService cursoService) {
      return new CursoController(cursoService);
    }
  }

  @RestControllerAdvice
  static class SecurityAdvice {
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void accessDenied() {}
  }
}
