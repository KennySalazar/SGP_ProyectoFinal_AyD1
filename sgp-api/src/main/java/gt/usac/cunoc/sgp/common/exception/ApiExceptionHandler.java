
package gt.usac.cunoc.sgp.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetails.create(exception.getStatus().value(), exception.getTitle(), exception.getMessage(), exception.getCode(), request);
        return response(problem, exception.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetails.create(400, "Solicitud invalida", "La solicitud contiene datos invalidos", "validation_error", request);
        List<Object> errores = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> java.util.Map.of("campo", error.getField(), "mensaje", error.getDefaultMessage() == null ? "Invalido" : error.getDefaultMessage()))
            .map(value -> (Object) value)
            .toList();
        problem.setProperty("errores", errores);
        return response(problem, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetails.create(409, "Conflicto de datos", "La operacion entra en conflicto con los datos existentes", "data_conflict", request);
        return response(problem, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected API failure", exception);
        ProblemDetail problem = ProblemDetails.create(500, "Error interno", "Ocurrio un error interno", "internal_error", request);
        return response(problem, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ProblemDetail> response(ProblemDetail problem, HttpStatus status) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
