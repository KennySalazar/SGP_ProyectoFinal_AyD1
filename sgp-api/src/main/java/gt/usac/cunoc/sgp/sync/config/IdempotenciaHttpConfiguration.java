package gt.usac.cunoc.sgp.sync.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.sync.http.IdempotenciaHttpFilter;
import gt.usac.cunoc.sgp.sync.service.IdempotenciaHttpService;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class IdempotenciaHttpConfiguration {
  @Bean
  FilterRegistrationBean<IdempotenciaHttpFilter> idempotenciaHttp(
      IdempotenciaHttpService operaciones, ObjectMapper json, PlatformTransactionManager tm) {
    var registro = new FilterRegistrationBean<>(new IdempotenciaHttpFilter(operaciones, json, tm));
    registro.setOrder(-90);
    registro.addUrlPatterns("/api/v1/*");
    return registro;
  }

  @Bean
  OpenApiCustomizer contratoIdempotencia() {
    return api ->
        api.getPaths()
            .forEach(
                (ruta, item) ->
                    item.readOperationsMap()
                        .forEach(
                            (metodo, op) -> {
                              if (!IdempotenciaHttpFilter.protegida(metodo.name(), ruta)) return;
                              boolean declarado =
                                  op.getParameters() != null
                                      && op.getParameters().stream()
                                          .anyMatch(
                                              p -> "Idempotency-Key".equalsIgnoreCase(p.getName()));
                              if (!declarado)
                                op.addParametersItem(
                                    new Parameter()
                                        .in("header")
                                        .name("Idempotency-Key")
                                        .required(false)
                                        .schema(new StringSchema().minLength(1).maxLength(100))
                                        .description(
                                            "Clave por usuario y operacion. Conservarla durante los reintentos durante 7 dias."));
                              if (op.getResponses() == null) op.setResponses(new ApiResponses());
                              var conflicto =
                                  op.getResponses()
                                      .computeIfAbsent("409", codigo -> new ApiResponse());
                              conflicto
                                  .description(
                                      "RFC 7807: operacion en proceso (Retry-After) o version de borrador desactualizada.")
                                  .addHeaderObject(
                                      "Retry-After",
                                      new Header()
                                          .schema(new StringSchema())
                                          .description(
                                              "Espera en segundos para una operacion en proceso."));
                              var distinta =
                                  op.getResponses()
                                      .computeIfAbsent("422", codigo -> new ApiResponse());
                              distinta.description(
                                  "RFC 7807: clave utilizada con otra solicitud o validacion de negocio.");
                              for (var error : java.util.List.of(conflicto, distinta)) {
                                if (error.getContent() == null) error.setContent(new Content());
                                error
                                    .getContent()
                                    .addMediaType(
                                        "application/problem+json",
                                        new MediaType().schema(new ObjectSchema()));
                              }
                              op.getResponses()
                                  .forEach(
                                      (codigo, respuesta) -> {
                                        if (codigo.startsWith("2") || codigo.startsWith("4"))
                                          respuesta.addHeaderObject(
                                              "Idempotent-Replay",
                                              new Header()
                                                  .schema(new StringSchema())
                                                  .description(
                                                      "true cuando se reproduce una respuesta guardada."));
                                      });
                            }));
  }
}
