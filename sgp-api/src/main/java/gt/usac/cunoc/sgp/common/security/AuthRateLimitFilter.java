package gt.usac.cunoc.sgp.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ProblemDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

  private static final int MAX_REQUESTS = 20;
  private static final long WINDOW_SECONDS = 60;
  private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
  private final Clock clock;
  private final ObjectMapper objectMapper;

  public AuthRateLimitFilter(Clock clock, ObjectMapper objectMapper) {
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
    String uri = request.getRequestURI();
    return !(uri.equals("/api/v1/auth/login")
        || uri.equals("/api/v1/auth/register")
        || uri.equals("/api/v1/auth/password-recovery")
        || uri.equals("/api/v1/auth/refresh"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Instant now = clock.instant();
    String key = request.getRemoteAddr() + '|' + request.getRequestURI();
    WindowCounter counter =
        counters.compute(
            key,
            (ignored, existing) -> {
              if (existing == null || existing.startedAt.plusSeconds(WINDOW_SECONDS).isBefore(now))
                return new WindowCounter(now, 1);
              return new WindowCounter(existing.startedAt, existing.count + 1);
            });
    if (counter.count > MAX_REQUESTS) {
      ProblemDetail problem =
          ProblemDetails.create(
              429,
              "Demasiadas solicitudes",
              "Espere antes de volver a intentarlo",
              "rate_limited",
              request);
      response.setStatus(429);
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      objectMapper.writeValue(response.getOutputStream(), problem);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private record WindowCounter(Instant startedAt, int count) {}
}
