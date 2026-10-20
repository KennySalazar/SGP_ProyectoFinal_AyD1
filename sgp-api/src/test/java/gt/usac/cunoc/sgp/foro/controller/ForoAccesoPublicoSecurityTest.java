package gt.usac.cunoc.sgp.foro.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.config.SecurityConfiguration;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.AuthRateLimitFilter;
import gt.usac.cunoc.sgp.common.security.JwtAuthenticationFilter;
import gt.usac.cunoc.sgp.common.security.JwtService;
import gt.usac.cunoc.sgp.common.security.ProblemAccessDeniedHandler;
import gt.usac.cunoc.sgp.common.security.ProblemAuthenticationEntryPoint;
import gt.usac.cunoc.sgp.foro.service.ForoService;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La cadena de seguridad real: el visitante puede leer el foro pero no participar (RN-FOR-09,
 * HU-063).
 */
@WebMvcTest(ForoController.class)
@Import({
  SecurityConfiguration.class,
  JwtAuthenticationFilter.class,
  AuthRateLimitFilter.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class,
  ApiExceptionHandler.class,
  ForoAccesoPublicoSecurityTest.Config.class
})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:4200")
class ForoAccesoPublicoSecurityTest {

  private static final UUID ID = UUID.fromString("019a0000-0000-7000-8000-000000006321");

  @Autowired private MockMvc mockMvc;
  @MockBean private ForoService foro;
  @MockBean private JwtService jwtService;
  @MockBean private UserAccountRepository usuarios;

  @Test
  void elVisitanteLeeLosHilosElHiloYSusComentariosSinSesion() throws Exception {
    when(foro.listarHilosDePuente(ID, 0, 20))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    when(foro.listarComentarios(ID, 0, 20))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc.perform(get("/api/v1/puentes/" + ID + "/hilos")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/hilos/" + ID + "/comentarios")).andExpect(status().isOk());
    // Sin hilo configurado en el servicio responde 200 con cuerpo vacío: lo que importa es que no
    // lo detenga la seguridad.
    mockMvc.perform(get("/api/v1/hilos/" + ID)).andExpect(status().isOk());
  }

  @Test
  void elVisitanteNoPuedeAbrirHilosNiComentar() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/puentes/" + ID + "/hilos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"Tema\",\"categoria\":\"CONSULTA\",\"comentario\":\"x\"}"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/v1/hilos/" + ID + "/comentarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contenido\":\"x\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unaRutaDelForoQueNoEsDeLecturaSigueExigiendoSesion() throws Exception {
    mockMvc.perform(get("/api/v1/hilos")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/hilos/" + ID + "/otra-cosa")).andExpect(status().isUnauthorized());
  }

  @TestConfiguration
  static class Config {

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }
  }
}
