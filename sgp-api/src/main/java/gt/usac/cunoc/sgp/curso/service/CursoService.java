package gt.usac.cunoc.sgp.curso.service;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.curso.dto.ActualizarCursoRequest;
import gt.usac.cunoc.sgp.curso.dto.CatedraticoResponse;
import gt.usac.cunoc.sgp.curso.dto.CrearCursoRequest;
import gt.usac.cunoc.sgp.curso.dto.CursoResponse;
import gt.usac.cunoc.sgp.curso.entity.Asignatura;
import gt.usac.cunoc.sgp.curso.entity.Curso;
import gt.usac.cunoc.sgp.curso.mapper.CursoMapper;
import gt.usac.cunoc.sgp.curso.repository.AsignaturaRepository;
import gt.usac.cunoc.sgp.curso.repository.CursoRepository;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class CursoService {

  private final AsignaturaRepository asignaturas;
  private final CursoRepository cursos;
  private final UserAccountRepository usuarios;
  private final CursoMapper mapper;
  private final Clock clock;

  public CursoService(
      AsignaturaRepository asignaturas,
      CursoRepository cursos,
      UserAccountRepository usuarios,
      CursoMapper mapper,
      Clock clock) {
    this.asignaturas = asignaturas;
    this.cursos = cursos;
    this.usuarios = usuarios;
    this.mapper = mapper;
    this.clock = clock;
  }

  public Page<CursoResponse> listar(int pagina, int tamanio) {
    return cursos.findAll(paginacion(pagina, tamanio)).map(mapper::toResponse);
  }

  public List<CatedraticoResponse> listarCatedraticos() {
    return usuarios.findByRole_NameAndActiveTrueOrderByEmailAsc(RoleName.CATEDRATICO).stream()
        .map(mapper::toCatedraticoResponse)
        .toList();
  }

  @Transactional
  public CursoResponse crear(CrearCursoRequest request) {
    String nombre = request.nombre().trim();
    String periodo = request.periodo().trim();

    if (cursos.existsByAsignatura_NombreIgnoreCaseAndPeriodo(nombre, periodo)) {
      throw conflicto("Ya existe un curso con el mismo nombre y periodo");
    }

    var catedratico = buscarCatedratico(request.catedraticoId());

    Instant ahora = clock.instant();
    Asignatura asignatura =
        asignaturas
            .findByNombreIgnoreCase(nombre)
            .orElseGet(
                () ->
                    asignaturas.save(
                        new Asignatura(
                            "ASIG-" + UUID.randomUUID().toString().replace("-", ""),
                            nombre,
                            ahora)));

    Curso curso =
        cursos.save(
            new Curso(
                asignatura,
                periodo,
                catedratico,
                request.fechaInicio(),
                request.fechaFin(),
                ahora));
    return mapper.toResponse(curso);
  }

  @Transactional
  public CursoResponse actualizar(UUID id, ActualizarCursoRequest request) {
    Curso curso = buscarCurso(id);
    String nombre = request.nombre().trim();
    String periodo = request.periodo().trim();
    if (cursos.existsByAsignatura_NombreIgnoreCaseAndPeriodoAndIdNot(nombre, periodo, id)) {
      throw conflicto("Ya existe un curso con el mismo nombre y periodo");
    }

    Instant ahora = clock.instant();
    Asignatura asignatura =
        asignaturas
            .findByNombreIgnoreCase(nombre)
            .orElseGet(
                () ->
                    asignaturas.save(
                        new Asignatura(
                            "ASIG-" + UUID.randomUUID().toString().replace("-", ""),
                            nombre,
                            ahora)));
    curso.actualizar(
        asignatura,
        periodo,
        buscarCatedratico(request.catedraticoId()),
        request.fechaInicio(),
        request.fechaFin(),
        ahora);
    return mapper.toResponse(curso);
  }

  @Transactional
  public CursoResponse finalizar(UUID id) {
    Curso curso = buscarCurso(id);
    if (!curso.isActivo()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "curso_finalizado",
          "Curso finalizado",
          "Solo es posible finalizar un curso vigente");
    }
    curso.finalizar(clock.instant());
    return mapper.toResponse(curso);
  }

  private PageRequest paginacion(int pagina, int tamanio) {
    if (pagina < 0 || tamanio < 1 || tamanio > 100) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "paginacion_invalida",
          "Paginacion invalida",
          "La pagina debe ser mayor o igual a cero y el tamano debe estar entre 1 y 100");
    }
    return PageRequest.of(pagina, tamanio, Sort.by("periodo").descending().and(Sort.by("id")));
  }

  private ApiException conflicto(String detalle) {
    return new ApiException(HttpStatus.CONFLICT, "curso_duplicado", "Curso duplicado", detalle);
  }

  private Curso buscarCurso(UUID id) {
    return cursos
        .findById(id)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "curso_no_encontrado",
                    "Curso no encontrado",
                    "No existe el curso solicitado"));
  }

  private UserAccount buscarCatedratico(UUID id) {
    return usuarios
        .findWithRoleById(id)
        .filter(
            usuario -> usuario.isActive() && usuario.getRole().getName() == RoleName.CATEDRATICO)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "catedratico_invalido",
                    "Catedratico invalido",
                    "El responsable debe ser un catedratico activo"));
  }
}
