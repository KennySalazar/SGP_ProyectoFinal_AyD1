package gt.usac.cunoc.sgp.puente.repository;

import gt.usac.cunoc.sgp.puente.entity.Municipio;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MunicipioRepository extends JpaRepository<Municipio, UUID> {

  @EntityGraph(attributePaths = "departamento")
  Optional<Municipio> findByIdAndActivoTrue(UUID id);

  @EntityGraph(attributePaths = "departamento")
  Page<Municipio> findByDepartamentoIdAndActivoTrue(UUID departamentoId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Municipio m where m.id = :id and m.activo = true")
  Optional<Municipio> findActivoByIdForUpdate(@Param("id") UUID id);

  @Query(
      value =
          """
                  SELECT ST_Covers(
                      l.geometria,
                      ST_SetSRID(ST_MakePoint(:longitud, :latitud), 4326)
                  )
                  FROM municipio_limite l
                  WHERE l.municipio_id = :municipioId
                  """,
      nativeQuery = true)
  Boolean ubicacionPerteneceAlMunicipio(
      @Param("municipioId") UUID municipioId,
      @Param("latitud") double latitud,
      @Param("longitud") double longitud);

  @Query(
      value =
          """
                  SELECT d.id AS "departamentoId",
                         d.codigo_ine AS "departamentoCodigoIne",
                         d.nombre AS "departamentoNombre",
                         m.id AS "municipioId",
                         m.codigo_ine AS "municipioCodigoIne",
                         m.nombre AS "municipioNombre"
                  FROM municipio_limite l
                  JOIN municipio m ON m.id = l.municipio_id
                  JOIN departamento d ON d.id = m.departamento_id
                  WHERE m.activo = true
                    AND d.activo = true
                    AND ST_Covers(
                        l.geometria,
                        ST_SetSRID(ST_MakePoint(:longitud, :latitud), 4326)
                    )
                  ORDER BY d.codigo_ine, m.codigo_ine
                  """,
      nativeQuery = true)
  List<UbicacionMunicipalProjection> findMunicipiosPorUbicacion(
      @Param("latitud") double latitud, @Param("longitud") double longitud);

  interface UbicacionMunicipalProjection {

    UUID getDepartamentoId();

    String getDepartamentoCodigoIne();

    String getDepartamentoNombre();

    UUID getMunicipioId();

    String getMunicipioCodigoIne();

    String getMunicipioNombre();
  }
}
