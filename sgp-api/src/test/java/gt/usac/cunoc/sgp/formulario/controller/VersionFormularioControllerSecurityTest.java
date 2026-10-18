package gt.usac.cunoc.sgp.formulario.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.formulario.dto.CrearVersionRequest;
import gt.usac.cunoc.sgp.formulario.dto.FormularioActivoResponse;
import gt.usac.cunoc.sgp.formulario.dto.VersionFormularioResponse;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.service.VersionFormularioService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.time.Instant;
import java.util.Collections;
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
@ContextConfiguration(classes = VersionFormularioControllerSecurityTest.Config.class)
class VersionFormularioControllerSecurityTest {

  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000000001");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private VersionFormularioService service;
  @Autowired private VersionFormularioController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(service);
    SecurityContextHolder.clearContext();
    mockMvc =
            MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new ApiExceptionHandler())
                    .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void permiteAdministradorListarVersiones() throws Exception {
    var auth = autenticar(RoleName.ADMINISTRADOR);
    when(service.listarVersiones()).thenReturn(Collections.emptyList());

    mockMvc.perform(get("/api/v1/formularios").principal(auth)).andExpect(status().isOk());

    verify(service).listarVersiones();
  }

  @ParameterizedTest
  @EnumSource(
          value = RoleName.class,
          names = {"CATEDRATICO", "ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void rechazaNoAdministradoresListarVersiones(RoleName rol) throws Exception {
    var auth = autenticar(rol);

    mockMvc
            .perform(get("/api/v1/formularios").principal(auth))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("access_denied"));

    verifyNoInteractions(service);
  }

  @Test
  void permiteCualquierUsuarioAutenticadoObtenerFormularioActivo() throws Exception {
    var auth = autenticar(RoleName.ESTUDIANTE);
    FormularioActivoResponse dummy =
            new FormularioActivoResponse(
                    UUID.randomUUID(),
                    "SIECA-v1",
                    1L,
                    OBJECT_MAPPER.readTree("{\"type\":\"object\"}"),
                    "hash123",
                    Instant.now());
    when(service.obtenerActiva()).thenReturn(dummy);

    mockMvc
            .perform(get("/api/v1/formularios/activo").principal(auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.codigo").value("SIECA-v1"));

    verify(service).obtenerActiva();
  }

  @ParameterizedTest
  @EnumSource(RoleName.class)
  void permiteCualquierUsuarioAutenticadoObtenerElEsquemaDeUnaVersionPublicada(RoleName rol)
          throws Exception {
    var auth = autenticar(rol);
    UUID id = UUID.randomUUID();
    when(service.obtenerPublicadaPorId(id))
            .thenReturn(
                    new FormularioActivoResponse(
                            id,
                            "SIECA-v1",
                            1L,
                            OBJECT_MAPPER.readTree("{\"type\":\"object\"}"),
                            "hash123",
                            Instant.now()));

    mockMvc
            .perform(get("/api/v1/formularios/" + id + "/esquema").principal(auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.codigo").value("SIECA-v1"))
            .andExpect(jsonPath("$.esquema.type").value("object"));

    verify(service).obtenerPublicadaPorId(id);
  }

  @Test
  void unIdentificadorMalFormadoResponde400EnVezDeError500() throws Exception {
    var auth = autenticar(RoleName.CATEDRATICO);

    mockMvc
            .perform(get("/api/v1/formularios/no-es-uuid/esquema").principal(auth))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_error"))
            .andExpect(jsonPath("$.errores[0].campo").value("id"));

    verifyNoInteractions(service);
  }

  @Test
  void respondeNoEncontradoParaUnaVersionNoPublicadaOInexistente() throws Exception {
    var auth = autenticar(RoleName.CATEDRATICO);
    UUID id = UUID.randomUUID();
    when(service.obtenerPublicadaPorId(id))
            .thenThrow(
                    new gt.usac.cunoc.sgp.common.exception.ApiException(
                            org.springframework.http.HttpStatus.NOT_FOUND,
                            "version_no_encontrada",
                            "Versión no encontrada",
                            "No existe una versión publicada"));

    mockMvc
            .perform(get("/api/v1/formularios/" + id + "/esquema").principal(auth))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("version_no_encontrada"));
  }

  @Test
  void permiteAdministradorCrearVersion() throws Exception {
    var auth = autenticar(RoleName.ADMINISTRADOR);
    VersionFormularioResponse resp =
            new VersionFormularioResponse(
                    UUID.randomUUID(),
                    "v1",
                    EstadoVersionFormulario.BORRADOR,
                    false,
                    OBJECT_MAPPER.readTree("{\"type\":\"object\"}"),
                    OBJECT_MAPPER.readTree("{}"),
                    OBJECT_MAPPER.readTree("{}"),
                    null,
                    "IC-SIECA-v1",
                    null,
                    null,
                    USUARIO_ID,
                    null,
                    null,
                    null,
                    null,
                    Instant.now(),
                    Instant.now(),
                    1L,
                    Collections.emptyList(),
                    Collections.emptyList());

    when(service.crear(any(CrearVersionRequest.class), eq(USUARIO_ID))).thenReturn(resp);

    mockMvc
            .perform(
                    post("/api/v1/formularios")
                            .principal(auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                                    {
                                      "codigo": "v1",
                                      "esquema": {"type": "object"}
                                    }
                                    """))
            .andExpect(status().isCreated());

    verify(service).crear(any(CrearVersionRequest.class), eq(USUARIO_ID));
  }

  @ParameterizedTest
  @EnumSource(
          value = RoleName.class,
          names = {"CATEDRATICO", "ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void rechazaNoAdministradoresCrearVersion(RoleName rol) throws Exception {
    var auth = autenticar(rol);

    mockMvc
            .perform(
                    post("/api/v1/formularios")
                            .principal(auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                                    {
                                      "codigo": "v1",
                                      "esquema": {"type": "object"}
                                    }
                                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("access_denied"));

    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @EnumSource(
          value = RoleName.class,
          names = {"CATEDRATICO", "ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void rechazaNoAdministradoresPublicar(RoleName rol) throws Exception {
    var auth = autenticar(rol);
    UUID id = UUID.randomUUID();

    mockMvc
            .perform(post("/api/v1/formularios/" + id + "/publicar").principal(auth))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("access_denied"));

    verifyNoInteractions(service);
  }

  @Test
  void permiteAdministradorConfigurarPesos() throws Exception {
    var auth = autenticar(RoleName.ADMINISTRADOR);
    UUID id = UUID.randomUUID();

    when(service.calibrarPesos(eq(id), any(), eq(USUARIO_ID))).thenReturn(Collections.emptyList());

    mockMvc
            .perform(
                    put("/api/v1/formularios/" + id + "/pesos")
                            .principal(auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                                                    [
                                                      {
                                                        "elementoRef": "/vigas",
                                                        "peso": 1,
                                                        "descripcion": "Vigas"
                                                      }
                                                    ]
                                                    """))
            .andExpect(status().isOk());

    verify(service).calibrarPesos(eq(id), any(), eq(USUARIO_ID));
  }

  @ParameterizedTest
  @EnumSource(
          value = RoleName.class,
          names = {"CATEDRATICO", "ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void rechazaNoAdministradoresConfigurarPesos(RoleName rol) throws Exception {
    var auth = autenticar(rol);
    UUID id = UUID.randomUUID();

    mockMvc
            .perform(
                    put("/api/v1/formularios/" + id + "/pesos")
                            .principal(auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                                                    [
                                                      {
                                                        "elementoRef": "/vigas",
                                                        "peso": 1,
                                                        "descripcion": "Vigas"
                                                      }
                                                    ]
                                                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("access_denied"));

    verifyNoInteractions(service);
  }

  @Test
  void permiteAdministradorConfigurarEscala() throws Exception {
    var auth = autenticar(RoleName.ADMINISTRADOR);
    UUID id = UUID.randomUUID();

    mockMvc
            .perform(
                    put("/api/v1/formularios/" + id + "/escala")
                            .principal(auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                                                    {
                                                      "valores": {
                                                        "NO_PRESENTA": 0,
                                                        "LEVE": 1,
                                                        "MODERADO": 2,
                                                        "SEVERO": 4
                                                      }
                                                    }
                                                    """))
            .andExpect(status().isOk());

    verify(service).configurarEscala(eq(id), any(), eq(USUARIO_ID));
  }

  @ParameterizedTest
  @EnumSource(
          value = RoleName.class,
          names = {"CATEDRATICO", "ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void rechazaNoAdministradoresConfigurarEscala(RoleName rol) throws Exception {
    var auth = autenticar(rol);
    UUID id = UUID.randomUUID();

    mockMvc
            .perform(
                    put("/api/v1/formularios/" + id + "/escala")
                            .principal(auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                                                    {
                                                      "valores": {
                                                        "NO_PRESENTA": 0,
                                                        "LEVE": 1,
                                                        "MODERADO": 2,
                                                        "SEVERO": 4
                                                      }
                                                    }
                                                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("access_denied"));

    verifyNoInteractions(service);
  }

  private UsernamePasswordAuthenticationToken autenticar(RoleName rol) {
    var authentication =
            new UsernamePasswordAuthenticationToken(
                    "admin@cunoc.edu.gt", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));

    authentication.setDetails(new JwtData("admin@cunoc.edu.gt", USUARIO_ID, rol, 0));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return authentication;
  }

  @Test
  void permiteAdministradorConfigurarReglasCriticas() throws Exception {
    var auth = autenticar(RoleName.ADMINISTRADOR);
    UUID id = UUID.randomUUID();
    mockMvc
            .perform(
                    put("/api/v1/formularios/" + id + "/reglas-criticas")
                            .principal(auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reglasAnulacion\":[]}"))
            .andExpect(status().isOk());
    verify(service).configurarReglasCriticas(eq(id), any(), eq(USUARIO_ID));
  }

  @ParameterizedTest
  @EnumSource(
          value = RoleName.class,
          names = {"CATEDRATICO", "ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void rechazaNoAdministradoresConfigurarReglasCriticas(RoleName rol) throws Exception {
    var auth = autenticar(rol);
    mockMvc
            .perform(
                    put("/api/v1/formularios/" + UUID.randomUUID() + "/reglas-criticas")
                            .principal(auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reglasAnulacion\":[]}"))
            .andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {

    @Bean
    VersionFormularioService versionFormularioService() {
      return mock(VersionFormularioService.class);
    }

    @Bean
    VersionFormularioController versionFormularioController(VersionFormularioService service) {
      return new VersionFormularioController(service);
    }
  }
}