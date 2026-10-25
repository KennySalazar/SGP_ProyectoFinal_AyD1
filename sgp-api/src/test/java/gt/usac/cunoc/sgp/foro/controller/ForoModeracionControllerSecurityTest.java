package gt.usac.cunoc.sgp.foro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.exception.ApiExceptionHandler;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.foro.dto.ComentarioResponse;
import gt.usac.cunoc.sgp.foro.dto.EditarComentarioRequest;
import gt.usac.cunoc.sgp.foro.dto.HiloResponse;
import gt.usac.cunoc.sgp.foro.dto.MotivoModeracionRequest;
import gt.usac.cunoc.sgp.foro.model.CategoriaHilo;
import gt.usac.cunoc.sgp.foro.service.ForoModeracionService;
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
 * Quién edita, elimina y modera el foro (HU-065): cualquier sesión llega a editar o eliminar (el
 * servicio decide si es el autor); solo el Catedrático y el Administrador ocultan, cierran y ven
 * las inspecciones publicadas sin revisión (RN-FOR-08, RN-REV-02). Cada acción se prueba por rol.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ForoModeracionControllerSecurityTest.Config.class)
class ForoModeracionControllerSecurityTest {

  private static final UUID USUARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000006501");
  private static final UUID COMENTARIO_ID = UUID.fromString("019a0000-0000-7000-8000-000000006502");
  private static final UUID HILO_ID = UUID.fromString("019a0000-0000-7000-8000-000000006503");
  private static final String COMENTARIO = "/api/v1/comentarios/" + COMENTARIO_ID;
  private static final String OCULTAR = COMENTARIO + "/ocultar";
  private static final String CERRAR = "/api/v1/hilos/" + HILO_ID + "/cerrar";
  private static final String SIN_REVISION = "/api/v1/moderacion/inspecciones-sin-revision";
  private static final String MOTIVO = "{\"motivo\":\"Lenguaje ofensivo\"}";

  @Autowired private ForoModeracionController controller;
  @Autowired private ForoModeracionService moderacion;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    reset(moderacion);
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

