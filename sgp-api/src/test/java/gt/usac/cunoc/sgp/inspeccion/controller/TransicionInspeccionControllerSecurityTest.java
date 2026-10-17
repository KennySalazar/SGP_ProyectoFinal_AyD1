package gt.usac.cunoc.sgp.inspeccion.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.service.TransicionInspeccionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TransicionInspeccionControllerSecurityTest.Config.class)
class TransicionInspeccionControllerSecurityTest {

  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-0000000000c1");
  private static final UUID INSPECCION_ID = UUID.fromString("019a0000-0000-7000-8000-0000000000c2");

  @Autowired private TransicionInspeccionController controller;
  @Autowired private TransicionInspeccionService service;

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
  void limpiarAutenticacion() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @EnumSource(value = RoleName.class, names = "ESTUDIANTE", mode = EnumSource.Mode.EXCLUDE)
  void soloElEstudianteEnviaARevision(RoleName rol) throws Exception {
    mockMvc
        .perform(post("/api/v1/inspecciones/{id}/enviar", INSPECCION_ID).principal(autenticar(rol)))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"CATEDRATICO", "PROFESIONAL_EXTERNO"},
      mode = EnumSource.Mode.EXCLUDE)
  void soloCatedraticoYProfesionalPublicanSinRevision(RoleName rol) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspecciones/{id}/publicar", INSPECCION_ID).principal(autenticar(rol)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("access_denied"));
    verifyNoInteractions(service);
  }

  @Test
  void elEstudianteEnviaConSuIdDeJwtData() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspecciones/{id}/enviar", INSPECCION_ID)
                .principal(autenticar(RoleName.ESTUDIANTE)))
        .andExpect(status().isOk());
    verify(service).enviar(INSPECCION_ID, USUARIO_ID);
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"CATEDRATICO", "PROFESIONAL_EXTERNO"})
  void catedraticoYProfesionalPublicanConSuIdDeJwtData(RoleName rol) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/inspecciones/{id}/publicar", INSPECCION_ID).principal(autenticar(rol)))
        .andExpect(status().isOk());
    verify(service).publicar(INSPECCION_ID, USUARIO_ID);
  }

  @Test
  void unaInspeccionIncompletaResponde422ConLoQueFalta() throws Exception {
    when(service.publicar(eq(INSPECCION_ID), eq(USUARIO_ID)))
        .thenThrow(
            new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "inspeccion_incompleta",
                "La inspeccion no puede enviarse ni publicarse",
                "Faltan 1 campos obligatorios y 1 categorias de fotografia.",
                List.of(
                    Map.of("campo", "subestructura", "mensaje", "Requerido"),
                    Map.of(
                        "campo",
                        "fotografias.CAUCE",
                        "mensaje",
                        "Falta al menos una fotografia de la categoria CAUCE"))));

    mockMvc
        .perform(
            post("/api/v1/inspecciones/{id}/publicar", INSPECCION_ID)
                .principal(autenticar(RoleName.CATEDRATICO)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("inspeccion_incompleta"))
        .andExpect(jsonPath("$.errores[0].campo").value("subestructura"))
        .andExpect(jsonPath("$.errores[1].campo").value("fotografias.CAUCE"));
  }

  @Test
  void unaTransicionNoPermitidaResponde409() throws Exception {
    when(service.publicar(eq(INSPECCION_ID), eq(USUARIO_ID)))
        .thenThrow(
            new ApiException(
                HttpStatus.CONFLICT,
                "estado_terminal",
                "Transicion no permitida",
                "La inspeccion esta PUBLICADA."));

    mockMvc
        .perform(
            post("/api/v1/inspecciones/{id}/publicar", INSPECCION_ID)
                .principal(autenticar(RoleName.CATEDRATICO)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("estado_terminal"));
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
    TransicionInspeccionService transicionInspeccionService() {
      return mock(TransicionInspeccionService.class);
    }

    @Bean
    TransicionInspeccionController transicionInspeccionController(
        TransicionInspeccionService service) {
      return new TransicionInspeccionController(service);
    }
  }
}
