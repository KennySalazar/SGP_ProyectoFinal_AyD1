package gt.usac.cunoc.sgp.puente.service;

import gt.usac.cunoc.sgp.common.audit.AuditService;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.curso.repository.CursoEstudianteRepository;
import gt.usac.cunoc.sgp.puente.dto.AsignacionPuenteResponse;
import gt.usac.cunoc.sgp.puente.dto.AsignarPuenteRequest;
import gt.usac.cunoc.sgp.puente.dto.EstudianteAsignableResponse;
import gt.usac.cunoc.sgp.puente.dto.PuenteAsignableResponse;
import gt.usac.cunoc.sgp.puente.dto.RevocarAsignacionPuenteRequest;
import gt.usac.cunoc.sgp.puente.entity.AsignacionPuente;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.AsignacionPuenteRepository;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
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
public class AsignacionPuenteService {

  private final UserAccountRepository usuarios;
  private final CursoEstudianteRepository inscripciones;
  private final PuenteRepository puentes;
  private final AsignacionPuenteRepository asignaciones;
  private final AuditService auditoria;
  private final Clock clock;

  public AsignacionPuenteService(
      UserAccountRepository usuarios,
      CursoEstudianteRepository inscripciones,
      PuenteRepository puentes,
      AsignacionPuenteRepository asignaciones,
      AuditService auditoria,
      Clock clock) {
    this.usuarios = usuarios;
    this.inscripciones = inscripciones;
    this.puentes = puentes;
    this.asignaciones = asignaciones;
    this.auditoria = auditoria;
    this.clock = clock;
  }

  @PreAuthorize("hasRole('CATEDRATICO')")
  public List<EstudianteAsignableResponse> listarEstudiantesAsignables(String emailCatedratico) {
    UserAccount catedratico = buscarCatedratico(emailCatedratico);
    return asignaciones.findEstudiantesAsignables(catedratico.getId()).stream()
        .map(
            inscripcion ->
                new EstudianteAsignableResponse(
                    inscripcion.getCursoEstudianteId(),
                    inscripcion.getEstudianteId(),
                    inscripcion.getEstudianteEmail(),
                    inscripcion.getCursoId(),
                    inscripcion.getCursoNombre(),
                    inscripcion.getPeriodo()))
        .toList();
  }

  @PreAuthorize("hasRole('CATEDRATICO')")
  public List<PuenteAsignableResponse> listarPuentesAsignables(String emailCatedratico) {
    buscarCatedratico(emailCatedratico);
    return puentes.findCatalogoActivo(null, org.springframework.data.domain.Pageable.unpaged()).stream()
        .map(puente -> new PuenteAsignableResponse(puente.getId(), puente.getCodigo(), puente.getNombre()))
        .toList();
  }

  @PreAuthorize("hasRole('CATEDRATICO')")
  public List<AsignacionPuenteResponse> listarAsignaciones(String emailCatedratico) {
    return asignaciones.findActivasByCatedraticoId(buscarCatedratico(emailCatedratico).getId()).stream()
        .map(this::respuesta)
        .toList();
  }

