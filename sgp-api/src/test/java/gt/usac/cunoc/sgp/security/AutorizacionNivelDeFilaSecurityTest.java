package gt.usac.cunoc.sgp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.AuditService;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.curso.entity.Curso;
import gt.usac.cunoc.sgp.curso.repository.CursoEstudianteRepository;
import gt.usac.cunoc.sgp.curso.repository.CursoRepository;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.mapper.InspeccionMapper;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.service.InspeccionService;
import gt.usac.cunoc.sgp.puente.repository.AsignacionPuenteRepository;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.usuario.dto.ActivarEstudianteRequest;
import gt.usac.cunoc.sgp.usuario.entity.Role;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import gt.usac.cunoc.sgp.usuario.service.ActivacionEstudianteService;
import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pruebas de autorización a nivel de fila (DT-SEC-04):
 *
 * <ul>
 *   <li>Un Estudiante solo accede a sus propios borradores de inspección.
 *   <li>Un Catedrático solo accede y gestiona los cursos que dirige.
 *   <li>Un recurso ajeno no revela su existencia (retorna 404 sin información).
 * </ul>
 */
class AutorizacionNivelDeFilaSecurityTest {

  private InspeccionRepository inspeccionRepository;
  private InspeccionService inspeccionService;

  private CursoRepository cursoRepository;
  private CursoEstudianteRepository cursoEstudianteRepository;
  private UserAccountRepository userAccountRepository;
  private AuditService auditService;
  private ActivacionEstudianteService activacionEstudianteService;

  private UUID estudianteAId;
  private UUID estudianteBId;
  private UUID catedraticoAId;
  private UUID catedraticoBId;
  private UUID cursoAId;
  private UUID inspeccionAId;

  @BeforeEach
  void setUp() {
    estudianteAId = UUID.randomUUID();
    estudianteBId = UUID.randomUUID();
    catedraticoAId = UUID.randomUUID();
    catedraticoBId = UUID.randomUUID();
    cursoAId = UUID.randomUUID();
    inspeccionAId = UUID.randomUUID();

    // Setup InspeccionService
    inspeccionRepository = mock(InspeccionRepository.class);
    inspeccionService =
        new InspeccionService(
            inspeccionRepository,
            mock(VersionFormularioRepository.class),
            mock(PuenteRepository.class),
            mock(JsonSchemaValidatorService.class),
            mock(InspeccionMapper.class),
            new ObjectMapper(),
            mock(AsignacionPuenteRepository.class));

    // Setup ActivacionEstudianteService
    cursoRepository = mock(CursoRepository.class);
    cursoEstudianteRepository = mock(CursoEstudianteRepository.class);
    userAccountRepository = mock(UserAccountRepository.class);
    auditService = mock(AuditService.class);
    activacionEstudianteService =
        new ActivacionEstudianteService(
            userAccountRepository,
            cursoRepository,
            cursoEstudianteRepository,
            auditService,
            Clock.fixed(Instant.parse("2026-10-28T18:00:00Z"), ZoneOffset.UTC));
  }

  // --- REGLA 1: Estudiante solo accede a sus propios borradores (DT-SEC-04) ---

  @Test
  @DisplayName("DT-SEC-04: Estudiante autor de borrador accede a su inspección")
  void estudianteAutorAccedeASuBorrador() {
    gt.usac.cunoc.sgp.puente.entity.Puente mockPuente =
        mock(gt.usac.cunoc.sgp.puente.entity.Puente.class);
    when(mockPuente.getId()).thenReturn(UUID.randomUUID());
    when(mockPuente.getNombre()).thenReturn("Puente Central");

    gt.usac.cunoc.sgp.formulario.entity.VersionFormulario mockVersion =
        mock(gt.usac.cunoc.sgp.formulario.entity.VersionFormulario.class);
    when(mockVersion.getCodigo()).thenReturn("sieca-v1");

    Inspeccion inspeccion = mock(Inspeccion.class);
    when(inspeccion.getId()).thenReturn(inspeccionAId);
    when(inspeccion.getAutorId()).thenReturn(estudianteAId);
    when(inspeccion.getEstado()).thenReturn("BORRADOR");
    when(inspeccion.getEliminadoEn()).thenReturn(null);
    when(inspeccion.getPuente()).thenReturn(mockPuente);
    when(inspeccion.getVersionFormulario()).thenReturn(mockVersion);
    when(inspeccionRepository.findConDetallesById(inspeccionAId))
        .thenReturn(Optional.of(inspeccion));

    var response = inspeccionService.obtenerBorrador(inspeccionAId, estudianteAId);
    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo(inspeccionAId);
  }

