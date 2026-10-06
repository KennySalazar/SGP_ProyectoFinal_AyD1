package gt.usac.cunoc.sgp.common.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.entity.Auditoria;
import gt.usac.cunoc.sgp.common.audit.mapper.AuditoriaMapperImpl;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.repository.AuditoriaRepository;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

class AuditoriaConsultaServiceTest {

  private static final UUID ACTOR = UUID.fromString("019a0000-0000-7000-8000-000000000006");
  private static final UUID ID = UUID.fromString("019a0000-0000-7000-8000-0000000000a1");
  private static final Instant FECHA = Instant.parse("2026-10-06T20:00:00Z");
  private final AuditoriaRepository repositorio = mock(AuditoriaRepository.class);
  private final UserAccountRepository usuarios = mock(UserAccountRepository.class);
  private final AuditoriaConsultaService servicio =
      new AuditoriaConsultaService(repositorio, new AuditoriaMapperImpl(), usuarios);
  private final ObjectMapper json = new ObjectMapper();

  @Test
  @DisplayName("Escenario: listado paginado de hasta 100 registros ordenado por fecha descendente")
  void paginaOrdenadaConCorreoLegible() {
    // Dado un registro y la cuenta de su autor
    UserAccount usuario = mock(UserAccount.class);
    when(usuario.getId()).thenReturn(ACTOR);
    when(usuario.getEmail()).thenReturn("admin@ejemplo.com");
    when(usuarios.findAllById(List.of(ACTOR))).thenReturn(List.of(usuario));
    when(repositorio.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(registro(null, null)), PageRequest.of(2, 100), 201));

    // Cuando se consulta una página de la bitácora
    var pagina = servicio.consultar(null, null, null, null, null, 2, 100);

    // Entonces la consulta solicita orden descendente estable y muestra el correo del autor
    var captura = ArgumentCaptor.forClass(Pageable.class);
    verify(repositorio).findAll(any(Specification.class), captura.capture());
    assertThat(captura.getValue().getPageSize()).isEqualTo(100);
    assertThat(captura.getValue().getPageNumber()).isEqualTo(2);
    assertThat(captura.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id")));
    assertThat(pagina.getContent().getFirst().usuarioEmail()).isEqualTo("admin@ejemplo.com");
    assertThat(pagina.getContent().getFirst().creadoEn().getOffset())
        .isEqualTo(ZoneOffset.ofHours(-6));
  }

