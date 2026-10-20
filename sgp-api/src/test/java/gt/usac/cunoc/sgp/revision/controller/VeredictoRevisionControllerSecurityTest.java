package gt.usac.cunoc.sgp.revision.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import gt.usac.cunoc.sgp.common.config.SecurityConfiguration;
import gt.usac.cunoc.sgp.common.security.*;
import gt.usac.cunoc.sgp.revision.dto.*;
import gt.usac.cunoc.sgp.revision.service.VeredictoRevisionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = VeredictoRevisionController.class,
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({
  SecurityConfiguration.class,
  JwtAuthenticationFilter.class,
  AuthRateLimitFilter.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class
})
class VeredictoRevisionControllerSecurityTest {
  @Autowired MockMvc mvc;
  @MockBean VeredictoRevisionService servicio;
  @MockBean JwtService jwt;
  @MockBean UserAccountRepository usuarios;
  @MockBean Clock clock;
  private final UUID ciclo = UUID.randomUUID(), actor = UUID.randomUUID();
  private final String base = "/api/v1/revisiones/" + ciclo;

  private UsernamePasswordAuthenticationToken sesion(RoleName rol) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "test", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    auth.setDetails(new JwtData("test", actor, rol, 0));
    return auth;
  }

  @Test
  void todasLasRutasRequierenSesion() throws Exception {
    for (var r :
        List.of(
            get(base + "/resultado"),
            get(base + "/estado-puente"),
            get("/api/v1/inspecciones/" + ciclo + "/revisiones"),
            post(base + "/veredicto").contentType(MediaType.APPLICATION_JSON).content("{}")))
      mvc.perform(r).andExpect(status().isUnauthorized());
    verifyNoInteractions(servicio);
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"CATEDRATICO", "ESTUDIANTE"},
      mode = EnumSource.Mode.EXCLUDE)
  void calificacionesNoSonConsultablesPorOtrosRoles(RoleName rol) throws Exception {
    mvc.perform(get(base + "/resultado").with(authentication(sesion(rol))))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/inspecciones/" + ciclo + "/revisiones").with(authentication(sesion(rol))))
        .andExpect(status().isForbidden());
    verifyNoInteractions(servicio);
  }

  @Test
  void estudianteSoloConsultaYRespuestasPrivadasNoSeCachean() throws Exception {
    mvc.perform(get(base + "/resultado").with(authentication(sesion(RoleName.ESTUDIANTE))))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    mvc.perform(
            post(base + "/veredicto")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(authentication(sesion(RoleName.ESTUDIANTE))))
        .andExpect(status().isForbidden());
    mvc.perform(get(base + "/estado-puente").with(authentication(sesion(RoleName.ESTUDIANTE))))
        .andExpect(status().isForbidden());
    verify(servicio).resultado(ciclo, actor);
    verify(servicio, never()).emitir(any(), any(), any());
  }

  @Test
  void actorSeObtieneDelJwtYElAjenoNoRecibeLaNota() throws Exception {
    when(servicio.resultado(ciclo, actor))
        .thenThrow(
            new gt.usac.cunoc.sgp.common.exception.ApiException(
                HttpStatus.NOT_FOUND,
                "revision_no_encontrada",
                "No encontrada",
                "La revisión no está disponible."));
    mvc.perform(
            get(base + "/resultado")
                .param("autorId", UUID.randomUUID().toString())
                .with(authentication(sesion(RoleName.CATEDRATICO))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.calificacion").doesNotExist());
    verify(servicio).resultado(ciclo, actor);
  }
}
