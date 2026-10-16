package gt.usac.cunoc.sgp.sync.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.sync.entity.OperacionIdempotente;
import gt.usac.cunoc.sgp.sync.model.*;
import gt.usac.cunoc.sgp.sync.repository.OperacionIdempotenteRepository;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class OperacionIdempotenteService {
  private final OperacionIdempotenteRepository repositorio;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final Clock clock;

  public OperacionIdempotenteService(
      OperacionIdempotenteRepository repositorio,
      JdbcTemplate jdbc,
      ObjectMapper json,
      Clock clock) {
    this.repositorio = repositorio;
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
  }

  private void bloquear(SolicitudOperacion s) {
    bloquear("operacion:" + s.usuarioId() + ":" + s.clave());
  }

  private void bloquear(String identidad) {
    jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))", rs -> {}, identidad);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void bloquearEntidad(String entidad, UUID id) {
    bloquear("entidad:" + entidad + ":" + id);
  }

  private void comprobar(OperacionIdempotente o, SolicitudOperacion s) {
    if (!o.getMetodo().equals(s.metodo())
        || !o.getRuta().equals(s.ruta())
        || !o.getHashSolicitud().equals(s.hashSolicitud())
        || !Objects.equals(o.getEntidad(), s.entidad())
        || (s.entidadId() != null && !s.entidadId().equals(o.getEntidadId())))
      throw conflicto("idempotencia_solicitud_distinta");
  }

  private ApiException conflicto(String code) {
    return new ApiException(
        HttpStatus.CONFLICT,
        code,
        "Conflicto de operación",
        "La clave o el intento no corresponden a una operación disponible con esta solicitud.");
  }

  private OperacionIdempotente nueva(SolicitudOperacion s) {
    Instant ahora = clock.instant();
    return new OperacionIdempotente(
        UuidV7Generator.generate(),
        s.usuarioId(),
        s.clave(),
        s.metodo(),
        s.ruta(),
        s.hashSolicitud(),
        "EN_PROCESO",
        null,
        null,
        s.entidad(),
        s.entidadId(),
        null,
        ahora.plus(Duration.ofMinutes(15)),
        ahora);
  }

  /**
   * Operación exclusivamente de BD: reserva, acción y respuesta se confirman o revierten juntas.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public <T> T ejecutar(
      SolicitudOperacion s,
      Class<T> tipo,
      int codigo,
      Supplier<T> accion,
      Function<T, UUID> destino) {
    return ejecutar(s, codigo, accion, destino, nodo -> json.convertValue(nodo, tipo));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public <T> T ejecutar(
      SolicitudOperacion s,
      int codigo,
      Supplier<T> accion,
      Function<T, UUID> destino,
      Function<JsonNode, T> reproducir) {
    bloquear(s);
    var existente = repositorio.findByUsuarioIdAndClave(s.usuarioId(), s.clave());
    if (existente.isPresent()) {
      var op = existente.get();
      comprobar(op, s);
      if (!"COMPLETADA".equals(op.getEstado())) throw conflicto("operacion_en_proceso");
      return reproducir.apply(op.getRespuesta().deepCopy());
    }
    var op = repositorio.saveAndFlush(nueva(s));
    T resultado = accion.get();
    op.completar(json.valueToTree(resultado), codigo, destino.apply(resultado), clock.instant());
    return resultado;
  }

  /** Reserva duradera para transferencias externas. El JSON conserva las claves del intento. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ReservaOperacion reservar(SolicitudOperacion s, JsonNode recursos) {
    bloquear(s);
    var op = repositorio.findByUsuarioIdAndClave(s.usuarioId(), s.clave()).orElse(null);
    if (op != null) {
      comprobar(op, s);
      if ("COMPLETADA".equals(op.getEstado()))
        return new ReservaOperacion(op.getId(), null, op.getExpiraEn(), true, op.getRespuesta());
      if (op.getRespuesta() == null || !op.getRespuesta().path("recuperada").asBoolean(false))
        throw conflicto("operacion_en_proceso");
    } else {
      op = nueva(s);
    }
    UUID intento = UUID.randomUUID();
    ObjectNode manifiesto =
        json.createObjectNode().put("intentoId", intento.toString()).put("recuperada", false);
    manifiesto.set("recursos", recursos.deepCopy());
    op.iniciarIntento(manifiesto, clock.instant().plus(Duration.ofMinutes(15)));
    repositorio.saveAndFlush(op);
    return new ReservaOperacion(op.getId(), intento, op.getExpiraEn(), false, null);
  }

  /** El llamador guarda las asociaciones de negocio en esta misma transacción. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void completar(ReservaOperacion reserva, JsonNode respuesta, int codigo, UUID entidadId) {
    var op =
        repositorio
            .findByIdForUpdate(reserva.operacionId())
            .orElseThrow(() -> conflicto("operacion_no_encontrada"));
    if (!"EN_PROCESO".equals(op.getEstado())
        || op.getExpiraEn() == null
        || !op.getExpiraEn().isAfter(clock.instant())
        || !Objects.equals(
            op.getRespuesta().path("intentoId").asText(),
            reserva.intentoId() == null ? null : reserva.intentoId().toString())
        || op.getRespuesta().path("recuperada").asBoolean()
        || (op.getEntidadId() != null && !op.getEntidadId().equals(entidadId)))
      throw conflicto("intento_no_vigente");
    op.completar(respuesta, codigo, entidadId, clock.instant());
  }

  @Transactional(readOnly = true)
  public List<UUID> vencidas() {
    return repositorio.findVencidas(clock.instant());
  }

  /** La limpieza falla con excepción: se conserva el manifiesto para volver a intentarlo. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean recuperar(UUID id, Consumer<JsonNode> limpiar) {
    var op =
        repositorio.findByIdForUpdate(id).orElseThrow(() -> conflicto("operacion_no_encontrada"));
    if (!"EN_PROCESO".equals(op.getEstado())
        || op.getExpiraEn() == null
        || op.getExpiraEn().isAfter(clock.instant())) return false;
    if (op.getRespuesta() == null || !op.getRespuesta().has("recursos")) return false;
    limpiar.accept(op.getRespuesta().get("recursos").deepCopy());
    var manifiesto = (ObjectNode) op.getRespuesta().deepCopy();
    manifiesto.put("recuperada", true);
    op.marcarRecuperada(manifiesto);
    return true;
  }

  @Transactional(readOnly = true)
  public Optional<JsonNode> buscarCompletada(SolicitudOperacion solicitud) {
    return repositorio
        .findByUsuarioIdAndClave(solicitud.usuarioId(), solicitud.clave())
        .map(
            op -> {
              comprobar(op, solicitud);
              return "COMPLETADA".equals(op.getEstado()) ? op.getRespuesta().deepCopy() : null;
            });
  }

  /** Solo después de compensar los objetos del intento propio; permite reintento inmediato. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void liberar(ReservaOperacion reserva) {
    var op =
        repositorio
            .findByIdForUpdate(reserva.operacionId())
            .orElseThrow(() -> conflicto("operacion_no_encontrada"));
    if ("EN_PROCESO".equals(op.getEstado())
        && Objects.equals(
            op.getRespuesta().path("intentoId").asText(), reserva.intentoId().toString())) {
      var manifiesto = (ObjectNode) op.getRespuesta().deepCopy();
      manifiesto.put("recuperada", true);
      op.marcarRecuperada(manifiesto);
    }
  }

  /**
   * Comprueba el resultado bajo bloqueo antes de eliminar objetos, incluso si el commit fue
   * ambiguo.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean compensar(ReservaOperacion reserva, Runnable limpieza) {
    var op =
        repositorio
            .findByIdForUpdate(reserva.operacionId())
            .orElseThrow(() -> conflicto("operacion_no_encontrada"));
    if (!"EN_PROCESO".equals(op.getEstado())
        || !Objects.equals(
            op.getRespuesta().path("intentoId").asText(), reserva.intentoId().toString()))
      return false;
    limpieza.run();
    var manifiesto = (ObjectNode) op.getRespuesta().deepCopy();
    manifiesto.put("recuperada", true);
    op.marcarRecuperada(manifiesto);
    return true;
  }
}
