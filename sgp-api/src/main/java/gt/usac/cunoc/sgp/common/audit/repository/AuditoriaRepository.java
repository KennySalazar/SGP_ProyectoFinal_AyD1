package gt.usac.cunoc.sgp.common.audit.repository;

import gt.usac.cunoc.sgp.common.audit.entity.Auditoria;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditoriaRepository
    extends JpaRepository<Auditoria, UUID>, JpaSpecificationExecutor<Auditoria> {}
