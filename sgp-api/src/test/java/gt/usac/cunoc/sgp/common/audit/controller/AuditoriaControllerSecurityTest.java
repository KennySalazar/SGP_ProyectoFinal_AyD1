package gt.usac.cunoc.sgp.common.audit.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.audit.dto.AuditoriaResponse;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaConsultaService;
import gt.usac.cunoc.sgp.common.config.SecurityConfiguration;
import gt.usac.cunoc.sgp.common.security.AuthRateLimitFilter;
import gt.usac.cunoc.sgp.common.security.JwtAuthenticationFilter;
import gt.usac.cunoc.sgp.common.security.JwtService;
import gt.usac.cunoc.sgp.common.security.ProblemAccessDeniedHandler;
import gt.usac.cunoc.sgp.common.security.ProblemAuthenticationEntryPoint;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = {AuditoriaController.class},
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({
  SecurityConfiguration.class,
  JwtAuthenticationFilter.class,
  AuthRateLimitFilter.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class
})
class AuditoriaControllerSecurityTest {

  @Autowired private MockMvc mvc;

  @MockBean private AuditoriaConsultaService consulta;
  @MockBean private JwtService jwtService;
  @MockBean private UserAccountRepository users;
  @MockBean private Clock clock;

  @Test
  @DisplayName("Escenario: consultar auditoría sin autenticación exige token")
  void consultaSinAutenticacionExigeToken() throws Exception {
    // Dado un visitante sin token
    // Cuando consulta la auditoría
    mvc.perform(get("/api/v1/auditoria"))
        // Entonces recibe 401
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Escenario: rol distinto de administrador no consulta la auditoría")
  @WithMockUser(roles = "ESTUDIANTE")
  void rolDistintoDeAdministradorNoConsulta() throws Exception {
    // Dado un usuario sin rol de administrador
    // Cuando consulta la auditoría
    mvc.perform(get("/api/v1/auditoria"))
        // Entonces recibe 403 y el servicio no se invoca
        .andExpect(status().isForbidden());

    verifyNoInteractions(consulta);
  }

  @Test
  @DisplayName("Escenario: un usuario no administrador tampoco puede abrir el detalle")
  @WithMockUser(roles = "ESTUDIANTE")
  void rolDistintoDeAdministradorNoAbreDetalle() throws Exception {
    // Dado un usuario estudiante y un identificador de auditoría
    UUID id = UUID.fromString("019a0000-0000-7000-8000-0000000000a1");

    // Cuando solicita el detalle de la bitácora
    mvc.perform(get("/api/v1/auditoria/{id}", id))
        // Entonces recibe 403 y no se consulta la información
        .andExpect(status().isForbidden());
    verifyNoInteractions(consulta);
  }

  @Test
  @DisplayName("Escenario: administrador consulta la auditoría paginada")
  @WithMockUser(roles = "ADMINISTRADOR")
  void administradorConsultaAuditoriaPaginada() throws Exception {
    // Dado un administrador y un registro de auditoría
    var auditoria =
        new AuditoriaResponse(
            UUID.fromString("019a0000-0000-7000-8000-0000000000a1"),
            UUID.fromString("019a0000-0000-7000-8000-000000000006"),
            AccionAuditoria.CREAR,
            "puente",
            UUID.fromString("019a0000-0000-7000-8000-0000000000b2"),
            null,
            null,
            null,
            OffsetDateTime.parse("2026-10-02T17:00:00-06:00"));

    when(consulta.consultar(null, null, null, null, null, 0, 20))
        .thenReturn(new PageImpl<>(List.of(auditoria), PageRequest.of(0, 20), 1));

    // Cuando consulta la auditoría
    mvc.perform(get("/api/v1/auditoria"))
        // Entonces recibe la página con el registro
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].accion").value("CREAR"))
        .andExpect(jsonPath("$.content[0].entidad").value("puente"))
        .andExpect(jsonPath("$.content[0].valoresAnteriores").doesNotExist())
        .andExpect(jsonPath("$.totalElements").value(1));
  }
}
