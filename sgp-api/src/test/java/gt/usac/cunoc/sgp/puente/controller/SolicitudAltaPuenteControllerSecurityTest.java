package gt.usac.cunoc.sgp.puente.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.puente.dto.CrearSolicitudAltaPuenteRequest;
import gt.usac.cunoc.sgp.puente.exception.PuenteExceptionHandler;
import gt.usac.cunoc.sgp.puente.service.SolicitudAltaPuenteService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
@ContextConfiguration(classes = SolicitudAltaPuenteControllerSecurityTest.Config.class)
class SolicitudAltaPuenteControllerSecurityTest {

  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000000004");

  private static final String SOLICITUD =
      """
      {
        "nombre": "Puente solicitado",
        "departamentoId": "019a0000-0000-7000-8000-000000000001",
        "municipioId": "019a0000-0000-7000-8000-000000000002",
        "ruta": "CA-1",
        "latitud": 14.8,
        "longitud": -91.5,
        "justificacion": "No aparece en el catálogo"
      }
      """;

  @Autowired private SolicitudAltaPuenteController controller;

  @Autowired private SolicitudAltaPuenteService service;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(service);
    SecurityContextHolder.clearContext();

    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new PuenteExceptionHandler())
            .build();
  }

  @AfterEach
  void limpiarAutenticacion() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @EnumSource(value = RoleName.class, names = "CATEDRATICO", mode = EnumSource.Mode.EXCLUDE)
  void rechazaCrearARolesDistintosDeCatedratico(RoleName rol) throws Exception {
    var authentication = autenticar(rol);

    mockMvc
        .perform(
            post("/api/v1/solicitudes-puente")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SOLICITUD))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @EnumSource(value = RoleName.class, names = "CATEDRATICO", mode = EnumSource.Mode.EXCLUDE)
  void rechazaConsultarSolicitudesARolesDistintosDeCatedratico(RoleName rol) throws Exception {
    var authentication = autenticar(rol);

    mockMvc
        .perform(get("/api/v1/solicitudes-puente/mias").principal(authentication))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void permiteCatedraticoCrearYObtieneSuIdDeJwtData() throws Exception {
    var authentication = autenticar(RoleName.CATEDRATICO);

    mockMvc
        .perform(
            post("/api/v1/solicitudes-puente")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SOLICITUD))
        .andExpect(status().isCreated());

    verify(service).crear(any(CrearSolicitudAltaPuenteRequest.class), eq(USUARIO_ID));
  }

  @Test
  void rechazaSolicitudSinDatosObligatoriosCon422() throws Exception {
    var authentication = autenticar(RoleName.CATEDRATICO);

    mockMvc
        .perform(
            post("/api/v1/solicitudes-puente")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "nombre": "   ", "justificacion": "texto" }
                    """))
        .andExpect(status().isUnprocessableEntity());

    verifyNoInteractions(service);
  }

  @Test
  void permiteCatedraticoConsultarSusSolicitudes() throws Exception {
    var authentication = autenticar(RoleName.CATEDRATICO);
    when(service.listarMias(USUARIO_ID, 0, 20))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc
        .perform(get("/api/v1/solicitudes-puente/mias").principal(authentication))
        .andExpect(status().isOk());

    verify(service).listarMias(USUARIO_ID, 0, 20);
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
    SolicitudAltaPuenteService solicitudAltaPuenteService() {
      return mock(SolicitudAltaPuenteService.class);
    }

    @Bean
    SolicitudAltaPuenteController solicitudAltaPuenteController(
        SolicitudAltaPuenteService solicitudAltaPuenteService) {
      return new SolicitudAltaPuenteController(solicitudAltaPuenteService);
    }
  }
}
