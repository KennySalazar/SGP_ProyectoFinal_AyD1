package gt.usac.cunoc.sgp.inspeccion.repository;

import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspeccionRepository extends JpaRepository<Inspeccion, UUID> {

  @EntityGraph(attributePaths = {"puente", "versionFormulario", "acompanantes", "datoTecnico"})
  Optional<Inspeccion> findConDetallesById(UUID id);

  Optional<Inspeccion> findByIdAndEliminadoEnIsNull(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Inspeccion i where i.id = :id and i.eliminadoEn is null")
  Optional<Inspeccion> findByIdAndEliminadoEnIsNullForUpdate(@Param("id") UUID id);
}
