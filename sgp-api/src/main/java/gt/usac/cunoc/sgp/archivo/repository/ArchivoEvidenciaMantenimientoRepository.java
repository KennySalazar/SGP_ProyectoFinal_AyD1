package gt.usac.cunoc.sgp.archivo.repository;

import gt.usac.cunoc.sgp.archivo.entity.ArchivoEvidenciaMantenimiento;
import gt.usac.cunoc.sgp.archivo.model.EstadoArchivo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

/** Evidencias de mantenimiento; se retiran, nunca se borran (RN-MTO-09). */
public interface ArchivoEvidenciaMantenimientoRepository
    extends Repository<ArchivoEvidenciaMantenimiento, UUID> {

  ArchivoEvidenciaMantenimiento saveAndFlush(ArchivoEvidenciaMantenimiento evidencia);

  @EntityGraph(attributePaths = "archivo")
  List<ArchivoEvidenciaMantenimiento> findByOrdenMantenimientoIdAndArchivo_EstadoOrderByCreadoEnAsc(
      UUID ordenMantenimientoId, EstadoArchivo estado);

  @EntityGraph(attributePaths = "archivo")
  Optional<ArchivoEvidenciaMantenimiento> findByOrdenMantenimientoIdAndArchivoId(
      UUID ordenMantenimientoId, UUID archivoId);

  long countByOrdenMantenimientoIdAndArchivo_Estado(
      UUID ordenMantenimientoId, EstadoArchivo estado);
}
