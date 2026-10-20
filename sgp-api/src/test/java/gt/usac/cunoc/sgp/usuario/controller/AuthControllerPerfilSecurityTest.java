package gt.usac.cunoc.sgp.usuario.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.config.JwtProperties;
import gt.usac.cunoc.sgp.common.config.RefreshCookieProperties;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.usuario.dto.ActualizarPerfilRequest;
import gt.usac.cunoc.sgp.usuario.dto.UserResponse;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.service.AuthService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Quién puede cambiar su perfil: cualquier rol con sesión, nadie sin ella. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AuthControllerPerfilSecurityTest.Config.class)
class AuthControllerPerfilSecurityTest {

  private static final String RUTA = "/api/v1/auth/me/perfil";
  private static final String CORREO = "persona@ejemplo.com";
  private static final String CUERPO =
      "{\"nombreCompleto\":\"Ana Reyes\",\"nombreUsuario\":\"ana.reyes\"}";

  @Autowired private AuthController controller;
  @Autowired private AuthService authService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(authService);
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
  @EnumSource(RoleName.class)
  void cualquierRolConSesionActualizaSuPerfil(RoleName rol) throws Exception {
    when(authService.actualizarPerfil(eq(CORREO), any(ActualizarPerfilRequest.class)))
        .thenReturn(respuesta(rol));

    mockMvc
        .perform(
            put(RUTA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO)
                .principal(autenticar(rol)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nombreUsuario").value("ana.reyes"))
        .andExpect(jsonPath("$.nombreCompleto").value("Ana Reyes"));

    verify(authService)
        .actualizarPerfil(CORREO, new ActualizarPerfilRequest("Ana Reyes", "ana.reyes"));
  }

  @Test
  void sinSesionRecibe401() throws Exception {
    mockMvc
        .perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(authService);
  }

  @Test
  void unPerfilIncompletoOMalFormadoSeRechazaCon400SinLlegarAlServicio() throws Exception {
    mockMvc
        .perform(
            put(RUTA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreCompleto\":\"\",\"nombreUsuario\":\"ana reyes\"}")
                .principal(autenticar(RoleName.ESTUDIANTE)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(authService);
  }

  @Test
  void unNombreDeUsuarioEnUsoSeEntregaComoConflicto() throws Exception {
    when(authService.actualizarPerfil(any(), any()))
        .thenThrow(
            new ApiException(
                HttpStatus.CONFLICT,
                "nombre_usuario_en_uso",
                "Nombre de usuario en uso",
                "Ese nombre de usuario ya está en uso. Elige otro."));

    mockMvc
        .perform(
            put(RUTA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO)
                .principal(autenticar(RoleName.CATEDRATICO)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("nombre_usuario_en_uso"));
  }

  private UserResponse respuesta(RoleName rol) {
    return new UserResponse(
        UUID.randomUUID(),
        CORREO,
        "Ana Reyes",
        "ana.reyes",
        rol.name(),
        true,
        true,
        true,
        false,
        null);
  }

  private UsernamePasswordAuthenticationToken autenticar(RoleName rol) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            CORREO, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return authentication;
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {

    @Bean
    AuthService authService() {
      return mock(AuthService.class);
    }

    @Bean
    AuthController authController(AuthService authService) {
      return new AuthController(
          authService, mock(RefreshCookieProperties.class), mock(JwtProperties.class));
    }
  }
}
