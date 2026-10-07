package gt.usac.cunoc.sgp.usuario.exception;

import gt.usac.cunoc.sgp.common.exception.ProblemDetails;
import gt.usac.cunoc.sgp.usuario.controller.InvitacionController;
import gt.usac.cunoc.sgp.usuario.controller.ProfesionalController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errores de entrada de invitaciones y profesionales que el manejador global trataría como 500:
 * cuerpo ilegible (por ejemplo, un rol inexistente) y parámetros con tipo inválido.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {InvitacionController.class, ProfesionalController.class})
public class InvitacionExceptionHandler {

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleCuerpoIlegible(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    return badRequest(
        request,
        "solicitud",
        "El cuerpo de la solicitud es obligatorio o contiene datos invalidos");
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ProblemDetail> handleParametroInvalido(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    return badRequest(request, exception.getName(), "Valor invalido");
  }

  private ResponseEntity<ProblemDetail> badRequest(
      HttpServletRequest request, String campo, String mensaje) {
    ProblemDetail problem =
        ProblemDetails.create(
            400,
            "Solicitud invalida",
            "La solicitud contiene datos invalidos",
            "validation_error",
            request);
    problem.setProperty("errores", List.of(Map.of("campo", campo, "mensaje", mensaje)));
    return ResponseEntity.status(400).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
  }
}