  @Transactional
  @PreAuthorize("hasRole('CATEDRATICO')")
  public MessageResponse asignar(String emailCatedratico, AsignarPuenteRequest request) {
    UserAccount catedratico = buscarCatedratico(emailCatedratico);
    var inscripcion =
        asignaciones
            .findInscripcionParaAsignacion(request.cursoEstudianteId(), catedratico.getId())
            .orElseThrow(() -> inscripcionNoEncontrada());
    validarInscripcion(inscripcion);
    Puente puente =
        puentes
            .findById(request.puenteId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "puente_no_encontrado",
                        "Puente no encontrado",
                        "No existe el puente solicitado"));
    if (!puente.isActivo()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "puente_inactivo",
          "Puente inactivo",
          "No es posible asignar un puente inactivo");
    }
    if (asignaciones.existsByCursoEstudiante_IdAndPuente_IdAndRevocadoEnIsNull(
        request.cursoEstudianteId(), request.puenteId())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "puente_ya_asignado",
          "Puente ya asignado",
          "El puente ya está asignado a este estudiante en el curso actual");
    }

    Instant ahora = clock.instant();
    AsignacionPuente asignacion =
        asignaciones.save(
            new AsignacionPuente(
                inscripciones.getReferenceById(inscripcion.getId()), puente, catedratico, ahora));
    auditoria.register(
        catedratico.getId(),
        "ASIGNAR_PUENTE",
        "asignacion_puente",
        asignacion.getId(),
        Map.of(
            "cursoEstudianteId", request.cursoEstudianteId(),
            "puenteId", puente.getId(),
            "cursoId", inscripcion.getCursoId()),
        ahora);
    return new MessageResponse("Puente asignado al estudiante correctamente");
  }

  @Transactional
  @PreAuthorize("hasRole('CATEDRATICO')")
  public MessageResponse revocar(
      String emailCatedratico, UUID asignacionId, RevocarAsignacionPuenteRequest request) {
    UserAccount catedratico = buscarCatedratico(emailCatedratico);
    AsignacionPuente asignacion =
        asignaciones
            .findByIdAndCursoEstudiante_Curso_Catedratico_IdAndRevocadoEnIsNull(
                asignacionId, catedratico.getId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "asignacion_no_encontrada",
                        "Asignación no encontrada",
                        "No existe una asignación activa para revocar"));
    Instant ahora = clock.instant();
    String motivo = request.motivo().trim();
    asignacion.revocar(motivo, ahora);
    auditoria.register(
        catedratico.getId(),
        "REVOCAR_ASIGNACION_PUENTE",
        "asignacion_puente",
        asignacion.getId(),
        Map.of("revocadoEn", ahora.toString(), "motivoRevocacion", motivo),
        ahora);
    return new MessageResponse("Asignación de puente revocada correctamente");
  }

  @PreAuthorize("hasRole('ESTUDIANTE')")
  public List<AsignacionPuenteResponse> listarMisPuentes(String emailEstudiante) {
    UserAccount estudiante = buscarEstudianteActivo(emailEstudiante);
    return asignaciones.findActivasByEstudianteId(estudiante.getId()).stream()
        .map(this::respuesta)
        .toList();
  }

  private void validarInscripcion(AsignacionPuenteRepository.InscripcionAsignableProjection inscripcion) {
    if (!inscripcion.getCursoActivo()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "curso_finalizado",
          "Curso finalizado",
          "No es posible asignar puentes en un curso finalizado");
    }
    if (!"ACTIVO".equals(inscripcion.getEstado())) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "estudiante_no_vinculado",
          "Estudiante no vinculado",
          "El estudiante no tiene una vinculación activa con el curso");
    }
    if (!inscripcion.getEstudianteActivo() || !inscripcion.getEstudianteActivado()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "estudiante_inactivo",
          "Estudiante inactivo",
          "El estudiante debe estar activo para recibir asignaciones");
    }
  }

  private ApiException inscripcionNoEncontrada() {
    return new ApiException(
        HttpStatus.NOT_FOUND,
        "inscripcion_no_encontrada",
        "Inscripción no encontrada",
        "El estudiante no está vinculado a uno de tus cursos");
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
                    "Solo un catedratico activo puede gestionar asignaciones"));
  }

  private UserAccount buscarEstudianteActivo(String email) {
    return usuarios
        .findByEmail(email)
        .filter(
            usuario ->
                usuario.isActive()
                    && usuario.isActivated()
                    && usuario.getRole().getName() == RoleName.ESTUDIANTE)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.FORBIDDEN,
                    "estudiante_invalido",
                    "Estudiante invalido",
                    "Solo un estudiante activo puede consultar sus asignaciones"));
  }

  private AsignacionPuenteResponse respuesta(
      AsignacionPuenteRepository.AsignacionPuenteProjection asignacion) {
    return new AsignacionPuenteResponse(
        asignacion.getId(),
        asignacion.getCursoEstudianteId(),
        asignacion.getEstudianteEmail(),
        asignacion.getCursoNombre(),
        asignacion.getPeriodo(),
        asignacion.getPuenteId(),
        asignacion.getPuenteCodigo(),
        asignacion.getPuenteNombre(),
        asignacion.getAsignadoEn());
  }
}
