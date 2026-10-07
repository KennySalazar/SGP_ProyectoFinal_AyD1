package gt.usac.cunoc.sgp.puente.repository;

import gt.usac.cunoc.sgp.puente.entity.AsignacionPuente;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsignacionPuenteRepository extends JpaRepository<AsignacionPuente, UUID> {

  boolean existsByCursoEstudiante_IdAndPuente_IdAndRevocadoEnIsNull(
      UUID cursoEstudianteId, UUID puenteId);

  Optional<AsignacionPuente> findByIdAndCursoEstudiante_Curso_Catedratico_IdAndRevocadoEnIsNull(
      UUID id, UUID catedraticoId);

  @Query(
      """
      SELECT ce.id AS id, c.id AS cursoId, c.activo AS cursoActivo, ce.estado AS estado,
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
             c.id AS cursoId, a.nombre AS cursoNombre, c.periodo AS periodo
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
    UUID getId();

    UUID getCursoId();

    boolean getCursoActivo();

    String getEstado();

    boolean getEstudianteActivo();

    boolean getEstudianteActivado();
  }

  interface EstudianteAsignableProjection {
    UUID getCursoEstudianteId();

    UUID getEstudianteId();

    String getEstudianteEmail();

    UUID getCursoId();

    String getCursoNombre();

    String getPeriodo();
  }
}
