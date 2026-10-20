package gt.usac.cunoc.sgp.sync.repository;

import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ConsultaSalidaRepository extends Repository<Puente, UUID> {
  @EntityGraph(attributePaths = {"municipio", "municipio.departamento"})
  @Query("SELECT p FROM Puente p WHERE p.id IN :ids AND p.activo = true ORDER BY p.codigo, p.id")
  List<Puente> seleccionados(@Param("ids") Collection<UUID> ids);

  @EntityGraph(attributePaths = {"municipio", "municipio.departamento"})
  @Query(
      """
            SELECT p FROM Puente p
            WHERE p.activo = true
              AND (:estudiante = false OR p.id IN :ids)
              AND (:departamento IS NULL OR p.municipio.departamento.id = :departamento)
              AND (LOWER(p.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(p.codigo) LIKE LOWER(CONCAT('%', :busqueda, '%')))
            """)
  Page<Puente> disponibles(
      @Param("estudiante") boolean estudiante,
      @Param("ids") Collection<UUID> ids,
      @Param("departamento") UUID departamento,
      @Param("busqueda") String busqueda,
      Pageable pagina);

  @Query(
      """
            SELECT i FROM Inspeccion i
            JOIN FETCH i.versionFormulario
            LEFT JOIN FETCH i.datoTecnico
            WHERE i.puente.id IN :ids AND i.estado = 'PUBLICADA' AND i.eliminadoEn IS NULL
              AND NOT EXISTS (
                SELECT otra.id FROM Inspeccion otra
                WHERE otra.puente.id = i.puente.id AND otra.estado = 'PUBLICADA'
                  AND otra.eliminadoEn IS NULL
                  AND (otra.publicadoEn > i.publicadoEn
                       OR (otra.publicadoEn = i.publicadoEn AND otra.id > i.id)))
            ORDER BY i.puente.id
            """)
  List<Inspeccion> ultimasPublicadas(@Param("ids") Collection<UUID> ids);
}
