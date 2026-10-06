package gt.usac.cunoc.sgp.puente.repository;

import gt.usac.cunoc.sgp.puente.entity.SolicitudAltaPuente;
import gt.usac.cunoc.sgp.puente.model.EstadoSolicitudAltaPuente;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitudAltaPuenteRepository extends JpaRepository<SolicitudAltaPuente, UUID> {

  @EntityGraph(attributePaths = {"municipio", "municipio.departamento", "puenteCreado"})
  Page<SolicitudAltaPuente> findBySolicitadoPorId(UUID solicitadoPorId, Pageable pageable);

  @EntityGraph(attributePaths = {"municipio", "municipio.departamento", "puenteCreado"})
  Page<SolicitudAltaPuente> findByEstado(EstadoSolicitudAltaPuente estado, Pageable pageable);

  @EntityGraph(attributePaths = {"municipio", "municipio.departamento", "puenteCreado"})
  Optional<SolicitudAltaPuente> findConRelacionesById(UUID id);

  /** Bloquea la fila para que dos revisiones simultáneas no decidan la misma solicitud. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from SolicitudAltaPuente s where s.id = :id")
  Optional<SolicitudAltaPuente> findByIdForUpdate(@Param("id") UUID id);
}
