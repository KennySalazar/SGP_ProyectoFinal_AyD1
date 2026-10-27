package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.inspeccion.dto.ConfirmacionEstadoIcResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.ConfirmarEstadoIcRequest;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfirmacionEstadoIcService {

  private final JdbcTemplate jdbc;
  private final PreparadorEntradaIcService preparador;
  private final CalculoIndiceCondicionService calculador;
  private final AuditoriaWriter auditoria;
  private final ObjectMapper json;
  private final Validator validator;
  private final Clock clock;

  public ConfirmacionEstadoIcService(
      JdbcTemplate jdbc,
      PreparadorEntradaIcService preparador,
      CalculoIndiceCondicionService calculador,
      AuditoriaWriter auditoria,
      ObjectMapper json,
      Validator validator,
      Clock clock) {
    this.jdbc = jdbc;
    this.preparador = preparador;
    this.calculador = calculador;
    this.auditoria = auditoria;
    this.json = json;
    this.validator = validator;
    this.clock = clock;
  }

  @Transactional
  @PreAuthorize("isAuthenticated()")
  public ConfirmacionEstadoIcResponse consultar(UUID inspeccionId) {
    return consultarInterno(inspeccionId, usuarioActual(), null);
  }

  @Transactional
  @PreAuthorize("hasRole('CATEDRATICO')")
  public ConfirmacionEstadoIcResponse consultarRevision(UUID revisionId) {
    UUID actor = usuarioActual();
    return consultarInterno(inspeccionDeRevision(revisionId, actor), actor, revisionId);
  }

  private ConfirmacionEstadoIcResponse consultarInterno(
      UUID inspeccionId, UUID usuarioId, UUID revisionId) {
    Inspeccion inspeccion = cargarYAutorizar(inspeccionId, usuarioId, revisionId);
    ResultadoCalculoIc calculo = calcular(inspeccion);
    JsonNode guardada = cargarConfirmacion(inspeccionId);
    boolean vigente =
        guardada != null
            && usuarioId.toString().equals(guardada.path("realizadoPorId").asText())
            && jsonEquivalente(json.valueToTree(calculo), guardada.get("calculo"));
    return new ConfirmacionEstadoIcResponse(
        inspeccionId,
        calculo,
        vigente ? EstadoCondicion.valueOf(guardada.path("estadoConfirmado").asText()) : null,
        vigente ? textoOpcional(guardada.get("justificacion")) : null,
        vigente ? usuarioId : null,
        vigente ? Instant.parse(guardada.path("realizadoEn").asText()) : null,
        vigente);
  }

  @Transactional
  @PreAuthorize("isAuthenticated()")
  public ConfirmacionEstadoIcResponse confirmar(
      UUID inspeccionId, ConfirmarEstadoIcRequest request) {
    return confirmarInterno(inspeccionId, usuarioActual(), null, request);
  }

  @Transactional
  @PreAuthorize("hasRole('CATEDRATICO')")
  public ConfirmacionEstadoIcResponse confirmarRevision(
      UUID revisionId, ConfirmarEstadoIcRequest request) {
    UUID actor = usuarioActual();
    return confirmarInterno(inspeccionDeRevision(revisionId, actor), actor, revisionId, request);
  }

  private ConfirmacionEstadoIcResponse confirmarInterno(
      UUID inspeccionId, UUID usuarioId, UUID revisionId, ConfirmarEstadoIcRequest request) {
    Inspeccion inspeccion = cargarYAutorizar(inspeccionId, usuarioId, revisionId);
    if (request == null) {
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "confirmacion_ic_invalida",
          "La solicitud de confirmacion es obligatoria.");
    }
    var errores = validator.validate(request);
    if (!errores.isEmpty()) {
      throw new ConstraintViolationException(errores);
    }
    ResultadoCalculoIc calculo = calcular(inspeccion);
    if (!calculo.hashEntrada().equals(request.hashEntrada())) {
      throw error(
          HttpStatus.CONFLICT,
          "calculo_ic_desactualizado",
          "Los datos del calculo cambiaron. Consulta el calculo actual y confirma nuevamente.");
    }
    boolean ajuste = request.estadoConfirmado() != calculo.estadoCalculado();
    String justificacion = request.justificacion() == null ? null : request.justificacion().strip();
    if (ajuste && (justificacion == null || justificacion.isBlank())) {
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "justificacion_ajuste_obligatoria",
          "Cambiar el estado calculado requiere una justificacion escrita.");
    }
    if (!ajuste) {
      justificacion = null;
    }
    JsonNode anteriores = cargarConfirmacion(inspeccionId);
    Instant ahora = clock.instant();
    JsonNode calculoJson = json.valueToTree(calculo);
    jdbc.update(
        """
            INSERT INTO confirmacion_estado_inspeccion (
              inspeccion_id, version_formulario_id, hash_entrada, version_algoritmo,
              indice_calculado, estado_calculado, estado_confirmado, justificacion_ajuste,
              realizado_por_id, realizado_en, calculo
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
            ON CONFLICT (inspeccion_id) DO UPDATE SET
              version_formulario_id = EXCLUDED.version_formulario_id,
              hash_entrada = EXCLUDED.hash_entrada,
              version_algoritmo = EXCLUDED.version_algoritmo,
              indice_calculado = EXCLUDED.indice_calculado,
              estado_calculado = EXCLUDED.estado_calculado,
              estado_confirmado = EXCLUDED.estado_confirmado,
              justificacion_ajuste = EXCLUDED.justificacion_ajuste,
              realizado_por_id = EXCLUDED.realizado_por_id,
              realizado_en = EXCLUDED.realizado_en,
              calculo = EXCLUDED.calculo
            """,
        inspeccionId,
        calculo.versionFormularioId(),
        calculo.hashEntrada(),
        calculo.versionAlgoritmo(),
        calculo.indiceCalculado(),
        calculo.estadoCalculado().name(),
        request.estadoConfirmado().name(),
        justificacion,
        usuarioId,
        OffsetDateTime.ofInstant(ahora, ZoneOffset.UTC),
        calculoJson.toString());
    JsonNode posteriores = cargarConfirmacion(inspeccionId);
    auditoria.registrarEnTransaccion(
        usuarioId,
        anteriores == null ? AccionAuditoria.CREAR : AccionAuditoria.MODIFICAR,
        "confirmacion_estado_inspeccion",
        inspeccionId,
        anteriores,
        posteriores,
        null);
    return new ConfirmacionEstadoIcResponse(
        inspeccionId, calculo, request.estadoConfirmado(), justificacion, usuarioId, ahora, true);
  }

  private ResultadoCalculoIc calcular(Inspeccion inspeccion) {
    return calculador.calcular(
        preparador.preparar(inspeccion.versionFormularioId(), inspeccion.datos()));
  }

  private UUID inspeccionDeRevision(UUID revisionId, UUID actor) {
    var ids =
        jdbc.query(
            "select r.inspeccion_id from revision r join inspeccion i on i.id=r.inspeccion_id "
                + "where r.id=? and r.revisor_id=? and i.eliminado_en is null",
            (rs, n) -> rs.getObject(1, UUID.class),
            revisionId,
            actor);
    if (ids.isEmpty())
      throw error(
          HttpStatus.NOT_FOUND, "revision_no_encontrada", "La revisión no está disponible.");
    return ids.get(0);
  }

  private Inspeccion cargarYAutorizar(UUID id, UUID usuarioId, UUID revisionId) {
    if (id == null) {
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "inspeccion_id_obligatorio",
          "La inspeccion es obligatoria.");
    }
    var encontrados =
        jdbc.query(
            """
            SELECT autor_id, version_formulario_id, estado, datos, eliminado_en
            FROM inspeccion WHERE id = ? FOR UPDATE
            """,
            (rs, numero) ->
                new Inspeccion(
                    rs.getObject("autor_id", UUID.class),
                    rs.getObject("version_formulario_id", UUID.class),
                    rs.getString("estado"),
                    leerJson(rs.getString("datos")),
                    rs.getObject("eliminado_en") != null),
            id);
    if (encontrados.isEmpty()) {
      throw error(HttpStatus.NOT_FOUND, "inspeccion_no_encontrada", "No existe la inspeccion.");
    }
    Inspeccion inspeccion = encontrados.get(0);
    if (revisionId == null && !usuarioId.equals(inspeccion.autorId())) {
      throw error(
          HttpStatus.FORBIDDEN,
          "evaluador_no_responsable",
          "Solo el autor responsable puede confirmar o ajustar el estado.");
    }
    if (revisionId != null
        && (!"EN_REVISION".equals(inspeccion.estado())
            || !Boolean.TRUE.equals(
                jdbc.queryForObject(
                    "select exists(select 1 from revision where id=? and inspeccion_id=? and revisor_id=? and estado='EN_CURSO')",
                    Boolean.class,
                    revisionId,
                    id,
                    usuarioId)))) {
      throw error(
          HttpStatus.CONFLICT,
          "revision_no_editable",
          "La confirmación requiere el ciclo EN_CURSO de esta inspección EN_REVISION.");
    }
    if (inspeccion.eliminada()) {
      throw error(
          HttpStatus.CONFLICT,
          "inspeccion_eliminada",
          "No se puede confirmar una inspeccion eliminada.");
    }
    if ("PUBLICADA".equals(inspeccion.estado())) {
      throw error(
          HttpStatus.CONFLICT,
          "inspeccion_publicada_inmutable",
          "Una inspeccion publicada requiere una nueva inspeccion de correccion.");
    }
    if ("RECHAZADA".equals(inspeccion.estado())) {
      throw error(
          HttpStatus.CONFLICT,
          "inspeccion_no_confirmable",
          "No se puede confirmar una inspeccion rechazada.");
    }
    return inspeccion;
  }

  private JsonNode cargarConfirmacion(UUID id) {
    var filas =
        jdbc.query(
            """
            SELECT estado_confirmado, justificacion_ajuste, realizado_por_id, realizado_en, calculo
            FROM confirmacion_estado_inspeccion WHERE inspeccion_id = ?
            """,
            (rs, numero) -> {
              ObjectNode valor = json.createObjectNode();
              valor.put("inspeccionId", id.toString());
              valor.put("estadoConfirmado", rs.getString("estado_confirmado"));
              valor.put("justificacion", rs.getString("justificacion_ajuste"));
              valor.put("realizadoPorId", rs.getObject("realizado_por_id", UUID.class).toString());
              valor.put(
                  "realizadoEn",
                  rs.getObject("realizado_en", OffsetDateTime.class).toInstant().toString());
              valor.set("calculo", leerJson(rs.getString("calculo")));
              return valor;
            },
            id);
    return filas.isEmpty() ? null : filas.get(0);
  }

  private UUID usuarioActual() {
    var autenticacion = SecurityContextHolder.getContext().getAuthentication();
    if (autenticacion == null
        || !autenticacion.isAuthenticated()
        || !(autenticacion.getDetails() instanceof JwtData jwt)) {
      throw error(
          HttpStatus.UNAUTHORIZED, "authentication_required", "Se requiere una sesion valida.");
    }
    return jwt.userId();
  }

  private boolean jsonEquivalente(JsonNode esperado, JsonNode guardado) {
    if (esperado == null || guardado == null) {
      return esperado == guardado;
    }
    if (esperado.isNumber() && guardado.isNumber()) {
      return esperado.decimalValue().compareTo(guardado.decimalValue()) == 0;
    }
    if (esperado.isObject() && guardado.isObject()) {
      if (esperado.size() != guardado.size()) {
        return false;
      }
      var campos = esperado.fields();
      while (campos.hasNext()) {
        var campo = campos.next();
        if (!jsonEquivalente(campo.getValue(), guardado.get(campo.getKey()))) {
          return false;
        }
      }
      return true;
    }
    if (esperado.isArray() && guardado.isArray()) {
      if (esperado.size() != guardado.size()) {
        return false;
      }
      for (int indice = 0; indice < esperado.size(); indice++) {
        if (!jsonEquivalente(esperado.get(indice), guardado.get(indice))) {
          return false;
        }
      }
      return true;
    }
    return esperado.equals(guardado);
  }

  private JsonNode leerJson(String contenido) {
    try {
      return json.readTree(contenido);
    } catch (JsonProcessingException excepcion) {
      throw new IllegalStateException("Los datos almacenados no contienen JSON valido.", excepcion);
    }
  }

  private String textoOpcional(JsonNode valor) {
    return valor == null || valor.isNull() ? null : valor.asText();
  }

  private ApiException error(HttpStatus estado, String codigo, String detalle) {
    return new ApiException(estado, codigo, "Confirmacion del estado de la inspeccion", detalle);
  }

  private record Inspeccion(
      UUID autorId, UUID versionFormularioId, String estado, JsonNode datos, boolean eliminada) {}
}
