package gt.usac.cunoc.sgp.notificacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.notificacion.dto.NotificacionResponse;
import gt.usac.cunoc.sgp.notificacion.entity.Notificacion;
import gt.usac.cunoc.sgp.notificacion.mapper.NotificacionMapper;
import gt.usac.cunoc.sgp.notificacion.repository.NotificacionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

  @Mock private NotificacionRepository notificaciones;
  @Mock private NotificacionMapper mapper;

  private final Instant fija = Instant.parse("2026-10-18T10:00:00Z");
  private final Clock clock = Clock.fixed(fija, ZoneOffset.UTC);

  private NotificacionService service;

  @BeforeEach
  void setUp() {
    service = new NotificacionService(notificaciones, mapper, clock);
  }

  @Test
  @DisplayName("Escenario: un único punto para avisar crea la notificación con los datos correctos")
  void notificarCreaAvisoExitosamente() {
    UUID usuarioId = UUID.randomUUID();
    UUID entidadId = UUID.randomUUID();
    String tipo = "INSPECCION_ENVIADA";
    String titulo = "Inspección recibida";
    String mensaje = "El estudiante envió la inspección para revisión.";
    String entidad = "INSPECCION";
    String claveEvento = "inspeccion:" + entidadId + ":enviada";

    when(notificaciones.existsByUsuarioIdAndClaveEvento(usuarioId, claveEvento)).thenReturn(false);

    service.notificar(usuarioId, tipo, titulo, mensaje, entidad, entidadId, claveEvento);

    ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
    verify(notificaciones).save(captor.capture());
    Notificacion guardada = captor.getValue();

    assertThat(guardada.getUsuarioId()).isEqualTo(usuarioId);
    assertThat(guardada.getTipo()).isEqualTo(tipo);
    assertThat(guardada.getTitulo()).isEqualTo(titulo);
    assertThat(guardada.getMensaje()).isEqualTo(mensaje);
    assertThat(guardada.getEntidad()).isEqualTo(entidad);
    assertThat(guardada.getEntidadId()).isEqualTo(entidadId);
    assertThat(guardada.getClaveEvento()).isEqualTo(claveEvento);
    assertThat(guardada.getCreadoEn()).isEqualTo(fija);
    assertThat(guardada.getLeidoEn()).isNull();
  }

  @Test
  @DisplayName("Escenario: un mismo evento avisa una sola vez si ya existe")
  void notificarNoDuplicaSiClaveExiste() {
    UUID usuarioId = UUID.randomUUID();
    String claveEvento = "evento:123";

    when(notificaciones.existsByUsuarioIdAndClaveEvento(usuarioId, claveEvento)).thenReturn(true);

    service.notificar(usuarioId, "TIPO", "Titulo", "Mensaje", null, null, claveEvento);

    verify(notificaciones, never()).save(any());
  }

  @Test
  @DisplayName("Escenario: concurrencia en clave de evento no hace fallar la operación")
  void notificarToleraDataIntegrityViolationPorConcurrencia() {
    UUID usuarioId = UUID.randomUUID();
    String claveEvento = "evento:concurrente";

    when(notificaciones.existsByUsuarioIdAndClaveEvento(usuarioId, claveEvento)).thenReturn(false);
    when(notificaciones.save(any(Notificacion.class)))
        .thenThrow(
            new DataIntegrityViolationException("duplicate key value violates unique constraint"));

    // No debe lanzar excepción
    service.notificar(usuarioId, "TIPO", "Titulo", "Mensaje", null, null, claveEvento);
  }

  @Test
  @DisplayName("Escenario: entidad e id de entidad deben ser coherentes")
  void validaEntidadCoherente() {
    UUID usuarioId = UUID.randomUUID();

    // Entidad presente sin ID -> error
    assertThatThrownBy(
            () -> service.notificar(usuarioId, "TIPO", "T", "M", "INSPECCION", null, "clave"))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e ->
                assertThat(((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

    // ID presente sin entidad -> error
    assertThatThrownBy(
            () -> service.notificar(usuarioId, "TIPO", "T", "M", null, UUID.randomUUID(), "clave"))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e ->
                assertThat(((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
  }

  @Test
  @DisplayName("Escenario: rechaza datos sensibles como tokens o calificaciones (DT-SEC-12)")
  void rechazaDatosSensibles() {
    UUID usuarioId = UUID.randomUUID();

    // Con token JWT
    assertThatThrownBy(
            () ->
                service.notificar(
                    usuarioId,
                    "TIPO",
                    "Aviso",
                    "Tu token es eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0",
                    null,
                    null,
                    "clave:token"))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e ->
                assertThat(((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

    // Con calificación de revisión
    assertThatThrownBy(
            () ->
                service.notificar(
                    usuarioId,
                    "TIPO",
                    "Veredicto",
                    "Tu revisión obtuvo una calificacion: 85 puntos",
                    null,
                    null,
                    "clave:calif"))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e ->
                assertThat(((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
  }

  @Test
  @DisplayName("Escenario: paginación máxima 100 y página >= 0")
  void validaPaginacion() {
    UUID usuarioId = UUID.randomUUID();

    assertThatThrownBy(() -> service.listar(usuarioId, false, -1, 20))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e ->
                assertThat(((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

    assertThatThrownBy(() -> service.listar(usuarioId, false, 0, 101))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e ->
                assertThat(((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
  }

  @Test
  @DisplayName("Escenario: cada quien ve solo lo suyo, 404 si es de otro usuario (DT-SEC-04)")
  void cadaQuienVeSoloLoSuyo() {
    UUID miUsuarioId = UUID.randomUUID();
    UUID otroUsuarioId = UUID.randomUUID();
    UUID avisoId = UUID.randomUUID();

    when(notificaciones.findByIdAndUsuarioId(avisoId, miUsuarioId)).thenReturn(Optional.empty());

    // Al consultar
    assertThatThrownBy(() -> service.obtener(avisoId, miUsuarioId))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

    // Al marcar como leído
    assertThatThrownBy(() -> service.marcarLeida(avisoId, miUsuarioId))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  @DisplayName("Escenario: marcar como leído actualiza leidoEn")
  void marcarLeidoExitosamente() {
    UUID usuarioId = UUID.randomUUID();
    UUID avisoId = UUID.randomUUID();
    Notificacion n =
        new Notificacion(
            avisoId,
            usuarioId,
            "TIPO",
            "Titulo",
            "Mensaje",
            null,
            null,
            "clave",
            fija.minusSeconds(60));

    when(notificaciones.findByIdAndUsuarioId(avisoId, usuarioId)).thenReturn(Optional.of(n));
    when(notificaciones.save(any(Notificacion.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var responseMock =
        new NotificacionResponse(
            avisoId,
            "TIPO",
            "Titulo",
            "Mensaje",
            null,
            null,
            "clave",
            fija,
            fija.minusSeconds(60),
            true);
    when(mapper.toResponse(any(Notificacion.class))).thenReturn(responseMock);

    NotificacionResponse resultado = service.marcarLeida(avisoId, usuarioId);

    assertThat(resultado.leido()).isTrue();
    assertThat(n.getLeidoEn()).isEqualTo(fija);
  }

  @Test
  @DisplayName("Escenario: contar no leídas y marcar todas como leídas")
  void conteoYMarcarTodasLeidas() {
    UUID usuarioId = UUID.randomUUID();

    when(notificaciones.countByUsuarioIdAndLeidoEnIsNull(usuarioId)).thenReturn(5L);
    when(notificaciones.marcarTodasLeidas(usuarioId, fija)).thenReturn(5);

    assertThat(service.contarNoLeidas(usuarioId)).isEqualTo(5L);
    assertThat(service.marcarTodasLeidas(usuarioId)).isEqualTo(5);
  }
}
