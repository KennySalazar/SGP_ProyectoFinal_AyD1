package gt.usac.cunoc.sgp.usuario.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.usuario.dto.CambiarRolRequest;
import gt.usac.cunoc.sgp.usuario.dto.DesactivarUsuarioRequest;
import gt.usac.cunoc.sgp.usuario.exception.InvitacionExceptionHandler;
import gt.usac.cunoc.sgp.usuario.model.EstadoUsuario;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.service.UsuarioService;
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
@ContextConfiguration(classes = UsuarioControllerSecurityTest.Config.class)
class UsuarioControllerSecurityTest {

  private static final UUID ADMINISTRADOR_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000080");
  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000000081");

  @Autowired private UsuarioController controller;

  @Autowired private UsuarioService service;

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
  void soloElAdministradorGestionaUsuarios(RoleName rol) throws Exception {
    var authentication = autenticar(rol);

    mockMvc
        .perform(get("/api/v1/usuarios").principal(authentication))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post("/api/v1/usuarios/{id}/desactivacion", USUARIO_ID).principal(authentication))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post("/api/v1/usuarios/{id}/reactivacion", USUARIO_ID).principal(authentication))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            put("/api/v1/usuarios/{id}/rol", USUARIO_ID)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"rol\": \"CATEDRATICO\" }"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("access_denied"));

    verifyNoInteractions(service);
  }

  @Test
  void listaConFiltrosDeRolYEstado() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);

    mockMvc
        .perform(
            get("/api/v1/usuarios?rol=ESTUDIANTE&estado=PENDIENTE&pagina=1&tamanio=50")
                .principal(authentication))
        .andExpect(status().isOk());

    verify(service).listar(RoleName.ESTUDIANTE, EstadoUsuario.PENDIENTE, 1, 50);
  }

  @Test
  void rechazaEstadoInexistenteCon400() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);

    mockMvc
        .perform(get("/api/v1/usuarios?estado=BORRADO").principal(authentication))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores[0].campo").value("estado"));

    verifyNoInteractions(service);
  }

  @Test
  void desactivaConMotivoOpcionalYDevuelveElEstadoActual() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);

    mockMvc
        .perform(
            post("/api/v1/usuarios/{id}/desactivacion", USUARIO_ID)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"motivo\": \"Finalizó su contrato\" }"))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/v1/usuarios/{id}/desactivacion", USUARIO_ID).principal(authentication))
        .andExpect(status().isOk());

    var orden = inOrder(service);
    orden
        .verify(service)
        .desactivar(
            USUARIO_ID, new DesactivarUsuarioRequest("Finalizó su contrato"), ADMINISTRADOR_ID);
    orden.verify(service).obtener(USUARIO_ID);
    verify(service).desactivar(eq(USUARIO_ID), isNull(), eq(ADMINISTRADOR_ID));
  }

  @Test
  void cambiaElRolConColegiadoYObtieneElIdDelAdministradorDeJwtData() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);

    mockMvc
        .perform(
            put("/api/v1/usuarios/{id}/rol", USUARIO_ID)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"rol\": \"PROFESIONAL_EXTERNO\", \"numeroColegiado\": \"12345\" }"))
        .andExpect(status().isOk());

    verify(service)
        .cambiarRol(
            USUARIO_ID,
            new CambiarRolRequest(RoleName.PROFESIONAL_EXTERNO, "12345"),
            ADMINISTRADOR_ID);
  }

  @Test
  void rechazaCambioDeRolSinRolCon400() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);

    mockMvc
        .perform(
            put("/api/v1/usuarios/{id}/rol", USUARIO_ID)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"numeroColegiado\": \"12345\" }"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errores[0].campo").value("rol"));

    verify(service, never()).cambiarRol(any(), any(), any());
  }

  private UsernamePasswordAuthenticationToken autenticar(RoleName rol) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            "admin@ejemplo.com", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    authentication.setDetails(new JwtData("admin@ejemplo.com", ADMINISTRADOR_ID, rol, 0));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return authentication;
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {

    @Bean
    UsuarioService usuarioService() {
      return mock(UsuarioService.class);
    }

    @Bean
    UsuarioController usuarioController(UsuarioService usuarioService) {
      return new UsuarioController(usuarioService);
    }
  }
}
