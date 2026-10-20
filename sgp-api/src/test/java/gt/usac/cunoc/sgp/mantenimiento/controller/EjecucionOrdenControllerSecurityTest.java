package gt.usac.cunoc.sgp.mantenimiento.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.mantenimiento.dto.RegistrarEjecucionRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.RetirarEvidenciaRequest;
import gt.usac.cunoc.sgp.mantenimiento.service.EjecucionOrdenService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Quién registra la ejecución, adjunta o retira evidencia y quién la consulta (HU-048). */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = EjecucionOrdenControllerSecurityTest.Config.class)
class EjecucionOrdenControllerSecurityTest {

  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000004841");
  private static final UUID ORDEN_ID = UUID.fromString("019a0000-0000-7000-8000-000000004842");
  private static final UUID EVIDENCIA_ID = UUID.fromString("019a0000-0000-7000-8000-000000004843");
  private static final String BASE = "/api/v1/ordenes-mantenimiento";

  @Autowired private EjecucionOrdenController controller;
  @Autowired private EjecucionOrdenService ejecucion;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(ejecucion);
    SecurityContextHolder.clearContext();
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @AfterEach
  void limpiarAutenticacion() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"CATEDRATICO", "ADMINISTRADOR"})
  void catedraticoYAdministradorRegistranAdjuntanYRetiran(RoleName rol) throws Exception {
    mockMvc.perform(registrar().principal(autenticar(rol))).andExpect(status().isOk());
    verify(ejecucion)
        .registrarEjecucion(
            ORDEN_ID,
            new RegistrarEjecucionRequest(LocalDate.of(2026, 10, 20), null, "Constructora"),
            USUARIO_ID);

    mockMvc.perform(adjuntar().principal(autenticar(rol))).andExpect(status().isCreated());
    verify(ejecucion)
        .adjuntarEvidencia(eq(ORDEN_ID), eq("/superestructura/drenaje"), any(), eq(USUARIO_ID));

    mockMvc.perform(retirar().principal(autenticar(rol))).andExpect(status().isNoContent());
    verify(ejecucion)
        .retirarEvidencia(
            ORDEN_ID, EVIDENCIA_ID, new RetirarEvidenciaRequest("Foto equivocada"), USUARIO_ID);

    mockMvc
        .perform(get(BASE + "/responsables").principal(autenticar(rol)))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"PROFESIONAL_EXTERNO", "ESTUDIANTE"})
  void otrosRolesReciben403(RoleName rol) throws Exception {
    for (MockHttpServletRequestBuilder peticion :
        List.of(registrar(), adjuntar(), retirar(), get(BASE + "/responsables"))) {
      mockMvc
          .perform(peticion.principal(autenticar(rol)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("access_denied"));
    }
    verifyNoInteractions(ejecucion);
  }

  @Test
  void unVisitanteRecibe401() throws Exception {
    for (MockHttpServletRequestBuilder peticion :
        List.of(
            registrar(),
            adjuntar(),
            retirar(),
            get(BASE + "/responsables"),
            get(BASE + "/{id}/ejecucion", ORDEN_ID),
            get(BASE + "/{id}/evidencias", ORDEN_ID),
            get(BASE + "/{id}/evidencias/{e}/contenido", ORDEN_ID, EVIDENCIA_ID),
            get(BASE + "/{id}/evidencias/{e}/miniatura", ORDEN_ID, EVIDENCIA_ID))) {
      mockMvc.perform(peticion).andExpect(status().isUnauthorized());
    }
    verifyNoInteractions(ejecucion);
  }

  @ParameterizedTest
  @EnumSource(RoleName.class)
  void cualquierUsuarioAutenticadoConsultaLaEvidenciaPorElExtremoAutorizado(RoleName rol)
      throws Exception {
    when(ejecucion.contenidoEvidencia(ORDEN_ID, EVIDENCIA_ID, true))
        .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

    mockMvc
        .perform(get(BASE + "/{id}/evidencias", ORDEN_ID).principal(autenticar(rol)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get(BASE + "/{id}/evidencias/{e}/miniatura", ORDEN_ID, EVIDENCIA_ID)
                .principal(autenticar(rol)))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/jpeg"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Cache-Control", "no-store, private"));
  }

  @Test
  void elAlmacenamientoCaidoSePropagaComo503() throws Exception {
    when(ejecucion.adjuntarEvidencia(eq(ORDEN_ID), isNull(), any(), eq(USUARIO_ID)))
        .thenThrow(
            new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "almacenamiento_no_disponible",
                "Almacenamiento no disponible",
                "No se pudo almacenar la fotografía de evidencia."));

    mockMvc
        .perform(
            multipart(BASE + "/{id}/evidencias", ORDEN_ID)
                .file(archivo())
                .principal(autenticar(RoleName.CATEDRATICO)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("almacenamiento_no_disponible"));
  }

  private MockHttpServletRequestBuilder registrar() {
    return put(BASE + "/{id}/ejecucion", ORDEN_ID)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"fechaEjecucion\":\"2026-10-20\",\"responsableNombre\":\"Constructora\"}");
  }

  private MockHttpServletRequestBuilder adjuntar() {
    return multipart(BASE + "/{id}/evidencias", ORDEN_ID)
        .file(archivo())
        .param("elementoRef", "/superestructura/drenaje");
  }

  private MockHttpServletRequestBuilder retirar() {
    return post(BASE + "/{id}/evidencias/{e}/retirar", ORDEN_ID, EVIDENCIA_ID)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"motivo\":\"Foto equivocada\"}");
  }

  private static MockMultipartFile archivo() {
    return new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", new byte[] {1, 2, 3});
  }

  private UsernamePasswordAuthenticationToken autenticar(RoleName rol) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            "usuario@ejemplo.com", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    authentication.setDetails(new JwtData("usuario@ejemplo.com", USUARIO_ID, rol, 0));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return authentication;
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {

    @Bean
    EjecucionOrdenService ejecucionOrdenService() {
      return mock(EjecucionOrdenService.class);
    }

    @Bean
    EjecucionOrdenController ejecucionOrdenController(EjecucionOrdenService ejecucion) {
      return new EjecucionOrdenController(ejecucion);
    }
  }
}
