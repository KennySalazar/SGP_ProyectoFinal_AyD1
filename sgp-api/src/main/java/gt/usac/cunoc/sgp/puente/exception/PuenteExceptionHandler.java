package gt.usac.cunoc.sgp.puente.exception;

import gt.usac.cunoc.sgp.common.exception.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "gt.usac.cunoc.sgp.puente.controller")
public class PuenteExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    ProblemDetail problem = validationProblem(request);

    var errores =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    Map.of(
                        "campo",
                        error.getField(),
                        "mensaje",
                        error.getDefaultMessage() == null
                            ? "Valor invalido"
                            : error.getDefaultMessage()))
            .distinct()
            .sorted(
                Comparator.comparing((Map<String, String> error) -> error.get("campo"))
                    .thenComparing(error -> error.get("mensaje")))
            .toList();

    problem.setProperty("errores", errores);
    return response(problem);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolation(
      ConstraintViolationException exception, HttpServletRequest request) {
    ProblemDetail problem = validationProblem(request);

    var errores =
        exception.getConstraintViolations().stream()
            .map(
                error ->
                    Map.of(
                        "campo", error.getPropertyPath().toString(), "mensaje", error.getMessage()))
            .distinct()
            .sorted(
                Comparator.comparing((Map<String, String> error) -> error.get("campo"))
                    .thenComparing(error -> error.get("mensaje")))
            .toList();

    problem.setProperty("errores", errores);
    return response(problem);
  }

  @ExceptionHandler(CercaniaPuenteException.class)
  public ResponseEntity<ProblemDetail> handleCercania(
      CercaniaPuenteException exception, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetails.create(
            409, exception.getTitle(), exception.getMessage(), exception.getCode(), request);

    var cercanos = exception.getCercanos();
    problem.setProperty("requiereConfirmacion", true);
    problem.setProperty("puentesCercanos", cercanos.getContent());
    problem.setProperty("totalPuentesCercanos", cercanos.getTotalElements());
    problem.setProperty("pagina", cercanos.getNumber());
    problem.setProperty("tamanoPagina", cercanos.getSize());
    problem.setProperty("totalPaginas", cercanos.getTotalPages());

    return response(problem);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(
      AccessDeniedException exception, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetails.create(
            403,
            "Acceso denegado",
            "No tiene permisos para realizar esta operacion",
            "access_denied",
            request);
    return response(problem);
  }

  private ProblemDetail validationProblem(HttpServletRequest request) {
    return ProblemDetails.create(
        422,
        "Datos de puente invalidos",
        "La solicitud contiene datos invalidos",
        "validation_error",
        request);
  }

  private ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }
}
