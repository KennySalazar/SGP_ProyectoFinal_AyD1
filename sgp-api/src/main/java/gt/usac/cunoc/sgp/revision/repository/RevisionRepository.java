package gt.usac.cunoc.sgp.revision.repository;

import gt.usac.cunoc.sgp.revision.entity.Revision;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface RevisionRepository extends JpaRepository<Revision, UUID> {

  @Query("select coalesce(max(r.numeroCiclo), 0) from Revision r where r.inspeccion.id = :id")
  int ultimoCiclo(@Param("id") UUID inspeccionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
  @Query("select r from Revision r join fetch r.inspeccion i where r.id = :id")
  Optional<Revision> findByIdForUpdate(@Param("id") UUID id);

  @Query(
      value =
          """
          SELECT i.id AS "inspeccionId", p.id AS "puenteId",
                 p.codigo AS "puenteCodigo", p.nombre AS "puenteNombre",
                 i.autor_id AS "estudianteId", u.email AS "estudianteEmail",
                 i.nombre_autor_capturado AS "estudianteNombre",
                 c.id AS "cursoId", a.nombre AS "cursoNombre", c.periodo AS periodo,
                 ce.seccion AS seccion, i.ultima_actividad_en AS "ultimaActividadEn"
          FROM inspeccion i
          JOIN puente p ON p.id = i.puente_id
          JOIN usuario u ON u.id = i.autor_id
          JOIN asignacion_puente ap ON ap.id = i.asignacion_puente_id
          JOIN curso_estudiante ce ON ce.id = ap.curso_estudiante_id
          JOIN curso c ON c.id = ce.curso_id
          JOIN asignatura a ON a.id = c.asignatura_id
          WHERE c.catedratico_id = :revisorId
            AND i.estado = 'BORRADOR'
            AND i.rol_autor_creacion = 'ESTUDIANTE'
            AND i.eliminado_en IS NULL
          ORDER BY i.ultima_actividad_en DESC
          """,
      countQuery =
          """
          SELECT count(*)
          FROM inspeccion i
          JOIN asignacion_puente ap ON ap.id = i.asignacion_puente_id
          JOIN curso_estudiante ce ON ce.id = ap.curso_estudiante_id
          JOIN curso c ON c.id = ce.curso_id
          WHERE c.catedratico_id = :revisorId
            AND i.estado = 'BORRADOR'
            AND i.rol_autor_creacion = 'ESTUDIANTE'
            AND i.eliminado_en IS NULL
          """,
      nativeQuery = true)
  Page<BorradorEstudianteProjection> buscarBorradores(
      @Param("revisorId") UUID revisorId, Pageable pageable);

  @Query(
      value =
          """
          SELECT r.id AS "revisionId", i.id AS "inspeccionId", p.id AS "puenteId",
                 p.codigo AS "puenteCodigo", p.nombre AS "puenteNombre",
                 i.autor_id AS "estudianteId", u.email AS "estudianteEmail",
                 i.nombre_autor_capturado AS "estudianteNombre",
                 c.id AS "cursoId", a.nombre AS "cursoNombre", c.periodo AS periodo,
                 ce.seccion AS seccion, r.numero_ciclo AS "numeroCiclo",
                 r.enviada_en AS "enviadaEn", r.iniciada_en AS "iniciadaEn",
                 i.estado AS "estadoInspeccion", r.estado AS "estadoRevision"
          FROM revision r
          JOIN inspeccion i ON i.id = r.inspeccion_id
          JOIN puente p ON p.id = i.puente_id
          JOIN usuario u ON u.id = i.autor_id
          JOIN asignacion_puente ap ON ap.id = i.asignacion_puente_id
          JOIN curso_estudiante ce ON ce.id = ap.curso_estudiante_id
          JOIN curso c ON c.id = ce.curso_id
          JOIN asignatura a ON a.id = c.asignatura_id
          WHERE r.revisor_id = :revisorId
            AND i.eliminado_en IS NULL
            AND i.estado IN ('ENVIADA', 'EN_REVISION')
            AND (:estado = '' OR i.estado = :estado)
            AND (lower(p.nombre) LIKE concat('%', lower(:puente), '%')
                 OR lower(p.codigo) LIKE concat('%', lower(:puente), '%'))
            AND (lower(u.email) LIKE concat('%', lower(:estudiante), '%')
                 OR lower(i.nombre_autor_capturado) LIKE concat('%', lower(:estudiante), '%'))
            AND (lower(a.nombre) LIKE concat('%', lower(:curso), '%')
                 OR lower(c.periodo) LIKE concat('%', lower(:curso), '%')
                 OR lower(ce.seccion) LIKE concat('%', lower(:curso), '%'))
            AND r.enviada_en >= :desde
            AND r.enviada_en < :hasta
          ORDER BY r.enviada_en ASC
          """,
      countQuery =
          """
          SELECT count(*)
          FROM revision r
          JOIN inspeccion i ON i.id = r.inspeccion_id
          JOIN puente p ON p.id = i.puente_id
          JOIN usuario u ON u.id = i.autor_id
          JOIN asignacion_puente ap ON ap.id = i.asignacion_puente_id
          JOIN curso_estudiante ce ON ce.id = ap.curso_estudiante_id
          JOIN curso c ON c.id = ce.curso_id
          JOIN asignatura a ON a.id = c.asignatura_id
          WHERE r.revisor_id = :revisorId
            AND i.eliminado_en IS NULL
            AND i.estado IN ('ENVIADA', 'EN_REVISION')
            AND (:estado = '' OR i.estado = :estado)
            AND (lower(p.nombre) LIKE concat('%', lower(:puente), '%')
                 OR lower(p.codigo) LIKE concat('%', lower(:puente), '%'))
            AND (lower(u.email) LIKE concat('%', lower(:estudiante), '%')
                 OR lower(i.nombre_autor_capturado) LIKE concat('%', lower(:estudiante), '%'))
            AND (lower(a.nombre) LIKE concat('%', lower(:curso), '%')
                 OR lower(c.periodo) LIKE concat('%', lower(:curso), '%')
                 OR lower(ce.seccion) LIKE concat('%', lower(:curso), '%'))
            AND r.enviada_en >= :desde
            AND r.enviada_en < :hasta
          """,
      nativeQuery = true)
  Page<RevisionBandejaProjection> buscarBandeja(
      @Param("revisorId") UUID revisorId,
      @Param("estado") String estado,
      @Param("puente") String puente,
      @Param("estudiante") String estudiante,
      @Param("curso") String curso,
      @Param("desde") Instant desde,
      @Param("hasta") Instant hasta,
      Pageable pageable);

  @Query(
      """
      select count(r) from Revision r
      where r.revisorId = :revisorId
        and r.estado in ('PENDIENTE', 'EN_CURSO')
        and r.inspeccion.estado in ('ENVIADA', 'EN_REVISION')
        and r.inspeccion.eliminadoEn is null
      """)
  long contarPendientes(@Param("revisorId") UUID revisorId);

  interface RevisionBandejaProjection {
    UUID getRevisionId();

    UUID getInspeccionId();

    UUID getPuenteId();

    String getPuenteCodigo();

    String getPuenteNombre();

    UUID getEstudianteId();

    String getEstudianteEmail();

    String getEstudianteNombre();

    UUID getCursoId();

    String getCursoNombre();

    String getPeriodo();

    String getSeccion();

    Integer getNumeroCiclo();

    Instant getEnviadaEn();

    Instant getIniciadaEn();

    String getEstadoInspeccion();

    String getEstadoRevision();
  }

  interface BorradorEstudianteProjection {
    UUID getInspeccionId();

    UUID getPuenteId();

    String getPuenteCodigo();

    String getPuenteNombre();

    UUID getEstudianteId();

    String getEstudianteEmail();

    String getEstudianteNombre();

    UUID getCursoId();

    String getCursoNombre();

    String getPeriodo();

    String getSeccion();

    Instant getUltimaActividadEn();
  }
}
