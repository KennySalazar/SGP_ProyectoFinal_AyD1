package gt.usac.cunoc.sgp.mantenimiento.repository;

import gt.usac.cunoc.sgp.mantenimiento.entity.ReglaMantenimiento;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReglaMantenimientoRepository
    extends JpaRepository<ReglaMantenimiento, UUID>, JpaSpecificationExecutor<ReglaMantenimiento> {

  Optional<ReglaMantenimiento> findByCodigoAndVersionFormularioIdAndActivaTrue(
      String codigo, UUID versionFormularioId);

  List<ReglaMantenimiento> findByVersionFormularioIdAndActivaTrue(UUID versionFormularioId);

  @Query(
      "SELECT MAX(r.numeroRevision) FROM ReglaMantenimiento r WHERE r.codigo = :codigo AND r.versionFormulario.id = :versionId")
  Optional<Integer> findMaxNumeroRevision(
      @Param("codigo") String codigo, @Param("versionId") UUID versionId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE ReglaMantenimiento r SET r.activa = false WHERE r.codigo = :codigo AND r.versionFormulario.id = :versionId AND r.id <> :id")
  int desactivarOtrasRevisiones(
      @Param("codigo") String codigo, @Param("versionId") UUID versionId, @Param("id") UUID id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE ReglaMantenimiento r SET r.activa = false WHERE r.codigo = :codigo AND r.versionFormulario.id = :versionId")
  int desactivarTodasLasRevisiones(
      @Param("codigo") String codigo, @Param("versionId") UUID versionId);

  boolean existsByVersionFormularioId(UUID versionFormularioId);

  boolean existsByVersionFormularioIdAndActivaTrue(UUID versionFormularioId);

  long countByVersionFormularioId(UUID versionFormularioId);

  long countByVersionFormularioIdAndActivaTrue(UUID versionFormularioId);

  long countByVersionFormularioIdAndValidadoPorIdIsNull(UUID versionFormularioId);

  List<ReglaMantenimiento> findByCodigoAndVersionFormularioIdOrderByNumeroRevisionDesc(
      String codigo, UUID versionFormularioId);
}