  // ------------------------------------------------------------- ocultar un comentario

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"CATEDRATICO", "ADMINISTRADOR"})
  void unModeradorOcultaUnComentarioConMotivo(RoleName rol) throws Exception {
    when(moderacion.ocultar(eq(COMENTARIO_ID), any(MotivoModeracionRequest.class), eq(USUARIO_ID)))
        .thenReturn(comentarioOculto());

    mockMvc
        .perform(
            post(OCULTAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MOTIVO)
                .principal(autenticar(rol)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.oculto").value(true))
        .andExpect(jsonPath("$.motivoOcultamiento").value("Lenguaje ofensivo"));

    verify(moderacion)
        .ocultar(COMENTARIO_ID, new MotivoModeracionRequest("Lenguaje ofensivo"), USUARIO_ID);
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void unUsuarioComunNoOcultaComentarios(RoleName rol) throws Exception {
    mockMvc
        .perform(
            post(OCULTAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MOTIVO)
                .principal(autenticar(rol)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(moderacion);
  }

  // ------------------------------------------------------------------ cerrar un hilo

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"CATEDRATICO", "ADMINISTRADOR"})
  void unModeradorCierraUnHiloConMotivo(RoleName rol) throws Exception {
    when(moderacion.cerrar(eq(HILO_ID), any(MotivoModeracionRequest.class), eq(USUARIO_ID)))
        .thenReturn(hiloCerrado());

    mockMvc
        .perform(
            post(CERRAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MOTIVO)
                .principal(autenticar(rol)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cerrado").value(true))
        .andExpect(jsonPath("$.motivoCierre").value("Lenguaje ofensivo"));

    verify(moderacion)
        .cerrar(HILO_ID, new MotivoModeracionRequest("Lenguaje ofensivo"), USUARIO_ID);
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void unUsuarioComunNoCierraHilos(RoleName rol) throws Exception {
    mockMvc
        .perform(
            post(CERRAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(MOTIVO)
                .principal(autenticar(rol)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(moderacion);
  }

  @Test
  void sinMotivoElModeradorRecibeUn422ConElDetalle() throws Exception {
    when(moderacion.cerrar(eq(HILO_ID), any(), eq(USUARIO_ID)))
        .thenThrow(
            new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "motivo_requerido",
                "Motivo requerido",
                "Indica el motivo de la moderación.",
                List.of(
                    Map.of("campo", "motivo", "mensaje", "Indica el motivo de la moderación."))));

    mockMvc
        .perform(post(CERRAR).principal(autenticar(RoleName.CATEDRATICO)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("motivo_requerido"))
        .andExpect(jsonPath("$.errores[0].campo").value("motivo"));
  }

  // ------------------------------------------------ inspecciones publicadas sin revisión

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"CATEDRATICO", "ADMINISTRADOR"})
  void unModeradorVeLasInspeccionesPublicadasSinRevision(RoleName rol) throws Exception {
    when(moderacion.listarInspeccionesSinRevision(0, 20))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mockMvc.perform(get(SIN_REVISION).principal(autenticar(rol))).andExpect(status().isOk());

    verify(moderacion).listarInspeccionesSinRevision(0, 20);
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void unUsuarioComunNoVeLasInspeccionesPublicadasSinRevision(RoleName rol) throws Exception {
    mockMvc.perform(get(SIN_REVISION).principal(autenticar(rol))).andExpect(status().isForbidden());

    verifyNoInteractions(moderacion);
  }

  // ------------------------------------------------------- editar y eliminar lo propio

  @ParameterizedTest
  @EnumSource(RoleName.class)
  void cualquierRolLlegaAEditarYEliminarSuComentario(RoleName rol) throws Exception {
    when(moderacion.editar(eq(COMENTARIO_ID), any(EditarComentarioRequest.class), eq(USUARIO_ID)))
        .thenReturn(comentario());
    when(moderacion.eliminar(COMENTARIO_ID, USUARIO_ID)).thenReturn(comentario());

    mockMvc
        .perform(
            put(COMENTARIO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contenido\":\"Corregido\"}")
                .principal(autenticar(rol)))
        .andExpect(status().isOk());
    mockMvc.perform(delete(COMENTARIO).principal(autenticar(rol))).andExpect(status().isOk());

    verify(moderacion).editar(COMENTARIO_ID, new EditarComentarioRequest("Corregido"), USUARIO_ID);
    verify(moderacion).eliminar(COMENTARIO_ID, USUARIO_ID);
  }

  @Test
  void quienNoEsElAutorRecibeUn403DelServicio() throws Exception {
    when(moderacion.eliminar(COMENTARIO_ID, USUARIO_ID))
        .thenThrow(
            new ApiException(
                HttpStatus.FORBIDDEN,
                "comentario_ajeno",
                "Comentario ajeno",
                "Solo el autor puede editar o eliminar su comentario."));

    mockMvc
        .perform(delete(COMENTARIO).principal(autenticar(RoleName.ADMINISTRADOR)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("comentario_ajeno"));
  }

  @Test
  void sinSesionNoSeEditaEliminaNiModera() throws Exception {
    mockMvc
        .perform(put(COMENTARIO).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(delete(COMENTARIO)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(post(OCULTAR).contentType(MediaType.APPLICATION_JSON).content(MOTIVO))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(post(CERRAR).contentType(MediaType.APPLICATION_JSON).content(MOTIVO))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get(SIN_REVISION)).andExpect(status().isUnauthorized());

    verifyNoInteractions(moderacion);
  }

  private ComentarioResponse comentario() {
    OffsetDateTime creado = OffsetDateTime.parse("2026-10-20T09:00:00-06:00");
    return new ComentarioResponse(
        COMENTARIO_ID,
        HILO_ID,
        null,
        null,
        null,
        USUARIO_ID,
        "ana",
        "ESTUDIANTE",
        "Corregido",
        creado,
        creado.plusMinutes(5),
        creado.plusMinutes(15),
        false,
        false,
        null);
  }

  private ComentarioResponse comentarioOculto() {
    OffsetDateTime creado = OffsetDateTime.parse("2026-10-20T09:00:00-06:00");
    return new ComentarioResponse(
        COMENTARIO_ID,
        HILO_ID,
        null,
        null,
        null,
        UUID.randomUUID(),
        "ana",
        "ESTUDIANTE",
        "Texto ofensivo",
        creado,
        null,
        creado.plusMinutes(15),
        false,
        true,
        "Lenguaje ofensivo");
  }

  private HiloResponse hiloCerrado() {
    OffsetDateTime ahora = OffsetDateTime.parse("2026-10-20T09:00:00-06:00");
    return new HiloResponse(
        HILO_ID,
        UUID.randomUUID(),
        null,
        CategoriaHilo.CONSULTA,
        "Tema",
        UUID.randomUUID(),
        "ana",
        "ESTUDIANTE",
        ahora,
        ahora,
        true,
        "Lenguaje ofensivo",
        3);
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
    ForoModeracionService foroModeracionService() {
      return mock(ForoModeracionService.class);
    }

    @Bean
    ForoModeracionController foroModeracionController(ForoModeracionService moderacion) {
      return new ForoModeracionController(moderacion);
    }
  }
}
