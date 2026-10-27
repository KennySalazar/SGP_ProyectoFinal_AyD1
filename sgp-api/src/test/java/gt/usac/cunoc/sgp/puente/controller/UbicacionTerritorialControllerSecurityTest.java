package gt.usac.cunoc.sgp.puente.controller;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.puente.dto.UbicacionTerritorialResponse;
import gt.usac.cunoc.sgp.puente.service.UbicacionTerritorialService;
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
@ContextConfiguration(classes = UbicacionTerritorialControllerSecurityTest.Config.class)
class UbicacionTerritorialControllerSecurityTest {

  @Autowired private UbicacionTerritorialController controller;
  @Autowired private UbicacionTerritorialService service;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.reset(service);
    SecurityContextHolder.clearContext();
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new SecurityAdvice())
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Resolver ubicación permite rol ADMINISTRADOR")
  void resolverPermiteAdmin() throws Exception {
    var auth = autenticar(RoleName.ADMINISTRADOR);
    when(service.resolver(anyDouble(), anyDouble()))
        .thenReturn(new UbicacionTerritorialResponse(14.64, -90.51, "15N", false, List.of()));

    mockMvc
        .perform(
            get("/api/v1/catalogos/ubicacion")
                .principal(auth)
                .param("latitud", "14.64")
                .param("longitud", "-90.51"))
        .andExpect(status().isOk());

    verify(service).resolver(14.64, -90.51);
  }

  @Test
  @DisplayName("Resolver ubicación permite rol CATEDRATICO")
  void resolverPermiteCatedratico() throws Exception {
    var auth = autenticar(RoleName.CATEDRATICO);
    when(service.resolver(anyDouble(), anyDouble()))
        .thenReturn(new UbicacionTerritorialResponse(14.83, -91.52, "15N", false, List.of()));

    mockMvc
        .perform(
            get("/api/v1/catalogos/ubicacion")
                .principal(auth)
                .param("latitud", "14.83")
                .param("longitud", "-91.52"))
        .andExpect(status().isOk());

    verify(service).resolver(14.83, -91.52);
  }

  @Test
  @DisplayName("Resolver ubicación rechaza rol ESTUDIANTE con 403")
  void resolverRechazaEstudiante() throws Exception {
    var auth = autenticar(RoleName.ESTUDIANTE);

    mockMvc
        .perform(
            get("/api/v1/catalogos/ubicacion")
                .principal(auth)
                .param("latitud", "14.83")
                .param("longitud", "-91.52"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("Resolver ubicación rechaza acceso anónimo sin autenticación")
  void resolverRechazaSinAutenticacion() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/catalogos/ubicacion")
                .param("latitud", "14.83")
                .param("longitud", "-91.52"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  private UsernamePasswordAuthenticationToken autenticar(RoleName rol) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            "user@ejemplo.com", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    authentication.setDetails(new JwtData("user@ejemplo.com", UUID.randomUUID(), rol, 0));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return authentication;
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {
    @Bean
    UbicacionTerritorialService ubicacionTerritorialService() {
      return mock(UbicacionTerritorialService.class);
    }

    @Bean
    UbicacionTerritorialController ubicacionTerritorialController(
        UbicacionTerritorialService ubicacionTerritorialService) {
      return new UbicacionTerritorialController(ubicacionTerritorialService);
    }
  }

  @RestControllerAdvice
  static class SecurityAdvice {
    @ExceptionHandler({
      AccessDeniedException.class,
      org.springframework.security.core.AuthenticationException.class
    })
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void accessDenied() {}
  }
}
