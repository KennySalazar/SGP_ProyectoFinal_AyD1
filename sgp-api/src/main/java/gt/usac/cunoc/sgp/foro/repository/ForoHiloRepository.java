package gt.usac.cunoc.sgp.foro.repository;

import gt.usac.cunoc.sgp.foro.entity.ForoHilo;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForoHiloRepository extends JpaRepository<ForoHilo, UUID> {

  /** Hilos propios del puente, sin los hilos de sus inspecciones. */
  Page<ForoHilo> findByPuenteIdAndInspeccionIdIsNull(UUID puenteId, Pageable pageable);

  /**
   * Marca la actividad de un hilo sin leerlo ni versionarlo, para que dos comentarios simultáneos
   * no choquen entre sí.
   */
  @Modifying
  @Query("UPDATE ForoHilo h SET h.actualizadoEn = :ahora WHERE h.id = :id")
  void registrarActividad(@Param("id") UUID id, @Param("ahora") Instant ahora);
}
