package gt.usac.cunoc.sgp.archivo.repository;

import gt.usac.cunoc.sgp.archivo.entity.ArchivoInspeccion;
import gt.usac.cunoc.sgp.archivo.model.EstadoArchivo;
import gt.usac.cunoc.sgp.archivo.model.TipoUsoArchivo;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ArchivoInspeccionRepository extends JpaRepository<ArchivoInspeccion, UUID> {
  long countByInspeccionIdAndTipoUsoAndArchivo_Estado(
      UUID inspeccionId, TipoUsoArchivo tipoUso, EstadoArchivo estado);

  @EntityGraph(attributePaths = {"archivo", "inspeccion"})
  List<ArchivoInspeccion> findByInspeccionIdAndTipoUsoOrderByCreadoEnAsc(
      UUID inspeccionId, TipoUsoArchivo tipoUso);

  @EntityGraph(attributePaths = {"archivo", "inspeccion"})
  Optional<ArchivoInspeccion> findByInspeccionIdAndArchivoIdAndTipoUso(
      UUID inspeccionId, UUID archivoId, TipoUsoArchivo tipoUso);
}
