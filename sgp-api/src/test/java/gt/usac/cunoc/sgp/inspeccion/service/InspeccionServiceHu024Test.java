package gt.usac.cunoc.sgp.inspeccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.inspeccion.dto.AcompananteRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.GuardarBorradorRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionBorradorResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.entity.InspeccionDatoTecnico;
import gt.usac.cunoc.sgp.inspeccion.model.EntradaCalculoIc;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionDatoTecnicoRepository;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.AsignacionPuenteRepository;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.sync.entity.OperacionIdempotente;
import gt.usac.cunoc.sgp.sync.repository.OperacionIdempotenteRepository;
import gt.usac.cunoc.sgp.usuario.entity.Role;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.entity.UsuarioProfesional;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import gt.usac.cunoc.sgp.usuario.repository.UsuarioProfesionalRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InspeccionServiceHu024Test {

  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(), 4326);
  private static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(AHORA, ZoneId.of("America/Guatemala"));

  @Mock private InspeccionRepository inspeccionRepository;
  @Mock private InspeccionDatoTecnicoRepository datoTecnicoRepository;
  @Mock private PuenteRepository puenteRepository;
  @Mock private AsignacionPuenteRepository asignacionPuenteRepository;
  @Mock private VersionFormularioRepository versionFormularioRepository;
  @Mock private UserAccountRepository userAccountRepository;
  @Mock private UsuarioProfesionalRepository profesionalRepository;
  @Mock private OperacionIdempotenteRepository operacionIdempotenteRepository;
  @Mock private AuditoriaWriter auditoriaWriter;
  @Mock private PreparadorEntradaIcService preparadorEntradaIcService;
  @Mock private CalculoIndiceCondicionService calculoIndiceCondicionService;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private InspeccionService inspeccionService;

  private UserAccount catedratico;
  private UserAccount profesionalExterno;
  private UserAccount estudiante;
  private Puente puenteActivo;
  private VersionFormulario versionActiva;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient()
        .when(operacionIdempotenteRepository.saveAndFlush(any(OperacionIdempotente.class)))
        .thenAnswer(invocacion -> invocacion.getArgument(0));
    inspeccionService =
        new InspeccionService(
            inspeccionRepository,
            datoTecnicoRepository,
            puenteRepository,
            versionFormularioRepository,
            userAccountRepository,
            profesionalRepository,
            new gt.usac.cunoc.sgp.sync.service.OperacionIdempotenteService(
                operacionIdempotenteRepository,
                org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class),
                objectMapper,
                CLOCK),
            auditoriaWriter,
            objectMapper,
            CLOCK,
            new CalculadorProgresoBorrador(),
            preparadorEntradaIcService,
            calculoIndiceCondicionService);
    inspeccionService.setAsignacionPuenteRepository(asignacionPuenteRepository);

    Role rolCatedratico = crearRol(RoleName.CATEDRATICO);
    Role rolProfesional = crearRol(RoleName.PROFESIONAL_EXTERNO);
    Role rolEstudiante = crearRol(RoleName.ESTUDIANTE);

    catedratico = new UserAccount("profesor@cunoc.edu.gt", "hash", rolCatedratico, true, true);
    catedratico.activate();
    ReflectionTestUtils.setField(catedratico, "id", UUID.randomUUID());

    profesionalExterno = new UserAccount("ing@colegio.org.gt", "hash", rolProfesional, true, true);
    profesionalExterno.activate();
    ReflectionTestUtils.setField(profesionalExterno, "id", UUID.randomUUID());

    estudiante = new UserAccount("alumno@cunoc.edu.gt", "hash", rolEstudiante, true, true);
    estudiante.activate();
    ReflectionTestUtils.setField(estudiante, "id", UUID.randomUUID());

    puenteActivo =
        new Puente(
            UUID.randomUUID(),
            "P-01-01-0001",
            1,
            null,
            "Puente Los Esclavos",
            "CA-1 Oriente",
            new BigDecimal("65.200"),
            GEOMETRY_FACTORY.createPoint(new Coordinate(-90.5132, 14.6407)),
            UUID.randomUUID(),
            AHORA);

    versionActiva = crearVersion("sieca-v1");
  }

  private Role crearRol(RoleName name) {
    try {
      java.lang.reflect.Constructor<Role> c = Role.class.getDeclaredConstructor();
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

  private VersionFormulario crearVersion(String codigo) {
    try {
      java.lang.reflect.Constructor<VersionFormulario> c =
          VersionFormulario.class.getDeclaredConstructor();
      c.setAccessible(true);

      VersionFormulario v = c.newInstance();

      ReflectionTestUtils.setField(v, "id", UUID.randomUUID());
      ReflectionTestUtils.setField(v, "codigo", codigo);
      ReflectionTestUtils.setField(v, "activa", true);
      ReflectionTestUtils.setField(v, "estado", EstadoVersionFormulario.PUBLICADA);

      return v;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @DisplayName("Catedrático inicia inspección exitosa en estado BORRADOR con auditoría")
  void iniciarInspeccion_exitoso_catedratico() {
    UUID usuarioId = catedratico.getId();
    when(userAccountRepository.findWithRoleById(usuarioId)).thenReturn(Optional.of(catedratico));
    when(puenteRepository.findById(puenteActivo.getId())).thenReturn(Optional.of(puenteActivo));
    when(versionFormularioRepository.findByActivaTrue()).thenReturn(Optional.of(versionActiva));

    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            null,
            puenteActivo.getId(),
            LocalDate.of(2026, 10, 15),
            "dispositivo-pixel-7",
            "Ing. Juan Pérez",
            14.6407,
            -90.5132,
            new BigDecimal("4.50"),
            false,
            List.of(new AcompananteRequest(null, "Carlos Acompañante", "COL-1234")));

    IniciarInspeccionResponse response =
        inspeccionService.iniciarInspeccionHu024(request, usuarioId, null);

    assertThat(response).isNotNull();
    assertThat(response.estado()).isEqualTo("BORRADOR");
    assertThat(response.nombreAutor()).isEqualTo("Ing. Juan Pérez");
    assertThat(response.rolAutor()).isEqualTo("CATEDRATICO");
    assertThat(response.codigoPuente()).isEqualTo("P-01-01-0001");
    assertThat(response.codigoVersionFormulario()).isEqualTo("sieca-v1");
    assertThat(response.dispositivoId()).isEqualTo("dispositivo-pixel-7");
    assertThat(response.advertenciaGps()).isFalse();
    assertThat(response.acompanantes()).hasSize(1);
    assertThat(response.acompanantes().get(0).nombre()).isEqualTo("Carlos Acompañante");

    verify(inspeccionRepository).save(any(Inspeccion.class));
    verify(auditoriaWriter)
        .registrarEnTransaccion(
            eq(usuarioId),
            eq(AccionAuditoria.CREAR),
            eq("inspeccion"),
            eq(response.id()),
            eq(null),
            any(),
            eq("HU-024"));
  }

  @Test
  @DisplayName("Profesional Externo con colegiado verificado puede iniciar inspección")
  void iniciarInspeccion_exitoso_profesionalVerificado() {
    UUID usuarioId = profesionalExterno.getId();
    UsuarioProfesional prof = new UsuarioProfesional(profesionalExterno, "COL-9999", AHORA);
    ReflectionTestUtils.setField(prof, "colegiadoVerificadoEn", AHORA);

    when(userAccountRepository.findWithRoleById(usuarioId))
        .thenReturn(Optional.of(profesionalExterno));
    when(profesionalRepository.findById(usuarioId)).thenReturn(Optional.of(prof));
    when(puenteRepository.findById(puenteActivo.getId())).thenReturn(Optional.of(puenteActivo));
    when(versionFormularioRepository.findByActivaTrue()).thenReturn(Optional.of(versionActiva));

    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            null,
            puenteActivo.getId(),
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            false,
            null);

    IniciarInspeccionResponse response =
        inspeccionService.iniciarInspeccionHu024(request, usuarioId, null);

    assertThat(response.rolAutor()).isEqualTo("PROFESIONAL_EXTERNO");
    assertThat(response.nombreAutor()).isEqualTo(profesionalExterno.getEmail());
    verify(inspeccionRepository).save(any(Inspeccion.class));
  }

  @Test
  @DisplayName("Profesional Externo sin verificar recibe 403 Forbidden")
  void iniciarInspeccion_falla_profesionalNoVerificado_lanza403() {
    UUID usuarioId = profesionalExterno.getId();
    UsuarioProfesional prof = new UsuarioProfesional(profesionalExterno, "COL-9999", AHORA);
    // colegiadoVerificadoEn permanece null

    when(userAccountRepository.findWithRoleById(usuarioId))
        .thenReturn(Optional.of(profesionalExterno));
    when(profesionalRepository.findById(usuarioId)).thenReturn(Optional.of(prof));

    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            null,
            puenteActivo.getId(),
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            false,
            null);

    assertThatThrownBy(() -> inspeccionService.iniciarInspeccionHu024(request, usuarioId, null))
        .isInstanceOf(ApiException.class)
        .matches(e -> ((ApiException) e).getStatus() == HttpStatus.FORBIDDEN)
        .hasMessageContaining("colegiado aún no ha sido verificado");
  }

  @Test
  @DisplayName("Estudiante sin asignación vigente recibe 403 Forbidden")
  void iniciarInspeccion_falla_estudianteSinAsignacion_lanza403() {
    UUID usuarioId = estudiante.getId();
    when(userAccountRepository.findWithRoleById(usuarioId)).thenReturn(Optional.of(estudiante));
    when(puenteRepository.findById(puenteActivo.getId())).thenReturn(Optional.of(puenteActivo));

    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            null,
            puenteActivo.getId(),
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            false,
            null);

    assertThatThrownBy(() -> inspeccionService.iniciarInspeccionHu024(request, usuarioId, null))
        .isInstanceOf(ApiException.class)
        .matches(e -> ((ApiException) e).getStatus() == HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("Estudiante con asignación vigente inicia un borrador vinculado")
  void iniciarInspeccion_exitoso_estudianteAsignado() {
    UUID usuarioId = estudiante.getId();
    UUID asignacionId = UUID.randomUUID();
    var asignacion =
        org.mockito.Mockito.mock(AsignacionPuenteRepository.AsignacionInspeccionProjection.class);
    when(asignacion.getId()).thenReturn(asignacionId);
    when(userAccountRepository.findWithRoleById(usuarioId)).thenReturn(Optional.of(estudiante));
    when(puenteRepository.findById(puenteActivo.getId())).thenReturn(Optional.of(puenteActivo));
    when(asignacionPuenteRepository.findVigenteParaInspeccion(
            usuarioId, puenteActivo.getId(), LocalDate.of(2026, 10, 15)))
        .thenReturn(Optional.of(asignacion));
    when(versionFormularioRepository.findByActivaTrue()).thenReturn(Optional.of(versionActiva));

    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            null,
            puenteActivo.getId(),
            LocalDate.of(2026, 10, 15),
            "disp-estudiante",
            null,
            null,
            null,
            null,
            false,
            null);

    IniciarInspeccionResponse response =
        inspeccionService.iniciarInspeccionHu024(request, usuarioId, null);

    assertThat(response.rolAutor()).isEqualTo("ESTUDIANTE");
    ArgumentCaptor<Inspeccion> inspeccion = ArgumentCaptor.forClass(Inspeccion.class);
    verify(inspeccionRepository).save(inspeccion.capture());
    assertThat(inspeccion.getValue().getAsignacionPuenteId()).isEqualTo(asignacionId);
    verify(auditoriaWriter)
        .registrarEnTransaccion(
            eq(usuarioId),
            eq(AccionAuditoria.CREAR),
            eq("inspeccion"),
            eq(response.id()),
            eq(null),
            any(),
            eq("HU-035"));
  }

  @Test
  @DisplayName("Solicitud sin autenticación recibe 401 Unauthorized")
  void iniciarInspeccion_falla_unauthenticated_lanza401() {
    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            null,
            puenteActivo.getId(),
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            false,
            null);

    assertThatThrownBy(() -> inspeccionService.iniciarInspeccionHu024(request, null, null))
        .isInstanceOf(ApiException.class)
        .matches(e -> ((ApiException) e).getStatus() == HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("Puente inactivo es rechazado con HTTP 409 Conflict")
  void iniciarInspeccion_falla_puenteInactivo_lanza409() {
    UUID usuarioId = catedratico.getId();
    when(userAccountRepository.findWithRoleById(usuarioId)).thenReturn(Optional.of(catedratico));
    ReflectionTestUtils.setField(puenteActivo, "activo", false);
    when(puenteRepository.findById(puenteActivo.getId())).thenReturn(Optional.of(puenteActivo));

    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            null,
            puenteActivo.getId(),
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            false,
            null);

    assertThatThrownBy(() -> inspeccionService.iniciarInspeccionHu024(request, usuarioId, null))
        .isInstanceOf(ApiException.class)
        .matches(e -> ((ApiException) e).getStatus() == HttpStatus.CONFLICT)
        .hasMessageContaining("inactivo");

    verify(inspeccionRepository, never()).save(any());
  }

  @Test
  @DisplayName("Fecha de inspección futura es rechazada con 422")
  void iniciarInspeccion_falla_fechaFutura_lanza422() {
    UUID usuarioId = catedratico.getId();
    when(userAccountRepository.findWithRoleById(usuarioId)).thenReturn(Optional.of(catedratico));
    when(puenteRepository.findById(puenteActivo.getId())).thenReturn(Optional.of(puenteActivo));
    when(versionFormularioRepository.findByActivaTrue()).thenReturn(Optional.of(versionActiva));

    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            null,
            puenteActivo.getId(),
            LocalDate.of(2026, 10, 16), // AHORA es 2026-10-15
            "disp-1",
            null,
            null,
            null,
            null,
            false,
            null);

    assertThatThrownBy(() -> inspeccionService.iniciarInspeccionHu024(request, usuarioId, null))
        .isInstanceOf(ApiException.class)
        .matches(e -> ((ApiException) e).getStatus() == HttpStatus.UNPROCESSABLE_ENTITY)
        .hasMessageContaining("posterior a la fecha actual");
  }

  @Test
  @DisplayName("Fecha anterior al año de construcción del puente es rechazada con 422")
  void iniciarInspeccion_falla_fechaAnteriorConstruccion_lanza422() {
    UUID usuarioId = catedratico.getId();
    UUID inspeccionVigenteId = UUID.randomUUID();
    ReflectionTestUtils.setField(puenteActivo, "inspeccionVigenteId", inspeccionVigenteId);

    InspeccionDatoTecnico datoTecnico = new InspeccionDatoTecnico();
    datoTecnico.setAnioConstruccion((short) 1980);

    when(userAccountRepository.findWithRoleById(usuarioId)).thenReturn(Optional.of(catedratico));
    when(puenteRepository.findById(puenteActivo.getId())).thenReturn(Optional.of(puenteActivo));
    when(versionFormularioRepository.findByActivaTrue()).thenReturn(Optional.of(versionActiva));
    when(datoTecnicoRepository.findByInspeccionId(inspeccionVigenteId))
        .thenReturn(Optional.of(datoTecnico));

    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            null,
            puenteActivo.getId(),
            LocalDate.of(1975, 5, 20),
            "disp-1",
            null,
            null,
            null,
            null,
            false,
            null);

    assertThatThrownBy(() -> inspeccionService.iniciarInspeccionHu024(request, usuarioId, null))
        .isInstanceOf(ApiException.class)
        .matches(e -> ((ApiException) e).getStatus() == HttpStatus.UNPROCESSABLE_ENTITY)
        .hasMessageContaining("anterior al año de construcción");
  }

  @Test
  @DisplayName(
      "Precarga datos permanentes de la última inspección publicada con confirmación explícita (sin daños)")
  void iniciarInspeccion_precargaDatosPermanentes_conConfirmacion() {
    UUID usuarioId = catedratico.getId();
    UUID inspeccionVigenteId = UUID.randomUUID();
    ReflectionTestUtils.setField(puenteActivo, "inspeccionVigenteId", inspeccionVigenteId);

    InspeccionDatoTecnico tecnicoVigente = new InspeccionDatoTecnico();
    tecnicoVigente.setLongitudM(new BigDecimal("120.50"));
    tecnicoVigente.setNumeroTramos(4);
    tecnicoVigente.setTipologiaPuente("Viga cajón");
    tecnicoVigente.setMaterialSuperestructura("Concreto presforzado");
    tecnicoVigente.setAnioConstruccion((short) 2005);

    when(userAccountRepository.findWithRoleById(usuarioId)).thenReturn(Optional.of(catedratico));
    when(puenteRepository.findById(puenteActivo.getId())).thenReturn(Optional.of(puenteActivo));
    when(versionFormularioRepository.findByActivaTrue()).thenReturn(Optional.of(versionActiva));
    when(datoTecnicoRepository.findByInspeccionId(inspeccionVigenteId))
        .thenReturn(Optional.of(tecnicoVigente));

    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            null,
            puenteActivo.getId(),
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            true, // Confirmación explícita
            null);

    IniciarInspeccionResponse response =
        inspeccionService.iniciarInspeccionHu024(request, usuarioId, null);

    assertThat(response.datosPermanentesConfirmados()).isTrue();
    assertThat(response.datosPermanentes()).isNotNull();
    assertThat(response.datosPermanentes().longitudM()).isEqualByComparingTo("120.50");
    assertThat(response.datosPermanentes().numeroTramos()).isEqualTo(4);
    assertThat(response.datosPermanentes().tipologiaPuente()).isEqualTo("Viga cajón");
    assertThat(response.datosPermanentes().anioConstruccion()).isEqualTo(2005);

    ArgumentCaptor<Inspeccion> captor = ArgumentCaptor.forClass(Inspeccion.class);
    verify(inspeccionRepository).save(captor.capture());
    Inspeccion guardada = captor.getValue();
    assertThat(guardada.getDatos().isEmpty()).isTrue(); // Daños nunca se precargan
    assertThat(guardada.getDatosPermanentesConfirmadosEn()).isNotNull();
  }

  @Test
  @DisplayName("Sin confirmación de datos permanentes no se precargan los datos")
  void iniciarInspeccion_sinConfirmacion_noPrecarga() {
    UUID usuarioId = catedratico.getId();
    UUID inspeccionVigenteId = UUID.randomUUID();
    ReflectionTestUtils.setField(puenteActivo, "inspeccionVigenteId", inspeccionVigenteId);

    InspeccionDatoTecnico tecnicoVigente = new InspeccionDatoTecnico();
    tecnicoVigente.setAnioConstruccion((short) 2005);

    when(userAccountRepository.findWithRoleById(usuarioId)).thenReturn(Optional.of(catedratico));
    when(puenteRepository.findById(puenteActivo.getId())).thenReturn(Optional.of(puenteActivo));
    when(versionFormularioRepository.findByActivaTrue()).thenReturn(Optional.of(versionActiva));
    when(datoTecnicoRepository.findByInspeccionId(inspeccionVigenteId))
        .thenReturn(Optional.of(tecnicoVigente));

    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            null,
            puenteActivo.getId(),
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            false, // Sin confirmación
            null);

    IniciarInspeccionResponse response =
        inspeccionService.iniciarInspeccionHu024(request, usuarioId, null);

    assertThat(response.datosPermanentesConfirmados()).isFalse();
    assertThat(response.datosPermanentes()).isNull();
  }

  @Test
  @DisplayName("GPS a más de 500m del puente genera advertencia sin bloquear la creación")
  void iniciarInspeccion_gpsMasDe500Metros_generaAdvertencia() {
    UUID usuarioId = catedratico.getId();
    when(userAccountRepository.findWithRoleById(usuarioId)).thenReturn(Optional.of(catedratico));
    when(puenteRepository.findById(puenteActivo.getId())).thenReturn(Optional.of(puenteActivo));
    when(versionFormularioRepository.findByActivaTrue()).thenReturn(Optional.of(versionActiva));

    // Puente en (-90.5132, 14.6407). Punto alejado ~2km en latitud: 14.6600
    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            null,
            puenteActivo.getId(),
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            14.6600,
            -90.5132,
            new BigDecimal("5.0"),
            false,
            null);

    IniciarInspeccionResponse response =
        inspeccionService.iniciarInspeccionHu024(request, usuarioId, null);

    assertThat(response.advertenciaGps()).isTrue();
    assertThat(response.mensajeAdvertenciaGps()).contains("más de 500 metros");
    assertThat(response.distanciaAlPuenteM()).isGreaterThan(BigDecimal.valueOf(500));
    verify(inspeccionRepository).save(any(Inspeccion.class));
  }

  @Test
  @DisplayName(
      "Idempotencia: reenvío con Idempotency-Key devuelve la inspección previa sin duplicar")
  void iniciarInspeccion_idempotencia_retornaMismaInspeccion() {
    UUID usuarioId = catedratico.getId();
    String key = "req-idempotent-key-123";

    when(userAccountRepository.findWithRoleById(usuarioId)).thenReturn(Optional.of(catedratico));

    Inspeccion inspeccionPrevia =
        new Inspeccion(
            UUID.randomUUID(),
            puenteActivo,
            versionActiva,
            usuarioId,
            "CATEDRATICO",
            "Profesor",
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            objectMapper.createObjectNode(),
            AHORA);

    OperacionIdempotente op =
        new OperacionIdempotente(
            UUID.randomUUID(),
            usuarioId,
            key,
            "POST",
            "/api/v1/inspecciones",
            hashSolicitudInicio(),
            "COMPLETADA",
            201,
            objectMapper.createObjectNode().put("id", inspeccionPrevia.getId().toString()),
            "inspeccion",
            inspeccionPrevia.getId(),
            AHORA,
            AHORA.plusSeconds(86400),
            AHORA);

    when(operacionIdempotenteRepository.findByUsuarioIdAndClave(usuarioId, key))
        .thenReturn(Optional.of(op));
    when(inspeccionRepository.findConDetallesById(inspeccionPrevia.getId()))
        .thenReturn(Optional.of(inspeccionPrevia));

    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            null,
            puenteActivo.getId(),
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            false,
            null);

    IniciarInspeccionResponse response =
        inspeccionService.iniciarInspeccionHu024(request, usuarioId, key);

    assertThat(response.id()).isEqualTo(inspeccionPrevia.getId());
    verify(inspeccionRepository, never()).save(any());
  }

  @Test
  @DisplayName("HU-025: Guarda datos parciales del borrador y calcula avance de las 8 secciones")
  void guardarBorrador_guardaDatosParciales_actualizaUltimaActividad_retornaProgreso() {
    UUID usuarioId = catedratico.getId();
    UUID inspeccionId = UUID.randomUUID();

    Inspeccion inspeccion =
        new Inspeccion(
            inspeccionId,
            puenteActivo,
            versionActiva,
            usuarioId,
            "CATEDRATICO",
            "Profesor",
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            objectMapper.createObjectNode(),
            AHORA);
    ReflectionTestUtils.setField(inspeccion, "version", 0L);

    when(inspeccionRepository.findConDetallesById(inspeccionId))
        .thenReturn(Optional.of(inspeccion));

    var datosParciales = objectMapper.createObjectNode();
    var datosGenerales = datosParciales.putObject("datos_generales");
    datosGenerales.put("longitud_m", 45.5);

    GuardarBorradorRequest request =
        new GuardarBorradorRequest(
            0L,
            datosParciales,
            null,
            LocalDate.of(2026, 10, 15),
            List.of(new AcompananteRequest(null, "Ing. Asistente", "12345")));

    InspeccionBorradorResponse response =
        inspeccionService.guardarBorrador(inspeccionId, request, usuarioId);

    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo(inspeccionId);
    assertThat(response.estado()).isEqualTo("BORRADOR");
    assertThat(response.acompanantes()).hasSize(1);
    assertThat(response.acompanantes().get(0).nombre()).isEqualTo("Ing. Asistente");
    assertThat(response.progreso()).isNotNull();
    assertThat(response.progreso().secciones()).hasSize(8);
    assertThat(response.progreso().totalCamposCompletados()).isPositive();
    assertThat(response.icPreliminar()).isNull();

    verify(inspeccionRepository).saveAndFlush(inspeccion);
  }

  @Test
  @DisplayName("HU-031: El autor elimina lógicamente su borrador y se registra auditoría")
  void eliminarBorrador_propietario_marcaEliminacionYAudita() {
    UUID usuarioId = catedratico.getId();
    UUID inspeccionId = UUID.randomUUID();
    Inspeccion inspeccion =
        new Inspeccion(
            inspeccionId,
            puenteActivo,
            versionActiva,
            usuarioId,
            "CATEDRATICO",
            "Profesor",
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            objectMapper.createObjectNode(),
            AHORA);
    when(inspeccionRepository.findConDetallesById(inspeccionId))
        .thenReturn(Optional.of(inspeccion));
    when(userAccountRepository.findWithRoleById(usuarioId)).thenReturn(Optional.of(catedratico));

    inspeccionService.eliminarBorrador(inspeccionId, usuarioId);

    assertThat(inspeccion.getEliminadoEn()).isEqualTo(AHORA);
    assertThat(inspeccion.getEliminadoPorId()).isEqualTo(usuarioId);
    verify(inspeccionRepository).save(inspeccion);
    verify(auditoriaWriter)
        .registrarEnTransaccion(
            eq(usuarioId),
            eq(AccionAuditoria.CAMBIAR_ESTADO),
            eq("inspeccion"),
            eq(inspeccionId),
            any(),
            any(),
            eq("HU-031"));
  }

  @Test
  @DisplayName("HU-031: No permite eliminar una inspección publicada")
  void eliminarBorrador_publicada_lanza409() {
    UUID usuarioId = catedratico.getId();
    UUID inspeccionId = UUID.randomUUID();
    Inspeccion inspeccion =
        new Inspeccion(
            inspeccionId,
            puenteActivo,
            versionActiva,
            usuarioId,
            "CATEDRATICO",
            "Profesor",
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            objectMapper.createObjectNode(),
            AHORA);
    ReflectionTestUtils.setField(inspeccion, "estado", "PUBLICADA");
    when(inspeccionRepository.findConDetallesById(inspeccionId))
        .thenReturn(Optional.of(inspeccion));
    when(userAccountRepository.findWithRoleById(usuarioId)).thenReturn(Optional.of(catedratico));

    assertThatThrownBy(() -> inspeccionService.eliminarBorrador(inspeccionId, usuarioId))
        .isInstanceOf(ApiException.class)
        .matches(e -> ((ApiException) e).getStatus() == HttpStatus.CONFLICT);
    verify(inspeccionRepository, never()).save(any());
  }

  @Test
  @DisplayName(
      "HU-025: Calcula IC preliminar etiquetado cuando existen datos suficientes sin alterar estado del puente")
  void guardarBorrador_conSuficientesDatos_calculaIcPreliminar() {
    UUID usuarioId = catedratico.getId();
    UUID inspeccionId = UUID.randomUUID();

    Inspeccion inspeccion =
        new Inspeccion(
            inspeccionId,
            puenteActivo,
            versionActiva,
            usuarioId,
            "CATEDRATICO",
            "Profesor",
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            objectMapper.createObjectNode(),
            AHORA);
    ReflectionTestUtils.setField(inspeccion, "version", 0L);

    when(inspeccionRepository.findConDetallesById(inspeccionId))
        .thenReturn(Optional.of(inspeccion));

    var datosCompletos = objectMapper.createObjectNode();
    datosCompletos.putObject("datos_generales").put("longitud_m", 50.0);

    EntradaCalculoIc entradaMock =
        new EntradaCalculoIc(
            versionActiva.getId(),
            "SIECA",
            "1.0",
            datosCompletos,
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            java.util.Collections.emptyMap());

    ResultadoCalculoIc resultadoMock =
        new ResultadoCalculoIc(
            versionActiva.getId(),
            "SIECA",
            "1.0",
            "hash",
            new BigDecimal("82.50000"),
            EstadoCondicion.BUENO,
            EstadoCondicion.BUENO,
            BigDecimal.TEN,
            BigDecimal.TEN,
            List.of(),
            List.of());

    when(preparadorEntradaIcService.preparar(eq(versionActiva.getId()), any()))
        .thenReturn(entradaMock);
    when(calculoIndiceCondicionService.calcular(entradaMock)).thenReturn(resultadoMock);

    GuardarBorradorRequest request =
        new GuardarBorradorRequest(0L, datosCompletos, null, LocalDate.of(2026, 10, 15), null);

    InspeccionBorradorResponse response =
        inspeccionService.guardarBorrador(inspeccionId, request, usuarioId);

    assertThat(response.icPreliminar()).isNotNull();
    assertThat(response.icPreliminar().indice()).isEqualByComparingTo("82.50000");
    assertThat(response.icPreliminar().estado()).isEqualTo("BUENO");
    assertThat(response.icPreliminar().etiqueta()).isEqualTo("Preliminar");

    // El puente no debe alterar su estado ni índice
    assertThat(puenteActivo.getEstadoActual()).isEqualTo("Sin evaluar");
    assertThat(puenteActivo.getIndiceCondicionActual()).isNull();
  }

  @Test
  @DisplayName("HU-035: Oculta el borrador a un usuario diferente al autor con 404 Not Found")
  void guardarBorrador_otroUsuario_lanza404() {
    UUID autorId = catedratico.getId();
    UUID otroUsuarioId = profesionalExterno.getId();
    UUID inspeccionId = UUID.randomUUID();

    Inspeccion inspeccion =
        new Inspeccion(
            inspeccionId,
            puenteActivo,
            versionActiva,
            autorId,
            "CATEDRATICO",
            "Profesor",
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            objectMapper.createObjectNode(),
            AHORA);

    when(inspeccionRepository.findConDetallesById(inspeccionId))
        .thenReturn(Optional.of(inspeccion));

    GuardarBorradorRequest request =
        new GuardarBorradorRequest(0L, objectMapper.createObjectNode(), null, null, null);

    assertThatThrownBy(
            () -> inspeccionService.guardarBorrador(inspeccionId, request, otroUsuarioId))
        .isInstanceOf(ApiException.class)
        .matches(e -> ((ApiException) e).getStatus() == HttpStatus.NOT_FOUND)
        .hasMessageContaining("no tiene acceso");
  }

  @Test
  @DisplayName(
      "HU-025: Rechaza edición si la inspección no está en estado BORRADOR con 409 Conflict")
  void guardarBorrador_estadoNoBorrador_lanza409() {
    UUID usuarioId = catedratico.getId();
    UUID inspeccionId = UUID.randomUUID();

    Inspeccion inspeccion =
        new Inspeccion(
            inspeccionId,
            puenteActivo,
            versionActiva,
            usuarioId,
            "CATEDRATICO",
            "Profesor",
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            objectMapper.createObjectNode(),
            AHORA);

    ReflectionTestUtils.setField(inspeccion, "estado", "PUBLICADA");

    when(inspeccionRepository.findConDetallesById(inspeccionId))
        .thenReturn(Optional.of(inspeccion));

    GuardarBorradorRequest request =
        new GuardarBorradorRequest(0L, objectMapper.createObjectNode(), null, null, null);

    assertThatThrownBy(() -> inspeccionService.guardarBorrador(inspeccionId, request, usuarioId))
        .isInstanceOf(ApiException.class)
        .matches(e -> ((ApiException) e).getStatus() == HttpStatus.CONFLICT)
        .hasMessageContaining("Solo las inspecciones en estado BORRADOR");
  }

  @Test
  @DisplayName(
      "HU-025: Rechaza actualización obsoleta por desincronización de versión con 409 Conflict")
  void guardarBorrador_versionObsoleta_lanza409() {
    UUID usuarioId = catedratico.getId();
    UUID inspeccionId = UUID.randomUUID();

    Inspeccion inspeccion =
        new Inspeccion(
            inspeccionId,
            puenteActivo,
            versionActiva,
            usuarioId,
            "CATEDRATICO",
            "Profesor",
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            objectMapper.createObjectNode(),
            AHORA);

    ReflectionTestUtils.setField(inspeccion, "version", 3L);

    when(inspeccionRepository.findConDetallesById(inspeccionId))
        .thenReturn(Optional.of(inspeccion));

    // El cliente envía version 2L, que es obsoleta comparada con 3L
    GuardarBorradorRequest request =
        new GuardarBorradorRequest(2L, objectMapper.createObjectNode(), null, null, null);

    assertThatThrownBy(() -> inspeccionService.guardarBorrador(inspeccionId, request, usuarioId))
        .isInstanceOf(ApiException.class)
        .matches(e -> ((ApiException) e).getStatus() == HttpStatus.CONFLICT)
        .hasMessageContaining("modificada concurrentemente");
  }

  @Test
  @DisplayName("HU-025: Rechaza fecha de inspección futura con 422 Unprocessable Entity")
  void guardarBorrador_fechaFutura_lanza422() {
    UUID usuarioId = catedratico.getId();
    UUID inspeccionId = UUID.randomUUID();

    Inspeccion inspeccion =
        new Inspeccion(
            inspeccionId,
            puenteActivo,
            versionActiva,
            usuarioId,
            "CATEDRATICO",
            "Profesor",
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            objectMapper.createObjectNode(),
            AHORA);
    ReflectionTestUtils.setField(inspeccion, "version", 0L);

    when(inspeccionRepository.findConDetallesById(inspeccionId))
        .thenReturn(Optional.of(inspeccion));

    GuardarBorradorRequest request =
        new GuardarBorradorRequest(
            0L,
            objectMapper.createObjectNode(),
            null,
            LocalDate.of(2026, 10, 20), // Posterior a 2026-10-15
            null);

    assertThatThrownBy(() -> inspeccionService.guardarBorrador(inspeccionId, request, usuarioId))
        .isInstanceOf(ApiException.class)
        .matches(e -> ((ApiException) e).getStatus() == HttpStatus.UNPROCESSABLE_ENTITY)
        .hasMessageContaining("posterior a la fecha actual");
  }

  @Test
  @DisplayName("HU-025: Rechaza fecha anterior al año de construcción con 422 Unprocessable Entity")
  void guardarBorrador_fechaAnteriorConstruccion_lanza422() {
    UUID usuarioId = catedratico.getId();
    UUID inspeccionId = UUID.randomUUID();

    Inspeccion inspeccion =
        new Inspeccion(
            inspeccionId,
            puenteActivo,
            versionActiva,
            usuarioId,
            "CATEDRATICO",
            "Profesor",
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            objectMapper.createObjectNode(),
            AHORA);
    ReflectionTestUtils.setField(inspeccion, "version", 0L);

    InspeccionDatoTecnico datoTecnico = new InspeccionDatoTecnico();
    datoTecnico.setAnioConstruccion((short) 1990);
    inspeccion.setDatoTecnico(datoTecnico);

    when(inspeccionRepository.findConDetallesById(inspeccionId))
        .thenReturn(Optional.of(inspeccion));

    GuardarBorradorRequest request =
        new GuardarBorradorRequest(
            0L,
            objectMapper.createObjectNode(),
            null,
            LocalDate.of(1985, 1, 1), // Anterior a 1990
            null);

    assertThatThrownBy(() -> inspeccionService.guardarBorrador(inspeccionId, request, usuarioId))
        .isInstanceOf(ApiException.class)
        .matches(e -> ((ApiException) e).getStatus() == HttpStatus.UNPROCESSABLE_ENTITY)
        .hasMessageContaining("anterior al año de construcción registrado");
  }

  @Test
  @DisplayName("HU-025: El autor puede consultar el borrador para reanudar edición")
  void obtenerBorrador_autor_retornaBorradorConProgreso() {
    UUID usuarioId = catedratico.getId();
    UUID inspeccionId = UUID.randomUUID();

    Inspeccion inspeccion =
        new Inspeccion(
            inspeccionId,
            puenteActivo,
            versionActiva,
            usuarioId,
            "CATEDRATICO",
            "Profesor",
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            objectMapper.createObjectNode(),
            AHORA);
    ReflectionTestUtils.setField(inspeccion, "version", 0L);

    when(inspeccionRepository.findConDetallesById(inspeccionId))
        .thenReturn(Optional.of(inspeccion));

    InspeccionBorradorResponse response =
        inspeccionService.obtenerBorrador(inspeccionId, usuarioId);

    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo(inspeccionId);
    assertThat(response.progreso()).isNotNull();
    assertThat(response.progreso().secciones()).hasSize(8);
  }

  @Test
  @DisplayName("HU-031: Una inspección propia fuera de BORRADOR no puede abrirse para edición")
  void obtenerBorrador_estadoNoBorrador_lanza404() {
    UUID usuarioId = catedratico.getId();
    UUID inspeccionId = UUID.randomUUID();
    Inspeccion inspeccion =
        new Inspeccion(
            inspeccionId,
            puenteActivo,
            versionActiva,
            usuarioId,
            "CATEDRATICO",
            "Profesor",
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            objectMapper.createObjectNode(),
            AHORA);
    ReflectionTestUtils.setField(inspeccion, "estado", "PUBLICADA");
    when(inspeccionRepository.findConDetallesById(inspeccionId))
        .thenReturn(Optional.of(inspeccion));

    assertThatThrownBy(() -> inspeccionService.obtenerBorrador(inspeccionId, usuarioId))
        .isInstanceOf(ApiException.class)
        .matches(e -> ((ApiException) e).getStatus() == HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("HU-025: Privacidad de borradores: usuario no autor recibe 404 Not Found")
  void obtenerBorrador_otroUsuario_lanza404() {
    UUID autorId = catedratico.getId();
    UUID otroUsuarioId = profesionalExterno.getId();
    UUID inspeccionId = UUID.randomUUID();

    Inspeccion inspeccion =
        new Inspeccion(
            inspeccionId,
            puenteActivo,
            versionActiva,
            autorId,
            "CATEDRATICO",
            "Profesor",
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            objectMapper.createObjectNode(),
            AHORA);

    when(inspeccionRepository.findConDetallesById(inspeccionId))
        .thenReturn(Optional.of(inspeccion));

    assertThatThrownBy(() -> inspeccionService.obtenerBorrador(inspeccionId, otroUsuarioId))
        .isInstanceOf(ApiException.class)
        .matches(e -> ((ApiException) e).getStatus() == HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("HU-035: El Catedrático asignado consulta el borrador estudiantil")
  void obtenerBorrador_catedraticoAsignado_retornaSoloLosDatos() {
    UUID autorId = estudiante.getId();
    UUID catedraticoId = catedratico.getId();
    UUID inspeccionId = UUID.randomUUID();
    UUID asignacionId = UUID.randomUUID();
    Inspeccion inspeccion =
        new Inspeccion(
            inspeccionId,
            puenteActivo,
            versionActiva,
            autorId,
            "ESTUDIANTE",
            "Alumno",
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            objectMapper.createObjectNode(),
            AHORA);
    inspeccion.setAsignacionPuenteId(asignacionId);
    ReflectionTestUtils.setField(inspeccion, "version", 0L);
    when(inspeccionRepository.findConDetallesById(inspeccionId))
        .thenReturn(Optional.of(inspeccion));
    when(asignacionPuenteRepository.perteneceACatedratico(asignacionId, catedraticoId))
        .thenReturn(true);

    InspeccionBorradorResponse response =
        inspeccionService.obtenerBorrador(inspeccionId, catedraticoId);

    assertThat(response.id()).isEqualTo(inspeccionId);
    assertThat(response.autorId()).isEqualTo(autorId);
    assertThat(response.rolAutor()).isEqualTo("ESTUDIANTE");
  }

  private String hashSolicitudInicio() {
    var r =
        new IniciarInspeccionRequest(
            null,
            puenteActivo.getId(),
            LocalDate.of(2026, 10, 15),
            "disp-1",
            null,
            null,
            null,
            null,
            false,
            null);
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(r.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}
