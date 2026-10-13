package gt.usac.cunoc.sgp.formulario.repository;

import gt.usac.cunoc.sgp.formulario.entity.MapeoCampoFormulario;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapeoCampoFormularioRepository extends JpaRepository<MapeoCampoFormulario, UUID> {

  List<MapeoCampoFormulario> findAllByVersionOrigenIdAndVersionDestinoId(
      UUID versionOrigenId, UUID versionDestinoId);

  @Modifying
  @Query(
      "DELETE FROM MapeoCampoFormulario m WHERE m.versionOrigen.id = :origenId AND m.versionDestino.id = :destinoId")
  int deleteAllByVersionOrigenIdAndVersionDestinoId(
      @Param("origenId") UUID origenId, @Param("destinoId") UUID destinoId);
}
