package gt.usac.cunoc.sgp.sync.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.sync.dto.UltimaPreparacionResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PreparacionSalidaRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public PreparacionSalidaRepository(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public void registrar(
      UUID id, UUID usuario, String dispositivo, UUID version, JsonNode contexto, Instant ahora) {
    jdbc.update(
        """
                INSERT INTO preparacion_salida
                  (id, usuario_id, dispositivo_id, version_formulario_id, contexto_autorizado,
                   preparado_en, creado_en)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                """,
        id,
        usuario,
        dispositivo,
        version,
        contexto.toString(),
        java.sql.Timestamp.from(ahora),
        java.sql.Timestamp.from(ahora));
  }

  public Optional<UltimaPreparacionResponse> ultima(UUID usuario, String dispositivo) {
    return jdbc
        .query(
            """
                    SELECT id, dispositivo_id, version_formulario_id, preparado_en, contexto_autorizado
                    FROM preparacion_salida WHERE usuario_id = ? AND dispositivo_id = ?
                    ORDER BY preparado_en DESC, id DESC LIMIT 1
                    """,
            (rs, fila) -> {
              try {
                return new UltimaPreparacionResponse(
                    rs.getObject("id", UUID.class),
                    rs.getString("dispositivo_id"),
                    rs.getObject("version_formulario_id", UUID.class),
                    rs.getTimestamp("preparado_en").toInstant(),
                    json.readTree(rs.getString("contexto_autorizado")));
              } catch (java.io.IOException e) {
                throw new IllegalStateException("No se pudo leer la preparacion", e);
              }
            },
            usuario,
            dispositivo)
        .stream()
        .findFirst();
  }
}
