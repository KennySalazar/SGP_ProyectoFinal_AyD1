package gt.usac.cunoc.sgp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class DatabaseMigrationIntegrationTest {

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(POSTGIS_IMAGE)
          .withDatabaseName("sgp_test")
          .withUsername("sgp_test")
          .withPassword("sgp_test");

  @Test
  void appliesAllMigrationsOnRealPostgresWithPostgis() throws Exception {
    Flyway flyway =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .load();

    var result = flyway.migrate();
    assertEquals(11, result.migrationsExecuted);
    flyway.validate();

    try (Connection connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement statement = connection.createStatement()) {
      assertTrue(extensionExists(statement, "postgis"));
      assertTrue(extensionExists(statement, "pg_trgm"));
      assertTrue(extensionExists(statement, "uuid-ossp"));
      assertTrue(tableExists(statement, "usuario"));
      assertTrue(tableExists(statement, "rol"));
      assertTrue(tableExists(statement, "auditoria"));
      assertTrue(tableExists(statement, "puente"));
      assertTrue(tableExists(statement, "version_formulario"));
      assertTrue(tableExists(statement, "inspeccion"));
      assertTrue(tableExists(statement, "revision"));
      assertTrue(tableExists(statement, "resultado_ic"));
      assertTrue(tableExists(statement, "archivo"));
      assertTrue(tableExists(statement, "orden_mantenimiento"));
      assertTrue(tableExists(statement, "operacion_idempotente"));
      assertEquals("character varying", tokenHashType(statement));
      assertTrue(indexExists(statement, "uq_version_formulario_activa"));
      assertTrue(indexExists(statement, "uq_revision_abierta_por_inspeccion"));
      assertTrue(indexExists(statement, "uq_resultado_ic_publicacion"));
      assertTrue(indexExists(statement, "uq_inspeccion_sucesora_publicada"));
      assertTrue(indexExists(statement, "uq_operacion_usuario_clave"));
      assertTrue(constraintExists(statement, "archivo_foto_disponible_metadatos_completos"));
      assertTrue(constraintExists(statement, "fk_orden_resultado_misma_inspeccion"));
    }
  }

  private boolean extensionExists(Statement statement, String extension) throws Exception {
    try (ResultSet result =
        statement.executeQuery(
            "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = '" + extension + "')")) {
      result.next();
      return result.getBoolean(1);
    }
  }

  private boolean tableExists(Statement statement, String table) throws Exception {
    try (ResultSet result =
        statement.executeQuery(
            "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '"
                + table
                + "')")) {
      result.next();
      return result.getBoolean(1);
    }
  }

  private String tokenHashType(Statement statement) throws Exception {
    try (ResultSet result =
        statement.executeQuery(
            "SELECT data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'token_refresco' AND column_name = 'token_hash'")) {
      result.next();
      return result.getString(1);
    }
  }

  private boolean indexExists(Statement statement, String index) throws Exception {
    try (ResultSet result =
        statement.executeQuery(
            "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = '"
                + index
                + "')")) {
      result.next();
      return result.getBoolean(1);
    }
  }

  private boolean constraintExists(Statement statement, String constraint) throws Exception {
    try (ResultSet result =
        statement.executeQuery(
            "SELECT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE table_schema = 'public' AND constraint_name = '"
                + constraint
                + "')")) {
      result.next();
      return result.getBoolean(1);
    }
  }
}
