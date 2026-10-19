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

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.mantenimiento.dto.ReglaMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.service.ReglaMantenimientoService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
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
@ContextConfiguration(classes = ReglaMantenimientoControllerSecurityTest.Config.class)
class ReglaMantenimientoControllerSecurityTest {

  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000000001");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private ReglaMantenimientoService service;
  @Autowired private VersionFormularioRepository versionRepository;
  @Autowired private ReglaMantenimientoController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(service, versionRepository);
    SecurityContextHolder.clearContext();
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private UsernamePasswordAuthenticationToken autenticar(RoleName rol) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            rol.name(), "N/A", List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    authentication.setDetails(new JwtData("user@cunoc.edu.gt", USUARIO_ID, rol, 0));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return authentication;
  }

  @Test
  @DisplayName("Permite al Administrador listar reglas")
  void permiteAdministradorListar() throws Exception {
    var auth = autenticar(RoleName.ADMINISTRADOR);
    when(service.listar(any(), any(), any(), any(), any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 20), 0));

    mockMvc.perform(get("/api/v1/reglas-mantenimiento").principal(auth)).andExpect(status().isOk());

    verify(service).listar(any(), any(), any(), any(), any(Pageable.class));
  }

  @Test
  @DisplayName("Permite al Catedrático consultar el catálogo (lectura)")
  void permiteCatedraticoConsultar() throws Exception {
    var auth = autenticar(RoleName.CATEDRATICO);
    when(service.listar(any(), any(), any(), any(), any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 20), 0));

    mockMvc.perform(get("/api/v1/reglas-mantenimiento").principal(auth)).andExpect(status().isOk());

    verify(service).listar(any(), any(), any(), any(), any(Pageable.class));
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  @DisplayName("Rechaza otros roles (Estudiante, Profesional) al consultar catálogo con 403")
  void rechazaOtrosRolesConsultar(RoleName rol) throws Exception {
    var auth = autenticar(rol);

    mockMvc
        .perform(get("/api/v1/reglas-mantenimiento").principal(auth))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("access_denied"));

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("Rechaza acceso anónimo con 401 No autorizado")
  void rechazaAnonimoConsultar() throws Exception {
    mockMvc.perform(get("/api/v1/reglas-mantenimiento")).andExpect(status().isUnauthorized());

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("Rechaza al Catedrático al intentar crear una regla (403 Forbidden)")
  void rechazaCatedraticoCrearRegla() throws Exception {
    var auth = autenticar(RoleName.CATEDRATICO);

    String body =
        """
        {
          "codigo": "RM-001",
          "versionFormularioId": "019a0000-0000-7000-8000-000000000002",
          "elementoRef": "/elementos_no_estructurales/junta_entrada",
          "condicion": {"indicador": "junta_limpia", "operador": "IGUAL", "umbral": false},
          "tipoMantenimiento": "RUTINARIO",
          "intervencionSugerida": "Limpieza",
          "reglaPrioridad": {"prioridad_base": "BAJA"},
          "activa": true
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/reglas-mantenimiento")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("Permite al Administrador crear una regla")
  void permiteAdministradorCrearRegla() throws Exception {
    var auth = autenticar(RoleName.ADMINISTRADOR);

    String body =
        """
        {
          "codigo": "RM-001",
          "versionFormularioId": "019a0000-0000-7000-8000-000000000002",
          "elementoRef": "/elementos_no_estructurales/junta_entrada",
          "condicion": {"indicador": "junta_limpia", "operador": "IGUAL", "umbral": false},
          "tipoMantenimiento": "RUTINARIO",
          "intervencionSugerida": "Limpieza",
          "reglaPrioridad": {"prioridad_base": "BAJA"},
          "activa": true
        }
        """;

    ReglaMantenimientoResponse response =
        new ReglaMantenimientoResponse(
            UUID.randomUUID(),
            "RM-001",
            1,
            UUID.randomUUID(),
            "SIECA-v1",
            "/elementos_no_estructurales/junta_entrada",
            OBJECT_MAPPER.readTree("{\"indicador\":\"junta_limpia\"}"),
            TipoMantenimiento.RUTINARIO,
            "Limpieza",
            OBJECT_MAPPER.readTree("{\"prioridad_base\":\"BAJA\"}"),
            true,
            true,
            "Provisional · sin validar",
            USUARIO_ID,
            null,
            null,
            Instant.now());

    when(service.crearRegla(any(), eq(USUARIO_ID))).thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/reglas-mantenimiento")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    verify(service).crearRegla(any(), eq(USUARIO_ID));
  }

  @Test
  @DisplayName("Permite al Administrador validar una regla")
  void permiteAdministradorValidarRegla() throws Exception {
    var auth = autenticar(RoleName.ADMINISTRADOR);
    UUID id = UUID.randomUUID();

    ReglaMantenimientoResponse response =
        new ReglaMantenimientoResponse(
            id,
            "RM-001",
            1,
            UUID.randomUUID(),
            "SIECA-v1",
            "/elementos_no_estructurales/junta_entrada",
            OBJECT_MAPPER.readTree("{\"indicador\":\"junta_limpia\"}"),
            TipoMantenimiento.RUTINARIO,
            "Limpieza",
            OBJECT_MAPPER.readTree("{\"prioridad_base\":\"BAJA\"}"),
            true,
            false,
            "Validada",
            USUARIO_ID,
            USUARIO_ID,
            Instant.now(),
            Instant.now());

    when(service.validarRegla(eq(id), eq(USUARIO_ID))).thenReturn(response);

    mockMvc
        .perform(post("/api/v1/reglas-mantenimiento/" + id + "/validar").principal(auth))
        .andExpect(status().isOk());

    verify(service).validarRegla(eq(id), eq(USUARIO_ID));
  }

  @Test
  @DisplayName("Rechaza al Catedrático validar una regla con 403 Forbidden")
  void rechazaCatedraticoValidarRegla() throws Exception {
    var auth = autenticar(RoleName.CATEDRATICO);
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(post("/api/v1/reglas-mantenimiento/" + id + "/validar").principal(auth))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {

    @Bean
    ReglaMantenimientoService reglaMantenimientoService() {
      return mock(ReglaMantenimientoService.class);
    }

    @Bean
    VersionFormularioRepository versionFormularioRepository() {
      return mock(VersionFormularioRepository.class);
    }

    @Bean
    ReglaMantenimientoController reglaMantenimientoController(
        ReglaMantenimientoService reglaService, VersionFormularioRepository versionRepository) {
      return new ReglaMantenimientoController(reglaService, versionRepository);
    }
  }
}
