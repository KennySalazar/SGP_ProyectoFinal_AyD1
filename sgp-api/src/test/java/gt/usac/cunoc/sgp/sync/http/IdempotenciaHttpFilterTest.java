package gt.usac.cunoc.sgp.sync.http;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.sync.service.IdempotenciaHttpService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class IdempotenciaHttpFilterTest {
  IdempotenciaHttpService operaciones;
  IdempotenciaHttpFilter filtro;
  UUID usuario = UUID.randomUUID();
  UUID reserva = UUID.randomUUID();

  @BeforeEach
  void preparar() {
    operaciones = mock(IdempotenciaHttpService.class);
    var tm = mock(PlatformTransactionManager.class);
    when(tm.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
    filtro = new IdempotenciaHttpFilter(operaciones, new ObjectMapper(), tm);
    when(operaciones.reservar(any(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new ReservaIdempotente(reserva, null));
    var auth = new UsernamePasswordAuthenticationToken("inspector", null, List.of());
    auth.setDetails(new JwtData("inspector@test.com", usuario, RoleName.CATEDRATICO, 0));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
  }

  MockHttpServletRequest solicitud(String clave, String cuerpo) {
    var r = new MockHttpServletRequest("POST", "/api/v1/inspecciones");
    r.setServletPath("/api/v1/inspecciones");
    if (clave != null) r.addHeader("Idempotency-Key", clave);
    r.setContentType("application/json");
    r.setContent(cuerpo.getBytes(StandardCharsets.UTF_8));
    return r;
  }

  @Test
  void guardaCodigoCuerpoYLocationSinEntregarClaveAlMecanismoAnterior() throws Exception {
    var salida = new MockHttpServletResponse();
    filtro.doFilter(
        solicitud("clave", "{\"puente\":1}"),
        salida,
        (r, s) -> {
          assertThat(((jakarta.servlet.http.HttpServletRequest) r).getHeader("Idempotency-Key"))
              .isNull();
          assertThat(new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
              .contains("puente");
          var http = (jakarta.servlet.http.HttpServletResponse) s;
          http.setStatus(201);
          http.setHeader("Location", "/api/v1/inspecciones/1");
          http.getWriter().write("{\"id\":1}");
        });
    var guardada = ArgumentCaptor.forClass(RespuestaIdempotente.class);
    verify(operaciones).completar(eq(reserva), eq(usuario), eq("clave"), guardada.capture());
    assertThat(guardada.getValue().codigo()).isEqualTo(201);
    assertThat(guardada.getValue().encabezados())
        .containsEntry("Location", "/api/v1/inspecciones/1");
    assertThat(salida.getContentAsString()).isEqualTo("{\"id\":1}");
  }

  @Test
  void replayNoEjecutaLaOperacionYReproduceBytes() throws Exception {
    var respuesta =
        new RespuestaIdempotente(
            422,
            "{\"error\":true}".getBytes(StandardCharsets.UTF_8),
            Map.of("Content-Type", "application/problem+json"));
    when(operaciones.reservar(any(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new ReservaIdempotente(reserva, respuesta));
    var salida = new MockHttpServletResponse();
    filtro.doFilter(solicitud("clave", "{}"), salida, (r, s) -> fail("No debe ejecutarse"));
    assertThat(salida.getStatus()).isEqualTo(422);
    assertThat(salida.getHeader("Idempotent-Replay")).isEqualTo("true");
    assertThat(salida.getContentAsByteArray()).isEqualTo(respuesta.cuerpo());
  }

  @Test
  void otraSolicitudDevuelve422() throws Exception {
    when(operaciones.reservar(any(), anyString(), anyString(), anyString(), anyString()))
        .thenThrow(
            new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "idempotencia_solicitud_distinta",
                "Clave utilizada",
                "Otra solicitud"));
    var salida = new MockHttpServletResponse();
    filtro.doFilter(solicitud("clave", "{}"), salida, (r, s) -> fail("No debe ejecutarse"));
    assertThat(salida.getStatus()).isEqualTo(422);
    assertThat(salida.getContentAsString()).contains("idempotencia_solicitud_distinta");
  }

  @Test
  void enProcesoDevuelve409ConRetryAfter() throws Exception {
    when(operaciones.reservar(any(), anyString(), anyString(), anyString(), anyString()))
        .thenThrow(
            new ApiException(HttpStatus.CONFLICT, "operacion_en_proceso", "En proceso", "Espere"));
    var salida = new MockHttpServletResponse();
    filtro.doFilter(solicitud("clave", "{}"), salida, (r, s) -> fail("No debe ejecutarse"));
    assertThat(salida.getStatus()).isEqualTo(409);
    assertThat(salida.getHeader("Retry-After")).isEqualTo("1");
  }

  @Test
  void conservaValidacionPeroLibera5xx() throws Exception {
    var salida = new MockHttpServletResponse();
    filtro.doFilter(
        solicitud("validacion", "{}"),
        salida,
        (r, s) -> {
          ((jakarta.servlet.http.HttpServletResponse) s).setStatus(409);
          s.getWriter().write("version desactualizada");
        });
    verify(operaciones).completarError(eq(reserva), eq(usuario), eq("validacion"), any());
    filtro.doFilter(
        solicitud("fallo", "{}"),
        new MockHttpServletResponse(),
        (r, s) -> {
          ((jakarta.servlet.http.HttpServletResponse) s).setStatus(503);
        });
    verify(operaciones).liberar(reserva);
  }

  @Test
  void excepcionLiberaLaClaveYNoDevuelveExito() throws Exception {
    var salida = new MockHttpServletResponse();
    filtro.doFilter(
        solicitud("fallo", "{}"),
        salida,
        (r, s) -> {
          throw new java.io.IOException("corte");
        });
    assertThat(salida.getStatus()).isEqualTo(500);
    verify(operaciones).liberar(reserva);
    verify(operaciones, never()).completar(any(), any(), any(), any());
  }

  @Test
  void ordenDePropiedadesNoCambiaLaHuella() throws Exception {
    var captor = ArgumentCaptor.forClass(String.class);
    filtro.doFilter(
        solicitud("a", "{\"a\":1,\"b\":2}"), new MockHttpServletResponse(), (r, s) -> {});
    filtro.doFilter(
        solicitud("b", "{\"b\":2,\"a\":1}"), new MockHttpServletResponse(), (r, s) -> {});
    verify(operaciones, times(2))
        .reservar(eq(usuario), anyString(), eq("POST"), anyString(), captor.capture());
    assertThat(captor.getAllValues().get(0)).isEqualTo(captor.getAllValues().get(1));
  }

  @Test
  void sinClaveConservaComportamientoYNoProtegeFotografias() throws Exception {
    var cuenta = new AtomicInteger();
    filtro.doFilter(
        solicitud(null, "{}"), new MockHttpServletResponse(), (r, s) -> cuenta.incrementAndGet());
    assertThat(cuenta.get()).isEqualTo(1);
    verifyNoInteractions(operaciones);
    assertThat(IdempotenciaHttpFilter.protegida("POST", "/api/v1/inspecciones/1/fotografias"))
        .isFalse();
    assertThat(IdempotenciaHttpFilter.protegida("PUT", "/api/v1/inspecciones/1/borrador")).isTrue();
    assertThat(IdempotenciaHttpFilter.protegida("POST", "/api/v1/inspecciones/1/reenviar"))
        .isTrue();
  }

  @Test
  void sinSesionNoReserva() throws Exception {
    SecurityContextHolder.clearContext();
    filtro.doFilter(solicitud("clave", "{}"), new MockHttpServletResponse(), (r, s) -> {});
    verifyNoInteractions(operaciones);
  }

  @Test
  void rechazaClaveVaciaODuplicada() throws Exception {
    var salida = new MockHttpServletResponse();
    filtro.doFilter(solicitud("", "{}"), salida, (r, s) -> fail("No debe ejecutarse"));
    assertThat(salida.getStatus()).isEqualTo(400);
    var duplicada = solicitud("a", "{}");
    duplicada.addHeader("Idempotency-Key", "b");
    salida = new MockHttpServletResponse();
    filtro.doFilter(duplicada, salida, (r, s) -> fail("No debe ejecutarse"));
    assertThat(salida.getStatus()).isEqualTo(400);
    verifyNoInteractions(operaciones);
  }
}
