package gt.usac.cunoc.sgp.integration;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.sync.http.IdempotenciaHttpFilter;
import gt.usac.cunoc.sgp.sync.service.IdempotenciaHttpService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  IdempotenciaHttpService.class,
  gt.usac.cunoc.sgp.sync.repository.OperacionHttpRepository.class,
  AuditoriaWriter.class,
  IdempotenciaHttpIntegrationTest.Config.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class IdempotenciaHttpIntegrationTest {
  static final Instant AHORA = Instant.parse("2026-10-19T04:00:00Z");

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
  }

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

  @Autowired IdempotenciaHttpService operaciones;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired PlatformTransactionManager tm;
  UUID usuario;
  IdempotenciaHttpFilter filtro;

  @BeforeEach
  void preparar() {
    usuario = nuevoUsuario();
    filtro = new IdempotenciaHttpFilter(operaciones, json, tm);
    jdbc.execute("create table if not exists efecto_hu043 (id uuid primary key)");
  }

  UUID nuevoUsuario() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into usuario(id,email,password_hash,rol_id,verificado,activado,activo) "
            + "select ?,?,'test',id,true,true,true from rol where nombre='CATEDRATICO'",
        id,
        id + "@test.com");
    return id;
  }

  MockHttpServletResponse peticion(
      UUID autor, String clave, String metodo, String ruta, String cuerpo, FilterChain accion)
      throws Exception {
    var auth = new UsernamePasswordAuthenticationToken("inspector", null, List.of());
    auth.setDetails(new JwtData("test@test.com", autor, RoleName.CATEDRATICO, 0));
    SecurityContextHolder.getContext().setAuthentication(auth);
    try {
      var entrada = new MockHttpServletRequest(metodo, ruta);
      entrada.setServletPath(ruta);
      entrada.setContentType("application/json");
      entrada.setContent(cuerpo.getBytes(StandardCharsets.UTF_8));
      entrada.addHeader("Idempotency-Key", clave);
      var salida = new MockHttpServletResponse();
      filtro.doFilter(entrada, salida, accion);
      return salida;
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  MockHttpServletResponse crear(String clave, FilterChain accion) throws Exception {
    return peticion(usuario, clave, "POST", "/api/v1/inspecciones", "{}", accion);
  }

  @Test
  void simultaneasSoloTienenUnEfectoYLaSegundaNoEspera() throws Exception {
    var iniciada = new CountDownLatch(1);
    var continuar = new CountDownLatch(1);
    UUID efecto = UUID.randomUUID();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var primera =
          pool.submit(
              () ->
                  crear(
                      "concurrente",
                      (r, s) -> {
                        jdbc.update("insert into efecto_hu043(id) values (?)", efecto);
                        iniciada.countDown();
                        try {
                          if (!continuar.await(10, TimeUnit.SECONDS))
                            throw new IllegalStateException("Tiempo agotado");
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                          throw new IllegalStateException(e);
                        }
                        ((jakarta.servlet.http.HttpServletResponse) s).setStatus(201);
                        s.getWriter().write("{\"id\":\"" + efecto + "\"}");
                      }));
      try {
        assertThat(iniciada.await(10, TimeUnit.SECONDS)).isTrue();
        var segunda =
            pool.submit(() -> crear("concurrente", (r, s) -> fail("Operacion duplicada")));
        var conflicto = segunda.get(3, TimeUnit.SECONDS);
        assertThat(conflicto.getStatus()).isEqualTo(409);
        assertThat(conflicto.getHeader("Retry-After")).isEqualTo("1");
      } finally {
        continuar.countDown();
      }
      var original = primera.get(10, TimeUnit.SECONDS);
      assertThat(original.getStatus()).isEqualTo(201);
      var repetida = crear("concurrente", (r, s) -> fail("No debe repetirse"));
      assertThat(repetida.getStatus()).isEqualTo(201);
      assertThat(repetida.getHeader("Idempotent-Replay")).isEqualTo("true");
      assertThat(repetida.getContentAsByteArray()).isEqualTo(original.getContentAsByteArray());
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from efecto_hu043 where id=?", Integer.class, efecto))
          .isEqualTo(1);
    }
  }

  @Test
  void rechazaCambiosDeCuerpoRutaYMetodoSinEjecutarlos() throws Exception {
    crear("misma", (r, s) -> s.getWriter().write("original"));
    var contador = new AtomicInteger();
    FilterChain accion = (r, s) -> contador.incrementAndGet();
    assertThat(
            peticion(usuario, "misma", "POST", "/api/v1/inspecciones", "{\"otro\":1}", accion)
                .getStatus())
        .isEqualTo(422);
    assertThat(
            peticion(usuario, "misma", "POST", "/api/v1/inspecciones/iniciar", "{}", accion)
                .getStatus())
        .isEqualTo(422);
    assertThat(
            peticion(usuario, "misma", "PUT", "/api/v1/inspecciones/1/datos", "{}", accion)
                .getStatus())
        .isEqualTo(422);
    assertThat(contador.get()).isZero();
  }

  @Test
  void usuariosDistintosNoCompartenLaRespuesta() throws Exception {
    crear("compartida", (r, s) -> s.getWriter().write("primero"));
    var otra =
        peticion(
            nuevoUsuario(),
            "compartida",
            "POST",
            "/api/v1/inspecciones",
            "{}",
            (r, s) -> s.getWriter().write("segundo"));
    assertThat(otra.getContentAsString()).isEqualTo("segundo");
    assertThat(otra.getHeader("Idempotent-Replay")).isNull();
  }

  @Test
  void errorDeterministaRevierteEfectosYSeRecuerda() throws Exception {
    UUID efecto = UUID.randomUUID();
    var original =
        crear(
            "validacion",
            (r, s) -> {
              jdbc.update("insert into efecto_hu043(id) values (?)", efecto);
              ((jakarta.servlet.http.HttpServletResponse) s).setStatus(422);
              s.setContentType("application/problem+json");
              s.getWriter().write("{\"status\":422,\"detail\":\"Faltan datos\"}");
            });
    assertThat(
            jdbc.queryForObject(
                "select count(*) from efecto_hu043 where id=?", Integer.class, efecto))
        .isZero();
    var replay = crear("validacion", (r, s) -> fail("No debe repetirse"));
    assertThat(replay.getStatus()).isEqualTo(422);
    assertThat(replay.getContentAsByteArray()).isEqualTo(original.getContentAsByteArray());
    assertThat(replay.getHeader("Idempotent-Replay")).isEqualTo("true");
  }

  @Test
  void fallo5xxLiberaLaClaveYRevierteLosDatos() throws Exception {
    UUID efecto = UUID.randomUUID();
    crear(
        "reintento",
        (r, s) -> {
          jdbc.update("insert into efecto_hu043(id) values (?)", efecto);
          ((jakarta.servlet.http.HttpServletResponse) s).setStatus(503);
        });
    assertThat(
            jdbc.queryForObject(
                "select count(*) from efecto_hu043 where id=?", Integer.class, efecto))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from operacion_idempotente where usuario_id=? and clave='reintento'",
                Integer.class,
                usuario))
        .isZero();
    var exito =
        crear(
            "reintento",
            (r, s) -> {
              jdbc.update("insert into efecto_hu043(id) values (?)", efecto);
              ((jakarta.servlet.http.HttpServletResponse) s).setStatus(201);
            });
    assertThat(exito.getStatus()).isEqualTo(201);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from efecto_hu043 where id=?", Integer.class, efecto))
        .isEqualTo(1);
  }

  @Test
  void reservaVenceEnSieteDiasYLaAuditoriaNoContieneElCuerpo() throws Exception {
    peticion(
        usuario,
        "auditoria",
        "POST",
        "/api/v1/inspecciones",
        "{\"secreto\":\"dato_privado\"}",
        (r, s) -> s.getWriter().write("ok"));
    var fila =
        jdbc.queryForMap(
            "select creado_en,expira_en,id from operacion_idempotente where usuario_id=? and clave='auditoria'",
            usuario);
    Instant inicio = ((java.sql.Timestamp) fila.get("creado_en")).toInstant();
    Instant expira = ((java.sql.Timestamp) fila.get("expira_en")).toInstant();
    assertThat(Duration.between(inicio, expira)).isEqualTo(Duration.ofDays(7));
    var valores =
        jdbc.queryForObject(
            "select valores_posteriores::text from auditoria where entidad='operacion_idempotente' and entidad_id=?",
            String.class,
            fila.get("id"));
    assertThat(valores).contains("auditoria", "200").doesNotContain("secreto", "dato_privado");
  }
}
