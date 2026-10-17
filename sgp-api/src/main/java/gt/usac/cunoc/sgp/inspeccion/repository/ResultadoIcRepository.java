package gt.usac.cunoc.sgp.inspeccion.repository;

import gt.usac.cunoc.sgp.inspeccion.entity.ResultadoIc;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResultadoIcRepository extends JpaRepository<ResultadoIc, UUID> {

  @Query(
      """
            SELECT r FROM ResultadoIc r
            WHERE r.inspeccionId = :inspeccionId
              AND r.tipo = 'PUBLICACION'
            """)
  Optional<ResultadoIc> findPublicacionByInspeccionId(@Param("inspeccionId") UUID inspeccionId);

  @Query(
      value =
          """
                  SELECT r.*
                  FROM resultado_ic r
                  JOIN inspeccion i ON i.id = r.inspeccion_id
                  WHERE i.puente_id = :puenteId
                    AND i.estado = 'PUBLICADA'
                    AND r.tipo = 'PUBLICACION'
                  ORDER BY i.publicado_en DESC, i.id DESC
                  LIMIT 1
                  """,
      nativeQuery = true)
  Optional<ResultadoIc> findPublicacionVigenteByPuenteId(@Param("puenteId") UUID puenteId);
}
