package gt.usac.cunoc.sgp.inspeccion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.config.SecurityConfiguration;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.AuthRateLimitFilter;
import gt.usac.cunoc.sgp.common.security.JwtAuthenticationFilter;
import gt.usac.cunoc.sgp.common.security.JwtService;
import gt.usac.cunoc.sgp.common.security.ProblemAccessDeniedHandler;
import gt.usac.cunoc.sgp.common.security.ProblemAuthenticationEntryPoint;
import gt.usac.cunoc.sgp.inspeccion.dto.ConfirmacionEstadoIcResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.ConfirmarEstadoIcRequest;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.service.ConfirmacionEstadoIcService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = ConfirmacionEstadoIcController.class,
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({
  SecurityConfiguration.class,
  JwtAuthenticationFilter.class,
  AuthRateLimitFilter.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class
})
class ConfirmacionEstadoIcControllerSecurityTest {

  private static final UUID INSPECCION_ID = UUID.fromString("019a0000-0000-7000-8000-000000000032");
  private static final String URL =
      "/api/v1/inspecciones/" + INSPECCION_ID + "/confirmacion-estado";
  private static final String SOLICITUD =
      """
      {
        "estadoConfirmado": "REGULAR",
        "hashEntrada": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        "justificacion": "Criterio profesional"
      }
      """;

  @Autowired private MockMvc mvc;
  @MockBean private ConfirmacionEstadoIcService service;
  @MockBean private JwtService jwtService;
  @MockBean private UserAccountRepository users;
  @MockBean private Clock clock;

  @Test
  void consultaSinSesionResponde401() throws Exception {
    mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }

  @Test
  void confirmacionSinSesionResponde401() throws Exception {
    mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @EnumSource(RoleName.class)
  void usuarioAutenticadoDelegaValidacionDeResponsableAlServicio(RoleName rol) throws Exception {
    when(service.consultar(INSPECCION_ID))
        .thenReturn(
            new ConfirmacionEstadoIcResponse(
                INSPECCION_ID,
                null,
                EstadoCondicion.REGULAR,
                "Criterio profesional",
                UUID.randomUUID(),
                Instant.parse("2026-10-15T22:00:00Z"),
                true));
    mvc.perform(get(URL).with(user("evaluador").roles(rol.name())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.inspeccionId").value(INSPECCION_ID.toString()))
        .andExpect(jsonPath("$.estadoConfirmado").value("REGULAR"))
        .andExpect(jsonPath("$.confirmacionVigente").value(true));
    verify(service).consultar(INSPECCION_ID);
  }

  @Test
  void confirmacionValidaDelegaSolicitudAlServicio() throws Exception {
    mvc.perform(
            put(URL)
                .with(user("evaluador").roles("PROFESIONAL_EXTERNO"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SOLICITUD))
        .andExpect(status().isOk());
    verify(service)
        .confirmar(
            INSPECCION_ID,
            new ConfirmarEstadoIcRequest(
                EstadoCondicion.REGULAR, "b".repeat(64), "Criterio profesional"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"estadoConfirmado\":\"REGULAR\"}",
        "{\"estadoConfirmado\":\"REGULAR\",\"hashEntrada\":\"incorrecta\"}"
      })
  void solicitudIncompletaResponde400SinInvocarServicio(String cuerpo) throws Exception {
    mvc.perform(
            put(URL)
                .with(user("evaluador").roles("ESTUDIANTE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_error"));
    verifyNoInteractions(service);
  }

  @Test
  void autorAjenoResponde403EnConsulta() throws Exception {
    when(service.consultar(INSPECCION_ID))
        .thenThrow(error(HttpStatus.FORBIDDEN, "evaluador_no_responsable"));
    mvc.perform(get(URL).with(user("ajeno").roles("ADMINISTRADOR")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("evaluador_no_responsable"));
  }

  @Test
  void autorAjenoResponde403AlConfirmar() throws Exception {
    when(service.confirmar(eq(INSPECCION_ID), any()))
        .thenThrow(error(HttpStatus.FORBIDDEN, "evaluador_no_responsable"));
    mvc.perform(
            put(URL)
                .with(user("ajeno").roles("ADMINISTRADOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SOLICITUD))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("evaluador_no_responsable"));
  }

  @Test
  void inspeccionPublicadaResponde409() throws Exception {
    when(service.confirmar(eq(INSPECCION_ID), any()))
        .thenThrow(error(HttpStatus.CONFLICT, "inspeccion_publicada_inmutable"));
    mvc.perform(
            put(URL)
                .with(user("evaluador").roles("ESTUDIANTE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SOLICITUD))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("inspeccion_publicada_inmutable"));
  }

  @Test
  void ajusteSinJustificacionResponde422() throws Exception {
    when(service.confirmar(eq(INSPECCION_ID), any()))
        .thenThrow(error(HttpStatus.UNPROCESSABLE_ENTITY, "justificacion_ajuste_obligatoria"));
    mvc.perform(
            put(URL)
                .with(user("evaluador").roles("ESTUDIANTE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SOLICITUD.replace("Criterio profesional", "")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("justificacion_ajuste_obligatoria"));
  }

  private ApiException error(HttpStatus estado, String codigo) {
    return new ApiException(estado, codigo, "Confirmacion del estado IC", "Operacion rechazada.");
  }
}
