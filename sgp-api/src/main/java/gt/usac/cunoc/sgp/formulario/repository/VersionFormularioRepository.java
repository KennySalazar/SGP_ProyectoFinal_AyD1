package gt.usac.cunoc.sgp.formulario.repository;

import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VersionFormularioRepository extends JpaRepository<VersionFormulario, UUID> {

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT v FROM VersionFormulario v WHERE v.id = :id")
  Optional<VersionFormulario> findByIdForUpdate(@Param("id") UUID id);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "UPDATE version_formulario SET version = version + 1 WHERE id = :id AND version = :versionEsperada",
      nativeQuery = true)
  int incrementarVersionCalibracion(
      @Param("id") UUID id, @Param("versionEsperada") long versionEsperada);

  @Query(
      "SELECT v FROM VersionFormulario v WHERE v.estado = gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario.PUBLICADA AND NOT EXISTS (SELECT a.id FROM AprobacionCalibracion a WHERE a.versionFormulario.id = v.id)")
  List<VersionFormulario> findPublicadasSinAprobacion();

  Optional<VersionFormulario> findByActivaTrue();

  Optional<VersionFormulario> findByCodigo(String codigo);

  boolean existsByCodigo(String codigo);

  List<VersionFormulario> findAllByOrderByCreadoEnDesc();

  @Modifying
  @Query("UPDATE VersionFormulario v SET v.activa = false WHERE v.activa = true")
  int desactivarTodas();

  @Modifying
  @Query("UPDATE VersionFormulario v SET v.activa = false WHERE v.id <> :id AND v.activa = true")
  int desactivarOtras(@Param("id") UUID id);
}
