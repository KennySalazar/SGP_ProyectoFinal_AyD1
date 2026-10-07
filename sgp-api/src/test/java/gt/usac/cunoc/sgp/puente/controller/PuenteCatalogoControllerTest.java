package gt.usac.cunoc.sgp.puente.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.config.SecurityConfiguration;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.AuthRateLimitFilter;
import gt.usac.cunoc.sgp.common.security.JwtAuthenticationFilter;
import gt.usac.cunoc.sgp.common.security.JwtService;
import gt.usac.cunoc.sgp.common.security.ProblemAccessDeniedHandler;
import gt.usac.cunoc.sgp.common.security.ProblemAuthenticationEntryPoint;
import gt.usac.cunoc.sgp.puente.dto.CandidatoTerritorialResponse;
import gt.usac.cunoc.sgp.puente.dto.DepartamentoResponse;
import gt.usac.cunoc.sgp.puente.dto.MunicipioResponse;
import gt.usac.cunoc.sgp.puente.dto.PuenteCatalogoResponse;
import gt.usac.cunoc.sgp.puente.dto.PuenteResponse;
import gt.usac.cunoc.sgp.puente.dto.UbicacionTerritorialResponse;
import gt.usac.cunoc.sgp.puente.service.CatalogoTerritorialService;
import gt.usac.cunoc.sgp.puente.service.PuenteService;
import gt.usac.cunoc.sgp.puente.service.UbicacionTerritorialService;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = {
      PuenteController.class,
      CatalogoTerritorialController.class,
      UbicacionTerritorialController.class
    },
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
  @MockBean private UbicacionTerritorialService ubicaciones;
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

  @ParameterizedTest
  @ValueSource(strings = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void municipiosNoEstanDisponiblesParaRolesSinPermiso(String rol) throws Exception {
    mvc.perform(
            get("/api/v1/catalogos/departamentos/" + UUID.randomUUID() + "/municipios")
                .with(user("usuario").roles(rol)))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ADMINISTRADOR", "CATEDRATICO"})
  void administradorYCatedraticoConsultanMunicipios(String rol) throws Exception {
    UUID departamentoId = UUID.randomUUID();
    when(territorios.listarMunicipios(departamentoId, 0, 100))
        .thenReturn(
            new PageImpl<>(
                List.of(new MunicipioResponse(UUID.randomUUID(), departamentoId, "0101", "Guate")),
                PageRequest.of(0, 100),
                1));

    mvc.perform(
            get("/api/v1/catalogos/departamentos/" + departamentoId + "/municipios")
                .with(user("usuario").roles(rol)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].nombre").value("Guate"));
  }

  @Test
  void ubicacionRequiereAutenticacion() throws Exception {
    mvc.perform(
            get("/api/v1/catalogos/ubicacion")
                .param("latitud", "14.481")
                .param("longitud", "-90.615"))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void ubicacionNoEstaDisponibleParaRolesSinPermiso(String rol) throws Exception {
    mvc.perform(
            get("/api/v1/catalogos/ubicacion")
                .param("latitud", "14.481")
                .param("longitud", "-90.615")
                .with(user("usuario").roles(rol)))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ADMINISTRADOR", "CATEDRATICO"})
  void administradorYCatedraticoResuelvenUbicacion(String rol) throws Exception {
    UUID departamentoId = UUID.randomUUID();
    when(ubicaciones.resolver(14.481, -90.615))
        .thenReturn(
            new UbicacionTerritorialResponse(
                14.481,
                -90.615,
                "15N",
                false,
                List.of(
                    new CandidatoTerritorialResponse(
                        new DepartamentoResponse(departamentoId, "01", "Guatemala"),
                        new MunicipioResponse(
                            UUID.randomUUID(), departamentoId, "0101", "Guatemala")))));

    mvc.perform(
            get("/api/v1/catalogos/ubicacion")
                .param("latitud", "14.481")
                .param("longitud", "-90.615")
                .with(user("usuario").roles(rol)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.zonaUtm").value("15N"))
        .andExpect(jsonPath("$.candidatos[0].municipio.nombre").value("Guatemala"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"departamentoId", "pagina", "tamanio"})
  void parametrosConTipoInvalidoDevuelvenProblema(String parametro) throws Exception {
    mvc.perform(get("/api/v1/puentes").param(parametro, "invalido"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("validation_error"));
  }

  @Test
  @WithMockUser(roles = "ADMINISTRADOR")
  void administradorPuedeConsultarPuentesInactivos() throws Exception {
    when(service.listarCatalogo(null, null, false, 0, 20))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mvc.perform(get("/api/v1/puentes").param("activo", "false")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = "ADMINISTRADOR")
  void administradorPuedeConsultarTodosLosPuentes() throws Exception {
    when(service.listarCatalogo(null, null, null, 0, 20))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mvc.perform(get("/api/v1/puentes").param("todos", "true")).andExpect(status().isOk());
  }

  @Test
  void visitanteConsultaFichaDetalleSinAutenticacion() throws Exception {
    UUID puenteId = UUID.randomUUID();
    var depto = new DepartamentoResponse(UUID.randomUUID(), "01", "Guatemala");
    var muni = new MunicipioResponse(UUID.randomUUID(), depto.id(), "0101", "Guatemala");
    var puente =
        new PuenteResponse(
            puenteId,
            "GT-01-0101-0001",
            "Puente La Asunción",
            depto,
            muni,
            "CA-1 Occidente",
            java.math.BigDecimal.valueOf(15.5),
            14.62843,
            -90.52271,
            null,
            true,
            "Sin evaluar",
            null,
            null,
            OffsetDateTime.now());

    when(service.obtenerPorId(puenteId)).thenReturn(puente);

    mvc.perform(get("/api/v1/puentes/{id}", puenteId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(puenteId.toString()))
        .andExpect(jsonPath("$.codigo").value("GT-01-0101-0001"))
        .andExpect(jsonPath("$.nombre").value("Puente La Asunción"))
        .andExpect(jsonPath("$.estadoActual").value("Sin evaluar"))
        .andExpect(jsonPath("$.indiceCondicionActual").doesNotExist())
        .andExpect(jsonPath("$.fechaUltimaInspeccion").doesNotExist())
        .andExpect(jsonPath("$.fotografias").doesNotExist())
        .andExpect(jsonPath("$.danos").doesNotExist())
        .andExpect(jsonPath("$.creadoPorId").doesNotExist());
  }

  @Test
  void consultarFichaInexistenteDevuelve404() throws Exception {
    UUID puenteId = UUID.randomUUID();
    when(service.obtenerPorId(puenteId))
        .thenThrow(
            new ApiException(
                HttpStatus.NOT_FOUND,
                "puente_no_encontrado",
                "Puente no encontrado",
                "El puente no existe o esta inactivo"));

    mvc.perform(get("/api/v1/puentes/{id}", puenteId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("puente_no_encontrado"));
  }
}
