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
import gt.usac.cunoc.sgp.revision.service.AtencionObservacionesService;
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
    controllers = AtencionObservacionesController.class,
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({
  SecurityConfiguration.class,
  JwtAuthenticationFilter.class,
  AuthRateLimitFilter.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class
})
class AtencionObservacionesControllerSecurityTest {
  @Autowired MockMvc mvc;
  @MockBean AtencionObservacionesService servicio;
  @MockBean JwtService jwt;
  @MockBean UserAccountRepository usuarios;
  @MockBean Clock clock;
  private final UUID ciclo = UUID.randomUUID(), actor = UUID.randomUUID(), obs = UUID.randomUUID();
  private final String base = "/api/v1/revisiones/" + ciclo;

  private UsernamePasswordAuthenticationToken sesion(RoleName rol) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "test", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    auth.setDetails(new JwtData("test", actor, rol, 0));
    return auth;
  }

  @Test
  void requiereSesion() throws Exception {
    mvc.perform(post(base + "/atender-cambios")).andExpect(status().isUnauthorized());
    mvc.perform(put(base + "/observaciones/" + obs + "/resolver"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(servicio);
  }

  @ParameterizedTest
  @EnumSource(value = RoleName.class, names = "ESTUDIANTE", mode = EnumSource.Mode.EXCLUDE)
  void soloEstudiantePuedeAtenderOResolver(RoleName rol) throws Exception {
    mvc.perform(post(base + "/atender-cambios").with(authentication(sesion(rol))))
        .andExpect(status().isForbidden());
    mvc.perform(put(base + "/observaciones/" + obs + "/resolver").with(authentication(sesion(rol))))
        .andExpect(status().isForbidden());
    verifyNoInteractions(servicio);
  }

  @Test
  void resolucionNoAceptaAutorNiFechaImpuestosPorElCliente() throws Exception {
    mvc.perform(
            put(base + "/observaciones/" + obs + "/resolver")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"respuesta\":\"Corregido\",\"resueltoPorId\":\""
                        + UUID.randomUUID()
                        + "\",\"resueltoEn\":\"2000-01-01T00:00:00Z\"}")
                .with(authentication(sesion(RoleName.ESTUDIANTE))))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    verify(servicio).resolver(ciclo, obs, actor, new ResolverObservacionRequest("Corregido"));
  }

  @Test
  void atenderUsaSoloLaIdentidadDelJwt() throws Exception {
    mvc.perform(
            post(base + "/atender-cambios")
                .param("autorId", UUID.randomUUID().toString())
                .with(authentication(sesion(RoleName.ESTUDIANTE))))
        .andExpect(status().isOk());
    verify(servicio).atender(ciclo, actor);
  }
}
