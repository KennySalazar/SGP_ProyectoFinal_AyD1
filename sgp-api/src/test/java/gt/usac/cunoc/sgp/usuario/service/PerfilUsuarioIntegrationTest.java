package gt.usac.cunoc.sgp.usuario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.usuario.entity.Role;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.RoleRepository;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Nombre completo y nombre de usuario contra PostgreSQL real: normalización, unicidad sin
 * distinguir mayúsculas, nombres reservados y la carrera de dos altas con el mismo nombre.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PerfilUsuarioService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class PerfilUsuarioIntegrationTest {

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(POSTGIS_IMAGE)
          .withDatabaseName("sgp_perfil_test")
          .withUsername("sgp_test")
          .withPassword("sgp_test");

  @DynamicPropertySource
  static void configurarBaseDeDatos(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired private PerfilUsuarioService perfiles;
  @Autowired private UserAccountRepository usuarios;
  @Autowired private RoleRepository roles;
  @Autowired private JdbcTemplate jdbc;

  private Role rolEstudiante;

  @BeforeEach
  void limpiar() {
    jdbc.update("DELETE FROM usuario WHERE email LIKE '%@perfil.test'");
    rolEstudiante = roles.findByName(RoleName.ESTUDIANTE).orElseThrow();
  }

  @Test
  void aplicaElPerfilNormalizadoYLoGuarda() {
    UserAccount cuenta = nuevaCuenta("ana");

    perfiles.aplicar(cuenta, "  Ana   María  Reyes ", "  Ana.Reyes  ");
    perfiles.guardar(cuenta);

    var fila =
        jdbc.queryForMap(
            "SELECT nombre_completo, nombre_usuario FROM usuario WHERE id = ?", cuenta.getId());
    assertThat(fila.get("nombre_completo")).isEqualTo("Ana María Reyes");
    assertThat(fila.get("nombre_usuario")).isEqualTo("ana.reyes");
  }

  @Test
  void elNombreDeUsuarioEsUnicoSinDistinguirMayusculas() {
    UserAccount primera = nuevaCuenta("primera");
    perfiles.aplicar(primera, "Primera Persona", "luis.perez");
    perfiles.guardar(primera);

    UserAccount segunda = nuevaCuenta("segunda");
    assertThatThrownBy(() -> perfiles.aplicar(segunda, "Segunda Persona", "LUIS.PEREZ"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("nombre_usuario_en_uso");
            });
  }

  @Test
  void quienYaTieneUnNombreDeUsuarioPuedeConservarloAlCambiarSoloElNombreCompleto() {
    UserAccount cuenta = nuevaCuenta("conserva");
    perfiles.aplicar(cuenta, "Nombre Viejo", "conserva.usuario");
    UserAccount guardada = perfiles.guardar(cuenta);

    perfiles.aplicar(guardada, "Nombre Nuevo", "Conserva.Usuario");
    perfiles.guardar(guardada);

    assertThat(
            jdbc.queryForObject(
                "SELECT nombre_completo FROM usuario WHERE id = ?", String.class, guardada.getId()))
        .isEqualTo("Nombre Nuevo");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"admin", "Administrador", "CATEDRATICO", "moderador", "sistema", "soporte", "sgp"})
  void losNombresQueSugierenUnCargoEstanReservados(String reservado) {
    UserAccount cuenta = nuevaCuenta("reservado");

    assertThatThrownBy(() -> perfiles.aplicar(cuenta, "Persona", reservado))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getCode()).isEqualTo("perfil_invalido");
              assertThat(e.getErrores())
                  .extracting(error -> error.get("campo"))
                  .containsExactly("nombreUsuario");
            });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "ab",
        "ana reyes",
        ".ana",
        "ana.",
        "-ana",
        "ana@usac",
        "áná",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      })
  void unNombreDeUsuarioFueraDeFormatoSeRechaza(String usuario) {
    UserAccount cuenta = nuevaCuenta("formato");

    assertThatThrownBy(() -> perfiles.aplicar(cuenta, "Persona", usuario))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo("perfil_invalido"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "A", "x\u0000y"})
  void unNombreCompletoVacioCortoOConCaracteresDeControlSeRechaza(String nombre) {
    UserAccount cuenta = nuevaCuenta("nombre");

    assertThatThrownBy(() -> perfiles.aplicar(cuenta, nombre, "usuario.valido"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e ->
                assertThat(e.getErrores())
                    .extracting(error -> error.get("campo"))
                    .containsExactly("nombreCompleto"));
  }

  @Test
  void losNombresSinAcentosNiEspaciosSonValidosEnLosLimites() {
    UserAccount cuenta = nuevaCuenta("limites");

    perfiles.aplicar(cuenta, "Li", "a-b");
    perfiles.guardar(cuenta);
    UserAccount otra = nuevaCuenta("limites2");
    perfiles.aplicar(otra, "x".repeat(200), "a" + "b".repeat(28) + "c");
    perfiles.guardar(otra);

    assertThat(cuenta.getNombreUsuario()).isEqualTo("a-b");
    assertThat(otra.getNombreCompleto()).hasSize(200);
  }

  @Test
  void lasCuentasAnterioresSinPerfilSiguenGuardandoseSinNombres() {
    UserAccount antigua = nuevaCuenta("antigua");

    UserAccount guardada = perfiles.guardar(antigua);

    assertThat(guardada.getNombreUsuario()).isNull();
    assertThat(guardada.getNombreCompleto()).isNull();
  }

  @Test
  void dosAltasSimultaneasConElMismoNombreDeUsuarioDejanSoloUnaGanadora() throws Exception {
    int hilos = 6;
    CountDownLatch salida = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(hilos);
    List<Future<Boolean>> resultados = new java.util.ArrayList<>();
    try {
      for (int i = 0; i < hilos; i++) {
        String correo = "carrera" + i;
        Callable<Boolean> alta =
            () -> {
              UserAccount cuenta = nuevaCuenta(correo);
              salida.await();
              try {
                perfiles.aplicar(cuenta, "Persona " + correo, "carrera.usuario");
                perfiles.guardar(cuenta);
                return true;
              } catch (ApiException e) {
                assertThat(e.getCode()).isEqualTo("nombre_usuario_en_uso");
                return false;
              }
            };
        resultados.add(pool.submit(alta));
      }
      salida.countDown();
      long ganadoras = 0;
      for (Future<Boolean> resultado : resultados) {
        try {
          if (resultado.get()) ganadoras++;
        } catch (ExecutionException e) {
          throw new AssertionError("Fallo inesperado", e.getCause());
        }
      }
      assertThat(ganadoras).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM usuario WHERE nombre_usuario = 'carrera.usuario'",
                Integer.class))
        .isEqualTo(1);
  }

  private UserAccount nuevaCuenta(String alias) {
    return new UserAccount(
        alias + "." + UUID.randomUUID() + "@perfil.test",
        "hash-de-prueba",
        rolEstudiante,
        false,
        false);
  }
}
