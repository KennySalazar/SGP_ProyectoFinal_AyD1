package gt.usac.cunoc.sgp.puente.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.puente.dto.CrearPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.DarBajaPuenteRequest;
import gt.usac.cunoc.sgp.puente.exception.PuenteExceptionHandler;
import gt.usac.cunoc.sgp.puente.service.PuenteService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
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
@ContextConfiguration(classes = PuenteControllerSecurityTest.Config.class)
class PuenteControllerSecurityTest {

  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000000003");

  private static final String SOLICITUD =
      """
            {
              "nombre": "Puente de prueba",
              "departamentoId": "019a0000-0000-7000-8000-000000000001",
              "municipioId": "019a0000-0000-7000-8000-000000000002",
              "ruta": "CA-1",
              "latitud": 14.8,
              "longitud": -91.5,
              "confirmarCercania": false
            }
            """;

  @Autowired private PuenteController puenteController;

  @Autowired private PuenteService puenteService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(puenteService);
    SecurityContextHolder.clearContext();

    mockMvc =
        MockMvcBuilders.standaloneSetup(puenteController)
            .setControllerAdvice(new PuenteExceptionHandler())
            .build();
  }

  @AfterEach
  void limpiarAutenticacion() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @EnumSource(value = RoleName.class, names = "ADMINISTRADOR", mode = EnumSource.Mode.EXCLUDE)
  void rechazaRolesDistintosDeAdministrador(RoleName rol) throws Exception {
    var authentication = autenticar(rol);

    mockMvc
        .perform(
            post("/api/v1/puentes")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SOLICITUD))
        .andExpect(status().isForbidden());

    verifyNoInteractions(puenteService);
  }

  @Test
  void permiteAdministradorYObtieneSuIdDeJwtData() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);

    mockMvc
        .perform(
            post("/api/v1/puentes")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SOLICITUD))
        .andExpect(status().isCreated());

    verify(puenteService).registrar(any(CrearPuenteRequest.class), eq(USUARIO_ID));
  }

  @ParameterizedTest
  @EnumSource(value = RoleName.class, names = "ADMINISTRADOR", mode = EnumSource.Mode.EXCLUDE)
  void rechazaBajaARolesDistintosDeAdministrador(RoleName rol) throws Exception {
    var authentication = autenticar(rol);
    UUID puenteId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/puentes/" + puenteId + "/baja")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "motivo": "demolido" }
                    """))
        .andExpect(status().isForbidden());

    verifyNoInteractions(puenteService);
  }

  @Test
  void permiteAdministradorDarDeBajaConMotivoValido() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);
    UUID puenteId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/puentes/" + puenteId + "/baja")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "motivo": "demolido" }
                    """))
        .andExpect(status().isOk());

    verify(puenteService).darDeBaja(eq(puenteId), any(DarBajaPuenteRequest.class), eq(USUARIO_ID));
  }

  @Test
  void rechazaBajaSinMotivoCon422() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);
    UUID puenteId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/puentes/" + puenteId + "/baja")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "motivo": "   " }
                    """))
        .andExpect(status().isUnprocessableEntity());

    verifyNoInteractions(puenteService);
  }

  @ParameterizedTest
  @EnumSource(value = RoleName.class, names = "ADMINISTRADOR", mode = EnumSource.Mode.EXCLUDE)
  void rechazaReactivarARolesDistintosDeAdministrador(RoleName rol) throws Exception {
    var authentication = autenticar(rol);
    UUID puenteId = UUID.randomUUID();

    mockMvc
        .perform(post("/api/v1/puentes/" + puenteId + "/reactivar").principal(authentication))
        .andExpect(status().isForbidden());

    verifyNoInteractions(puenteService);
  }

  @Test
  void permiteAdministradorReactivar() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);
    UUID puenteId = UUID.randomUUID();

    mockMvc
        .perform(post("/api/v1/puentes/" + puenteId + "/reactivar").principal(authentication))
        .andExpect(status().isOk());

    verify(puenteService).reactivar(eq(puenteId), eq(USUARIO_ID));
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
    PuenteService puenteService() {
      return mock(PuenteService.class);
    }

    @Bean
    PuenteController puenteController(PuenteService puenteService) {
      return new PuenteController(puenteService);
    }
  }
}
