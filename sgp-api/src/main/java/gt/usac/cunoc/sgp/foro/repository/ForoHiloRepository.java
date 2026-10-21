package gt.usac.cunoc.sgp.foro.repository;

import gt.usac.cunoc.sgp.foro.entity.ForoHilo;
import java.time.Instant;
import java.util.Optional;
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

  /** El hilo de una inspección, si ya se abrió. */
  Optional<ForoHilo> findByInspeccionId(UUID inspeccionId);

  /**
   * Abre el hilo de una inspección solo si todavía no existe y devuelve cuántas filas insertó (0 o
   * 1). El índice único parcial resuelve en la base de datos la carrera entre el evento de
   * publicación y la creación perezosa, sin abortar la transacción de quien pierde.
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO foro_hilo (id, puente_id, inspeccion_id, autor_id, categoria, titulo,"
              + " creado_en, actualizado_en) VALUES (:id, :puenteId, :inspeccionId, :autorId,"
              + " :categoria, :titulo, :ahora, :ahora)"
              + " ON CONFLICT (inspeccion_id) WHERE inspeccion_id IS NOT NULL DO NOTHING",
      nativeQuery = true)
  int abrirHiloDeInspeccion(
      @Param("id") UUID id,
      @Param("puenteId") UUID puenteId,
      @Param("inspeccionId") UUID inspeccionId,
      @Param("autorId") UUID autorId,
      @Param("categoria") String categoria,
      @Param("titulo") String titulo,
      @Param("ahora") Instant ahora);

  /**
   * Marca la actividad de un hilo sin leerlo ni versionarlo, para que dos comentarios simultáneos
   * no choquen entre sí.
   */
  @Modifying
  @Query("UPDATE ForoHilo h SET h.actualizadoEn = :ahora WHERE h.id = :id")
  void registrarActividad(@Param("id") UUID id, @Param("ahora") Instant ahora);
}
