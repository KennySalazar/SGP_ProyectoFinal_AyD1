package gt.usac.cunoc.sgp.notificacion.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.notificacion.dto.NotificacionResponse;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = NotificacionControllerSecurityTest.Config.class)
class NotificacionControllerSecurityTest {

  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000000005");

  @Autowired private NotificacionController controller;
  @Autowired private NotificacionService service;

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
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Escenario: visitante sin autenticación recibe 401 Unauthorized")
  void visitanteSinAutenticacionRecibe401() throws Exception {
    mockMvc.perform(get("/api/v1/notificaciones")).andExpect(status().isUnauthorized());

    mockMvc.perform(get("/api/v1/notificaciones/resumen")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(patch("/api/v1/notificaciones/{id}/leer", UUID.randomUUID()))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(post("/api/v1/notificaciones/marcar-todas-leidas"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Escenario: usuario autenticado lista sus avisos")
  void usuarioAutenticadoListaAvisos() throws Exception {
    var authentication = autenticar(RoleName.ESTUDIANTE);
    UUID avisoId = UUID.randomUUID();
    var aviso =
        new NotificacionResponse(
            avisoId,
            "INSPECCION_ENVIADA",
            "Inspección enviada",
            "Tu inspección fue enviada a revisión.",
            "INSPECCION",
            UUID.randomUUID(),
            "clave:1",
            null,
            Instant.parse("2026-10-18T10:00:00Z"),
            false);

    when(service.listar(USUARIO_ID, false, 0, 20))
        .thenReturn(new PageImpl<>(List.of(aviso), PageRequest.of(0, 20), 1));

    mockMvc
        .perform(get("/api/v1/notificaciones").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(avisoId.toString()))
        .andExpect(jsonPath("$.content[0].tipo").value("INSPECCION_ENVIADA"))
        .andExpect(jsonPath("$.content[0].titulo").value("Inspección enviada"))
        .andExpect(jsonPath("$.content[0].leido").value(false))
        .andExpect(jsonPath("$.totalElements").value(1));

    verify(service).listar(USUARIO_ID, false, 0, 20);
  }

  @Test
  @DisplayName("Escenario: usuario autenticado filtra por solo no leídos")
  void usuarioAutenticadoFiltraSoloNoLeidos() throws Exception {
    var authentication = autenticar(RoleName.CATEDRATICO);

    when(service.listar(USUARIO_ID, true, 0, 10))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

    mockMvc
        .perform(
            get("/api/v1/notificaciones")
                .param("soloNoLeidos", "true")
                .param("pagina", "0")
                .param("tamanio", "10")
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    verify(service).listar(USUARIO_ID, true, 0, 10);
  }

  @Test
  @DisplayName("Escenario: resumen devuelve conteo de avisos no leídos")
  void resumenDevuelveConteoNoLeidas() throws Exception {
    var authentication = autenticar(RoleName.ADMINISTRADOR);

    when(service.contarNoLeidas(USUARIO_ID)).thenReturn(7L);

    mockMvc
        .perform(get("/api/v1/notificaciones/resumen").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.noLeidas").value(7));

    verify(service).contarNoLeidas(USUARIO_ID);
  }

  @Test
  @DisplayName("Escenario: marcar como leído marca el aviso propio")
  void marcarAvisoComoLeido() throws Exception {
    var authentication = autenticar(RoleName.ESTUDIANTE);
    UUID avisoId = UUID.randomUUID();
    var aviso =
        new NotificacionResponse(
            avisoId,
            "VEREDICTO_REVISION",
            "Revisión completada",
            "Tu inspección fue aprobada.",
            "INSPECCION",
            UUID.randomUUID(),
            "clave:veredicto",
            Instant.parse("2026-10-18T10:05:00Z"),
            Instant.parse("2026-10-18T10:00:00Z"),
            true);

    when(service.marcarLeida(avisoId, USUARIO_ID)).thenReturn(aviso);

    mockMvc
        .perform(patch("/api/v1/notificaciones/{id}/leer", avisoId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(avisoId.toString()))
        .andExpect(jsonPath("$.leido").value(true));

    verify(service).marcarLeida(avisoId, USUARIO_ID);
  }

  @Test
  @DisplayName("Escenario: consultar o marcar aviso de otra persona responde 404 (DT-SEC-04)")
  void avisoDeOtraPersonaResponde404() throws Exception {
    var authentication = autenticar(RoleName.ESTUDIANTE);
    UUID avisoAjenoId = UUID.randomUUID();

    when(service.obtener(avisoAjenoId, USUARIO_ID))
        .thenThrow(
            new ApiException(
                HttpStatus.NOT_FOUND,
                "notificacion_no_encontrada",
                "Aviso no encontrado",
                "No existe."));

    when(service.marcarLeida(avisoAjenoId, USUARIO_ID))
        .thenThrow(
            new ApiException(
                HttpStatus.NOT_FOUND,
                "notificacion_no_encontrada",
                "Aviso no encontrado",
                "No existe."));

    mockMvc
        .perform(get("/api/v1/notificaciones/{id}", avisoAjenoId).principal(authentication))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(patch("/api/v1/notificaciones/{id}/leer", avisoAjenoId).principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Escenario: marcar todos como leídos devuelve la cantidad afectada")
  void marcarTodosComoLeidosDevuelveCantidad() throws Exception {
    var authentication = autenticar(RoleName.PROFESIONAL_EXTERNO);

    when(service.marcarTodasLeidas(USUARIO_ID)).thenReturn(4);

    mockMvc
        .perform(post("/api/v1/notificaciones/marcar-todas-leidas").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.marcadas").value(4));

    verify(service).marcarTodasLeidas(USUARIO_ID);
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
    NotificacionService notificacionService() {
      return mock(NotificacionService.class);
    }

    @Bean
    NotificacionController notificacionController(NotificacionService notificacionService) {
      return new NotificacionController(notificacionService);
    }
  }
}
