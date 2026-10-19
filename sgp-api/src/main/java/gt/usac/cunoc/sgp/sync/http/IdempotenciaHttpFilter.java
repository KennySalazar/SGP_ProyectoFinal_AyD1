package gt.usac.cunoc.sgp.sync.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.security.JwtData;
import gt.usac.cunoc.sgp.sync.service.HuellaSolicitud;
import gt.usac.cunoc.sgp.sync.service.IdempotenciaHttpService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

public class IdempotenciaHttpFilter extends OncePerRequestFilter {
  private final IdempotenciaHttpService operaciones;
  private final ObjectMapper json;
  private final TransactionTemplate transaccion;

  public IdempotenciaHttpFilter(
      IdempotenciaHttpService operaciones, ObjectMapper json, PlatformTransactionManager tm) {
    this.operaciones = operaciones;
    this.json = json;
    this.transaccion = new TransactionTemplate(tm);
  }

  public static boolean protegida(String metodo, String ruta) {
    if ("POST".equals(metodo)) {
      return ruta.equals("/api/v1/inspecciones")
          || ruta.equals("/api/v1/inspecciones/iniciar")
          || ruta.matches("/api/v1/inspecciones/[^/]+/(enviar|reenviar)")
          || ruta.equals("/api/v1/preparaciones-salida");
    }
    return "PUT".equals(metodo) && ruta.matches("/api/v1/inspecciones/[^/]+(/(borrador|datos))?");
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !protegida(request.getMethod(), request.getServletPath())
        || request.getHeader("Idempotency-Key") == null;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || !(auth.getDetails() instanceof JwtData jwt)) {
      chain.doFilter(request, response);
      return;
    }
    String clave = request.getHeader("Idempotency-Key");
    if (!clave.matches("[!-~]{1,100}")
        || java.util.Collections.list(request.getHeaders("Idempotency-Key")).size() != 1) {
      problema(
          request,
          response,
          new ApiException(
              HttpStatus.BAD_REQUEST,
              "clave_idempotente_invalida",
              "Clave invalida",
              "Idempotency-Key debe contener entre 1 y 100 caracteres ASCII visibles."));
      return;
    }
    byte[] cuerpo = request.getInputStream().readAllBytes();
    String huella;
    try {
      var nodo = json.createObjectNode();
      nodo.put("consulta", request.getQueryString());
      nodo.put("contentType", request.getContentType());
      nodo.set("cuerpo", cuerpo.length == 0 ? json.nullNode() : json.readTree(cuerpo));
      huella = HuellaSolicitud.calcular(nodo);
    } catch (IOException e) {
      huella = HuellaSolicitud.calcular(json.getNodeFactory().binaryNode(cuerpo));
    }
    ReservaIdempotente reserva;
    try {
      reserva =
          operaciones.reservar(
              jwt.userId(), clave, request.getMethod(), request.getServletPath(), huella);
    } catch (ApiException e) {
      problema(request, response, e);
      return;
    }
    if (reserva.repetida()) {
      response.setHeader("Idempotent-Replay", "true");
      escribir(response, reserva.respuesta());
      return;
    }
    var cache = new ContentCachingResponseWrapper(response);
    try {
      transaccion.executeWithoutResult(
          estado -> {
            try {
              chain.doFilter(new SolicitudIdempotente(request, cuerpo), cache);
            } catch (IOException | ServletException e) {
              throw new IllegalStateException("No se pudo completar la operacion HTTP", e);
            }
            if (cache.getStatus() >= 200 && cache.getStatus() < 300) {
              operaciones.completar(reserva.id(), jwt.userId(), clave, capturar(cache));
            } else {
              estado.setRollbackOnly();
            }
          });
    } catch (RuntimeException e) {
      if (cache.getStatus() < 400 || cache.getStatus() >= 500) {
        operaciones.liberar(reserva.id());
        response.resetBuffer();
        problema(
            request,
            response,
            new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "error_interno",
                "Error interno",
                "No se pudo completar la operacion. Reintente con la misma clave."));
        return;
      }
    }
    if (cache.getStatus() >= 400 && cache.getStatus() < 500) {
      operaciones.completarError(reserva.id(), jwt.userId(), clave, capturar(cache));
    } else if (cache.getStatus() < 200 || cache.getStatus() >= 300) {
      operaciones.liberar(reserva.id());
    }
    cache.copyBodyToResponse();
  }

  private RespuestaIdempotente capturar(ContentCachingResponseWrapper response) {
    var encabezados = new LinkedHashMap<String, String>();
    for (String nombre : List.of("Content-Type", "Location", "Retry-After", "ETag")) {
      String valor = response.getHeader(nombre);
      if (valor != null) encabezados.put(nombre, valor);
    }
    return new RespuestaIdempotente(
        response.getStatus(), response.getContentAsByteArray(), encabezados);
  }

  private void escribir(HttpServletResponse response, RespuestaIdempotente resultado)
      throws IOException {
    response.setStatus(resultado.codigo());
    resultado.encabezados().forEach(response::setHeader);
    response.getOutputStream().write(resultado.cuerpo());
  }

  private void problema(HttpServletRequest request, HttpServletResponse response, ApiException e)
      throws IOException {
    response.setStatus(e.getStatus().value());
    response.setContentType("application/problem+json");
    if ("operacion_en_proceso".equals(e.getCode())) response.setHeader("Retry-After", "1");
    var problema =
        json.createObjectNode()
            .put("type", "about:blank")
            .put("status", e.getStatus().value())
            .put("title", e.getTitle())
            .put("detail", e.getMessage())
            .put("code", e.getCode())
            .put("instance", request.getRequestURI());
    json.writeValue(response.getOutputStream(), problema);
  }
}
