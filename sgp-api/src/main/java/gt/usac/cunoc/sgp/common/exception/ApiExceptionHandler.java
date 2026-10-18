package gt.usac.cunoc.sgp.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> handleApi(ApiException exception, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetails.create(
            exception.getStatus().value(),
            exception.getTitle(),
            exception.getMessage(),
            exception.getCode(),
            request);
    if (exception.getErrores() != null && !exception.getErrores().isEmpty()) {
      problem.setProperty("errores", exception.getErrores());
    }
    return response(problem, exception.getStatus());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetails.create(
            400,
            "Solicitud invalida",
            "La solicitud contiene datos invalidos",
            "validation_error",
            request);
    List<Object> errores =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    java.util.Map.of(
                        "campo",
                        error.getField(),
                        "mensaje",
                        error.getDefaultMessage() == null ? "Invalido" : error.getDefaultMessage()))
            .map(value -> (Object) value)
            .toList();
    problem.setProperty("errores", errores);
    return response(problem, HttpStatus.BAD_REQUEST);
  }

  /** Un parámetro de ruta o consulta con formato inválido (por ejemplo un UUID mal formado). */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ProblemDetail> handleTipoInvalido(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetails.create(
            400,
            "Solicitud invalida",
            "La solicitud contiene datos invalidos",
            "validation_error",
            request);
    problem.setProperty(
        "errores",
        List.of(java.util.Map.of("campo", exception.getName(), "mensaje", "Valor invalido")));
    return response(problem, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> handleConflict(
      DataIntegrityViolationException exception, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetails.create(
            409,
            "Conflicto de datos",
            "La operacion entra en conflicto con los datos existentes",
            "data_conflict",
            request);
    return response(problem, HttpStatus.CONFLICT);
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> handleOptimisticLock(
      OptimisticLockingFailureException exception, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetails.create(
            409,
            "Conflicto de concurrencia",
            "La inspección fue modificada concurrentemente por otra sesión o dispositivo",
            "concurrencia_conflicto",
            request);
    return response(problem, HttpStatus.CONFLICT);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> handleAccessDenied(
      AccessDeniedException exception, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetails.create(
            403,
            "Acceso denegado",
            "No tiene permisos para realizar esta operacion",
            "access_denied",
            request);
    return response(problem, HttpStatus.FORBIDDEN);
  }

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> handleTamano(Exception e, HttpServletRequest request) {
    return response(
        ProblemDetails.create(
            413,
            "Carga demasiado grande",
            "El archivo o la petición exceden el tamaño permitido",
            "carga_demasiado_grande",
            request),
        HttpStatus.PAYLOAD_TOO_LARGE);
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.multipart.support.MissingServletRequestPartException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class
  })
  ResponseEntity<ProblemDetail> handleMultipart(Exception e, HttpServletRequest request) {
    return response(
        ProblemDetails.create(
            400,
            "Solicitud inválida",
            "Faltan partes o los metadatos no tienen un formato válido",
            "validation_error",
            request),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
    LOGGER.error("Unexpected API failure", exception);
    ProblemDetail problem =
        ProblemDetails.create(
            500, "Error interno", "Ocurrio un error interno", "internal_error", request);
    return response(problem, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private ResponseEntity<ProblemDetail> response(ProblemDetail problem, HttpStatus status) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }
}
