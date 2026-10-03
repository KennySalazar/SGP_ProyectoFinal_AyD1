package gt.usac.cunoc.sgp.puente.repository;

import gt.usac.cunoc.sgp.puente.entity.Puente;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PuenteRepository extends JpaRepository<Puente, UUID> {

  @EntityGraph(attributePaths = {"municipio", "municipio.departamento"})
  Optional<Puente> findByIdAndActivoTrue(UUID id);

  @Query(
      value =
          """
                    SELECT p.id AS id,
                           p.codigo AS codigo,
                           p.nombre AS nombre,
                           p.activo AS activo,
                           ST_Distance(p.ubicacion, q.punto) AS "distanciaMetros"
                    FROM puente p
                    CROSS JOIN (
                        SELECT CAST(
                            ST_SetSRID(ST_MakePoint(:longitud, :latitud), 4326)
                            AS geography
                        ) AS punto
                    ) q
                    WHERE ST_DWithin(p.ubicacion, q.punto, 100)
                      AND ST_Distance(p.ubicacion, q.punto) < 100
                    ORDER BY ST_Distance(p.ubicacion, q.punto), p.id
                    """,
      countQuery =
          """
                    SELECT count(*)
                    FROM puente p
                    CROSS JOIN (
                        SELECT CAST(
                            ST_SetSRID(ST_MakePoint(:longitud, :latitud), 4326)
                            AS geography
                        ) AS punto
                    ) q
                    WHERE ST_DWithin(p.ubicacion, q.punto, 100)
                      AND ST_Distance(p.ubicacion, q.punto) < 100
                    """,
      nativeQuery = true)
  Page<PuenteCercanoProjection> findCercanos(
      @Param("latitud") double latitud, @Param("longitud") double longitud, Pageable pageable);

  @Query(
      value =
          """
                    SELECT q.zona AS zona,
                           q.epsg AS epsg,
                           ST_X(q.punto) AS este,
                           ST_Y(q.punto) AS norte
                    FROM (
                        SELECT CASE WHEN :longitud < -90 THEN 15 ELSE 16 END AS zona,
                               CASE WHEN :longitud < -90 THEN 32615 ELSE 32616 END AS epsg,
                               ST_Transform(
                                   ST_SetSRID(ST_MakePoint(:longitud, :latitud), 4326),
                                   CASE WHEN :longitud < -90 THEN 32615 ELSE 32616 END
                               ) AS punto
                    ) q
                    """,
      nativeQuery = true)
  CoordenadaUtmProjection calcularUtm(
      @Param("latitud") double latitud, @Param("longitud") double longitud);

  interface PuenteCercanoProjection {

    UUID getId();

    String getCodigo();

    String getNombre();

    boolean getActivo();

    Double getDistanciaMetros();
  }

  interface CoordenadaUtmProjection {

    Integer getZona();

    Integer getEpsg();

    Double getEste();

    Double getNorte();
  }
}
