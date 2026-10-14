package gt.usac.cunoc.sgp.formulario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.dto.AnalisisMapeoResponse;
import gt.usac.cunoc.sgp.formulario.dto.ConfiguracionIcElementoRequest;
import gt.usac.cunoc.sgp.formulario.dto.CrearVersionRequest;
import gt.usac.cunoc.sgp.formulario.dto.DeclaracionMapeoRequest;
import gt.usac.cunoc.sgp.formulario.dto.FormularioActivoResponse;
import gt.usac.cunoc.sgp.formulario.dto.VersionFormularioResponse;
import gt.usac.cunoc.sgp.formulario.dto.VersionFormularioResumenResponse;
import gt.usac.cunoc.sgp.formulario.entity.ConfiguracionIcElemento;
import gt.usac.cunoc.sgp.formulario.entity.MapeoCampoFormulario;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.mapper.VersionFormularioMapper;
import gt.usac.cunoc.sgp.formulario.mapper.VersionFormularioMapperImpl;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.model.TipoMapeoCampo;
import gt.usac.cunoc.sgp.formulario.repository.ConfiguracionIcElementoRepository;
import gt.usac.cunoc.sgp.formulario.repository.MapeoCampoFormularioRepository;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.formulario.validation.CampoSchemaExtractor;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class VersionFormularioServiceTest {

  @Mock private VersionFormularioRepository versiones;
  @Mock private MapeoCampoFormularioRepository mapeos;
  @Mock private ConfiguracionIcElementoRepository configuracionesIc;

  private VersionFormularioMapper mapper;
  private JsonSchemaValidatorService schemaValidator;
  private CampoSchemaExtractor campoExtractor;
  private ObjectMapper objectMapper;
  private Clock clock;

  private VersionFormularioService service;

  private final Instant fija = Instant.parse("2026-10-13T01:00:00Z");
  private final UUID usuarioId = UUID.fromString("019a0000-0000-7000-8000-000000000001");

  @BeforeEach
  void setUp() {
    mapper = new VersionFormularioMapperImpl();
    schemaValidator = new JsonSchemaValidatorService();
    campoExtractor = new CampoSchemaExtractor();
    objectMapper = new ObjectMapper();
    clock = Clock.fixed(fija, ZoneOffset.UTC);

    service =
        new VersionFormularioService(
            versiones,
            mapeos,
            configuracionesIc,
            mapper,
            schemaValidator,
            campoExtractor,
            objectMapper,
            clock);
  }

  @Test
  void listarVersionesRetornaResumen() {
    VersionFormulario v1 =
        crearEntidad(UUID.randomUUID(), "v1", EstadoVersionFormulario.PUBLICADA, true);
    VersionFormulario v2 =
        crearEntidad(UUID.randomUUID(), "v2", EstadoVersionFormulario.BORRADOR, false);
    when(versiones.findAllByOrderByCreadoEnDesc()).thenReturn(List.of(v2, v1));

    List<VersionFormularioResumenResponse> lista = service.listarVersiones();

    assertThat(lista).hasSize(2);
    assertThat(lista.get(0).codigo()).isEqualTo("v2");
    assertThat(lista.get(1).codigo()).isEqualTo("v1");
  }

  @Test
  void obtenerPorIdFallaCuandoNoExiste() {
    UUID id = UUID.randomUUID();
    when(versiones.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.obtenerPorId(id))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(apiEx.getCode()).isEqualTo("version_no_encontrada");
            });
  }

  @Test
  void obtenerActivaRetornaVersionActiva() throws Exception {
    VersionFormulario v =
        crearEntidad(UUID.randomUUID(), "SIECA-v1", EstadoVersionFormulario.PUBLICADA, true);
    when(versiones.findByActivaTrue()).thenReturn(Optional.of(v));

    FormularioActivoResponse res = service.obtenerActiva();

    assertThat(res.codigo()).isEqualTo("SIECA-v1");
    assertThat(res.esquema()).isNotNull();
  }

  @Test
  void cargarPlantillaSiecaInicialCreaBorradorConPesos() {
    when(versiones.existsByCodigo("SIECA-v1")).thenReturn(false);
    when(versiones.save(any(VersionFormulario.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    VersionFormularioResponse response = service.cargarPlantillaSiecaInicial("SIECA-v1", usuarioId);

    assertThat(response.codigo()).isEqualTo("SIECA-v1");
    assertThat(response.estado()).isEqualTo(EstadoVersionFormulario.BORRADOR);
    assertThat(response.activa()).isFalse();

    ArgumentCaptor<ConfiguracionIcElemento> captor =
        ArgumentCaptor.forClass(ConfiguracionIcElemento.class);
    verify(configuracionesIc, org.mockito.Mockito.atLeast(40)).save(captor.capture());
    assertThat(captor.getAllValues()).isNotEmpty();
  }

  @Test
  void cargarPlantillaSiecaFallaSiCodigoYaExiste() {
    when(versiones.existsByCodigo("SIECA-v1")).thenReturn(true);

    assertThatThrownBy(() -> service.cargarPlantillaSiecaInicial("SIECA-v1", usuarioId))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(apiEx.getCode()).isEqualTo("codigo_duplicado");
            });
  }

  @Test
  void crearNuevaVersionDesdeBaseCopiaConfiguraciones() throws Exception {
    UUID baseId = UUID.randomUUID();
    VersionFormulario base =
        crearEntidad(baseId, "SIECA-v1", EstadoVersionFormulario.PUBLICADA, true);
    ConfiguracionIcElemento pesoBase =
        new ConfiguracionIcElemento(
            UUID.randomUUID(), base, "/vigas", BigDecimal.valueOf(1.5), "Vigas", fija);

    when(versiones.existsByCodigo("SIECA-v2")).thenReturn(false);
    when(versiones.findById(baseId)).thenReturn(Optional.of(base));
    when(configuracionesIc.findAllByVersionFormularioId(baseId)).thenReturn(List.of(pesoBase));
    when(versiones.save(any(VersionFormulario.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    JsonNode nuevoEsquema = objectMapper.readTree("{\"type\":\"object\"}");
    CrearVersionRequest req =
        new CrearVersionRequest("SIECA-v2", baseId, false, nuevoEsquema, null, null);

    VersionFormularioResponse res = service.crear(req, usuarioId);

    assertThat(res.codigo()).isEqualTo("SIECA-v2");
    assertThat(res.versionAnteriorId()).isEqualTo(baseId);
    verify(configuracionesIc).save(any(ConfiguracionIcElemento.class));
  }

  @Test
  void actualizarBorradorRechazaVersionPublicada() throws Exception {
    UUID id = UUID.randomUUID();
    VersionFormulario publicada = crearEntidad(id, "v1", EstadoVersionFormulario.PUBLICADA, true);
    when(versiones.findById(id)).thenReturn(Optional.of(publicada));

    JsonNode nuevoEsquema = objectMapper.readTree("{\"type\":\"object\"}");

    assertThatThrownBy(
            () -> service.actualizarBorrador(id, nuevoEsquema, null, null, "v1-nuevo", usuarioId))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(apiEx.getCode()).isEqualTo("version_inmutable");
            });
  }

  @Test
  void calibrarPesosRechazaPesosMenoresOIgualesACero() {
    UUID id = UUID.randomUUID();
    VersionFormulario borrador = crearEntidad(id, "v1", EstadoVersionFormulario.BORRADOR, false);
    when(versiones.findById(id)).thenReturn(Optional.of(borrador));

    List<ConfiguracionIcElementoRequest> request =
        List.of(new ConfiguracionIcElementoRequest("/vigas", BigDecimal.ZERO, "desc"));

    assertThatThrownBy(() -> service.calibrarPesos(id, request, usuarioId))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getCode()).isEqualTo("peso_invalido");
            });
  }

  @Test
  void calibrarPesosRechazaModificacionSiEstaPublicada() {
    UUID id = UUID.randomUUID();
    VersionFormulario publicada = crearEntidad(id, "v1", EstadoVersionFormulario.PUBLICADA, true);
    when(versiones.findById(id)).thenReturn(Optional.of(publicada));

    List<ConfiguracionIcElementoRequest> request =
        List.of(new ConfiguracionIcElementoRequest("/vigas", BigDecimal.ONE, "desc"));

    assertThatThrownBy(() -> service.calibrarPesos(id, request, usuarioId))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(apiEx.getCode()).isEqualTo("version_inmutable");
            });
  }

  @Test
  void guardarMapeosRechazaMapeoIncoherenteNuevoConOrigen() {
    UUID v1Id = UUID.randomUUID();
    UUID v2Id = UUID.randomUUID();
    VersionFormulario v1 = crearEntidad(v1Id, "v1", EstadoVersionFormulario.PUBLICADA, true);
    VersionFormulario v2 = crearEntidad(v2Id, "v2", EstadoVersionFormulario.BORRADOR, false);
    v2.setVersionAnterior(v1);

    when(versiones.findById(v2Id)).thenReturn(Optional.of(v2));

    List<DeclaracionMapeoRequest> decs =
        List.of(
            new DeclaracionMapeoRequest("/campo_orig", "/campo_dest", TipoMapeoCampo.NUEVO, "obs"));

    assertThatThrownBy(() -> service.guardarMapeos(v2Id, decs, usuarioId))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getCode()).isEqualTo("mapeo_incoherente");
            });
  }

  @Test
  void analizarMapeoDetectaCamposOrigenPendientes() throws Exception {
    UUID v1Id = UUID.randomUUID();
    UUID v2Id = UUID.randomUUID();

    JsonNode esquemaV1 =
        objectMapper.readTree(
            """
            {
              "type": "object",
              "properties": {
                "campo1": { "type": "string" },
                "campo2": { "type": "string" }
              }
            }
            """);

    JsonNode esquemaV2 =
        objectMapper.readTree(
            """
            {
              "type": "object",
              "properties": {
                "campo1": { "type": "string" },
                "campo3": { "type": "string" }
              }
            }
            """);

    VersionFormulario v1 = crearEntidad(v1Id, "v1", EstadoVersionFormulario.PUBLICADA, true);
    v1.setEsquema(esquemaV1);
    VersionFormulario v2 = crearEntidad(v2Id, "v2", EstadoVersionFormulario.BORRADOR, false);
    v2.setEsquema(esquemaV2);
    v2.setVersionAnterior(v1);

    when(versiones.findById(v2Id)).thenReturn(Optional.of(v2));
    MapeoCampoFormulario m1 =
        new MapeoCampoFormulario(
            UUID.randomUUID(),
            v1,
            v2,
            "/campo1",
            "/campo1",
            TipoMapeoCampo.EQUIVALENTE,
            null,
            usuarioId,
            fija);
    when(mapeos.findAllByVersionOrigenIdAndVersionDestinoId(v1Id, v2Id)).thenReturn(List.of(m1));

    AnalisisMapeoResponse analisis = service.analizarMapeo(v2Id);

    assertThat(analisis.mapeoCompleto()).isFalse();
    assertThat(analisis.camposOrigenSinMapeo()).containsExactly("/campo2");
  }

  @Test
  void publicarRechazaSiMapeoIncompleto() throws Exception {
    UUID v1Id = UUID.randomUUID();
    UUID v2Id = UUID.randomUUID();

    JsonNode esquemaV1 =
        objectMapper.readTree(
            """
            {
              "type": "object",
              "properties": {
                "campo1": { "type": "string" }
              }
            }
            """);
    JsonNode esquemaV2 = objectMapper.readTree("{\"type\":\"object\"}");

    VersionFormulario v1 = crearEntidad(v1Id, "v1", EstadoVersionFormulario.PUBLICADA, true);
    v1.setEsquema(esquemaV1);
    VersionFormulario v2 = crearEntidad(v2Id, "v2", EstadoVersionFormulario.BORRADOR, false);
    v2.setEsquema(esquemaV2);
    v2.setVersionAnterior(v1);

    when(versiones.findById(v2Id)).thenReturn(Optional.of(v2));
    when(mapeos.findAllByVersionOrigenIdAndVersionDestinoId(v1Id, v2Id))
        .thenReturn(Collections.emptyList());

    assertThatThrownBy(() -> service.publicar(v2Id, null, usuarioId))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getCode()).isEqualTo("mapeo_incompleto");
            });
  }

  @Test
  void publicarRechazaSiNoHayPesosConfigurados() throws Exception {
    UUID v1Id = UUID.randomUUID();
    VersionFormulario v1 = crearEntidad(v1Id, "v1", EstadoVersionFormulario.BORRADOR, false);
    when(versiones.findById(v1Id)).thenReturn(Optional.of(v1));
    when(configuracionesIc.findAllByVersionFormularioId(v1Id)).thenReturn(Collections.emptyList());

    assertThatThrownBy(() -> service.publicar(v1Id, null, usuarioId))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getCode()).isEqualTo("pesos_incompletos");
            });
  }

  @Test
  void publicarExitosoDesactivaVersionesPreviasYActivaActual() {
    UUID id = UUID.randomUUID();
    VersionFormulario borrador = crearEntidad(id, "v1", EstadoVersionFormulario.BORRADOR, false);
    ConfiguracionIcElemento peso =
        new ConfiguracionIcElemento(
            UUID.randomUUID(), borrador, "/vigas", BigDecimal.ONE, "desc", fija);

    when(versiones.findById(id)).thenReturn(Optional.of(borrador));
    when(configuracionesIc.findAllByVersionFormularioId(id)).thenReturn(List.of(peso));
    when(versiones.saveAndFlush(any(VersionFormulario.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    VersionFormularioResponse response = service.publicar(id, null, usuarioId);

    verify(versiones).desactivarTodas();
    assertThat(response.estado()).isEqualTo(EstadoVersionFormulario.PUBLICADA);
    assertThat(response.activa()).isTrue();
    assertThat(response.hashContenido()).isNotEmpty();
    assertThat(response.publicadoPorId()).isEqualTo(usuarioId);
  }

  private VersionFormulario crearEntidad(
      UUID id, String codigo, EstadoVersionFormulario estado, boolean activa) {
    try {
      JsonNode esquema = objectMapper.readTree("{\"type\":\"object\"}");
      JsonNode vinculos = objectMapper.readTree("{}");
      JsonNode reglas = objectMapper.readTree("{}");
      return new VersionFormulario(
          id, codigo, estado, activa, esquema, vinculos, reglas, null, usuarioId, fija, fija);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
