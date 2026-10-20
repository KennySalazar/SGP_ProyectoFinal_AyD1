package gt.usac.cunoc.sgp.revision.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import gt.usac.cunoc.sgp.common.config.SecurityConfiguration;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.*;
import gt.usac.cunoc.sgp.revision.dto.*;
import gt.usac.cunoc.sgp.revision.model.EstadoObservacion;
import gt.usac.cunoc.sgp.revision.service.ObservacionService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(
    controllers = {ObservacionController.class, ObservacionAutorController.class},
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({
  SecurityConfiguration.class,
  JwtAuthenticationFilter.class,
  AuthRateLimitFilter.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class
})
class ObservacionControllerSecurityTest {
  @Autowired MockMvc mvc;
  @MockBean ObservacionService servicio;
  @MockBean JwtService jwt;
  @MockBean UserAccountRepository usuarios;
  @MockBean Clock clock;
  private final UUID ciclo = UUID.randomUUID(), id = UUID.randomUUID(), actor = UUID.randomUUID();
  private final String base = "/api/v1/revisiones/" + ciclo + "/observaciones";

  private UsernamePasswordAuthenticationToken sesion(RoleName rol) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "test", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    auth.setDetails(new JwtData("test", actor, rol, 0));
    return auth;
  }

  private List<MockHttpServletRequestBuilder> operaciones() {
    return List.of(
        get(base),
        get(base + "/contador"),
        get(base + "/general"),
        get(base + "/" + id),
        post(base)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"destino\":{\"tipo\":\"ELEMENTO\",\"elementoRef\":\"/campo\"},\"texto\":\"Texto\"}"),
        put(base + "/" + id)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"texto\":\"Texto\"}"),
        delete(base + "/" + id),
        put(base + "/general")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"texto\":\"General\"}"));
  }

  @Test
  void todasLasOperacionesExigenSesion() throws Exception {
    for (var request : operaciones())
      mvc.perform(request)
          .andExpect(status().isUnauthorized())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    verifyNoInteractions(servicio);
  }

  @ParameterizedTest
  @EnumSource(
      value = RoleName.class,
      names = {"CATEDRATICO", "ESTUDIANTE"},
      mode = EnumSource.Mode.EXCLUDE)
  void otrosRolesNoPuedenConsultarNiEscribir(RoleName rol) throws Exception {
    for (var request : operaciones())
      mvc.perform(request.with(authentication(sesion(rol)))).andExpect(status().isForbidden());
    verifyNoInteractions(servicio);
  }

  @Test
  void estudianteSoloLeeYNoPuedeEscribir() throws Exception {
    for (var request : operaciones()) {
      boolean lectura =
          request
              .buildRequest(new org.springframework.mock.web.MockServletContext())
              .getMethod()
              .equals("GET");
      mvc.perform(request.with(authentication(sesion(RoleName.ESTUDIANTE))))
          .andExpect(lectura ? status().isOk() : status().isForbidden());
    }
    verify(servicio).listar(ciclo, actor);
    verify(servicio).contar(ciclo, actor);
    verify(servicio).obtenerGeneral(ciclo, actor);
    verify(servicio).obtener(ciclo, id, actor);
    verify(servicio, never()).crear(any(), any(), any());
    verify(servicio, never()).editar(any(), any(), any(), any());
    verify(servicio, never()).eliminar(any(), any(), any());
    verify(servicio, never()).guardarGeneral(any(), any(), any());
  }

  @Test
  void recibidasUsaSoloActorJwtYExigeEstudiante() throws Exception {
    String ruta = "/api/v1/observaciones/recibidas";
    mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
    for (RoleName rol :
        List.of(RoleName.CATEDRATICO, RoleName.ADMINISTRADOR, RoleName.PROFESIONAL_EXTERNO))
      mvc.perform(get(ruta).with(authentication(sesion(rol)))).andExpect(status().isForbidden());
    mvc.perform(
            get(ruta)
                .param("autorId", UUID.randomUUID().toString())
                .with(authentication(sesion(RoleName.ESTUDIANTE))))
        .andExpect(status().isOk());
    verify(servicio).recibidas(actor, 0, 20);
  }

  @Test
  void crearUsaIdentidadJwtYNoCamposInyectadosDelCliente() throws Exception {
    when(servicio.crear(eq(ciclo), eq(actor), any()))
        .thenReturn(
            new ObservacionResponse(
                id,
                ciclo,
                null,
                "Texto",
                EstadoObservacion.PENDIENTE,
                actor,
                Instant.parse("2026-10-19T12:00:00Z")));
    mvc.perform(
            post(base)
                .with(authentication(sesion(RoleName.CATEDRATICO)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"destino\":{\"tipo\":\"ELEMENTO\",\"elementoRef\":\"/campo\"},\"texto\":\"Texto\",\"estado\":\"RESUELTA\",\"creadoPorId\":\""
                        + UUID.randomUUID()
                        + "\",\"creadoEn\":\"1900-01-01\",\"revisorId\":\""
                        + UUID.randomUUID()
                        + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.estado").value("PENDIENTE"))
        .andExpect(jsonPath("$.creadoPorId").value(actor.toString()));
    verify(servicio)
        .crear(
            eq(ciclo),
            eq(actor),
            argThat(r -> r.texto().equals("Texto") && r.destino().elementoRef().equals("/campo")));
  }

  @Test
  void getPutDeleteYGeneralDeleganEnElServicioConActorDeSesion() throws Exception {
    for (var request :
        operaciones().stream()
            .filter(
                r ->
                    !r.buildRequest(new org.springframework.mock.web.MockServletContext())
                        .getMethod()
                        .equals("POST"))
            .toList()) {
      mvc.perform(request.with(authentication(sesion(RoleName.CATEDRATICO))))
          .andExpect(status().is2xxSuccessful());
    }
    verify(servicio).listar(ciclo, actor);
    verify(servicio).contar(ciclo, actor);
    verify(servicio).obtenerGeneral(ciclo, actor);
    verify(servicio).obtener(ciclo, id, actor);
    verify(servicio)
        .editar(eq(ciclo), eq(id), eq(actor), eq(new EditarObservacionRequest("Texto")));
    verify(servicio).eliminar(ciclo, id, actor);
    verify(servicio).guardarGeneral(ciclo, actor, new ObservacionGeneralRequest("General"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"destino\":{\"tipo\":\"CATEGORIA_FOTOGRAFICA\",\"categoriaFotografica\":\"INVALIDA\"},\"texto\":\"Texto\"}",
        "{\"destino\":{\"tipo\":\"ELEMENTO\",\"elementoRef\":\"/tramos\",\"instanciaElementoId\":\"no-uuid\"},\"texto\":\"Texto\"}",
        "{",
        ""
      })
  void erroresDelCuerpoUsan422ProblemDetailSoloEnHu037(String body) throws Exception {
    mvc.perform(
            post(base)
                .with(authentication(sesion(RoleName.CATEDRATICO)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("observacion_invalida"))
        .andExpect(jsonPath("$.errores[0].campo").isNotEmpty());
    verifyNoInteractions(servicio);
  }

  @ParameterizedTest
  @ValueSource(ints = {404, 409, 422})
  void conservaErroresDeDominioYDetalleDeCampo(int estado) throws Exception {
    when(servicio.crear(eq(ciclo), eq(actor), any()))
        .thenThrow(
            new ApiException(
                HttpStatus.valueOf(estado),
                "observacion_invalida",
                "Observación",
                "Error",
                List.of(Map.of("campo", "texto", "mensaje", "Texto inválido"))));
    mvc.perform(operaciones().get(4).with(authentication(sesion(RoleName.CATEDRATICO))))
        .andExpect(status().is(estado))
        .andExpect(jsonPath("$.errores[0].campo").value("texto"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void bearerInvalidoEsRechazadoPorElFiltroReal() throws Exception {
    when(jwt.parse("invalido")).thenReturn(Optional.empty());
    mvc.perform(get(base).header("Authorization", "Bearer invalido"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(servicio);
  }

  @Test
  void autenticacionSinIdentidadJwtNoSeUsaComoActor() throws Exception {
    var auth = sesion(RoleName.CATEDRATICO);
    auth.setDetails(null);
    mvc.perform(get(base).with(authentication(auth))).andExpect(status().isUnauthorized());
    verifyNoInteractions(servicio);
  }
}
