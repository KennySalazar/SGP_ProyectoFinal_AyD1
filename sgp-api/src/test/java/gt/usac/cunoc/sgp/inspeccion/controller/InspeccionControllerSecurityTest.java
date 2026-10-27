package gt.usac.cunoc.sgp.inspeccion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.dto.GuardarRespuestasRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionVersionadaRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionVersionadaResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.ValidarRespuestasRequest;
import gt.usac.cunoc.sgp.inspeccion.service.InspeccionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
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
@ContextConfiguration(classes = InspeccionControllerSecurityTest.Config.class)
class InspeccionControllerSecurityTest {

  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000000001");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

  @Autowired private InspeccionService service;
  @Autowired private InspeccionController controller;

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
  void iniciarInspeccion_conDatosValidos_retorna201() throws Exception {
    var auth = autenticar(RoleName.CATEDRATICO);
    UUID puenteId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    UUID inspeccionId = UUID.randomUUID();

    IniciarInspeccionVersionadaRequest request =
        new IniciarInspeccionVersionadaRequest(
            puenteId, versionId, LocalDate.now(), "web", "Estudiante Inspector", null);

    IniciarInspeccionVersionadaResponse dummy =
        new IniciarInspeccionVersionadaResponse(
            inspeccionId,
            puenteId,
            "Puente San José",
            versionId,
            "SIECA-v1",
            USUARIO_ID,
            "CATEDRATICO",
            "Estudiante Inspector",
            "ORIGINAL",
            "BORRADOR",
            LocalDate.now(),
            "web",
            OBJECT_MAPPER.createObjectNode(),
            Instant.now(),
            Instant.now());

    when(service.iniciarInspeccion(
            any(IniciarInspeccionVersionadaRequest.class), eq(USUARIO_ID), eq("CATEDRATICO")))
        .thenReturn(dummy);

    mockMvc
        .perform(
            post("/api/v1/inspecciones/iniciar")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(inspeccionId.toString()))
        .andExpect(jsonPath("$.estado").value("BORRADOR"));
  }

  @Test
  void inicioAcademicoTransmiteAsignacionExplicitaEIdentidadAutenticada() throws Exception {
    var auth = autenticar(RoleName.ESTUDIANTE);
    var request =
        new IniciarInspeccionVersionadaRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "web",
            "Estudiante",
            OBJECT_MAPPER.createObjectNode(),
            UUID.randomUUID());

    mockMvc
        .perform(
            post("/api/v1/inspecciones/iniciar")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(request)))
        .andExpect(status().isCreated());

    org.mockito.Mockito.verify(service).iniciarInspeccion(request, USUARIO_ID, "ESTUDIANTE");
  }

  @Test
  void iniciarInspeccion_conVersionNoUtilizable_retorna422Rfc7807() throws Exception {
    var auth = autenticar(RoleName.CATEDRATICO);
    IniciarInspeccionVersionadaRequest request =
        new IniciarInspeccionVersionadaRequest(
            UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), "web", "Inspector", null);

    when(service.iniciarInspeccion(
            any(IniciarInspeccionVersionadaRequest.class), eq(USUARIO_ID), any()))
        .thenThrow(
            new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "version_no_utilizable",
                "Versión no utilizable",
                "Solo se pueden iniciar inspecciones con versiones de formulario publicadas"));

    mockMvc
        .perform(
            post("/api/v1/inspecciones/iniciar")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("version_no_utilizable"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Solo se pueden iniciar inspecciones con versiones de formulario publicadas"));
  }

  @Test
  void estudianteNoPuedeOmitirLaValidacionDeAsignacionConInicioVersionado() throws Exception {
    var auth = autenticar(RoleName.ESTUDIANTE);
    IniciarInspeccionVersionadaRequest request =
        new IniciarInspeccionVersionadaRequest(
            UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), "web", "Estudiante", null);

    mockMvc
        .perform(
            post("/api/v1/inspecciones/iniciar")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void guardarRespuestas_conTipoOValorIncorrecto_retorna422ConListaErrores() throws Exception {
    var auth = autenticar(RoleName.PROFESIONAL_EXTERNO);
    UUID inspeccionId = UUID.randomUUID();

    GuardarRespuestasRequest request =
        new GuardarRespuestasRequest(
            OBJECT_MAPPER.readTree(
                """
                {
                  "subestructura": {
                    "estribo_entrada": {
                      "material": "PLASTICO_INVALIDO"
                    }
                  }
                }
                """));

    when(service.guardarRespuestas(eq(inspeccionId), any(), eq(USUARIO_ID)))
        .thenThrow(
            new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "datos_formulario_invalidos",
                "Respuestas de inspección inválidas",
                "Los datos de la inspección no cumplen con el esquema de la versión",
                List.of(
                    Map.of(
                        "campo",
                        "subestructura.estribo_entrada.material",
                        "mensaje",
                        "Valor no permitido para el campo 'subestructura.estribo_entrada.material'"))));

    mockMvc
        .perform(
            put("/api/v1/inspecciones/" + inspeccionId + "/datos")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("datos_formulario_invalidos"))
        .andExpect(jsonPath("$.errores[0].campo").value("subestructura.estribo_entrada.material"))
        .andExpect(
            jsonPath("$.errores[0].mensaje")
                .value(
                    "Valor no permitido para el campo 'subestructura.estribo_entrada.material'"));
  }

  @Test
  void validarRespuestas_conRespuestasValidas_retornaOk() throws Exception {
    var auth = autenticar(RoleName.ESTUDIANTE);
    UUID inspeccionId = UUID.randomUUID();

    ValidarRespuestasRequest request =
        new ValidarRespuestasRequest(
            OBJECT_MAPPER.readTree("{\"identificacion\": {\"nombre\": \"Puente\"}}"), true);

    mockMvc
        .perform(
            post("/api/v1/inspecciones/" + inspeccionId + "/validar")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valido").value(true));

    verify(service).validarRespuestasInspeccion(eq(inspeccionId), any(), eq(true), eq(USUARIO_ID));
  }

  private UsernamePasswordAuthenticationToken autenticar(RoleName rol) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "test@cunoc.edu.gt", "N/A", List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    auth.setDetails(new JwtData("test@cunoc.edu.gt", USUARIO_ID, rol, 1));
    SecurityContextHolder.getContext().setAuthentication(auth);
    return auth;
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {

    @Bean
    InspeccionService inspeccionService() {
      return mock(InspeccionService.class);
    }

    @Bean
    InspeccionController inspeccionController(InspeccionService service) {
      return new InspeccionController(service);
    }
  }
}
