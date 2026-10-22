package gt.usac.cunoc.sgp.revision.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.revision.service.ObservacionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.AuthenticationException;
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
@ContextConfiguration(classes = ObservacionAutorControllerSecurityTest.Config.class)
class ObservacionAutorControllerSecurityTest {

  @Autowired private ObservacionAutorController controller;
  @Autowired private ObservacionService service;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.reset(service);
    SecurityContextHolder.clearContext();
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .setControllerAdvice(new SecurityAdvice())
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Listar observaciones recibidas permite rol ESTUDIANTE")
  void listarPermiteEstudiante() throws Exception {
    UUID studentId = UUID.randomUUID();
    var auth = autenticar(RoleName.ESTUDIANTE, studentId);
    when(service.recibidas(org.mockito.ArgumentMatchers.eq(studentId), anyInt(), anyInt()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc
        .perform(get("/api/v1/observaciones/recibidas").principal(auth))
        .andExpect(status().isOk());

    verify(service).recibidas(studentId, 0, 20);
  }

  @Test
  @DisplayName("Listar observaciones recibidas rechaza rol CATEDRATICO con 403")
  void listarRechazaCatedratico() throws Exception {
    var auth = autenticar(RoleName.CATEDRATICO, UUID.randomUUID());

    mockMvc
        .perform(get("/api/v1/observaciones/recibidas").principal(auth))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("Listar observaciones recibidas rechaza rol ADMINISTRADOR con 403")
  void listarRechazaAdmin() throws Exception {
    var auth = autenticar(RoleName.ADMINISTRADOR, UUID.randomUUID());

    mockMvc
        .perform(get("/api/v1/observaciones/recibidas").principal(auth))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("Listar observaciones recibidas rechaza acceso anónimo con 401")
  void listarRechazaAnonimo() throws Exception {
    mockMvc.perform(get("/api/v1/observaciones/recibidas")).andExpect(status().isUnauthorized());

    verifyNoInteractions(service);
  }

  private UsernamePasswordAuthenticationToken autenticar(RoleName rol, UUID userId) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            "user@ejemplo.com", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    authentication.setDetails(new JwtData("user@ejemplo.com", userId, rol, 0));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return authentication;
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {
    @Bean
    ObservacionService observacionService() {
      return mock(ObservacionService.class);
    }

    @Bean
    ObservacionAutorController observacionAutorController(ObservacionService observacionService) {
      return new ObservacionAutorController(observacionService);
    }
  }

  @RestControllerAdvice
  static class SecurityAdvice {
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void accessDenied() {}

    @ExceptionHandler({AuthenticationException.class, ApiException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    void unauthenticated() {}
  }
}
