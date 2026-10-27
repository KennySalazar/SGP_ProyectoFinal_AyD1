package gt.usac.cunoc.sgp.foro.repository;

import gt.usac.cunoc.sgp.foro.entity.ForoComentario;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForoComentarioRepository extends JpaRepository<ForoComentario, UUID> {

  /** Total de comentarios de un hilo. */
  interface TotalPorHilo {
    UUID getHiloId();

    long getTotal();
  }

  /** Total de comentarios de un campo o elemento de un hilo, con sus respuestas. */
  interface TotalPorAncla {
    String getElementoRef();

    UUID getInstanciaElementoId();

    long getTotal();
  }

  /** Comentarios y respuestas de un hilo, en una sola lista paginada. */
  Page<ForoComentario> findByHiloId(UUID hiloId, Pageable pageable);

  /** Los de un elemento que no es repetible (o sin instancia concreta). */
  Page<ForoComentario> findByHiloIdAndElementoRefAndInstanciaElementoIdIsNull(
      UUID hiloId, String elementoRef, Pageable pageable);

  /** Los que no están anclados a ningún campo ni elemento. */
  Page<ForoComentario> findByHiloIdAndElementoRefIsNull(UUID hiloId, Pageable pageable);

  /**
   * Los de una sección: anclados a ella misma o a cualquier cosa que contenga. {@code prefijo} es
   * la raíz con las barras ya escapadas para LIKE (con {@code !}) y terminada en {@code /%}.
   */
  @Query(
      "SELECT c FROM ForoComentario c WHERE c.hiloId = :hilo AND (c.elementoRef = :raiz"
          + " OR c.elementoRef LIKE :prefijo ESCAPE '!')")
  Page<ForoComentario> findBySeccion(
      @Param("hilo") UUID hilo,
      @Param("raiz") String raiz,
      @Param("prefijo") String prefijo,
      Pageable pageable);

  /** Los de una instancia concreta de un elemento repetible. */
  Page<ForoComentario> findByHiloIdAndElementoRefAndInstanciaElementoId(
      UUID hiloId, String elementoRef, UUID instanciaElementoId, Pageable pageable);

  Optional<ForoComentario> findByIdAndHiloId(UUID id, UUID hiloId);

  @Query(
      "SELECT c.hiloId AS hiloId, COUNT(c) AS total FROM ForoComentario c"
          + " WHERE c.hiloId IN :hilos GROUP BY c.hiloId")
  List<TotalPorHilo> contarPorHilos(@Param("hilos") Collection<UUID> hilos);

  @Query(
      "SELECT c.elementoRef AS elementoRef, c.instanciaElementoId AS instanciaElementoId,"
          + " COUNT(c) AS total FROM ForoComentario c"
          + " WHERE c.hiloId = :hilo AND c.elementoRef IS NOT NULL"
          + " GROUP BY c.elementoRef, c.instanciaElementoId"
          + " ORDER BY c.elementoRef, c.instanciaElementoId")
  List<TotalPorAncla> contarPorAncla(@Param("hilo") UUID hilo);

  /**
   * Cuándo publicó un autor sus comentarios más recientes desde un instante, del más nuevo al más
   * viejo. Alimenta el límite de tasa (DT-SEC-07); {@code limite} acota cuántos se leen.
   */
  @Query(
      "SELECT c.creadoEn FROM ForoComentario c WHERE c.autorId = :autor AND c.creadoEn > :desde"
          + " ORDER BY c.creadoEn DESC")
  List<Instant> publicadosDesde(
      @Param("autor") UUID autor, @Param("desde") Instant desde, Pageable limite);

  /**
   * Registra que un comentario menciona a un usuario (RN-FOR-10) y devuelve 1 si la mención es
   * nueva o 0 si ya estaba: así cada mencionado recibe un solo aviso por comentario, también al
   * editarlo.
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO comentario_mencion (id, comentario_id, usuario_mencionado_id, creado_en)"
              + " VALUES (:id, :comentario, :usuario, :ahora)"
              + " ON CONFLICT (comentario_id, usuario_mencionado_id) DO NOTHING",
      nativeQuery = true)
  int registrarMencion(
      @Param("id") UUID id,
      @Param("comentario") UUID comentario,
      @Param("usuario") UUID usuario,
      @Param("ahora") Instant ahora);
}
