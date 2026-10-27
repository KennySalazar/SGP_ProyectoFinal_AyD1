package gt.usac.cunoc.sgp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    assertEquals(25, result.migrationsExecuted);

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
      assertTrue(columnExists(statement, "auditoria", "proceso_automatico"));
      assertTrue(indexExists(statement, "idx_auditoria_creado"));
      assertTrue(tableExists(statement, "usuario_profesional"));
      assertTrue(tableExists(statement, "departamento"));
      assertTrue(tableExists(statement, "municipio"));
      assertTrue(tableExists(statement, "limite_territorial"));
      assertEquals(1, rowCount(statement, "limite_territorial"));
      assertTrue(indexExists(statement, "idx_limite_territorial_geometria_gist"));
      assertTrue(indexExists(statement, "uq_foro_hilo_inspeccion"));

      try (ResultSet limite =
          statement.executeQuery(
              """
                                                                                            SELECT codigo,
                                                                                                   ST_SRID(geometria) AS srid,
                                                                                                   ST_IsValid(geometria) AS valida,
                                                                                                   ST_IsEmpty(geometria) AS vacia
                                                                                            FROM limite_territorial
                                                                                            """)) {
        assertTrue(limite.next());
        assertEquals("GTM", limite.getString("codigo"));
        assertEquals(4326, limite.getInt("srid"));
        assertTrue(limite.getBoolean("valida"));
        assertFalse(limite.getBoolean("vacia"));
      }
      assertTrue(tableExists(statement, "asignatura"));
      assertTrue(tableExists(statement, "puente"));
      assertTrue(columnExists(statement, "puente", "estado_actual"));
      assertTrue(columnExists(statement, "puente", "indice_condicion_actual"));
      assertTrue(columnExists(statement, "puente", "fecha_ultima_inspeccion"));
      assertTrue(columnExists(statement, "puente", "inspeccion_vigente_id"));
      assertTrue(columnExists(statement, "puente", "resultado_ic_vigente_id"));
      assertTrue(columnExists(statement, "puente", "inspeccion_vigente_publicada_en"));
      assertTrue(constraintExists(statement, "puente_estado_actual_valido"));
      assertTrue(constraintExists(statement, "puente_ic_actual_rango"));
      assertTrue(constraintExists(statement, "puente_estado_vigente_completo"));
      assertTrue(constraintExists(statement, "fk_puente_inspeccion_vigente"));
      assertTrue(constraintExists(statement, "fk_puente_resultado_ic_vigente"));

      try (ResultSet estado =
          statement.executeQuery(
              """
                                                                               SELECT column_default, is_nullable
                                                                               FROM information_schema.columns
                                                                               WHERE table_schema = 'public'
                                                                                 AND table_name = 'puente'
                                                                                 AND column_name = 'estado_actual'
                                                                               """)) {
        assertTrue(estado.next());
        assertTrue(estado.getString("column_default").contains("Sin evaluar"));
        assertEquals("NO", estado.getString("is_nullable"));
      }

      assertTrue(tableExists(statement, "aprobacion_calibracion_ic"));
      assertTrue(constraintExists(statement, "uq_aprobacion_calibracion_version"));
      assertTrue(tableExists(statement, "version_formulario"));
      assertTrue(tableExists(statement, "inspeccion"));
      assertTrue(tableExists(statement, "inspeccion_dato_tecnico"));
      assertTrue(tableExists(statement, "revision"));
      assertTrue(columnExists(statement, "observacion_revision", "categoria_fotografica"));
      assertTrue(tableExists(statement, "resultado_ic"));
      assertTrue(indexExists(statement, "uq_resultado_ic_recalculo"));
      assertTrue(tableExists(statement, "confirmacion_estado_inspeccion"));
      for (String columna :
          new String[] {
            "inspeccion_id",
            "version_formulario_id",
            "hash_entrada",
            "version_algoritmo",
            "indice_calculado",
            "estado_calculado",
            "estado_confirmado",
            "justificacion_ajuste",
            "realizado_por_id",
            "realizado_en",
            "calculo"
          }) {
        assertTrue(columnExists(statement, "confirmacion_estado_inspeccion", columna), columna);
      }
      assertTrue(constraintExists(statement, "confirmacion_estado_ic_rango"));
      assertTrue(constraintExists(statement, "confirmacion_estado_valores_validos"));
      assertTrue(constraintExists(statement, "confirmacion_estado_justificacion_coherente"));
      assertTrue(constraintExists(statement, "confirmacion_estado_hash_valido"));
      assertTrue(constraintExists(statement, "confirmacion_estado_algoritmo_no_vacio"));
      assertTrue(constraintExists(statement, "confirmacion_estado_calculo_objeto"));
      assertEquals(0, rowCount(statement, "confirmacion_estado_inspeccion"));

      try (ResultSet claves =
          statement.executeQuery(
              """
                                                     SELECT constraint_type, count(*) AS cantidad
                                                     FROM information_schema.table_constraints
                                                     WHERE table_schema = 'public'
                                                       AND table_name = 'confirmacion_estado_inspeccion'
                                                       AND constraint_type IN ('PRIMARY KEY', 'FOREIGN KEY')
                                                     GROUP BY constraint_type
                                                     """)) {
        int tipos = 0;
        while (claves.next()) {
          int cantidadEsperada = "PRIMARY KEY".equals(claves.getString("constraint_type")) ? 1 : 3;
          assertEquals(cantidadEsperada, claves.getInt("cantidad"));
          tipos++;
        }
        assertEquals(2, tipos);
      }

      assertTrue(tableExists(statement, "archivo"));
      assertTrue(tableExists(statement, "archivo_inspeccion"));
      assertTrue(tableExists(statement, "archivo_evidencia_mantenimiento"));
      assertTrue(tableExists(statement, "archivo_acta_calibracion"));
      assertTrue(tableExists(statement, "orden_mantenimiento"));
      assertTrue(tableExists(statement, "orden_mantenimiento_ejecucion"));
      assertTrue(tableExists(statement, "operacion_idempotente"));
      assertEquals("character varying", tokenHashType(statement));
      assertTrue(columnExists(statement, "curso", "asignatura_id"));
      assertFalse(columnExists(statement, "curso", "seccion"));
      assertTrue(columnExists(statement, "curso_estudiante", "seccion"));
      assertFalse(columnExists(statement, "curso", "codigo"));
      assertFalse(columnExists(statement, "curso", "nombre"));
      assertTrue(columnExists(statement, "usuario_profesional", "numero_colegiado"));
      assertFalse(columnExists(statement, "usuario", "numero_colegiado"));
      assertTrue(columnExists(statement, "inspeccion_dato_tecnico", "longitud_m"));
      assertFalse(columnExists(statement, "inspeccion", "longitud_m"));
      assertTrue(columnExists(statement, "orden_mantenimiento_ejecucion", "fecha_ejecucion"));
      assertFalse(columnExists(statement, "orden_mantenimiento", "fecha_ejecucion"));
      assertTrue(columnExists(statement, "archivo_inspeccion", "inspeccion_id"));
      assertFalse(columnExists(statement, "archivo", "inspeccion_id"));
      assertTrue(indexExists(statement, "uq_version_formulario_activa"));
      assertTrue(indexExists(statement, "uq_revision_abierta_por_inspeccion"));
      assertTrue(indexExists(statement, "uq_resultado_ic_publicacion"));
      assertTrue(indexExists(statement, "uq_inspeccion_sucesora_publicada"));
      assertTrue(indexExists(statement, "uq_operacion_usuario_clave"));
      assertTrue(constraintExists(statement, "archivo_foto_disponible_metadatos_completos"));
      assertTrue(constraintExists(statement, "fk_orden_resultado_misma_inspeccion"));
      assertEquals(22, rowCount(statement, "departamento"));
      assertEquals(340, rowCount(statement, "municipio"));
      assertTrue(municipiosCorrespondenADepartamento(statement));

      assertTrue(tableExists(statement, "municipio_limite"));
      assertEquals(340, rowCount(statement, "municipio_limite"));
      assertTrue(indexExists(statement, "idx_municipio_limite_geometria_gist"));

      try (ResultSet limites =
          statement.executeQuery(
              """
                                                                                            SELECT
                                                                                                NOT EXISTS (
                                                                                                    SELECT 1
                                                                                                    FROM municipio_limite
                                                                                                    WHERE ST_SRID(geometria) <> 4326
                                                                                                       OR NOT ST_IsValid(geometria)
                                                                                                       OR ST_IsEmpty(geometria)
                                                                                                       OR ST_GeometryType(geometria) <> 'ST_MultiPolygon'
                                                                                                ) AS geometrias_validas,
                                                                                                NOT EXISTS (
                                                                                                    SELECT 1
                                                                                                    FROM municipio m
                                                                                                    LEFT JOIN municipio_limite l ON l.municipio_id = m.id
                                                                                                    WHERE l.municipio_id IS NULL
                                                                                                ) AS catalogo_completo
                                                                                            """)) {
        assertTrue(limites.next());
        assertTrue(limites.getBoolean("geometrias_validas"));
        assertTrue(limites.getBoolean("catalogo_completo"));
      }
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

  private boolean columnExists(Statement statement, String table, String column) throws Exception {
    try (ResultSet result =
        statement.executeQuery(
            "SELECT EXISTS ("
                + "SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = 'public' "
                + "AND table_name = '"
                + table
                + "' AND column_name = '"
                + column
                + "')")) {
      result.next();
      return result.getBoolean(1);
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

  private long rowCount(Statement statement, String table) throws Exception {
    try (ResultSet result = statement.executeQuery("SELECT count(*) FROM " + table)) {
      result.next();
      return result.getLong(1);
    }
  }

  private boolean municipiosCorrespondenADepartamento(Statement statement) throws Exception {
    String query =
        """
                            SELECT NOT EXISTS (
                                SELECT 1
                                FROM municipio AS municipio
                                JOIN departamento AS departamento ON departamento.id = municipio.departamento_id
                                WHERE left(municipio.codigo_ine, 2) <> departamento.codigo_ine
                            )
                            """;

    try (ResultSet result = statement.executeQuery(query)) {
      result.next();
      return result.getBoolean(1);
    }
  }
}
