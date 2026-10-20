package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapper;
import gt.usac.cunoc.sgp.sync.dto.PrepararSalidaRequest;
import gt.usac.cunoc.sgp.sync.http.IdempotenciaHttpFilter;
import gt.usac.cunoc.sgp.sync.repository.*;
import gt.usac.cunoc.sgp.sync.service.*;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  PreparacionSalidaService.class,
  PreparacionSalidaRepository.class,
  IdempotenciaHttpService.class,
  OperacionHttpRepository.class,
  AuditoriaWriter.class,
  PreparacionSalidaIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class PreparacionSalidaIntegrationTest {
  static final Instant AHORA = Instant.parse("2026-10-20T02:00:00Z");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.flyway.url", postgres::getJdbcUrl);
    r.add("spring.flyway.user", postgres::getUsername);
    r.add("spring.flyway.password", postgres::getPassword);
  }

  @TestConfiguration
  static class Config {
    @Bean
    Clock clock() {
      return Clock.fixed(AHORA, ZoneOffset.UTC);
    }

    @Bean
    ObjectMapper json() {
      return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    PuenteMapper mapper() {
      return Mappers.getMapper(PuenteMapper.class);
    }
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired PreparacionSalidaService servicio;
  @Autowired IdempotenciaHttpService operaciones;
  @Autowired ObjectMapper json;
  @Autowired PlatformTransactionManager tm;
  UUID usuario, puente, formulario, municipio;

  UUID usuario(String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO usuario(id,email,password_hash,rol_id,verificado,activado,activo) "
            + "SELECT ?,?,'test',id,true,true,true FROM rol WHERE nombre=?",
        id,
        id + "@test.com",
        rol);
    return id;
  }

  @BeforeEach
  void preparar() {
    usuario = usuario("CATEDRATICO");
    municipio =
        jdbc.queryForObject("SELECT id FROM municipio ORDER BY codigo_ine LIMIT 1", UUID.class);
    puente = puente();
    formulario = UUID.randomUUID();
    jdbc.update("UPDATE version_formulario SET activa=false WHERE activa");
    jdbc.update(
        "INSERT INTO version_formulario(id,codigo,estado,activa,esquema,hash_contenido,"
            + "version_algoritmo_ic,creado_por_id,publicado_por_id,publicado_en) "
            + "VALUES (?,?,'PUBLICADA',true,'{\"type\":\"object\"}',?,'ic-v1',?,?,CURRENT_TIMESTAMP)",
        formulario,
        "salida-" + formulario,
        "a".repeat(64),
        usuario,
        usuario);
  }

  UUID puente() {
    UUID id = UUID.randomUUID();
    int correlativo =
        jdbc.queryForObject(
            "SELECT COALESCE(MAX(correlativo_municipal),0)+1 FROM puente WHERE municipio_id=?",
            Integer.class,
            municipio);
    jdbc.update(
        "INSERT INTO puente(id,codigo,correlativo_municipal,municipio_id,nombre,ruta,ubicacion,creado_por_id) "
            + "VALUES (?,?,?,?,?,'RN-1',ST_SetSRID(ST_MakePoint(-90.615,14.481),4326)::geography,?)",
        id,
        "S-" + id,
        correlativo,
        municipio,
        "Puente salida " + id,
        usuario);
    return id;
  }

  UUID inspeccion(String estado, Instant publicada, int longitud) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO inspeccion(id,puente_id,version_formulario_id,autor_id,rol_autor_creacion,"
            + "nombre_autor_capturado,estado,fecha_inspeccion,dispositivo_id,publicado_en,datos) "
            + "VALUES (?,?,?,?,'CATEDRATICO','Autor',?,'2026-10-19','equipo',?,'{\"campo\":42}')",
        id,
        puente,
        formulario,
        usuario,
        estado,
        publicada == null ? null : java.sql.Timestamp.from(publicada));
    jdbc.update(
        "INSERT INTO inspeccion_dato_tecnico(inspeccion_id,longitud_m,anio_construccion) VALUES (?,?,2000)",
        id,
        longitud);
    return id;
  }

  PrepararSalidaRequest request() {
    return new PrepararSalidaRequest("equipo-hu041", List.of(puente));
  }

  @Test
  void descargaSoloUltimaPublicadaYRegistraContexto() {
    inspeccion("PUBLICADA", AHORA.minusSeconds(300), 10);
    UUID ultima = inspeccion("PUBLICADA", AHORA.minusSeconds(60), 20);
    inspeccion("BORRADOR", null, 30);
    var salida = servicio.preparar(usuario, request());
    assertThat(salida.paquete().formulario().id()).isEqualTo(formulario);
    assertThat(salida.paquete().puentes())
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.id()).isEqualTo(puente);
              assertThat(p.latitud()).isEqualTo(14.481);
            });
    assertThat(salida.paquete().inspecciones())
        .singleElement()
        .satisfies(
            i -> {
              assertThat(i.id()).isEqualTo(ultima);
              assertThat(i.datosPermanentes().longitudM()).isEqualByComparingTo("20");
            });
    assertThat(servicio.ultima(usuario, "equipo-hu041")).isPresent();
    assertThat(servicio.ultima(usuario("CATEDRATICO"), "equipo-hu041")).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT contexto_autorizado->>'rol' FROM preparacion_salida WHERE id=?",
                String.class,
                salida.id()))
        .isEqualTo("CATEDRATICO");
  }

  @Test
  void estimarYBuscarNoRegistranPreparaciones() {
    assertThat(servicio.estimar(usuario, request()).numeroPuentes()).isEqualTo(1);
    assertThat(servicio.disponibles(usuario, null, puente.toString(), 0, 20).getContent())
        .hasSize(1);
    assertThat(cantidad()).isZero();
  }

  @Test
  void replayDevuelveMismoPaqueteYUnaSolaSalida() throws Exception {
    var primera = peticion("replay", false);
    var segunda = peticion("replay", false);
    assertThat(primera.getStatus()).isEqualTo(201);
    assertThat(segunda.getHeader("Idempotent-Replay")).isEqualTo("true");
    assertThat(segunda.getContentType()).isEqualTo("application/json");
    assertThat(segunda.getContentAsByteArray()).isEqualTo(primera.getContentAsByteArray());
    assertThat(cantidad()).isEqualTo(1);
  }

  @Test
  void errorTrasRegistrarRevierteSalidaYPermiteReintento() throws Exception {
    assertThat(peticion("fallo", true).getStatus()).isEqualTo(500);
    assertThat(cantidad()).isZero();
    assertThat(peticion("fallo", false).getStatus()).isEqualTo(201);
    assertThat(cantidad()).isEqualTo(1);
  }

  @Test
  void estudianteSinAsignacionNoRecibePuentesNiPuedeInyectarlos() {
    UUID estudiante = usuario("ESTUDIANTE");
    assertThat(servicio.disponibles(estudiante, null, "", 0, 20)).isEmpty();
    assertThatThrownBy(() -> servicio.preparar(estudiante, request()))
        .isInstanceOf(gt.usac.cunoc.sgp.common.exception.ApiException.class)
        .hasMessageContaining("sin asignacion vigente");
    assertThat(cantidad()).isZero();
  }

  @Test
  void unaSeleccionInvalidaNoRegistraParcialmente() {
    assertThatThrownBy(
            () ->
                servicio.preparar(
                    usuario,
                    new PrepararSalidaRequest("equipo-hu041", List.of(puente, UUID.randomUUID()))))
        .isInstanceOf(gt.usac.cunoc.sgp.common.exception.ApiException.class);
    assertThat(cantidad()).isZero();
  }

  @Test
  void estudianteSoloRecibeAsignacionesVigentesYDejaDeVerRevocadas() {
    UUID estudiante = usuario("ESTUDIANTE");
    UUID asignatura = UUID.randomUUID();
    UUID curso = UUID.randomUUID();
    UUID inscripcion = UUID.randomUUID();
    UUID asignacion = UUID.randomUUID();
    UUID otroPuente = puente();
    jdbc.update(
        "INSERT INTO asignatura(id,codigo,nombre) VALUES (?,?, 'Curso prueba')",
        asignatura,
        asignatura.toString());
    jdbc.update(
        "INSERT INTO curso(id,asignatura_id,periodo,catedratico_id,fecha_inicio,fecha_fin) VALUES (?,?,'2026',?,'2026-01-01','2026-12-31')",
        curso,
        asignatura,
        usuario);
    jdbc.update(
        "INSERT INTO curso_estudiante(id,curso_id,estudiante_id,inscrito_por_id,seccion) VALUES (?,?,?,?,'A')",
        inscripcion,
        curso,
        estudiante,
        usuario);
    jdbc.update(
        "INSERT INTO asignacion_puente(id,curso_estudiante_id,puente_id,asignado_por_id) VALUES (?,?,?,?)",
        asignacion,
        inscripcion,
        puente,
        usuario);
    var solicitud = new PrepararSalidaRequest("equipo-estudiante", null);
    var salida = servicio.preparar(estudiante, solicitud);
    assertThat(salida.paquete().puentes()).extracting(p -> p.id()).containsExactly(puente);
    assertThat(salida.paquete().asignaciones())
        .singleElement()
        .satisfies(a -> assertThat(a.id()).isEqualTo(asignacion));
    assertThat(servicio.disponibles(estudiante, null, "", 0, 20).getContent())
        .extracting(p -> p.id())
        .containsExactly(puente);
    assertThatThrownBy(
            () ->
                servicio.preparar(
                    estudiante,
                    new PrepararSalidaRequest("equipo-estudiante", List.of(otroPuente))))
        .isInstanceOf(gt.usac.cunoc.sgp.common.exception.ApiException.class);
    jdbc.update(
        "UPDATE asignacion_puente SET revocado_en=CURRENT_TIMESTAMP,motivo_revocacion='Prueba' WHERE id=?",
        asignacion);
    assertThat(servicio.disponibles(estudiante, null, "", 0, 20)).isEmpty();
  }

  @Test
  void publicarOtraVersionNoModificaElRegistroAnterior() {
    var salida = servicio.preparar(usuario, request());
    jdbc.update("UPDATE version_formulario SET activa=false WHERE id=?", formulario);
    assertThatThrownBy(() -> servicio.preparar(usuario, request()))
        .isInstanceOf(gt.usac.cunoc.sgp.common.exception.ApiException.class)
        .hasMessageContaining("version activa");
    assertThat(servicio.ultima(usuario, "equipo-hu041"))
        .get()
        .satisfies(
            u -> assertThat(u.versionFormularioId()).isEqualTo(salida.paquete().formulario().id()));
    assertThat(cantidad()).isEqualTo(1);
  }

  long cantidad() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM preparacion_salida WHERE usuario_id=?", Long.class, usuario);
  }

  MockHttpServletResponse peticion(String clave, boolean fallar) throws Exception {
    var auth = new UsernamePasswordAuthenticationToken("prueba", null, List.of());
    auth.setDetails(new JwtData("prueba@test.com", usuario, RoleName.CATEDRATICO, 0));
    SecurityContextHolder.getContext().setAuthentication(auth);
    try {
      var entrada = new MockHttpServletRequest("POST", "/api/v1/preparaciones-salida");
      entrada.setServletPath("/api/v1/preparaciones-salida");
      entrada.setContentType("application/json");
      entrada.setContent(json.writeValueAsBytes(request()));
      entrada.addHeader("Idempotency-Key", clave);
      var salida = new MockHttpServletResponse();
      new IdempotenciaHttpFilter(operaciones, json, tm)
          .doFilter(
              entrada,
              salida,
              (r, s) -> {
                var resultado = servicio.preparar(usuario, request());
                if (fallar) throw new IllegalStateException("Corte antes de confirmar");
                var http = (jakarta.servlet.http.HttpServletResponse) s;
                http.setStatus(201);
                http.setContentType("application/json");
                json.writeValue(s.getOutputStream(), resultado);
              });
      return salida;
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
