package gt.usac.cunoc.sgp.mantenimiento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.mantenimiento.dto.CrearReglaMantenimientoRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.EditarReglaMantenimientoRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.ReglaMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.ResumenReglasVersionResponse;
import gt.usac.cunoc.sgp.mantenimiento.entity.ReglaMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.mapper.ReglaMantenimientoMapper;
import gt.usac.cunoc.sgp.mantenimiento.model.PrioridadOrden;
import gt.usac.cunoc.sgp.mantenimiento.model.TipoOrden;
import gt.usac.cunoc.sgp.mantenimiento.repository.ReglaMantenimientoRepository;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ReglaMantenimientoServiceTest {

  private static final UUID USUARIO_ADMIN_ID =
      UUID.fromString("019a0000-0000-7000-8000-000000000001");
  private static final UUID VERSION_ID = UUID.fromString("019a0000-0000-7000-8000-000000000002");
  private static final Instant AHORA = Instant.parse("2026-10-18T18:00:00Z");

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Clock clock = Clock.fixed(AHORA, ZoneOffset.UTC);
  private final ReglaMantenimientoMapper mapper = Mappers.getMapper(ReglaMantenimientoMapper.class);
  private final ValidadorReglaMantenimiento validador = new ValidadorReglaMantenimiento();

  @Mock private ReglaMantenimientoRepository reglaRepository;
  @Mock private VersionFormularioRepository versionRepository;
  @Mock private UserAccountRepository userRepository;

  private ReglaMantenimientoService service;
  private VersionFormulario versionSieca;
  private JsonNode esquemaSieca;

  @BeforeEach
  void setUp() throws Exception {
    service =
        new ReglaMantenimientoService(
            reglaRepository,
            versionRepository,
            userRepository,
            mapper,
            validador,
            objectMapper,
            clock,
            org.mockito.Mockito.mock(
                gt.usac.cunoc.sgp.notificacion.service.AvisosAcademicosService.class));

    try (InputStream is =
        new ClassPathResource("formulario/sieca-v1/esquema.schema.json").getInputStream()) {
      esquemaSieca = objectMapper.readTree(is);
    }

    versionSieca =
        new VersionFormulario(
            VERSION_ID,
            "SIECA-v1",
            EstadoVersionFormulario.PUBLICADA,
            true,
            esquemaSieca,
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            null,
            USUARIO_ADMIN_ID,
            AHORA,
            AHORA);
  }

  @Test
  @DisplayName(
      "Sembrar catálogo siembra exactamente 18 reglas provisionales con los 4 tipos de mantenimiento")
  void sembrarCatalogoProvisionalExitoso() {
    when(reglaRepository.existsByVersionFormularioId(VERSION_ID)).thenReturn(false);
    when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.of(versionSieca));
    UserAccount admin = org.mockito.Mockito.mock(UserAccount.class);
    when(admin.getId()).thenReturn(USUARIO_ADMIN_ID);
    when(userRepository.findByRole_NameAndActiveTrueOrderByEmailAsc(RoleName.ADMINISTRADOR))
        .thenReturn(List.of(admin));

    int sembradas = service.sembrarCatalogoProvisionalSiNoExiste(VERSION_ID, null);

    assertThat(sembradas).isEqualTo(18);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ReglaMantenimiento>> captor = ArgumentCaptor.forClass(List.class);
    verify(reglaRepository).saveAll(captor.capture());
    List<ReglaMantenimiento> guardadas = captor.getValue();
    assertThat(guardadas).hasSize(18);

    // Todas son provisionales y activas
    for (ReglaMantenimiento regla : guardadas) {
      assertThat(regla.isProvisional()).isTrue();
      assertThat(regla.getValidadoPorId()).isNull();
      assertThat(regla.getValidadoEn()).isNull();
      assertThat(regla.isActiva()).isTrue();
      assertThat(regla.getNumeroRevision()).isEqualTo(1);
    }

    // RN-MTO-03: Contiene los 4 tipos de mantenimiento
    assertThat(guardadas.stream().anyMatch(r -> r.getTipoMantenimiento() == TipoOrden.RUTINARIO))
        .isTrue();
    assertThat(guardadas.stream().anyMatch(r -> r.getTipoMantenimiento() == TipoOrden.PREVENTIVO))
        .isTrue();
    assertThat(guardadas.stream().anyMatch(r -> r.getTipoMantenimiento() == TipoOrden.CORRECTIVO))
        .isTrue();
    assertThat(guardadas.stream().anyMatch(r -> r.getTipoMantenimiento() == TipoOrden.EMERGENCIA))
        .isTrue();

    // Daños graves proponen EMERGENCIA con prioridad más alta (CRITICA)
    List<ReglaMantenimiento> emergencias =
        guardadas.stream().filter(r -> r.getTipoMantenimiento() == TipoOrden.EMERGENCIA).toList();
    assertThat(emergencias).isNotEmpty();
    for (ReglaMantenimiento regla : emergencias) {
      assertThat(regla.getPrioridadBase()).isEqualTo(PrioridadOrden.CRITICA);
    }
  }

  @Test
  @DisplayName("Sembrar catálogo es idempotente si la versión ya cuenta con reglas")
  void sembrarCatalogoIdempotente() {
    when(reglaRepository.existsByVersionFormularioId(VERSION_ID)).thenReturn(true);

    int sembradas = service.sembrarCatalogoProvisionalSiNoExiste(VERSION_ID, null);

    assertThat(sembradas).isEqualTo(0);
  }

  @Test
  @DisplayName("Rechaza regla con elemento_ref inexistente con 422 Unprocessable Entity")
  void rechazaElementoRefInexistente() throws Exception {
    when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.of(versionSieca));

    JsonNode condicion =
        objectMapper.readTree(
            "{\"indicador\":\"estado\",\"operador\":\"IGUAL\",\"umbral\":\"MALO\"}");
    CrearReglaMantenimientoRequest request =
        new CrearReglaMantenimientoRequest(
            "RM-TEST-001",
            VERSION_ID,
            "/elemento_fantasma/inexistente",
            condicion,
            TipoOrden.CORRECTIVO,
            "Reparación",
            objectMapper.readTree("{\"prioridad_base\":\"ALTA\"}"),
            true);

    assertThatThrownBy(() -> service.crearRegla(request, USUARIO_ADMIN_ID))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getCode()).isEqualTo("regla_mantenimiento_invalida");
              assertThat(apiEx.getMessage()).contains("/elemento_fantasma/inexistente");
            });
  }

  @Test
  @DisplayName("Rechaza regla con indicador inexistente con 422 Unprocessable Entity")
  void rechazaIndicadorInexistente() throws Exception {
    when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.of(versionSieca));

    JsonNode condicion =
        objectMapper.readTree(
            "{\"indicador\":\"indicador_inexistente\",\"operador\":\"IGUAL\",\"umbral\":\"MALO\"}");
    CrearReglaMantenimientoRequest request =
        new CrearReglaMantenimientoRequest(
            "RM-TEST-002",
            VERSION_ID,
            "/elementos_no_estructurales/junta_entrada",
            condicion,
            TipoOrden.CORRECTIVO,
            "Reparación de junta",
            objectMapper.readTree("{\"prioridad_base\":\"ALTA\"}"),
            true);

    assertThatThrownBy(() -> service.crearRegla(request, USUARIO_ADMIN_ID))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getCode()).isEqualTo("regla_mantenimiento_invalida");
              assertThat(apiEx.getMessage()).contains("indicador_inexistente");
            });
  }

  @Test
  @DisplayName("Crear regla guarda revisión 1 y no validada")
  void crearReglaExitoso() throws Exception {
    when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.of(versionSieca));
    when(reglaRepository.findMaxNumeroRevision("RM-JUNTAS-001", VERSION_ID))
        .thenReturn(Optional.empty());

    JsonNode condicion =
        objectMapper.readTree(
            "{\"indicador\":\"junta_limpia\",\"operador\":\"IGUAL\",\"umbral\":false}");
    JsonNode reglaPrioridad = objectMapper.readTree("{\"prioridad_base\":\"BAJA\"}");

    CrearReglaMantenimientoRequest request =
        new CrearReglaMantenimientoRequest(
            "RM-JUNTAS-001",
            VERSION_ID,
            "/elementos_no_estructurales/junta_entrada",
            condicion,
            TipoOrden.RUTINARIO,
            "Limpieza de junta",
            reglaPrioridad,
            true);

    when(reglaRepository.save(any(ReglaMantenimiento.class))).thenAnswer(inv -> inv.getArgument(0));

    ReglaMantenimientoResponse response = service.crearRegla(request, USUARIO_ADMIN_ID);

    assertThat(response.codigo()).isEqualTo("RM-JUNTAS-001");
    assertThat(response.numeroRevision()).isEqualTo(1);
    assertThat(response.activa()).isTrue();
    assertThat(response.provisional()).isTrue();
    assertThat(response.estadoValidacion()).isEqualTo("Provisional · sin validar");
  }

  @Test
  @DisplayName("Editar regla crea nueva revisión incrementada y desactiva las anteriores")
  void editarReglaCreaNuevaRevision() throws Exception {
    UUID reglaOriginalId = UUID.randomUUID();
    ReglaMantenimiento original =
        new ReglaMantenimiento(
            reglaOriginalId,
            "RM-VIGA-001",
            1,
            versionSieca,
            "/superestructura/elemento_portante_acero",
            objectMapper.readTree(
                "{\"indicador\":\"pintura\",\"operador\":\"IGUAL\",\"umbral\":\"MALO\"}"),
            TipoOrden.PREVENTIVO,
            "Pintura general",
            objectMapper.readTree("{\"prioridad_base\":\"MEDIA\"}"),
            true,
            USUARIO_ADMIN_ID,
            null,
            null,
            AHORA);

    original.validar(UUID.randomUUID(), AHORA.minusSeconds(3600));
    when(reglaRepository.findByIdForUpdate(reglaOriginalId)).thenReturn(Optional.of(original));
    when(reglaRepository.findMaxNumeroRevision("RM-VIGA-001", VERSION_ID))
        .thenReturn(Optional.of(1));

    EditarReglaMantenimientoRequest request =
        new EditarReglaMantenimientoRequest(
            "/superestructura/elemento_portante_acero",
            objectMapper.readTree(
                "{\"indicador\":\"pintura\",\"operador\":\"IGUAL\",\"umbral\":\"PESIMO\"}"),
            TipoOrden.CORRECTIVO,
            "Reemplazo de recubrimiento y protección anticorrosiva",
            objectMapper.readTree("{\"prioridad_base\":\"ALTA\"}"),
            true);

    when(reglaRepository.save(any(ReglaMantenimiento.class))).thenAnswer(inv -> inv.getArgument(0));

    ReglaMantenimientoResponse response =
        service.editarRegla(reglaOriginalId, request, USUARIO_ADMIN_ID);

    verify(reglaRepository).desactivarTodasLasRevisiones("RM-VIGA-001", VERSION_ID);
    assertThat(response.codigo()).isEqualTo("RM-VIGA-001");
    assertThat(response.numeroRevision()).isEqualTo(2);
    assertThat(response.tipoMantenimiento()).isEqualTo(TipoOrden.CORRECTIVO);
    assertThat(response.provisional()).isTrue();
    assertThat(response.validadoPorId()).isNull();
    assertThat(response.validadoEn()).isNull();
    assertThat(original.isProvisional()).isFalse();
  }

  @Test
  @DisplayName("Validar regla registra validador, fecha y quita rotulado provisional")
  void validarReglaExitoso() throws Exception {
    UUID reglaId = UUID.randomUUID();
    ReglaMantenimiento regla =
        new ReglaMantenimiento(
            reglaId,
            "RM-VIGA-001",
            1,
            versionSieca,
            "/superestructura/elemento_portante_acero",
            objectMapper.readTree(
                "{\"indicador\":\"pintura\",\"operador\":\"IGUAL\",\"umbral\":\"MALO\"}"),
            TipoOrden.PREVENTIVO,
            "Pintura",
            objectMapper.readTree("{\"prioridad_base\":\"MEDIA\"}"),
            true,
            USUARIO_ADMIN_ID,
            null,
            null,
            AHORA);

    when(reglaRepository.findByIdForUpdate(reglaId)).thenReturn(Optional.of(regla));
    when(reglaRepository.save(any(ReglaMantenimiento.class))).thenAnswer(inv -> inv.getArgument(0));

    UserAccount docente = org.mockito.Mockito.mock(UserAccount.class);
    when(docente.getNombreCompleto()).thenReturn("Kenny Salazar");
    when(userRepository.findById(USUARIO_ADMIN_ID)).thenReturn(Optional.of(docente));
    ReglaMantenimientoResponse response = service.validarRegla(reglaId, USUARIO_ADMIN_ID);

    assertThat(response.provisional()).isFalse();
    assertThat(response.validadoPorId()).isEqualTo(USUARIO_ADMIN_ID);
    assertThat(response.validadoEn()).isEqualTo(AHORA);
    assertThat(response.estadoValidacion()).isEqualTo("Validada");
    assertThat(response.validadoPorNombre()).isEqualTo("Kenny Salazar");
    assertThat(response.rotuloValidacion()).isEqualTo("Validada por Kenny Salazar el 18/10/2026");
  }

  @Test
  @DisplayName("Resumen de versión advierte cuando no hay reglas activas")
  void resumenVersionAdvierteSinReglasActivas() {
    when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.of(versionSieca));
    when(reglaRepository.countByVersionFormularioId(VERSION_ID)).thenReturn(5L);
    when(reglaRepository.countByVersionFormularioIdAndActivaTrue(VERSION_ID)).thenReturn(0L);
    when(reglaRepository.countByVersionFormularioIdAndValidadoPorIdIsNull(VERSION_ID))
        .thenReturn(5L);

    ResumenReglasVersionResponse resumen = service.obtenerResumenVersion(VERSION_ID);

    assertThat(resumen.tieneReglasActivas()).isFalse();
    assertThat(resumen.advertenciaSinReglasActivas())
        .isEqualTo("Esta versión no tiene reglas de mantenimiento activas");
  }

  @Test
  void validacionRepetidaConservaAutorYFecha() {
    UUID autor = UUID.randomUUID();
    ReglaMantenimiento regla = reglaValidable(true, autor, AHORA.minusSeconds(60));
    when(reglaRepository.findByIdForUpdate(regla.getId())).thenReturn(Optional.of(regla));
    var response = service.validarRegla(regla.getId(), UUID.randomUUID());
    assertThat(response.validadoPorId()).isEqualTo(autor);
    assertThat(response.validadoEn()).isEqualTo(AHORA.minusSeconds(60));
    verify(reglaRepository, never()).save(any());
    org.mockito.Mockito.verifyNoInteractions(userRepository);
  }

  @Test
  void revisionInactivaNoSeValidaAunqueYaEstuvieraValidada() {
    ReglaMantenimiento regla = reglaValidable(false, UUID.randomUUID(), AHORA);
    when(reglaRepository.findByIdForUpdate(regla.getId())).thenReturn(Optional.of(regla));
    assertThatThrownBy(() -> service.validarRegla(regla.getId(), UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    verify(reglaRepository, never()).save(any());
  }

  @Test
  void reglaInexistenteNoSeValida() {
    UUID id = UUID.randomUUID();
    when(reglaRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.validarRegla(id, UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void nombreVacioUsaCorreoYFechaDeGuatemala() {
    ReglaMantenimiento regla = reglaValidable(true, null, null);
    when(reglaRepository.findByIdForUpdate(regla.getId())).thenReturn(Optional.of(regla));
    when(reglaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    UserAccount docente = org.mockito.Mockito.mock(UserAccount.class);
    when(docente.getNombreCompleto()).thenReturn("  ");
    when(docente.getEmail()).thenReturn("docente@cunoc.edu.gt");
    when(userRepository.findById(USUARIO_ADMIN_ID)).thenReturn(Optional.of(docente));
    var response = service.validarRegla(regla.getId(), USUARIO_ADMIN_ID);
    assertThat(response.validadoPorNombre()).isEqualTo("docente@cunoc.edu.gt");
    var madrugada = response.conNombreValidador("Docente");
    var dto =
        new ReglaMantenimientoResponse(
            madrugada.id(),
            madrugada.codigo(),
            1,
            VERSION_ID,
            "SIECA-v1",
            "elemento",
            null,
            TipoOrden.PREVENTIVO,
            "Pintura",
            null,
            true,
            false,
            "Validada",
            USUARIO_ADMIN_ID,
            USUARIO_ADMIN_ID,
            Instant.parse("2026-10-24T02:30:00Z"),
            AHORA,
            "Docente");
    assertThat(dto.rotuloValidacion()).isEqualTo("Validada por Docente el 23/10/2026");
  }

  private ReglaMantenimiento reglaValidable(boolean activa, UUID autor, Instant fecha) {
    return new ReglaMantenimiento(
        UUID.randomUUID(),
        "RM-TEST",
        1,
        versionSieca,
        "/superestructura/elemento_portante_acero",
        objectMapper.createObjectNode(),
        TipoOrden.PREVENTIVO,
        "Pintura",
        objectMapper.createObjectNode(),
        activa,
        USUARIO_ADMIN_ID,
        autor,
        fecha,
        AHORA);
  }
}
