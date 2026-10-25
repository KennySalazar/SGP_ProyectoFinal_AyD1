package gt.usac.cunoc.sgp.puente.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import gt.usac.cunoc.sgp.common.config.SecurityConfiguration;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.*;
import gt.usac.cunoc.sgp.formulario.controller.AprobacionCalibracionController;
import gt.usac.cunoc.sgp.formulario.dto.EstadoCalibracionResponse;
import gt.usac.cunoc.sgp.formulario.service.AprobacionCalibracionService;
import gt.usac.cunoc.sgp.puente.service.EstadoCalibracionPuenteService;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = {EstadoCalibracionPuenteController.class, AprobacionCalibracionController.class},
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({
  SecurityConfiguration.class,
  JwtAuthenticationFilter.class,
  AuthRateLimitFilter.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class
})
class EstadoCalibracionPuenteControllerTest {
  @Autowired MockMvc mvc;
  @MockBean EstadoCalibracionPuenteService servicio;
  @MockBean AprobacionCalibracionService aprobaciones;
  @MockBean JwtService jwtService;
  @MockBean UserAccountRepository users;
  @MockBean Clock clock;
  private final UUID puenteId = UUID.randomUUID();
  private final UUID versionId = UUID.randomUUID();

  private EstadoCalibracionResponse aprobada() {
    return new EstadoCalibracionResponse(
        versionId,
        "APROBADA",
        "Aprobada por Kenny el 23/10/2026",
        UUID.randomUUID(),
        "Kenny",
        Instant.parse("2026-10-23T06:50:00Z"));
  }

  @Test
  void visitanteLeeSoloResumenDelIcVigenteSinObservacionesYConNoStore() throws Exception {
    when(servicio.consultar(puenteId)).thenReturn(aprobada());
    mvc.perform(get(ruta()))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.versionFormularioId").value(versionId.toString()))
        .andExpect(jsonPath("$.aprobadoPorNombre").value("Kenny"))
        .andExpect(jsonPath("$.estado").value("APROBADA"))
        .andExpect(jsonPath("$.observaciones").doesNotExist());
    verifyNoInteractions(aprobaciones, jwtService, users);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ADMINISTRADOR", "CATEDRATICO", "ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void todosLosRolesConsultanElMismoResumen(String rol) throws Exception {
    when(servicio.consultar(puenteId)).thenReturn(aprobada());
    mvc.perform(get(ruta()).with(user("lector").roles(rol)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versionFormularioId").value(versionId.toString()))
        .andExpect(jsonPath("$.observaciones").doesNotExist());
  }

  @Test
  void sinIcDevuelveRespuestaVaciaEnLugarDeUnRespaldoSupuesto() throws Exception {
    mvc.perform(get(ruta())).andExpect(status().isOk()).andExpect(content().string(""));
  }

  @Test
  void puenteNoDisponibleConserva404() throws Exception {
    when(servicio.consultar(puenteId))
        .thenThrow(
            new ApiException(
                HttpStatus.NOT_FOUND,
                "puente_no_encontrado",
                "Puente no encontrado",
                "El puente no existe o esta inactivo"));
    mvc.perform(get(ruta()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("puente_no_encontrado"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"aprobacion-calibracion", "estado-calibracion"})
  void resumenPublicoNoHabilitaConsultasPrivadasDeFormularios(String recurso) throws Exception {
    mvc.perform(get("/api/v1/formularios/" + versionId + "/" + recurso))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(aprobaciones);
  }

  private String ruta() {
    return "/api/v1/puentes/" + puenteId + "/estado-calibracion";
  }
}
