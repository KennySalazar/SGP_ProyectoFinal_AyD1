package gt.usac.cunoc.sgp.usuario.repository;

import gt.usac.cunoc.sgp.usuario.entity.UsuarioProfesional;
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

public interface UsuarioProfesionalRepository extends JpaRepository<UsuarioProfesional, UUID> {

  boolean existsByNumeroColegiado(String numeroColegiado);

  boolean existsByNumeroColegiadoAndUsuarioIdNot(String numeroColegiado, UUID usuarioId);

  /** Bloquea la fila para que dos verificaciones simultáneas no se registren dos veces. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from UsuarioProfesional p join fetch p.usuario where p.usuarioId = :usuarioId")
  Optional<UsuarioProfesional> findByUsuarioIdForUpdate(@Param("usuarioId") UUID usuarioId);

  @EntityGraph(attributePaths = "usuario")
  @Query(
      """
      SELECT p FROM UsuarioProfesional p
      WHERE :verificado IS NULL
         OR (:verificado = true AND p.colegiadoVerificadoEn IS NOT NULL)
         OR (:verificado = false AND p.colegiadoVerificadoEn IS NULL)
      """)
  Page<UsuarioProfesional> findByVerificacion(
      @Param("verificado") Boolean verificado, Pageable pageable);
}
