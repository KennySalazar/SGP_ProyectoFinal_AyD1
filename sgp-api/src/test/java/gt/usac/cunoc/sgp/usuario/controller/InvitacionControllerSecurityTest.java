package gt.usac.cunoc.sgp.usuario.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.usuario.dto.AceptarInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.dto.CrearInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.dto.TokenInvitacionRequest;
import gt.usac.cunoc.sgp.usuario.exception.InvitacionExceptionHandler;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.service.InvitacionService;
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
@ContextConfiguration(classes = InvitacionControllerSecurityTest.Config.class)
class InvitacionControllerSecurityTest {

  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000000020");
  private static final UUID INVITACION_ID = UUID.fromString("019a0000-0000-7000-8000-000000000021");

  private static final String INVITACION =
      """
      { "email": "catedratico@usac.edu.gt", "rol": "CATEDRATICO" }
      """;

  @Autowired private InvitacionController controller;

  @Autowired private InvitacionService service;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(service);
    SecurityContextHolder.clearContext();

    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new InvitacionExceptionHandler(), new ApiExceptionHandler())
            .build();
  }

  @AfterEach
  void limpiarAutenticacion() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @EnumSource(value = RoleName.class, names = "ADMINISTRADOR", mode = EnumSource.Mode.EXCLUDE)
  void rechazaInvitarARolesDistintosDeAdministradorCon403(RoleName rol) throws Exception {
    var authentication = autenticar(rol);

    mockMvc
        .perform(
            post("/api/v1/invitaciones")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(INVITACION))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("access_denied"));

    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @EnumSource(value = RoleName.class, names = "ADMINISTRADOR", mode = EnumSource.Mode.EXCLUDE)
  void rechazaListarYReenviarARolesDistintosDeAdministrador(RoleName rol) throws Exception {
    var authentication = autenticar(rol);

    mockMvc
        .perform(get("/api/v1/invitaciones").principal(authentication))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post("/api/v1/invitaciones/{id}/reenvio", INVITACION_ID).principal(authentication))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void permiteAdministradorInvitarYObtieneSuIdDeJwtData() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);

    mockMvc
        .perform(
            post("/api/v1/invitaciones")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(INVITACION))
        .andExpect(status().isCreated());

    verify(service)
        .invitar(
            eq(new CrearInvitacionRequest("catedratico@usac.edu.gt", RoleName.CATEDRATICO)),
            eq(USUARIO_ID));
  }

  @Test
  void permiteAdministradorReenviarYListar() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);

    mockMvc
        .perform(post("/api/v1/invitaciones/{id}/reenvio", INVITACION_ID).principal(authentication))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get("/api/v1/invitaciones").principal(authentication))
        .andExpect(status().isOk());

    verify(service).reenviar(INVITACION_ID, USUARIO_ID);
    verify(service).listar(null, 0, 20);
  }

  @Test
  void rechazaInvitacionSinCorreoValidoCon400() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);

    mockMvc
        .perform(
            post("/api/v1/invitaciones")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"no-es-correo\", \"rol\": \"CATEDRATICO\" }"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_error"))
        .andExpect(jsonPath("$.errores[0].campo").value("email"));

    verifyNoInteractions(service);
  }

  @Test
  void rechazaRolInexistenteCon400() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);

    mockMvc
        .perform(
            post("/api/v1/invitaciones")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"catedratico@usac.edu.gt\", \"rol\": \"DECANO\" }"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_error"));

    verifyNoInteractions(service);
  }

  @Test
  void rechazaIdentificadorInvalidoCon400() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);

    mockMvc
        .perform(post("/api/v1/invitaciones/no-es-uuid/reenvio").principal(authentication))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores[0].campo").value("id"));

    verifyNoInteractions(service);
  }

  @Test
  void validacionYAceptacionSonPublicas() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/invitaciones/validacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"token\": \"abc\" }"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/invitaciones/aceptacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"token\": \"abc\", \"password\": \"Catedra2026\" }"))
        .andExpect(status().isOk());

    verify(service).validar(new TokenInvitacionRequest("abc"));
    verify(service).aceptar(new AceptarInvitacionRequest("abc", "Catedra2026"));
  }

  @Test
  void rechazaAceptacionConContrasenaQueIncumpleLaPoliticaCon400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/invitaciones/aceptacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"token\": \"abc\", \"password\": \"solamenteletras\" }"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores[0].campo").value("password"));

    verify(service, never()).aceptar(any());
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
    InvitacionService invitacionService() {
      return mock(InvitacionService.class);
    }

    @Bean
    InvitacionController invitacionController(InvitacionService invitacionService) {
      return new InvitacionController(invitacionService);
    }
  }
}
