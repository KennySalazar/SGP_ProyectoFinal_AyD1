package gt.usac.cunoc.sgp.sync.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OperacionHttpRepository {
  private final JdbcTemplate jdbc;

  public OperacionHttpRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean bloquear(UUID usuario, String clave) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
            Boolean.class,
            "operacion:" + usuario + ":" + clave));
  }

  public List<Map<String, Object>> buscar(UUID usuario, String clave) {
    return jdbc.queryForList(
        "select id,metodo,ruta,hash_solicitud,estado,codigo_respuesta,respuesta,entidad "
            + "from operacion_idempotente where usuario_id=? and clave=?",
        usuario,
        clave);
  }

  public void insertar(
      UUID id,
      UUID usuario,
      String clave,
      String metodo,
      String ruta,
      String huella,
      Instant ahora,
      Instant expira) {
    jdbc.update(
        "insert into operacion_idempotente "
            + "(id,usuario_id,clave,metodo,ruta,hash_solicitud,estado,entidad,creado_en,expira_en) "
            + "values (?,?,?,?,?,?,'EN_PROCESO','http_hu043',?,?)",
        id,
        usuario,
        clave,
        metodo,
        ruta,
        huella,
        Timestamp.from(ahora),
        Timestamp.from(expira));
  }

  public int completar(UUID id, UUID usuario, int codigo, String respuesta, Instant ahora) {
    return jdbc.update(
        "update operacion_idempotente set estado='COMPLETADA',codigo_respuesta=?,"
            + "respuesta=?::jsonb,completado_en=? where id=? and usuario_id=? and estado='EN_PROCESO'",
        codigo,
        respuesta,
        Timestamp.from(ahora),
        id,
        usuario);
  }

  public void liberar(UUID id) {
    jdbc.update(
        "delete from operacion_idempotente where id=? and entidad='http_hu043' and estado='EN_PROCESO'",
        id);
  }
}
