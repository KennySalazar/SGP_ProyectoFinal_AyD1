package gt.usac.cunoc.sgp.revision.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import gt.usac.cunoc.sgp.common.exception.ProblemDetails;
import gt.usac.cunoc.sgp.revision.controller.ObservacionController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Solo HU-037: preserva el tratamiento de errores de las demás historias. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ObservacionController.class)
public class ObservacionExceptionHandler {
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> cuerpoInvalido(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    String campo = "solicitud";
    Throwable causa = exception.getCause();
    if (causa instanceof JsonMappingException mapping) {
      String ruta =
          mapping.getPath().stream()
              .map(JsonMappingException.Reference::getFieldName)
              .filter(java.util.Objects::nonNull)
              .collect(Collectors.joining("."));
      if (!ruta.isEmpty()) campo = ruta;
    }
    var problem =
        ProblemDetails.create(
            422,
            "Observación inválida",
            "El cuerpo o sus campos tienen un formato inválido.",
            "observacion_invalida",
            request);
    problem.setProperty(
        "errores",
        List.of(Map.of("campo", campo, "mensaje", "Valor inválido o solicitud ausente")));
    return ResponseEntity.unprocessableEntity()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }
}
