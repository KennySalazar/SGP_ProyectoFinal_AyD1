package gt.usac.cunoc.sgp.common.audit.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaSnapshotService;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.security.JwtData;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Aspecto {@code @Around} que registra las acciones anotadas con {@link Auditable} (HU-006,
 * DT-BD-12).
 *
 */
@Aspect
@Component
@Order(0)
public class AuditoriaAspect {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuditoriaAspect.class);

  private final AuditoriaWriter escritor;
  private final AuditoriaSnapshotService snapshots;

  public AuditoriaAspect(AuditoriaWriter escritor, AuditoriaSnapshotService snapshots) {
    this.escritor = escritor;
    this.snapshots = snapshots;
  }

  @Around("@annotation(auditable)")
  public Object auditar(ProceedingJoinPoint punto, Auditable auditable) throws Throwable {
    UUID entidadId = null;
    String email = null;
    JsonNode anteriores = null;
    try {
      entidadId = entidadId(punto, auditable);
      email = email(punto, auditable);
      anteriores =
          email == null
              ? snapshots.cargar(auditable.tipo(), entidadId)
              : snapshots.cargarPorEmail(auditable.tipo(), email);
    } catch (RuntimeException | StackOverflowError excepcion) {
      LOGGER.error("Auditoria: captura previa fallida para {}", auditable.entidad(), excepcion);
    }

    Object resultado = punto.proceed();

    registrar(auditable, entidadId, email, anteriores, resultado);
    return resultado;
  }

  private void registrar(
      Auditable auditable, UUID entidadId, String email, JsonNode anteriores, Object resultado) {
    try {
      JsonNode posteriores =
          email == null
              ? snapshots.serializar(resultado)
              : snapshots.cargarPorEmail(auditable.tipo(), email);
      if (posteriores == null && entidadId != null) {
        posteriores = snapshots.cargar(auditable.tipo(), entidadId);
      }
      UUID entidadIdFinal = entidadId;
      if (entidadIdFinal == null) {
        entidadIdFinal = idDesde(posteriores);
      }
      if (Objects.equals(anteriores, posteriores) && anteriores != null) return;
      AccionAuditoria accion =
          auditable.accion() == AccionAuditoria.CREAR && anteriores != null
              ? AccionAuditoria.MODIFICAR
              : auditable.accion();
      UUID usuarioId = auditable.proceso().isBlank() ? usuarioActual() : null;
      if (usuarioId == null && auditable.actorEsEntidad()) usuarioId = entidadIdFinal;
      UUID actorFinal = usuarioId;
      UUID idFinal = entidadIdFinal;
      JsonNode antesFinal = anteriores;
      JsonNode despuesFinal = posteriores;
      Runnable escritura =
          () -> escribir(actorFinal, accion, auditable, idFinal, antesFinal, despuesFinal);
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
              @Override
              public void afterCommit() {
                escritura.run();
              }
            });
      } else {
        escritura.run();
      }
    } catch (RuntimeException | StackOverflowError excepcion) {
      LOGGER.error(
          "Auditoria no registrada para {}.{}: {}",
          auditable.entidad(),
          auditable.accion(),
          excepcion.getMessage(),
          excepcion);
    }
  }

  private void escribir(
      UUID usuarioId,
      AccionAuditoria accion,
      Auditable auditable,
      UUID entidadId,
      JsonNode anteriores,
      JsonNode posteriores) {
    try {
      escritor.registrar(
          usuarioId,
          accion,
          auditable.entidad(),
          entidadId,
          anteriores,
          posteriores,
          auditable.proceso());
    } catch (RuntimeException | StackOverflowError excepcion) {
      LOGGER.error("Auditoria no registrada para {}.{}", auditable.entidad(), accion, excepcion);
    }
  }

  private UUID entidadId(ProceedingJoinPoint punto, Auditable auditable) {
    if (auditable.idArg().isBlank()) {
      return null;
    }
    String[] nombres = ((MethodSignature) punto.getSignature()).getParameterNames();
    Object[] argumentos = punto.getArgs();
    if (nombres == null || argumentos == null) return null;
    for (int indice = 0; indice < Math.min(nombres.length, argumentos.length); indice++) {
      if (nombres[indice].equals(auditable.idArg()) && argumentos[indice] instanceof UUID id) {
        return id;
      }
    }
    LOGGER.warn(
        "Auditoria: parametro {} de {} no encontrado",
        auditable.idArg(),
        punto.getSignature().getName());
    return null;
  }

  private String email(ProceedingJoinPoint punto, Auditable auditable) {
    int indice = auditable.emailArgIndex();
    if (indice < 0 || punto.getArgs() == null || indice >= punto.getArgs().length) return null;
    Object argumento = punto.getArgs()[indice];
    if (argumento instanceof String correo) return correo;
    if (argumento == null) return null;
    try {
      Method metodo = argumento.getClass().getMethod("email");
      Object valor = metodo.invoke(argumento);
      return valor instanceof String correo ? correo : null;
    } catch (ReflectiveOperationException excepcion) {
      LOGGER.warn("Auditoria: argumento de correo no valido para {}", auditable.entidad());
      return null;
    }
  }

  private UUID idDesde(JsonNode posteriores) {
    if (posteriores == null || !posteriores.hasNonNull("id")) {
      return null;
    }
    try {
      return UUID.fromString(posteriores.get("id").asText());
    } catch (IllegalArgumentException excepcion) {
      return null;
    }
  }

  private UUID usuarioActual() {
    Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
    if (autenticacion == null) {
      return null;
    }
    if (autenticacion.getDetails() instanceof JwtData jwt) {
      return jwt.userId();
    }
    return null;
  }
}
