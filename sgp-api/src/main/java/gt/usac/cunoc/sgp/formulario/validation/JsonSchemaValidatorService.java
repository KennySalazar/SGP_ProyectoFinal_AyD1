package gt.usac.cunoc.sgp.formulario.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JsonSchemaValidatorService {

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
}
