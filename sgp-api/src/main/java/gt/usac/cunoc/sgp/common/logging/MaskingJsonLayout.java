package gt.usac.cunoc.sgp.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.LayoutBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Layout de Logback para estructuración de bitácoras en JSON (DT-DEP-09) y enmascaramiento de datos
 * sensibles (DT-SEC-12).
 */
public class MaskingJsonLayout extends LayoutBase<ILoggingEvent> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final Pattern PASSWORD_PATTERN =
      Pattern.compile(
          "(?i)(\"?(?:password|contrase[ñn]a|clave|secret|currentPassword|newPassword|confirmPassword)\"?\\s*[:=]\\s*\")([^\"]*)(\")");

  private static final Pattern BEARER_PATTERN =
      Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9\\-_.~+/]+=*");

  private static final Pattern TOKEN_PATTERN =
      Pattern.compile(
          "(?i)(\"?(?:token|refreshToken|accessToken|rawRefreshToken|recoveryToken|invitationToken)\"?\\s*[:=]\\s*\")([^\"]*)(\")");

  private static final Pattern URL_TOKEN_PATTERN =
      Pattern.compile("(?i)([?&](?:token|code|otp)=)[^&\\s]+");

  @Override
  public String doLayout(ILoggingEvent event) {
    Map<String, Object> logEntry = new LinkedHashMap<>();
    logEntry.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
    logEntry.put("level", event.getLevel().toString());
    logEntry.put("thread", event.getThreadName());
    logEntry.put("logger", event.getLoggerName());

    String message = event.getFormattedMessage();
    if (message != null) {
      logEntry.put("message", mask(message));
    }

    IThrowableProxy throwable = event.getThrowableProxy();
    if (throwable != null) {
      logEntry.put("exception", mask(ThrowableProxyUtil.asString(throwable)));
    }

    Map<String, String> mdc = event.getMDCPropertyMap();
    if (mdc != null && !mdc.isEmpty()) {
      logEntry.put("mdc", mdc);
    }

    try {
      return MAPPER.writeValueAsString(logEntry) + "\n";
    } catch (JsonProcessingException e) {
      return "{\"timestamp\":\""
          + Instant.now()
          + "\",\"level\":\"ERROR\",\"message\":\"Fallo al serializar log en JSON\"}\n";
    }
  }

  public String mask(String input) {
    if (input == null || input.isBlank()) {
      return input;
    }
    String masked = PASSWORD_PATTERN.matcher(input).replaceAll("$1[PROTEGIDO]$3");
    masked = BEARER_PATTERN.matcher(masked).replaceAll("$1[PROTEGIDO]");
    masked = TOKEN_PATTERN.matcher(masked).replaceAll("$1[PROTEGIDO]$3");
    masked = URL_TOKEN_PATTERN.matcher(masked).replaceAll("$1[PROTEGIDO]");
    return masked;
  }
}
