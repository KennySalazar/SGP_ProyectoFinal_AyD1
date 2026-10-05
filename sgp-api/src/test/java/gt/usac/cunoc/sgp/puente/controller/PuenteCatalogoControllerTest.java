package gt.usac.cunoc.sgp.puente.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.config.SecurityConfiguration;
import gt.usac.cunoc.sgp.common.security.AuthRateLimitFilter;
import gt.usac.cunoc.sgp.common.security.JwtAuthenticationFilter;
import gt.usac.cunoc.sgp.common.security.JwtService;
import gt.usac.cunoc.sgp.common.security.ProblemAccessDeniedHandler;
import gt.usac.cunoc.sgp.common.security.ProblemAuthenticationEntryPoint;
import gt.usac.cunoc.sgp.puente.dto.DepartamentoResponse;
import gt.usac.cunoc.sgp.puente.dto.PuenteCatalogoResponse;
import gt.usac.cunoc.sgp.puente.service.CatalogoTerritorialService;
import gt.usac.cunoc.sgp.puente.service.PuenteService;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
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
    controllers = {PuenteController.class, CatalogoTerritorialController.class},
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({
  SecurityConfiguration.class,
  JwtAuthenticationFilter.class,
  AuthRateLimitFilter.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class
})
class PuenteCatalogoControllerTest {

  @Autowired private MockMvc mvc;
  @MockBean private PuenteService service;
  @MockBean private CatalogoTerritorialService territorios;
  @MockBean private JwtService jwtService;
  @MockBean private UserAccountRepository users;
  @MockBean private Clock clock;

  @Test
  void visitanteConsultaDatosPublicosSinAutenticacion() throws Exception {
    var puente =
        new PuenteCatalogoResponse(
            UUID.randomUUID(),
            "GT-01-0114-0001",
            "Puente público",
            null,
            null,
            14.481,
            -90.615,
            true,
            "Sin evaluar");
    when(service.listarCatalogo(null, null, 0, 20))
        .thenReturn(new PageImpl<>(List.of(puente), PageRequest.of(0, 20), 1));

    mvc.perform(get("/api/v1/puentes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].nombre").value("Puente público"))
        .andExpect(jsonPath("$.content[0].estadoActual").value("Sin evaluar"))
        .andExpect(jsonPath("$.content[0].fotografias").doesNotExist())
        .andExpect(jsonPath("$.content[0].danos").doesNotExist())
        .andExpect(jsonPath("$.content[0].creadoPorId").doesNotExist())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  @WithMockUser(roles = "ESTUDIANTE")
  void usuarioAutenticadoPuedeConsultarFiltrosYPaginacion() throws Exception {
    UUID departamentoId = UUID.randomUUID();
    when(service.listarCatalogo(departamentoId, "Sin evaluar", 1, 100))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 100), 0));
    mvc.perform(
            get("/api/v1/puentes")
                .param("departamentoId", departamentoId.toString())
                .param("estado", "Sin evaluar")
                .param("pagina", "1")
                .param("tamanio", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.number").value(1))
        .andExpect(jsonPath("$.size").value(100));
  }

  @Test
  void registroSigueRequiriendoAutenticacion() throws Exception {
    mvc.perform(post("/api/v1/puentes")).andExpect(status().isUnauthorized());
  }

  @Test
  void visitanteConsultaDepartamentosParaFiltrar() throws Exception {
    when(territorios.listarDepartamentos(0, 100))
        .thenReturn(
            new PageImpl<>(
                List.of(new DepartamentoResponse(UUID.randomUUID(), "01", "Guatemala")),
                PageRequest.of(0, 100),
                1));
    mvc.perform(get("/api/v1/catalogos/departamentos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].nombre").value("Guatemala"));
  }

  @Test
  void municipiosSiguenRequiriendoAutenticacion() throws Exception {
    mvc.perform(get("/api/v1/catalogos/departamentos/" + UUID.randomUUID() + "/municipios"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "ESTUDIANTE")
  void municipiosSiguenRestringidosAlAdministrador() throws Exception {
    mvc.perform(get("/api/v1/catalogos/departamentos/" + UUID.randomUUID() + "/municipios"))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @ValueSource(strings = {"departamentoId", "pagina", "tamanio"})
  void parametrosConTipoInvalidoDevuelvenProblema(String parametro) throws Exception {
    mvc.perform(get("/api/v1/puentes").param(parametro, "invalido"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("validation_error"));
  }
}
