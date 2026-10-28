package gt.usac.cunoc.sgp.puente.repository;

import gt.usac.cunoc.sgp.puente.entity.AsignacionPuente;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsignacionPuenteRepository extends JpaRepository<AsignacionPuente, UUID> {

  @Query(
      """
          SELECT count(ap) > 0 FROM AsignacionPuente ap
          WHERE ap.id = :id AND ap.revocadoEn IS NULL
            AND ap.cursoEstudiante.estudiante.id = :autorId AND ap.puente.id = :puenteId
          """)
  boolean existsVigenteParaInspeccion(
      @Param("id") UUID id, @Param("autorId") UUID autorId, @Param("puenteId") UUID puenteId);

  @Query(
      """
          SELECT count(ap) > 0 FROM AsignacionPuente ap
          WHERE ap.id = :id AND ap.revocadoEn IS NULL
            AND ap.cursoEstudiante.estudiante.id = :autorId AND ap.puente.id = :puenteId
            AND ap.cursoEstudiante.curso.catedratico.id = :catedraticoId
          """)
  boolean existsVigenteParaLecturaFotografica(
      @Param("id") UUID id,
      @Param("autorId") UUID autorId,
      @Param("puenteId") UUID puenteId,
      @Param("catedraticoId") UUID catedraticoId);

  boolean existsByCursoEstudiante_IdAndPuente_IdAndRevocadoEnIsNull(
      UUID cursoEstudianteId, UUID puenteId);

  Optional<AsignacionPuente> findByIdAndCursoEstudiante_Curso_Catedratico_IdAndRevocadoEnIsNull(
      UUID id, UUID catedraticoId);

  @Query(
      """
          SELECT ce.id AS id, c.id AS cursoId, c.activo AS cursoActivo, c.fechaInicio AS fechaInicio, c.fechaFin AS fechaFin, ce.estado AS estado,
                 u.active AS estudianteActivo, u.activated AS estudianteActivado
          FROM CursoEstudiante ce
          JOIN ce.curso c
          JOIN ce.estudiante u
          WHERE ce.id = :id AND c.catedratico.id = :catedraticoId
          """)
  Optional<InscripcionAsignableProjection> findInscripcionParaAsignacion(
      @Param("id") UUID id, @Param("catedraticoId") UUID catedraticoId);

  @Query(
      """
          SELECT ce.id AS cursoEstudianteId, u.id AS estudianteId, u.email AS estudianteEmail,
                 c.id AS cursoId, c.fechaInicio AS fechaInicio, c.fechaFin AS fechaFin, a.nombre AS cursoNombre, c.periodo AS periodo,
                 u.nombreCompleto AS estudianteNombre
          FROM CursoEstudiante ce
          JOIN ce.curso c
          JOIN c.asignatura a
          JOIN ce.estudiante u
          WHERE c.catedratico.id = :catedraticoId
            AND c.activo = true
            AND ce.estado = 'ACTIVO'
            AND u.active = true
            AND u.activated = true
          ORDER BY c.periodo DESC, a.nombre, u.email
          """)
  List<EstudianteAsignableProjection> findEstudiantesAsignables(
      @Param("catedraticoId") UUID catedraticoId);

  @Query(
      """
          SELECT ap.id AS id, ce.id AS cursoEstudianteId, u.email AS estudianteEmail,
                 a.nombre AS cursoNombre, c.periodo AS periodo, p.id AS puenteId,
                 p.codigo AS puenteCodigo, p.nombre AS puenteNombre, ap.asignadoEn AS asignadoEn
          FROM AsignacionPuente ap
          JOIN ap.cursoEstudiante ce
          JOIN ce.curso c
          JOIN c.asignatura a
          JOIN ce.estudiante u
          JOIN ap.puente p
          WHERE c.catedratico.id = :catedraticoId AND ap.revocadoEn IS NULL
          ORDER BY ap.asignadoEn DESC
          """)
  List<AsignacionPuenteProjection> findActivasByCatedraticoId(
      @Param("catedraticoId") UUID catedraticoId);

  @Query(
      """
          SELECT ap.id AS id, ce.id AS cursoEstudianteId, u.email AS estudianteEmail,
                 a.nombre AS cursoNombre, c.periodo AS periodo, p.id AS puenteId,
                 p.codigo AS puenteCodigo, p.nombre AS puenteNombre, ap.asignadoEn AS asignadoEn
          FROM AsignacionPuente ap
          JOIN ap.cursoEstudiante ce
          JOIN ce.curso c
          JOIN c.asignatura a
          JOIN ce.estudiante u
          JOIN ap.puente p
          WHERE u.id = :estudianteId AND ap.revocadoEn IS NULL
          ORDER BY ap.asignadoEn DESC
          """)
  List<AsignacionPuenteProjection> findActivasByEstudianteId(
      @Param("estudianteId") UUID estudianteId);

  @Query(
      """
          SELECT ap.id AS id, ce.id AS cursoEstudianteId, u.email AS estudianteEmail,
                 a.nombre AS cursoNombre, c.periodo AS periodo, p.id AS puenteId,
                 p.codigo AS puenteCodigo, p.nombre AS puenteNombre, ap.asignadoEn AS asignadoEn
          FROM AsignacionPuente ap
          JOIN ap.cursoEstudiante ce
          JOIN ce.curso c
          JOIN c.asignatura a
          JOIN ce.estudiante u
          JOIN ap.puente p
          WHERE u.id = :estudianteId
            AND ap.revocadoEn IS NULL
            AND ce.estado = 'ACTIVO'
            AND c.activo = true
            AND c.fechaInicio <= :fecha
            AND c.fechaFin >= :fecha
            AND p.activo = true
          ORDER BY ap.asignadoEn DESC
          """)
  List<AsignacionPuenteProjection> findVigentesByEstudianteId(
      @Param("estudianteId") UUID estudianteId, @Param("fecha") LocalDate fecha);

  @Query(
      """
          SELECT ap.id AS id, c.catedratico.id AS catedraticoId
          FROM AsignacionPuente ap
          JOIN ap.cursoEstudiante ce
          JOIN ce.curso c
          JOIN ap.puente p
          WHERE ce.estudiante.id = :estudianteId
            AND p.id = :puenteId
            AND ap.revocadoEn IS NULL
            AND ce.estado = 'ACTIVO'
            AND c.activo = true
            AND c.fechaInicio <= :fecha
            AND c.fechaFin >= :fecha
          """)
  Optional<AsignacionInspeccionProjection> findVigenteParaInspeccion(
      @Param("estudianteId") UUID estudianteId,
      @Param("puenteId") UUID puenteId,
      @Param("fecha") LocalDate fecha);

  @Query(
      """
          SELECT ap.id AS id, c.catedratico.id AS catedraticoId
          FROM AsignacionPuente ap
          JOIN ap.cursoEstudiante ce
          JOIN ce.curso c
          WHERE ap.id = :asignacionId
            AND ce.estudiante.id = :estudianteId
            AND ap.revocadoEn IS NULL
            AND ce.estado = 'ACTIVO'
            AND c.activo = true
            AND c.fechaInicio <= :fecha
            AND c.fechaFin >= :fecha
          """)
  Optional<AsignacionInspeccionProjection> findVigentePorId(
      @Param("asignacionId") UUID asignacionId,
      @Param("estudianteId") UUID estudianteId,
      @Param("fecha") LocalDate fecha);

  @Query(
      """
          SELECT CASE WHEN count(ap) > 0 THEN true ELSE false END
          FROM AsignacionPuente ap
          WHERE ap.id = :asignacionId
            AND ap.cursoEstudiante.curso.catedratico.id = :catedraticoId
          """)
  boolean perteneceACatedratico(
      @Param("asignacionId") UUID asignacionId, @Param("catedraticoId") UUID catedraticoId);

  interface AsignacionPuenteProjection {
    UUID getId();

    UUID getCursoEstudianteId();

    String getEstudianteEmail();

    String getCursoNombre();

    String getPeriodo();

    UUID getPuenteId();

    String getPuenteCodigo();

    String getPuenteNombre();

    Instant getAsignadoEn();
  }

  interface InscripcionAsignableProjection {
    java.time.LocalDate getFechaInicio();

    java.time.LocalDate getFechaFin();

    UUID getId();

    UUID getCursoId();

    boolean getCursoActivo();

    String getEstado();

    boolean getEstudianteActivo();

    boolean getEstudianteActivado();
  }

  interface EstudianteAsignableProjection {
    java.time.LocalDate getFechaInicio();

    java.time.LocalDate getFechaFin();

    String getEstudianteNombre();

    UUID getCursoEstudianteId();

    UUID getEstudianteId();

    String getEstudianteEmail();

    UUID getCursoId();

    String getCursoNombre();

    String getPeriodo();
  }

  interface AsignacionInspeccionProjection {
    UUID getId();

    UUID getCatedraticoId();
  }

  @Query(
      value =
          "SELECT DISTINCT a.asignado_por_id FROM asignacion_puente a"
              + " JOIN usuario u ON u.id = a.asignado_por_id JOIN rol r ON r.id = u.rol_id"
              + " WHERE a.puente_id = :puenteId AND a.revocado_en IS NULL"
              + " AND r.nombre = 'CATEDRATICO'",
      nativeQuery = true)
  List<UUID> findCatedraticosConAsignacionVigente(@Param("puenteId") UUID puenteId);
}
