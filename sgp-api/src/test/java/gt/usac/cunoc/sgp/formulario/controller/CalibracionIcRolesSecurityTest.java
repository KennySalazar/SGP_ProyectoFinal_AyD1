package gt.usac.cunoc.sgp.formulario.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import gt.usac.cunoc.sgp.common.config.SecurityConfiguration;
import gt.usac.cunoc.sgp.common.security.*;
import gt.usac.cunoc.sgp.formulario.service.ActaCalibracionService;
import gt.usac.cunoc.sgp.formulario.service.VersionFormularioService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(
    controllers = {VersionFormularioController.class, ActaCalibracionController.class},
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({
  SecurityConfiguration.class,
  JwtAuthenticationFilter.class,
  AuthRateLimitFilter.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class
})
class CalibracionIcRolesSecurityTest {
  @Autowired MockMvc mvc;
  @MockBean VersionFormularioService versiones;
  @MockBean ActaCalibracionService actas;
  @MockBean JwtService jwt;
  @MockBean UserAccountRepository usuarios;
  @MockBean Clock clock;
  private static final UUID ID = UUID.fromString("019a0000-0000-7000-8000-000000000053");

  static Stream<Arguments> permisos() {
    return Stream.of(
            "listar",
            "detalle",
            "crear",
            "analisis",
            "borrador",
            "pesos",
            "escala",
            "criticas",
            "acta",
            "contenido",
            "adjuntar",
            "plantilla",
            "publicar",
            "mapeos")
        .flatMap(
            endpoint ->
                Stream.of(
                        "ADMINISTRADOR",
                        "CATEDRATICO",
                        "ESTUDIANTE",
                        "PROFESIONAL_EXTERNO",
                        "ANONIMO")
                    .map(rol -> Arguments.of(endpoint, rol)));
  }

  private MockHttpServletRequestBuilder solicitud(String endpoint) {
    String ruta = "/api/v1/formularios/" + ID;
    return switch (endpoint) {
      case "plantilla" -> post("/api/v1/formularios/plantilla-sieca");
      case "publicar" -> post(ruta + "/publicar");
      case "mapeos" -> put(ruta + "/mapeos").contentType("application/json").content("[]");
      case "listar" -> get("/api/v1/formularios");
      case "detalle" -> get(ruta);
      case "crear" ->
          post("/api/v1/formularios")
              .contentType("application/json")
              .content("{\"codigo\":\"v53\",\"esquema\":{\"type\":\"object\"}}");
      case "analisis" -> get(ruta + "/mapeos/analisis");
      case "borrador" ->
          put(ruta)
              .header("If-Match", "\"0\"")
              .contentType("application/json")
              .content("{\"esquema\":{\"type\":\"object\"},\"reglasEvaluacionIc\":{}}");
      case "pesos" ->
          put(ruta + "/pesos")
              .header("If-Match", "0")
              .contentType("application/json")
              .content("[{\"elementoRef\":\"/viga\",\"peso\":1,\"descripcion\":\"Viga\"}]");
      case "escala" ->
          put(ruta + "/escala")
              .header("If-Match", "0")
              .contentType("application/json")
              .content("{\"valores\":{\"NO_PRESENTA\":0,\"LEVE\":1,\"MODERADO\":2,\"SEVERO\":4}}");
      case "criticas" ->
          put(ruta + "/reglas-criticas")
              .header("If-Match", "0")
              .contentType("application/json")
              .content("{\"reglasAnulacion\":[]}");
      case "acta" -> get(ruta + "/acta-calibracion");
      case "contenido" -> get(ruta + "/acta-calibracion/contenido");
      case "adjuntar" ->
          multipart(ruta + "/acta-calibracion")
              .file(new MockMultipartFile("archivo", "acta.pdf", "application/pdf", new byte[] {1}))
              .with(
                  r -> {
                    r.setMethod("PUT");
                    return r;
                  })
              .header("If-Match", "0");
      default -> throw new IllegalArgumentException(endpoint);
    };
  }

  @ParameterizedTest
  @MethodSource("permisos")
  void cadaEndpointRespetaElRol(String endpoint, String rol) throws Exception {
    when(actas.descargar(ID)).thenReturn(new byte[] {1});
    var request = solicitud(endpoint);
    if (!"ANONIMO".equals(rol)) {
      var auth =
          new UsernamePasswordAuthenticationToken(
              "test", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
      auth.setDetails(new JwtData("test@hu053.test", ID, RoleName.valueOf(rol), 0));
      request.with(authentication(auth));
    }
    boolean permitido =
        "ADMINISTRADOR".equals(rol)
            || ("CATEDRATICO".equals(rol)
                && !List.of("plantilla", "publicar", "mapeos").contains(endpoint));
    int esperado =
        "ANONIMO".equals(rol)
            ? 401
            : permitido ? (List.of("crear", "plantilla").contains(endpoint) ? 201 : 200) : 403;
    mvc.perform(request).andExpect(status().is(esperado));
    if (esperado == 401 || esperado == 403) {
      verifyNoInteractions(versiones);
      verify(actas, never()).consultar(any());
      verify(actas, never()).adjuntar(any(), any(), any(), anyLong());
      verify(actas, never()).descargar(any());
    }
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"borrador", "pesos", "escala", "criticas", "adjuntar"})
  void todosLosGuardadosExigenLaVersionConsultada(String endpoint) throws Exception {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "test", null, List.of(new SimpleGrantedAuthority("ROLE_CATEDRATICO")));
    auth.setDetails(new JwtData("test@hu053.test", ID, RoleName.CATEDRATICO, 0));
    var request =
        solicitud(endpoint)
            .with(authentication(auth))
            .with(
                r -> {
                  r.removeHeader("If-Match");
                  return r;
                });
    mvc.perform(request)
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.code").value("version_calibracion_requerida"));
    verifyNoInteractions(versiones, actas);
  }
}
