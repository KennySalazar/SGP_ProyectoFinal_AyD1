package gt.usac.cunoc.sgp.common.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Construye los JSON de valores anteriores y posteriores de la auditoria.
 */
@Service
public class AuditoriaSnapshotService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuditoriaSnapshotService.class);

  private final ObjectMapper objectMapper;
  private final List<AuditoriaSnapshotProvider> providers;

  public AuditoriaSnapshotService(
      ObjectMapper objectMapper, List<AuditoriaSnapshotProvider> providers) {
    this.objectMapper = objectMapper;
    this.providers = providers;
  }

  public JsonNode serializar(Object valor) {
    if (valor == null) {
      return null;
    }
    try {
      return objectMapper.valueToTree(valor);
    } catch (RuntimeException | StackOverflowError excepcion) {
      LOGGER.warn("Auditoria: valor no serializable ({})", excepcion.getMessage());
      return null;
    }
  }

  @Transactional(readOnly = true)
  public JsonNode cargar(Class<?> tipo, UUID id) {
    if (tipo == null || tipo.equals(Void.class) || id == null) {
      return null;
    }
    try {
      for (AuditoriaSnapshotProvider provider : providers) {
        if (provider.tipo().equals(tipo)) {
          return serializar(provider.cargar(id));
        }
      }
      LOGGER.warn("Auditoria: no hay proyeccion segura para {}", tipo.getSimpleName());
      return null;
    } catch (RuntimeException | StackOverflowError excepcion) {
      LOGGER.warn(
          "Auditoria: entidad {} no cargable ({})", tipo.getSimpleName(), excepcion.getMessage());
      return null;
    }
  }

  @Transactional(readOnly = true)
  public JsonNode cargarPorEmail(Class<?> tipo, String email) {
    if (email == null || email.isBlank()) return null;
    for (AuditoriaSnapshotProvider provider : providers) {
      if (provider.tipo().equals(tipo)) {
        try {
          return serializar(provider.cargarPorEmail(email));
        } catch (RuntimeException excepcion) {
          LOGGER.warn("Auditoria: usuario no cargable ({})", excepcion.getMessage());
          return null;
        }
      }
    }
    return null;
  }
}
