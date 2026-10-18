package gt.usac.cunoc.sgp.archivo.repository;

import gt.usac.cunoc.sgp.archivo.entity.ArchivoActaCalibracion;
import gt.usac.cunoc.sgp.archivo.model.EstadoArchivo;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchivoActaCalibracionRepository
        extends JpaRepository<ArchivoActaCalibracion, UUID> {
    @EntityGraph(attributePaths = "archivo")
    Optional<ArchivoActaCalibracion>
    findFirstByVersionFormularioIdAndArchivoEstadoOrderByCreadoEnDescArchivoIdDesc(
            UUID versionId, EstadoArchivo estado);
}