package gt.usac.cunoc.sgp.usuario.service;

import gt.usac.cunoc.sgp.common.audit.AuditService;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.curso.entity.Curso;
import gt.usac.cunoc.sgp.curso.entity.CursoEstudiante;
import gt.usac.cunoc.sgp.curso.repository.CursoEstudianteRepository;
import gt.usac.cunoc.sgp.curso.repository.CursoRepository;
import gt.usac.cunoc.sgp.usuario.dto.ActivarEstudianteRequest;
import gt.usac.cunoc.sgp.usuario.dto.CursoDisponibleResponse;
import gt.usac.cunoc.sgp.usuario.dto.EstudiantePendienteResponse;
import gt.usac.cunoc.sgp.usuario.dto.MessageResponse;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('CATEDRATICO')")
public class ActivacionEstudianteService {

  private final UserAccountRepository usuarios;
  private final CursoRepository cursos;
  private final CursoEstudianteRepository inscripciones;
  private final AuditService auditoria;
  private final Clock clock;

  public ActivacionEstudianteService(
      UserAccountRepository usuarios,
      CursoRepository cursos,
      CursoEstudianteRepository inscripciones,
      AuditService auditoria,
      Clock clock) {
    this.usuarios = usuarios;
    this.cursos = cursos;
    this.inscripciones = inscripciones;
    this.auditoria = auditoria;
    this.clock = clock;
  }

  public List<EstudiantePendienteResponse> listarPendientes() {
    return usuarios
        .findByRole_NameAndActiveTrueAndVerifiedTrueAndActivatedFalseOrderByEmailAsc(
            RoleName.ESTUDIANTE)
        .stream()
        .map(estudiante -> new EstudiantePendienteResponse(estudiante.getId(), estudiante.getEmail()))
        .toList();
  }

  public List<CursoDisponibleResponse> listarCursosVigentes(String emailCatedratico) {
    UserAccount catedratico = buscarCatedratico(emailCatedratico);
    return cursos.findByCatedratico_IdAndActivoTrueOrderByPeriodoDesc(catedratico.getId()).stream()
        .map(
            curso ->
                new CursoDisponibleResponse(
                    curso.getId(), curso.getAsignatura().getNombre(), curso.getPeriodo()))
        .toList();
  }

  @Transactional
  public MessageResponse activar(String emailCatedratico, ActivarEstudianteRequest request) {
    UserAccount catedratico = buscarCatedratico(emailCatedratico);
    UserAccount estudiante = buscarEstudiante(request.estudianteId());
    Curso curso = buscarCursoDelCatedratico(request.cursoId(), catedratico.getId());

    if (!curso.isActivo()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "curso_finalizado",
          "Curso finalizado",
          "No es posible vincular estudiantes a un curso finalizado");
    }
    if (inscripciones.existsByEstudiante_IdAndEstado(estudiante.getId(), "ACTIVO")) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "estudiante_ya_activado",
          "Estudiante ya activado",
          "El estudiante ya se encuentra activo y vinculado a un curso");
    }
    if (estudiante.isActivated()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "estudiante_no_pendiente",
          "Estudiante no pendiente",
          "La cuenta ya no se encuentra pendiente de activacion");
    }

    Instant ahora = clock.instant();
    CursoEstudiante inscripcion =
        inscripciones.save(
            new CursoEstudiante(
                curso, estudiante, request.seccion().trim(), catedratico, ahora));
    estudiante.activate();
    auditoria.register(
        catedratico.getId(),
        "ACTIVAR_ESTUDIANTE",
        "curso_estudiante",
        inscripcion.getId(),
        Map.of(
            "estudianteId", estudiante.getId(),
            "cursoId", curso.getId(),
            "seccion", request.seccion().trim()),
        ahora);
    return new MessageResponse("Estudiante activado y vinculado al curso correctamente");
  }

  private UserAccount buscarCatedratico(String email) {
    return usuarios
        .findByEmail(email)
        .filter(usuario -> usuario.isActive() && usuario.getRole().getName() == RoleName.CATEDRATICO)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.FORBIDDEN,
                    "catedratico_invalido",
                    "Catedratico invalido",
                    "Solo un catedratico activo puede activar estudiantes"));
  }

  private UserAccount buscarEstudiante(UUID id) {
    return usuarios
        .findWithRoleById(id)
        .filter(
            usuario ->
                usuario.getRole().getName() == RoleName.ESTUDIANTE
                    && usuario.isVerified())
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "estudiante_no_pendiente",
                    "Estudiante no pendiente",
                    "La cuenta debe ser un estudiante verificado pendiente de activacion"));
  }

  private Curso buscarCursoDelCatedratico(UUID id, UUID catedraticoId) {
    return cursos
        .findByIdAndCatedratico_Id(id, catedraticoId)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "curso_no_encontrado",
                    "Curso no encontrado",
                    "No existe un curso asignado al catedratico solicitado"));
  }
}
