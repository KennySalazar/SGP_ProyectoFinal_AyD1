package gt.usac.cunoc.sgp.formulario.repository;

import gt.usac.cunoc.sgp.formulario.entity.ConfiguracionIcElemento;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConfiguracionIcElementoRepository
    extends JpaRepository<ConfiguracionIcElemento, UUID> {

  List<ConfiguracionIcElemento> findAllByVersionFormularioId(UUID versionFormularioId);

  @Modifying
  @Query("DELETE FROM ConfiguracionIcElemento c WHERE c.versionFormulario.id = :versionId")
  int deleteAllByVersionFormularioId(@Param("versionId") UUID versionId);
}
