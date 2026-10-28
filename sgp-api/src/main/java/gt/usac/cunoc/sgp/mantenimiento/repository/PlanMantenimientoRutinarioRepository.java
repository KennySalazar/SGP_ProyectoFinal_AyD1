package gt.usac.cunoc.sgp.mantenimiento.repository;

import gt.usac.cunoc.sgp.mantenimiento.entity.PlanMantenimientoRutinario;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PlanMantenimientoRutinarioRepository
    extends Repository<PlanMantenimientoRutinario, UUID> {

  PlanMantenimientoRutinario save(PlanMantenimientoRutinario plan);

  PlanMantenimientoRutinario saveAndFlush(PlanMantenimientoRutinario plan);

  boolean existsByPuente_Id(UUID puenteId);

  @EntityGraph(attributePaths = {"puente", "actividades"})
  @Query("select p from PlanMantenimientoRutinario p where p.puente.id = :puenteId")
  Optional<PlanMantenimientoRutinario> findByPuenteId(@Param("puenteId") UUID puenteId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PlanMantenimientoRutinario p where p.puente.id = :puenteId")
  Optional<PlanMantenimientoRutinario> findByPuenteIdForUpdate(@Param("puenteId") UUID puenteId);

  @EntityGraph(attributePaths = {"puente", "actividades"})
  @Query("select p from PlanMantenimientoRutinario p order by p.puente.codigo")
  List<PlanMantenimientoRutinario> findAllForListing();

  @EntityGraph(attributePaths = {"puente", "actividades"})
  @Query(
      "select p from PlanMantenimientoRutinario p where p.activo = true and p.puente.activo = true"
          + " order by p.puente.codigo")
  List<PlanMantenimientoRutinario> findDueCandidates();

  @Query(value = "select activo from puente where id = :puenteId for update", nativeQuery = true)
  Optional<Boolean> lockBridgeActive(@Param("puenteId") UUID puenteId);

  @Query(
      value =
          "select b.id from puente b where b.activo = true and not exists "
              + "(select 1 from plan_mantenimiento_rutinario p where p.puente_id = b.id)",
      nativeQuery = true)
  List<UUID> findActiveBridgesWithoutPlan();
}
