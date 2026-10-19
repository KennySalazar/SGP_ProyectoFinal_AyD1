package gt.usac.cunoc.sgp.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.sync.http.ReservaIdempotente;
import gt.usac.cunoc.sgp.sync.http.RespuestaIdempotente;
import gt.usac.cunoc.sgp.sync.repository.OperacionHttpRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotenciaHttpService {
  private static final Logger LOGGER = LoggerFactory.getLogger(IdempotenciaHttpService.class);
  private final OperacionHttpRepository repositorio;
  private final ObjectMapper json;
  private final Clock clock;
  private final AuditoriaWriter auditoria;

  public IdempotenciaHttpService(
      OperacionHttpRepository repositorio,
      ObjectMapper json,
      Clock clock,
      AuditoriaWriter auditoria) {
    this.repositorio = repositorio;
    this.json = json;
    this.clock = clock;
    this.auditoria = auditoria;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ReservaIdempotente reservar(
      UUID usuario, String clave, String metodo, String ruta, String huella) {
    if (!repositorio.bloquear(usuario, clave)) throw enProceso();
    var filas = repositorio.buscar(usuario, clave);
    if (!filas.isEmpty()) {
      var fila = filas.getFirst();
      if (!metodo.equals(fila.get("metodo"))
          || !ruta.equals(fila.get("ruta"))
          || !huella.equals(fila.get("hash_solicitud"))
          || !"http_hu043".equals(fila.get("entidad"))) {
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "idempotencia_solicitud_distinta",
            "Clave utilizada",
            "La clave ya se uso con otra solicitud. Utilice una clave nueva.");
      }
      if (!"COMPLETADA".equals(fila.get("estado"))) throw enProceso();
      try {
        JsonNode valor = json.readTree(fila.get("respuesta").toString());
        var encabezados = new LinkedHashMap<String, String>();
        valor
            .path("encabezados")
            .fields()
            .forEachRemaining(e -> encabezados.put(e.getKey(), e.getValue().asText()));
        return new ReservaIdempotente(
            (UUID) fila.get("id"),
            new RespuestaIdempotente(
                ((Number) fila.get("codigo_respuesta")).intValue(),
                Base64.getDecoder().decode(valor.path("cuerpoBase64").asText()),
                encabezados));
      } catch (java.io.IOException | IllegalArgumentException e) {
        throw new IllegalStateException("No se pudo recuperar la respuesta idempotente", e);
      }
    }
    UUID id = UuidV7Generator.generate();
    var ahora = clock.instant();
    repositorio.insertar(
        id, usuario, clave, metodo, ruta, huella, ahora, ahora.plus(Duration.ofDays(7)));
    return new ReservaIdempotente(id, null);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void completar(UUID id, UUID usuario, String clave, RespuestaIdempotente respuesta) {
    var valor = json.createObjectNode();
    valor.put("cuerpoBase64", Base64.getEncoder().encodeToString(respuesta.cuerpo()));
    valor.set("encabezados", json.valueToTree(respuesta.encabezados()));
    int filas =
        repositorio.completar(id, usuario, respuesta.codigo(), valor.toString(), clock.instant());
    if (filas != 1) throw new IllegalStateException("La reserva idempotente no esta disponible");
    auditoria.registrarEnTransaccion(
        usuario,
        AccionAuditoria.MODIFICAR,
        "operacion_idempotente",
        id,
        null,
        json.createObjectNode().put("clave", clave).put("codigo", respuesta.codigo()),
        "HU-043");
    LOGGER.info("Idempotencia usuario={} clave={} codigo={}", usuario, clave, respuesta.codigo());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void completarError(UUID id, UUID usuario, String clave, RespuestaIdempotente respuesta) {
    completar(id, usuario, clave, respuesta);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void liberar(UUID id) {
    repositorio.liberar(id);
  }

  private ApiException enProceso() {
    return new ApiException(
        HttpStatus.CONFLICT,
        "operacion_en_proceso",
        "Operacion en proceso",
        "La operacion sigue en proceso. Reintente con la misma clave.");
  }
}
