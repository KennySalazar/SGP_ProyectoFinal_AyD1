package gt.usac.cunoc.sgp.foro.repository;

import gt.usac.cunoc.sgp.foro.entity.ForoComentario;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForoComentarioRepository extends JpaRepository<ForoComentario, UUID> {

  /** Total de comentarios de un hilo. */
  interface TotalPorHilo {
    UUID getHiloId();

    long getTotal();
  }

  /** Comentarios y respuestas de un hilo, en una sola lista paginada. */
  Page<ForoComentario> findByHiloId(UUID hiloId, Pageable pageable);

  Optional<ForoComentario> findByIdAndHiloId(UUID id, UUID hiloId);

  @Query(
      "SELECT c.hiloId AS hiloId, COUNT(c) AS total FROM ForoComentario c"
          + " WHERE c.hiloId IN :hilos GROUP BY c.hiloId")
  List<TotalPorHilo> contarPorHilos(@Param("hilos") Collection<UUID> hilos);
}
