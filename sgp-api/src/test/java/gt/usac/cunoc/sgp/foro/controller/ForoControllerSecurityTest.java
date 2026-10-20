package gt.usac.cunoc.sgp.foro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.foro.dto.ComentarioResponse;
import gt.usac.cunoc.sgp.foro.dto.CrearComentarioRequest;
import gt.usac.cunoc.sgp.foro.dto.CrearHiloRequest;
import gt.usac.cunoc.sgp.foro.dto.HiloResponse;
import gt.usac.cunoc.sgp.foro.model.CategoriaHilo;
import gt.usac.cunoc.sgp.foro.service.ForoService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

/**
 * Quién puede leer el foro y quién puede participar (HU-063, RN-FOR-09): leer es público, abrir
 * hilos y comentar exige una sesión de cualquier rol.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ForoControllerSecurityTest.Config.class)
class ForoControllerSecurityTest {

  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000006311");
  private static final UUID PUENTE_ID = UUID.fromString("019a0000-0000-7000-8000-000000006312");
  private static final UUID HILO_ID = UUID.fromString("019a0000-0000-7000-8000-000000006313");
  private static final String HILOS = "/api/v1/puentes/" + PUENTE_ID + "/hilos";
  private static final String HILO = "/api/v1/hilos/" + HILO_ID;
  private static final String CUERPO_HILO =
      "{\"titulo\":\"Tema\",\"categoria\":\"CONSULTA\",\"comentario\":\"Texto\"}";
  private static final String CUERPO_COMENTARIO = "{\"contenido\":\"Texto\"}";

  @Autowired private ForoController controller;
  @Autowired private ForoService foro;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(foro);
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

  @Test
  void unVisitanteLeeLosHilosElHiloYSusComentarios() throws Exception {
    when(foro.listarHilosDePuente(eq(PUENTE_ID), eq(0), eq(20))).thenReturn(paginaVacia());
    when(foro.obtenerHilo(HILO_ID)).thenReturn(hilo());
    when(foro.listarComentarios(eq(HILO_ID), eq(0), eq(20))).thenReturn(paginaVacia());

    mockMvc.perform(get(HILOS)).andExpect(status().isOk());
    mockMvc
        .perform(get(HILO))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(HILO_ID.toString()))
        .andExpect(jsonPath("$.categoria").value("CONSULTA"));
    mockMvc.perform(get(HILO + "/comentarios")).andExpect(status().isOk());
  }

  @ParameterizedTest
  @EnumSource(RoleName.class)
  void cualquierRolAutenticadoTambienPuedeLeer(RoleName rol) throws Exception {
    when(foro.listarHilosDePuente(eq(PUENTE_ID), eq(0), eq(20))).thenReturn(paginaVacia());

    mockMvc.perform(get(HILOS).principal(autenticar(rol))).andExpect(status().isOk());
  }

  @Test
  void laPaginacionTomaLosValoresDeLaPeticionOLosPredeterminados() throws Exception {
    when(foro.listarHilosDePuente(any(), eq(2), eq(50))).thenReturn(paginaVacia());
    when(foro.listarComentarios(any(), eq(0), eq(20))).thenReturn(paginaVacia());

    mockMvc.perform(get(HILOS).param("pagina", "2").param("tamanio", "50"));
    mockMvc.perform(get(HILO + "/comentarios"));

    verify(foro).listarHilosDePuente(PUENTE_ID, 2, 50);
    verify(foro).listarComentarios(HILO_ID, 0, 20);
  }

  @ParameterizedTest
  @EnumSource(RoleName.class)
  void cualquierRolAutenticadoAbreHilosYComenta(RoleName rol) throws Exception {
    when(foro.crearHilo(eq(PUENTE_ID), any(CrearHiloRequest.class), eq(USUARIO_ID)))
        .thenReturn(hilo());
    when(foro.crearComentario(eq(HILO_ID), any(CrearComentarioRequest.class), eq(USUARIO_ID)))
        .thenReturn(comentario());

    mockMvc
        .perform(
            post(HILOS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO_HILO)
                .principal(autenticar(rol)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.titulo").value("Tema"));
    mockMvc
        .perform(
            post(HILO + "/comentarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO_COMENTARIO)
                .principal(autenticar(rol)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.contenido").value("Texto"));

    verify(foro).crearHilo(eq(PUENTE_ID), any(CrearHiloRequest.class), eq(USUARIO_ID));
    verify(foro).crearComentario(eq(HILO_ID), any(CrearComentarioRequest.class), eq(USUARIO_ID));
  }

  @Test
  void unVisitanteNoPuedeAbrirHilosNiComentar() throws Exception {
    mockMvc
        .perform(post(HILOS).contentType(MediaType.APPLICATION_JSON).content(CUERPO_HILO))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post(HILO + "/comentarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO_COMENTARIO))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(foro);
  }

  @Test
  void unaValidacionDelServicioSeEntregaComoProblemaConSuDetalle() throws Exception {
    when(foro.crearHilo(any(), any(), any()))
        .thenThrow(
            new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "hilo_invalido",
                "Hilo inválido",
                "El hilo contiene datos inválidos.",
                List.of(Map.of("campo", "titulo", "mensaje", "El título es obligatorio."))));

    mockMvc
        .perform(
            post(HILOS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .principal(autenticar(RoleName.ESTUDIANTE)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("hilo_invalido"))
        .andExpect(jsonPath("$.errores[0].campo").value("titulo"));
  }

  private static <T> Page<T> paginaVacia() {
    return new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
  }

  private HiloResponse hilo() {
    OffsetDateTime ahora = OffsetDateTime.parse("2026-10-20T09:00:00-06:00");
    return new HiloResponse(
        HILO_ID,
        PUENTE_ID,
        null,
        CategoriaHilo.CONSULTA,
        "Tema",
        USUARIO_ID,
        "Ana",
        "ESTUDIANTE",
        ahora,
        ahora,
        false,
        1);
  }

  private ComentarioResponse comentario() {
    return new ComentarioResponse(
        UUID.randomUUID(),
        HILO_ID,
        null,
        USUARIO_ID,
        "Ana",
        "ESTUDIANTE",
        "Texto",
        OffsetDateTime.parse("2026-10-20T09:00:00-06:00"));
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
    ForoService foroService() {
      return mock(ForoService.class);
    }

    @Bean
    ForoController foroController(ForoService foro) {
      return new ForoController(foro);
    }
  }
}
