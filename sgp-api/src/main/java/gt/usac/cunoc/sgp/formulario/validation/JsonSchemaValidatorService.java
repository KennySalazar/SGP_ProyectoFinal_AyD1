package gt.usac.cunoc.sgp.formulario.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JsonSchemaValidatorService {

  private final ConcurrentMap<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

  public void validarEsquema(JsonNode esquema) {
    if (esquema == null || !esquema.isObject() || esquema.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "esquema_invalido",
          "Esquema inválido",
          "El esquema debe ser un objeto JSON válido y no puede estar vacío");
    }

    String dialecto =
        esquema.hasNonNull("$schema")
            ? esquema.get("$schema").asText()
            : "https://json-schema.org/draft/2020-12/schema";

    SpecVersion.VersionFlag versionFlag =
        dialecto.contains("draft-07")
            ? SpecVersion.VersionFlag.V7
            : SpecVersion.VersionFlag.V202012;

    try {
      JsonSchemaFactory factory = JsonSchemaFactory.getInstance(versionFlag);
      JsonSchema metaSchema = factory.getSchema(SchemaLocation.of(dialecto));
      Set<ValidationMessage> errores = metaSchema.validate(esquema);

      if (!errores.isEmpty()) {
        ValidationMessage primerError = errores.iterator().next();
        String ruta = primerError.getInstanceLocation().toString();
        if (ruta.isBlank()) {
          ruta = primerError.getEvaluationPath().toString();
        }
        if (ruta.isBlank()) {
          ruta = "/";
        }
        String motivo = primerError.getMessage();
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "esquema_invalido",
            "Esquema inválido",
            "Error en la ruta '" + ruta + "': " + motivo);
      }

      // Probar compilación del esquema
      factory.getSchema(esquema);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "esquema_invalido",
          "Esquema inválido",
          "Error de estructura JSON Schema: " + e.getMessage());
    }
  }

  public List<ErrorValidacionCampo> obtenerErroresValidacion(
      JsonNode esquema, JsonNode datos, boolean esBorrador) {
    if (datos == null || !datos.isObject()) {
      return List.of(
          new ErrorValidacionCampo(
              "datos", "Los datos de la inspección deben ser un objeto JSON válido"));
    }

    JsonSchema validador = compilarEsquema(esquema, esBorrador);
    Set<ValidationMessage> mensajes = validador.validate(datos);
    if (mensajes.isEmpty()) {
      return List.of();
    }

    return mensajes.stream()
        .map(this::convertirAErrorValidacion)
        .distinct()
        .sorted(
            Comparator.comparing(ErrorValidacionCampo::campo)
                .thenComparing(ErrorValidacionCampo::mensaje))
        .toList();
  }

  public void validarRespuestas(JsonNode esquema, JsonNode datos, boolean esBorrador) {
    List<ErrorValidacionCampo> errores = obtenerErroresValidacion(esquema, datos, esBorrador);
    if (!errores.isEmpty()) {
      List<Map<String, String>> erroresMap =
          errores.stream()
              .map(e -> Map.of("campo", e.campo(), "mensaje", e.mensaje()))
              .toList();
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "datos_formulario_invalidos",
          "Respuestas de inspección inválidas",
          "Los datos de la inspección no cumplen con el esquema de la versión del formulario",
          erroresMap);
    }
  }

  public JsonSchema compilarEsquema(JsonNode esquema, boolean esBorrador) {
    if (esquema == null || !esquema.isObject()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "esquema_invalido",
          "Esquema inválido",
          "El esquema debe ser un objeto JSON válido");
    }

    String dialecto =
        esquema.hasNonNull("$schema")
            ? esquema.get("$schema").asText()
            : "https://json-schema.org/draft/2020-12/schema";

    SpecVersion.VersionFlag versionFlag =
        dialecto.contains("draft-07")
            ? SpecVersion.VersionFlag.V7
            : SpecVersion.VersionFlag.V202012;

    JsonNode esquemaEfectivo = esBorrador ? crearEsquemaBorrador(esquema) : esquema;
    String cacheKey = calcularHash(esquemaEfectivo) + ":" + (esBorrador ? "draft" : "strict");

    return schemaCache.computeIfAbsent(
        cacheKey,
        k -> {
          JsonSchemaFactory factory = JsonSchemaFactory.getInstance(versionFlag);
          return factory.getSchema(esquemaEfectivo);
        });
  }

  public JsonNode crearEsquemaBorrador(JsonNode esquema) {
    if (esquema == null || !esquema.isObject()) {
      return esquema;
    }
    JsonNode copia = esquema.deepCopy();
    removerRestriccionesObligatorias(copia);
    return copia;
  }

  private void removerRestriccionesObligatorias(JsonNode nodo) {
    if (nodo == null || !nodo.isContainerNode()) {
      return;
    }
    if (nodo.isObject()) {
      ObjectNode obj = (ObjectNode) nodo;
      obj.remove("required");
      obj.remove("minItems");
      Iterator<JsonNode> elementos = obj.elements();
      while (elementos.hasNext()) {
        removerRestriccionesObligatorias(elementos.next());
      }
    } else if (nodo.isArray()) {
      ArrayNode arr = (ArrayNode) nodo;
      for (JsonNode item : arr) {
        removerRestriccionesObligatorias(item);
      }
    }
  }

  private ErrorValidacionCampo convertirAErrorValidacion(ValidationMessage msg) {
    String tipo = msg.getType();
    Object[] args = msg.getArguments();
    String campo = formatearRuta(msg.getInstanceLocation().toString(), tipo, args);
    String mensaje = construirMensajeError(tipo, campo, msg, args);
    return new ErrorValidacionCampo(campo, mensaje);
  }

  private String formatearRuta(String instanceLocation, String tipo, Object[] args) {
    String ruta = instanceLocation == null ? "" : instanceLocation.trim();
    if (ruta.startsWith("$.")) {
      ruta = ruta.substring(2);
    } else if (ruta.startsWith("$")) {
      ruta = ruta.substring(1);
    }
    if (ruta.startsWith("/")) {
      ruta = ruta.substring(1);
    }
    ruta = ruta.replace('/', '.');
    ruta = ruta.replaceAll("\\.(\\d+)", "[$1]");
    if (ruta.matches("^\\d+.*")) {
      ruta = ruta.replaceFirst("^(\\d+)", "[$1]");
    }

    if ("required".equalsIgnoreCase(tipo) && args != null && args.length > 0) {
      String prop = String.valueOf(args[0]);
      ruta = ruta.isEmpty() ? prop : (ruta.endsWith("]") ? ruta + "." + prop : ruta + "." + prop);
    } else if ("additionalProperties".equalsIgnoreCase(tipo) && args != null && args.length > 0) {
      String prop = String.valueOf(args[0]);
      if (!ruta.endsWith(prop)) {
        ruta = ruta.isEmpty() ? prop : (ruta.endsWith("]") ? ruta + "." + prop : ruta + "." + prop);
      }
    }

    return ruta.isEmpty() ? "raiz" : ruta;
  }

  private String construirMensajeError(
      String tipo, String campo, ValidationMessage msg, Object[] args) {
    return switch (tipo == null ? "" : tipo.toLowerCase()) {
      case "required" -> "El campo '" + campo + "' es obligatorio";
      case "type" -> {
        if (args != null && args.length >= 2) {
          yield "Tipo de dato inválido para '"
              + campo
              + "': se esperaba "
              + args[0]
              + ", pero se recibió "
              + args[1];
        }
        yield "Tipo de dato inválido para '" + campo + "'";
      }
      case "enum" -> {
        if (args != null && args.length >= 1) {
          yield "Valor no permitido para el campo '" + campo + "'. Valores válidos: " + args[0];
        }
        yield "Valor no permitido para el campo '" + campo + "'";
      }
      case "additionalproperties" -> "El campo '" + campo + "' no está permitido en el esquema";
      case "minimum" -> "El valor del campo '" + campo + "' es menor que el mínimo permitido";
      case "maximum" -> "El valor del campo '" + campo + "' es mayor que el máximo permitido";
      case "minlength" -> "La longitud del campo '" + campo + "' es menor que la mínima requerida";
      case "maxlength" -> "La longitud del campo '" + campo + "' es mayor que la máxima permitida";
      case "minitems" -> "La lista '" + campo + "' contiene menos elementos de los requeridos";
      case "maxitems" -> "La lista '" + campo + "' contiene más elementos de los permitidos";
      case "pattern", "format" -> "El formato del campo '" + campo + "' no es válido";
      default -> {
        String original = msg.getMessage();
        yield original != null && !original.isBlank()
            ? original
            : "El valor del campo '" + campo + "' no es válido";
      }
    };
  }

  private String calcularHash(JsonNode nodo) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(nodo.toString().getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      return String.valueOf(nodo.hashCode());
    }
  }
}
