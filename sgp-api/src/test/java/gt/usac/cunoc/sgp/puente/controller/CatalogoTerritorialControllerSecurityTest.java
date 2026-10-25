package gt.usac.cunoc.sgp.puente.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.puente.service.CatalogoTerritorialService;
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
@ContextConfiguration(classes = CatalogoTerritorialControllerSecurityTest.Config.class)
class CatalogoTerritorialControllerSecurityTest {

  @Autowired private CatalogoTerritorialController controller;
  @Autowired private CatalogoTerritorialService service;

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
  @DisplayName("Listar departamentos es público (permitAll)")
  void listarDepartamentosEsPublico() throws Exception {
    when(service.listarDepartamentos(anyInt(), anyInt()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

    mockMvc.perform(get("/api/v1/catalogos/departamentos")).andExpect(status().isOk());

    verify(service).listarDepartamentos(0, 100);
  }

  @Test
  @DisplayName("Listar municipios permite rol ADMINISTRADOR")
  void listarMunicipiosPermiteAdmin() throws Exception {
    var auth = autenticar(RoleName.ADMINISTRADOR);
    UUID deptoId = UUID.randomUUID();
    when(service.listarMunicipios(org.mockito.ArgumentMatchers.eq(deptoId), anyInt(), anyInt()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

    mockMvc
        .perform(
            get("/api/v1/catalogos/departamentos/{departamentoId}/municipios", deptoId)
                .principal(auth))
        .andExpect(status().isOk());

    verify(service).listarMunicipios(deptoId, 0, 100);
  }

  @Test
  @DisplayName("Listar municipios permite rol CATEDRATICO")
  void listarMunicipiosPermiteCatedratico() throws Exception {
    var auth = autenticar(RoleName.CATEDRATICO);
    UUID deptoId = UUID.randomUUID();
    when(service.listarMunicipios(org.mockito.ArgumentMatchers.eq(deptoId), anyInt(), anyInt()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

    mockMvc
        .perform(
            get("/api/v1/catalogos/departamentos/{departamentoId}/municipios", deptoId)
                .principal(auth))
        .andExpect(status().isOk());

    verify(service).listarMunicipios(deptoId, 0, 100);
  }

  @Test
  @DisplayName("Listar municipios rechaza rol ESTUDIANTE con 403")
  void listarMunicipiosRechazaEstudiante() throws Exception {
    var auth = autenticar(RoleName.ESTUDIANTE);
    UUID deptoId = UUID.randomUUID();

    mockMvc
        .perform(
            get("/api/v1/catalogos/departamentos/{departamentoId}/municipios", deptoId)
                .principal(auth))
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
    CatalogoTerritorialService catalogoTerritorialService() {
      return mock(CatalogoTerritorialService.class);
    }

    @Bean
    CatalogoTerritorialController catalogoTerritorialController(
        CatalogoTerritorialService catalogoTerritorialService) {
      return new CatalogoTerritorialController(catalogoTerritorialService);
    }
  }

  @RestControllerAdvice
  static class SecurityAdvice {
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void accessDenied() {}
  }
}
