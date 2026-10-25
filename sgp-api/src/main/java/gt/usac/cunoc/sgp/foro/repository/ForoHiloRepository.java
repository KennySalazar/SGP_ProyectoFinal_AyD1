package gt.usac.cunoc.sgp.foro.repository;

import gt.usac.cunoc.sgp.foro.entity.ForoHilo;
import java.time.Instant;
import java.util.List;
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

  /**
   * Cuándo abrió un usuario sus hilos más recientes desde un instante, del más nuevo al más viejo.
   * No cuenta los hilos de inspección, que abre el sistema (DT-SEC-07).
   */
  @Query(
      "SELECT h.creadoEn FROM ForoHilo h WHERE h.autorId = :autor AND h.inspeccionId IS NULL"
          + " AND h.creadoEn > :desde ORDER BY h.creadoEn DESC")
  List<Instant> abiertosDesde(
      @Param("autor") UUID autor, @Param("desde") Instant desde, Pageable limite);

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
   * Marca la actividad de un hilo abierto sin leerlo ni versionarlo, para que dos comentarios
   * simultáneos no choquen entre sí. Devuelve 0 si el hilo ya está cerrado: la fila bloqueada hace
   * que un comentario y un cierre simultáneos nunca dejen un comentario después del cierre.
   */
  @Modifying
  @Query("UPDATE ForoHilo h SET h.actualizadoEn = :ahora WHERE h.id = :id AND h.cerradoEn IS NULL")
  int registrarActividad(@Param("id") UUID id, @Param("ahora") Instant ahora);

  /**
   * Cierra un hilo abierto con su motivo (RN-FOR-08) y devuelve cuántas filas cambió: 0 si ya
   * estaba cerrado. No toca la actividad ni la versión del hilo.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "UPDATE ForoHilo h SET h.cerradoEn = :ahora, h.cerradoPorId = :moderador,"
          + " h.motivoCierre = :motivo WHERE h.id = :id AND h.cerradoEn IS NULL")
  int cerrar(
      @Param("id") UUID id,
      @Param("moderador") UUID moderador,
      @Param("motivo") String motivo,
      @Param("ahora") Instant ahora);
}
