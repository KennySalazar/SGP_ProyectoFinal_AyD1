package gt.usac.cunoc.sgp.inspeccion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionResumenResponse;
import gt.usac.cunoc.sgp.inspeccion.service.CorreccionInspeccionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
@ContextConfiguration(classes = CorreccionInspeccionControllerTest.Config.class)
class CorreccionInspeccionControllerTest {

  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-0000000000d1");
  private static final UUID ORIGINAL_ID = UUID.fromString("019a0000-0000-7000-8000-0000000000d2");
  private static final UUID CORRECCION_ID = UUID.fromString("019a0000-0000-7000-8000-0000000000d3");

  @Autowired private CorreccionInspeccionController controller;
  @Autowired private CorreccionInspeccionService service;

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
  void limpiar() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void creaLaCorreccionConElUsuarioYRolDeLaSesion() throws Exception {
    when(service.crear(eq(ORIGINAL_ID), eq("Error de medición"), eq(USUARIO_ID), any()))
        .thenReturn(correccion());

    mockMvc
        .perform(
            post("/api/v1/inspecciones/{id}/correcciones", ORIGINAL_ID)
                .principal(autenticar(RoleName.CATEDRATICO))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"motivo\": \"Error de medición\" }"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/inspecciones/" + CORRECCION_ID))
        .andExpect(jsonPath("$.tipo").value("CORRECCION"))
        .andExpect(jsonPath("$.estado").value("BORRADOR"));

    verify(service).crear(ORIGINAL_ID, "Error de medición", USUARIO_ID, RoleName.CATEDRATICO);
  }

  @Test
  void sinCuerpoDelegaUnMotivoNuloQueElServicioRechazaCon422() throws Exception {
    when(service.crear(eq(ORIGINAL_ID), isNull(), eq(USUARIO_ID), any()))
        .thenThrow(
            new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "motivo_requerido",
                "Motivo requerido",
                "Una correccion exige indicar el motivo."));

    mockMvc
        .perform(
            post("/api/v1/inspecciones/{id}/correcciones", ORIGINAL_ID)
                .principal(autenticar(RoleName.ESTUDIANTE)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("motivo_requerido"));
  }

  private InspeccionResumenResponse correccion() {
    Instant ahora = Instant.parse("2026-10-17T12:00:00Z");
    return new InspeccionResumenResponse(
        CORRECCION_ID,
        UUID.randomUUID(),
        "P-01",
        "Puente",
        null,
        null,
        UUID.randomUUID(),
        "sieca-v1",
        USUARIO_ID,
        "CATEDRATICO",
        "autor@ejemplo.com",
        "BORRADOR",
        "CORRECCION",
        LocalDate.of(2026, 10, 10),
        null,
        ahora,
        ahora,
        ahora);
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
    CorreccionInspeccionService correccionInspeccionService() {
      return mock(CorreccionInspeccionService.class);
    }

    @Bean
    CorreccionInspeccionController correccionInspeccionController(
        CorreccionInspeccionService service) {
      return new CorreccionInspeccionController(service);
    }
  }
}
