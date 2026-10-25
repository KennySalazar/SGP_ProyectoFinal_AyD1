package gt.usac.cunoc.sgp.inspeccion.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.service.RecalculoIcService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RecalculoIcControllerSecurityTest.Config.class)
class RecalculoIcControllerSecurityTest {
  private static final UUID USUARIO = UUID.randomUUID();
  private static final UUID INSPECCION = UUID.randomUUID();
  private static final String BASE = "/api/v1/inspecciones/" + INSPECCION;
  @Autowired private RecalculoIcController controller;
  @Autowired private RecalculoIcService servicio;
  private MockMvc mvc;

  @BeforeEach
  void preparar() {
    reset(servicio);
    SecurityContextHolder.clearContext();
    mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @EnumSource(RoleName.class)
  void soloAdminYCatedraticoRecalculan(RoleName rol) throws Exception {
    var auth = autenticar(rol);
    var solicitud = mvc.perform(post(BASE + "/recalculos-ic").principal(auth));
    if (rol == RoleName.ADMINISTRADOR || rol == RoleName.CATEDRATICO) {
      solicitud.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
      verify(servicio).recalcular(INSPECCION, USUARIO);
    } else {
      solicitud.andExpect(status().isForbidden());
      verifyNoInteractions(servicio);
    }
  }

  @ParameterizedTest
  @EnumSource(RoleName.class)
  void todosLosRolesAutenticadosPuedenCompararUnaPublicacion(RoleName rol) throws Exception {
    mvc.perform(get(BASE + "/resultados-ic").principal(autenticar(rol)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    verify(servicio).historial(INSPECCION);
  }

  @Test
  void visitanteNoPuedeLeerNiRecalcular() throws Exception {
    mvc.perform(post(BASE + "/recalculos-ic")).andExpect(status().isUnauthorized());
    mvc.perform(get(BASE + "/resultados-ic")).andExpect(status().isUnauthorized());
    verifyNoInteractions(servicio);
  }

  @Test
  void noAceptaUnaIdentidadSinDetallesJwt() throws Exception {
    var auth = autenticar(RoleName.CATEDRATICO);
    auth.setDetails(null);
    mvc.perform(post(BASE + "/recalculos-ic").principal(auth)).andExpect(status().isUnauthorized());
    verifyNoInteractions(servicio);
  }

  private UsernamePasswordAuthenticationToken autenticar(RoleName rol) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "usuario", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    auth.setDetails(new JwtData("usuario@ejemplo.com", USUARIO, rol, 0));
    SecurityContextHolder.getContext().setAuthentication(auth);
    return auth;
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {
    @Bean
    RecalculoIcService servicio() {
      return mock(RecalculoIcService.class);
    }

    @Bean
    RecalculoIcController controller(RecalculoIcService servicio) {
      return new RecalculoIcController(servicio);
    }
  }
}
