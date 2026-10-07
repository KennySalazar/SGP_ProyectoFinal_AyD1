package gt.usac.cunoc.sgp.common.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

  private final AuditLogRepository auditLogs;

  public AuditService(AuditLogRepository auditLogs) {
    this.auditLogs = auditLogs;
  }

  public void register(
      UUID userId,
      String action,
      String entity,
      UUID entityId,
      Map<String, Object> valuesAfter,
      Instant now) {
    auditLogs.save(new AuditLog(userId, action, entity, entityId, valuesAfter, now));
  }
}