  @Test
  @DisplayName(
      "DT-SEC-04: Estudiante ajeno recibe 404 al intentar consultar borrador de otro estudiante")
  void estudianteAjenoRecibe404AlConsultarBorrador() {
    Inspeccion inspeccion = mock(Inspeccion.class);
    when(inspeccion.getId()).thenReturn(inspeccionAId);
    when(inspeccion.getAutorId()).thenReturn(estudianteAId); // Autor es estudiante A
    when(inspeccion.getEstado()).thenReturn("BORRADOR");
    when(inspeccion.getEliminadoEn()).thenReturn(null);
    when(inspeccionRepository.findConDetallesById(inspeccionAId))
        .thenReturn(Optional.of(inspeccion));

    // Estudiante B intenta acceder al borrador de Estudiante A -> 404 Not Found (no revela
    // existencia)
    assertThatThrownBy(() -> inspeccionService.obtenerBorrador(inspeccionAId, estudianteBId))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e -> {
              ApiException apiEx = (ApiException) e;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(apiEx.getCode()).isEqualTo("inspeccion_no_encontrada");
            });
  }

  // --- REGLA 2: Catedrático solo accede a los cursos que dirige (DT-SEC-04) ---

  @Test
  @DisplayName(
      "DT-SEC-04: Catedrático titular puede vincular y activar estudiante en su propio curso")
  void catedraticoTitularPuedeGestionarSuCurso() {
    Role rolCatedratico = crearRol(RoleName.CATEDRATICO);
    UserAccount catedraticoA =
        new UserAccount("profesorA@cunoc.edu.gt", "hash", rolCatedratico, true, true);
    catedraticoA.activate();
    ReflectionTestUtils.setField(catedraticoA, "id", catedraticoAId);

    Role rolEstudiante = crearRol(RoleName.ESTUDIANTE);
    UserAccount estudiante =
        new UserAccount("alumno@cunoc.edu.gt", "hash", rolEstudiante, false, false);
    estudiante.verify();
    ReflectionTestUtils.setField(estudiante, "id", estudianteAId);

    Curso cursoA = mock(Curso.class);
    when(cursoA.getId()).thenReturn(cursoAId);
    when(cursoA.isActivo()).thenReturn(true);
    when(cursoA.getFechaInicio()).thenReturn(LocalDate.of(2026, 7, 1));
    when(cursoA.getFechaFin()).thenReturn(LocalDate.of(2026, 12, 31));

    when(userAccountRepository.findByEmail("profesorA@cunoc.edu.gt"))
        .thenReturn(Optional.of(catedraticoA));
    when(userAccountRepository.findWithRoleById(catedraticoAId))
        .thenReturn(Optional.of(catedraticoA));
    when(userAccountRepository.findWithRoleById(estudianteAId)).thenReturn(Optional.of(estudiante));
    when(cursoRepository.findByIdAndCatedratico_Id(cursoAId, catedraticoAId))
        .thenReturn(Optional.of(cursoA));
    when(cursoEstudianteRepository.existsByEstudiante_IdAndEstado(estudianteAId, "ACTIVO"))
        .thenReturn(false);
    when(cursoEstudianteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ActivarEstudianteRequest request = new ActivarEstudianteRequest(estudianteAId, cursoAId, "A");

    var response = activacionEstudianteService.activar("profesorA@cunoc.edu.gt", request);
    assertThat(response).isNotNull();
    assertThat(response.message()).contains("Estudiante activado y vinculado");
  }

  @Test
  @DisplayName(
      "DT-SEC-04: Catedrático ajeno recibe 404 al intentar gestionar curso de otro catedrático")
  void catedraticoAjenoRecibe404AlGestionarCursoAjeno() {
    Role rolCatedratico = crearRol(RoleName.CATEDRATICO);
    UserAccount catedraticoB =
        new UserAccount("profesorB@cunoc.edu.gt", "hash", rolCatedratico, true, true);
    catedraticoB.activate();
    ReflectionTestUtils.setField(catedraticoB, "id", catedraticoBId);

    Role rolEstudiante = crearRol(RoleName.ESTUDIANTE);
    UserAccount estudiante =
        new UserAccount("alumno@cunoc.edu.gt", "hash", rolEstudiante, false, false);
    estudiante.verify();
    ReflectionTestUtils.setField(estudiante, "id", estudianteAId);

    when(userAccountRepository.findByEmail("profesorB@cunoc.edu.gt"))
        .thenReturn(Optional.of(catedraticoB));
    when(userAccountRepository.findWithRoleById(catedraticoBId))
        .thenReturn(Optional.of(catedraticoB));
    when(userAccountRepository.findWithRoleById(estudianteAId)).thenReturn(Optional.of(estudiante));
    // Curso A NO pertenece a Catedrático B -> findByIdAndCatedratico_Id retorna vacío
    when(cursoRepository.findByIdAndCatedratico_Id(cursoAId, catedraticoBId))
        .thenReturn(Optional.empty());

    ActivarEstudianteRequest request = new ActivarEstudianteRequest(estudianteAId, cursoAId, "A");

    assertThatThrownBy(() -> activacionEstudianteService.activar("profesorB@cunoc.edu.gt", request))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e -> {
              ApiException apiEx = (ApiException) e;
              // No revela la existencia del curso ajeno al otro catedrático (404 Not Found)
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(apiEx.getCode()).isEqualTo("curso_no_encontrado");
            });
  }

  private Role crearRol(RoleName name) {
    try {
      Constructor<Role> c = Role.class.getDeclaredConstructor();
      c.setAccessible(true);
      Role r = c.newInstance();
      ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
      ReflectionTestUtils.setField(r, "name", name);
      ReflectionTestUtils.setField(r, "description", name.name());
      return r;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
