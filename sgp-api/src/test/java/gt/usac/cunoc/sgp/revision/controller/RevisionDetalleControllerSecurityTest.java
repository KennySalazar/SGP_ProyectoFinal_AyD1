package gt.usac.cunoc.sgp.revision.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import gt.usac.cunoc.sgp.common.config.SecurityConfiguration;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.*;
import gt.usac.cunoc.sgp.revision.service.RevisionDetalleService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
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
    controllers = RevisionDetalleController.class,
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({
  SecurityConfiguration.class,
  JwtAuthenticationFilter.class,
  AuthRateLimitFilter.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class
})
class RevisionDetalleControllerSecurityTest {
  @Autowired MockMvc mvc;
  @MockBean RevisionDetalleService servicio;
  @MockBean JwtService jwt;
  @MockBean UserAccountRepository usuarios;
  @MockBean Clock clock;
  private final UUID ciclo = UUID.randomUUID(), actor = UUID.randomUUID();
  private final String ruta = "/api/v1/revisiones/" + ciclo;

  private UsernamePasswordAuthenticationToken sesion(RoleName rol) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "test", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    auth.setDetails(new JwtData("test", actor, rol, 0));
    return auth;
  }

  @Test
  void anonimoRecibe401() throws Exception {
    mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
    verifyNoInteractions(servicio);
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"CATEDRATICO", "ESTUDIANTE"},
      mode = EnumSource.Mode.EXCLUDE)
  void otrosRolesReciben403(RoleName rol) throws Exception {
    mvc.perform(get(ruta).with(authentication(sesion(rol)))).andExpect(status().isForbidden());
    verifyNoInteractions(servicio);
  }

  @Test
  void usaIdentidadJwtSinAceptarActorDelClienteYEvitaCachePrivada() throws Exception {
    mvc.perform(
            get(ruta)
                .param("actor", UUID.randomUUID().toString())
                .with(authentication(sesion(RoleName.CATEDRATICO))))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("private")));
    verify(servicio).obtener(ciclo, actor);
  }

  @Test
  void autorConsultaDetalleConSuIdentidadJwtSinCache() throws Exception {
    mvc.perform(get(ruta).with(authentication(sesion(RoleName.ESTUDIANTE))))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    verify(servicio).obtener(ciclo, actor);
  }

  @Test
  void ajenoRecibe404EnFormatoExistenteSinContenidoPrivado() throws Exception {
    when(servicio.obtener(ciclo, actor))
        .thenThrow(
            new ApiException(
                HttpStatus.NOT_FOUND,
                "revision_no_encontrada",
                "Revisión no encontrada",
                "No disponible"));
    mvc.perform(get(ruta).with(authentication(sesion(RoleName.CATEDRATICO))))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("revision_no_encontrada"))
        .andExpect(jsonPath("$.datos").doesNotExist());
  }

  @Test
  void identidadJwtAusenteRecibe401() throws Exception {
    var auth = sesion(RoleName.CATEDRATICO);
    auth.setDetails(null);
    mvc.perform(get(ruta).with(authentication(auth))).andExpect(status().isUnauthorized());
    verifyNoInteractions(servicio);
  }
}
