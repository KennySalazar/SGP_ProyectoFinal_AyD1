package gt.usac.cunoc.sgp.common.audit.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.audit.dto.AuditoriaDetalleResponse;
import gt.usac.cunoc.sgp.common.audit.dto.AuditoriaResponse;
import gt.usac.cunoc.sgp.common.audit.dto.CambioAuditoria;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
            OffsetDateTime.parse("2026-10-02T17:00:00-06:00"),
            "admin@ejemplo.com");

    when(consulta.consultar(null, null, null, null, null, null, 0, 20))
        .thenReturn(new PageImpl<>(List.of(auditoria), PageRequest.of(0, 20), 1));

    // Cuando consulta la auditoría
    mvc.perform(get("/api/v1/auditoria"))
        // Entonces recibe la página con el registro
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].accion").value("CREAR"))
        .andExpect(jsonPath("$.content[0].entidad").value("puente"))
        .andExpect(jsonPath("$.content[0].usuarioEmail").value("admin@ejemplo.com"))
        .andExpect(jsonPath("$.content[0].valoresAnteriores").doesNotExist())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  @DisplayName("Escenario: el Administrador combina correo y fechas de Guatemala")
  @WithMockUser(roles = "ADMINISTRADOR")
  void combinaFiltrosDeUsuarioYFechas() throws Exception {
    // Dado un administrador y filtros de usuario y días completos
    when(consulta.consultar(
            null,
            "admin@ejemplo.com",
            null,
            null,
            Instant.parse("2026-10-06T06:00:00Z"),
            Instant.parse("2026-10-07T05:59:59.999999Z"),
            0,
            100))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

    // Cuando aplica ambos filtros
    mvc.perform(
            get("/api/v1/auditoria")
                .param("usuarioEmail", "admin@ejemplo.com")
                .param("desde", "2026-10-06T00:00:00-06:00")
                .param("hasta", "2026-10-06T23:59:59.999999-06:00")
                .param("tamanio", "100"))
        // Entonces se consulta el rango en UTC y se devuelve la página correspondiente
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("Escenario: el Administrador ve los valores anteriores y posteriores legibles")
  @WithMockUser(roles = "ADMINISTRADOR")
  void administradorAbreDetalleLegible() throws Exception {
    // Dado un registro con un nombre modificado
    UUID id = UUID.randomUUID();
    when(consulta.detalle(id))
        .thenReturn(
            new AuditoriaDetalleResponse(
                id,
                UUID.randomUUID(),
                AccionAuditoria.MODIFICAR,
                "puente",
                UUID.randomUUID(),
                null,
                OffsetDateTime.parse("2026-10-06T14:00:00-06:00"),
                List.of(new CambioAuditoria("nombre", "Anterior", "Posterior")),
                "admin@ejemplo.com"));

    // Cuando abre el detalle, entonces recibe campos legibles sin snapshots JSON
    mvc.perform(get("/api/v1/auditoria/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cambios[0].campo").value("nombre"))
        .andExpect(jsonPath("$.cambios[0].anterior").value("Anterior"))
        .andExpect(jsonPath("$.cambios[0].posterior").value("Posterior"))
        .andExpect(jsonPath("$.valoresAnteriores").doesNotExist())
        .andExpect(jsonPath("$.valoresPosteriores").doesNotExist());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ESTUDIANTE", "CATEDRATICO", "PROFESIONAL_EXTERNO"})
  @DisplayName("Escenario: todo rol distinto de Administrador recibe 403 en listado y detalle")
  void todosLosOtrosRolesReciben403(String rol) throws Exception {
    // Dado un usuario cuyo rol no es Administrador
    // Cuando consulta listado o detalle, entonces recibe 403 sin acceder a los datos
    mvc.perform(get("/api/v1/auditoria").with(user("usuario").roles(rol)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/auditoria/{id}", UUID.randomUUID()).with(user("usuario").roles(rol)))
        .andExpect(status().isForbidden());
    verifyNoInteractions(consulta);
  }
}
