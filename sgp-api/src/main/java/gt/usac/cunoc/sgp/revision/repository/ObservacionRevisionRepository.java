package gt.usac.cunoc.sgp.revision.repository;

import gt.usac.cunoc.sgp.revision.entity.ObservacionRevision;
import gt.usac.cunoc.sgp.revision.entity.Revision;
import gt.usac.cunoc.sgp.revision.model.EstadoObservacion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ObservacionRevisionRepository extends JpaRepository<ObservacionRevision, UUID> {
  List<ObservacionRevision> findByRevisionIdOrderByCreadoEnAscIdAsc(UUID revisionId);

  Optional<ObservacionRevision> findByIdAndRevisionId(UUID id, UUID revisionId);

  long countByRevisionId(UUID revisionId);

  long countByRevisionIdAndEstado(UUID revisionId, EstadoObservacion estado);

  @Query(
      "select count(r) > 0 from Revision r where r.id = :id and r.revisorId = :actor and r.inspeccion.eliminadoEn is null")
  boolean existeCicloDelRevisor(@Param("id") UUID id, @Param("actor") UUID actor);

  @Query(
      "select count(r) > 0 from Revision r where r.id = :id "
          + "and (r.revisorId = :actor or r.inspeccion.autorId = :actor) "
          + "and r.inspeccion.eliminadoEn is null")
  boolean existeCicloLegible(@Param("id") UUID id, @Param("actor") UUID actor);

  @Query(
      """
      select r from Revision r
      where r.inspeccion.autorId = :actor and r.inspeccion.eliminadoEn is null
        and (r.observacionGeneral is not null or exists
          (select o.id from ObservacionRevision o where o.revision = r))
      order by r.enviadaEn desc, r.id desc
      """)
  Page<Revision> ciclosDelAutor(@Param("actor") UUID actor, Pageable pagina);

  @Query(
      """
      select count(o) as total,
             coalesce(sum(case when o.estado = :estado then 1 else 0 end), 0) as pendientes
      from ObservacionRevision o where o.revision.id = :revisionId
      """)
  ConteoObservaciones contarPorCiclo(
      @Param("revisionId") UUID revisionId, @Param("estado") EstadoObservacion estado);

  /** Acceso al campo ya mapeado en Revision, sin duplicar su representación persistida. */
  @Query("select r.observacionGeneral from Revision r where r.id = :revisionId")
  String buscarGeneral(@Param("revisionId") UUID revisionId);

  /**
   * El servicio debe poseer los bloqueos de ciclo e inspección y auditar en la misma transacción.
   */
  @Modifying(flushAutomatically = true)
  @Query("update Revision r set r.observacionGeneral = :texto where r.id = :revisionId")
  int actualizarGeneral(@Param("revisionId") UUID revisionId, @Param("texto") String texto);

  interface ConteoObservaciones {
    long getTotal();

    long getPendientes();
  }
}