  @ParameterizedTest
  @CsvSource({"-1,20", "0,0", "0,101"})
  @DisplayName("Escenario: una paginación fuera del límite se rechaza antes de consultar")
  void rechazaPaginacionInvalida(int pagina, int tamanio) {
    // Dado un tamaño o página inválidos
    // Cuando se intenta consultar, entonces se responde 422 sin acceder a los datos
    assertThatThrownBy(() -> servicio.consultar(null, null, null, null, null, pagina, tamanio))
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> {
              assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(error.getCode()).isEqualTo("paginacion_invalida");
            });
    verifyNoInteractions(repositorio, usuarios);
  }

  @Test
  @DisplayName("Escenario: un correo desconocido no devuelve acciones de otros usuarios")
  void correoDesconocidoDevuelvePaginaVacia() {
    // Dado un correo que no corresponde a una cuenta
    when(usuarios.findByEmail("ausente@ejemplo.com")).thenReturn(Optional.empty());

    // Cuando se filtra por ese correo normalizado y por fechas
    var pagina = servicio.consultar(null, " Ausente@Ejemplo.com ", null, null, FECHA, FECHA, 0, 20);

    // Entonces no hay resultados y no se consulta la auditoría sin el filtro
    assertThat(pagina.isEmpty()).isTrue();
    verify(usuarios).findByEmail("ausente@ejemplo.com");
    verifyNoInteractions(repositorio);
  }

  @Test
  @DisplayName(
      "Escenario: filtros de identificador y correo de usuarios distintos no se sustituyen")
  void filtrosDeUsuarioSeCombinan() {
    // Dado un correo cuyo usuario es distinto al identificador del filtro
    UserAccount usuario = mock(UserAccount.class);
    when(usuario.getId()).thenReturn(UUID.randomUUID());
    when(usuarios.findByEmail("otro@ejemplo.com")).thenReturn(Optional.of(usuario));

    // Cuando se aplican ambos filtros, entonces no hay coincidencias
    assertThat(
            servicio.consultar(ACTOR, "otro@ejemplo.com", null, null, null, null, 0, 20).isEmpty())
        .isTrue();
    verifyNoInteractions(repositorio);
  }

  @Test
  @DisplayName("Escenario: un rango de fechas invertido se rechaza")
  void rechazaRangoInvertido() {
    // Dado que la fecha inicial es posterior a la final
    // Cuando se consulta, entonces se informa el rango inválido
    assertThatThrownBy(
            () -> servicio.consultar(null, null, null, FECHA.plusSeconds(1), FECHA, 0, 20))
        .isInstanceOfSatisfying(
            ApiException.class, error -> assertThat(error.getCode()).isEqualTo("rango_invalido"));
    verifyNoInteractions(repositorio, usuarios);
  }

  @Test
  @DisplayName("Escenario: el detalle muestra cambios anidados, nulos y booleanos sin JSON crudo")
  void detalleMuestraValoresLegibles() throws Exception {
    // Dado un cambio de nombre, estado, campos anidados, nulos y listas vacías
    var anteriores =
        json.readTree(
            """
        {"nombre":"Anterior","activo":true,"ubicacion":{"ruta":"CA-9"},"eliminado":"valor","sinCambio":1}
        """);
    var posteriores =
        json.readTree(
            """
        {"nombre":null,"activo":false,"ubicacion":{"ruta":"CA-1"},"lista":[],"objeto":{},"nuevo":null,"sinCambio":1}
        """);
    when(repositorio.findById(ID)).thenReturn(Optional.of(registro(anteriores, posteriores)));
    UserAccount usuario = mock(UserAccount.class);
    when(usuario.getEmail()).thenReturn("admin@ejemplo.com");
    when(usuarios.findById(ACTOR)).thenReturn(Optional.of(usuario));

    // Cuando se abre el registro
    var detalle = servicio.detalle(ID);

    // Entonces los cambios se presentan como campos y valores, incluido el paso a nulo
    assertThat(detalle.usuarioEmail()).isEqualTo("admin@ejemplo.com");
    assertThat(detalle.cambios()).extracting(c -> c.campo()).doesNotContain("sinCambio");
    assertThat(detalle.cambios())
        .anySatisfy(
            cambio -> {
              assertThat(cambio.campo()).isEqualTo("nombre");
              assertThat(cambio.anterior()).isEqualTo("Anterior");
              assertThat(cambio.posterior()).isNull();
            });
    assertThat(detalle.cambios())
        .anySatisfy(
            cambio -> {
              assertThat(cambio.campo()).isEqualTo("activo");
              assertThat(cambio.anterior()).isEqualTo("Sí");
              assertThat(cambio.posterior()).isEqualTo("No");
            });
    assertThat(detalle.cambios())
        .anySatisfy(
            cambio -> {
              assertThat(cambio.campo()).isEqualTo("ubicacion.ruta");
              assertThat(cambio.posterior()).isEqualTo("CA-1");
            });
    assertThat(detalle.cambios())
        .anySatisfy(
            cambio -> {
              assertThat(cambio.campo()).isEqualTo("lista");
              assertThat(cambio.posterior()).isEqualTo("Lista vacía");
            });
    assertThat(detalle.cambios())
        .anySatisfy(
            cambio -> {
              assertThat(cambio.campo()).isEqualTo("eliminado");
              assertThat(cambio.anterior()).isEqualTo("valor");
              assertThat(cambio.posterior()).isNull();
            });
  }

  @Test
  @DisplayName("Escenario: un registro inexistente devuelve 404")
  void detalleInexistente() {
    // Dado un identificador inexistente
    when(repositorio.findById(ID)).thenReturn(Optional.empty());
    // Cuando se abre el registro, entonces se indica que no existe
    assertThatThrownBy(() -> servicio.detalle(ID))
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  private Auditoria registro(
      com.fasterxml.jackson.databind.JsonNode anteriores,
      com.fasterxml.jackson.databind.JsonNode posteriores) {
    return new Auditoria(
        ID,
        ACTOR,
        AccionAuditoria.MODIFICAR,
        "puente",
        UUID.randomUUID(),
        anteriores,
        posteriores,
        null,
        FECHA);
  }
}
