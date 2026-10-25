package gt.usac.cunoc.sgp.formulario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

  @Mock
  private gt.usac.cunoc.sgp.formulario.repository.AprobacionCalibracionRepository aprobaciones;

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
            clock,
            aprobaciones,
            org.mockito.Mockito.mock(
                gt.usac.cunoc.sgp.notificacion.service.AvisosAcademicosService.class));
  }

  @Test
  void detalleConsultaAprobacionSinRelacionPersistenteEnVersion() {
    var entidad = crearEntidad(UUID.randomUUID(), "v1", EstadoVersionFormulario.PUBLICADA, true);
    var aprobacion =
        new gt.usac.cunoc.sgp.formulario.entity.AprobacionCalibracion(
            UUID.randomUUID(), entidad, usuarioId, "Kenny", fija, "Conforme");
    when(versiones.findById(entidad.getId())).thenReturn(Optional.of(entidad));
    when(aprobaciones.findByVersionFormularioId(entidad.getId()))
        .thenReturn(Optional.of(aprobacion));
    var respuesta = service.obtenerPorId(entidad.getId());
    assertThat(respuesta.aprobacionCalibracion().aprobadoPorId()).isEqualTo(usuarioId);
    assertThat(respuesta.aprobacionCalibracion().observaciones()).isEqualTo("Conforme");
    assertThat(respuesta.rotuloCalibracion()).isEqualTo("Aprobada por Kenny el 12/10/2026");
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
  void obtenerPublicadaPorIdRetornaLaVersionAnteriorNoActiva() throws Exception {
    UUID id = UUID.randomUUID();
    VersionFormulario anterior =
        crearEntidad(id, "SIECA-v1", EstadoVersionFormulario.PUBLICADA, false);
    when(versiones.findById(id)).thenReturn(Optional.of(anterior));

    FormularioActivoResponse res = service.obtenerPublicadaPorId(id);

    assertThat(res.id()).isEqualTo(id);
    assertThat(res.codigo()).isEqualTo("SIECA-v1");
    assertThat(res.esquema()).isNotNull();
  }

  @Test
  void obtenerPublicadaPorIdNoExponeBorradores() throws Exception {
    UUID id = UUID.randomUUID();
    VersionFormulario borrador =
        crearEntidad(id, "SIECA-v2", EstadoVersionFormulario.BORRADOR, false);
    when(versiones.findById(id)).thenReturn(Optional.of(borrador));

    assertThatThrownBy(() -> service.obtenerPublicadaPorId(id))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> {
              assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(ex.getCode()).isEqualTo("version_no_encontrada");
            });
  }

  @Test
  void obtenerPublicadaPorIdFallaCuandoNoExiste() {
    UUID id = UUID.randomUUID();
    when(versiones.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.obtenerPublicadaPorId(id))
        .isInstanceOfSatisfying(
            ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void cargarPlantillaSiecaInicialCargaLos45PesosProvisionales() {
    when(versiones.existsByCodigo("SIECA-v1")).thenReturn(false);
    when(versiones.save(any(VersionFormulario.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var pesosGuardados = new java.util.ArrayList<ConfiguracionIcElemento>();
    when(configuracionesIc.save(any(ConfiguracionIcElemento.class)))
        .thenAnswer(
            invocacion -> {
              ConfiguracionIcElemento peso = invocacion.getArgument(0);
              pesosGuardados.add(peso);
              return peso;
            });
    when(configuracionesIc.findAllByVersionFormularioId(any())).thenReturn(pesosGuardados);
    VersionFormularioResponse response = service.cargarPlantillaSiecaInicial("SIECA-v1", usuarioId);

    assertThat(response.codigo()).isEqualTo("SIECA-v1");
    assertThat(response.estado()).isEqualTo(EstadoVersionFormulario.BORRADOR);
    assertThat(response.activa()).isFalse();
    assertThat(response.pesos()).hasSize(45);
    assertThat(response.rotuloCalibracion()).isEqualTo("Calibracion provisional · sin aprobar");

    ArgumentCaptor<VersionFormulario> captor = ArgumentCaptor.forClass(VersionFormulario.class);
    verify(versiones).save(captor.capture());

    JsonNode catalogo = captor.getValue().getReglasEvaluacionIc();
    assertThat(catalogo.get("elementos").size()).isEqualTo(45);

    for (JsonNode elemento : catalogo.get("elementos")) {
      assertThat(elemento.get("peso").decimalValue()).isPositive();
    }

    verify(configuracionesIc, org.mockito.Mockito.times(45))
        .save(any(ConfiguracionIcElemento.class));
  }

  @Test
  void mapeoExigeDeclararCamposNuevosAunqueElOrigenEsteCompleto() throws Exception {
    var origen = crearEntidad(UUID.randomUUID(), "base", EstadoVersionFormulario.PUBLICADA, true);
    var destino = crearEntidad(UUID.randomUUID(), "nueva", EstadoVersionFormulario.BORRADOR, false);
    origen.setEsquema(
        objectMapper.readTree(
            "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"}}}"));
    destino.setEsquema(
        objectMapper.readTree(
            "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"},\"b\":{\"type\":\"string\"}}}"));
    destino.setVersionAnterior(origen);
    when(versiones.findById(destino.getId())).thenReturn(Optional.of(destino));
    when(mapeos.findAllByVersionOrigenIdAndVersionDestinoId(origen.getId(), destino.getId()))
        .thenReturn(
            List.of(
                new MapeoCampoFormulario(
                    UUID.randomUUID(),
                    origen,
                    destino,
                    "/a",
                    "/a",
                    TipoMapeoCampo.EQUIVALENTE,
                    null,
                    usuarioId,
                    fija)));
    var analisis = service.analizarMapeo(destino.getId());
    assertThat(analisis.camposOrigenSinMapeo()).isEmpty();
    assertThat(analisis.camposDestinoSinMapeo()).containsExactly("/b");
    assertThat(analisis.mapeoCompleto()).isFalse();
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
    assertThat(analisis.camposDestinoSinMapeo()).containsExactly("/campo3");
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

  @Test
  void calibrarPesosInvalidosNoBorraLaConfiguracionAnterior() {
    UUID id = UUID.randomUUID();
    VersionFormulario borrador = crearEntidad(id, "v1", EstadoVersionFormulario.BORRADOR, false);
    when(versiones.findById(id)).thenReturn(Optional.of(borrador));

    var request =
        List.of(
            new ConfiguracionIcElementoRequest("/vigas", BigDecimal.ONE, "Vigas"),
            new ConfiguracionIcElementoRequest("/apoyos", BigDecimal.ZERO, "Apoyos"));

    assertThatThrownBy(() -> service.calibrarPesos(id, request, usuarioId))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("peso_invalido"));

    org.mockito.Mockito.verifyNoInteractions(configuracionesIc);
    verify(versiones, org.mockito.Mockito.never()).save(any(VersionFormulario.class));
  }

  @Test
  void calibrarPesosRechazaReferenciasDuplicadasSinBorrarLosAnteriores() {
    UUID id = UUID.randomUUID();
    VersionFormulario borrador = crearEntidad(id, "v1", EstadoVersionFormulario.BORRADOR, false);
    when(versiones.findById(id)).thenReturn(Optional.of(borrador));

    var request =
        List.of(
            new ConfiguracionIcElementoRequest("/vigas", BigDecimal.ONE, "Vigas"),
            new ConfiguracionIcElementoRequest("/vigas", BigDecimal.TEN, "Vigas"));

    assertThatThrownBy(() -> service.calibrarPesos(id, request, usuarioId))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("elemento_ic_duplicado"));

    org.mockito.Mockito.verifyNoInteractions(configuracionesIc);
  }

  private VersionFormulario crearEntidad(
      UUID id, String codigo, EstadoVersionFormulario estado, boolean activa) {
    try {
      JsonNode esquema = objectMapper.readTree("{\"type\":\"object\"}");
      JsonNode vinculos = objectMapper.readTree("{}");

      JsonNode reglas =
          objectMapper.readTree(
              """
                                                              {
                                                                "elementos": [
                                                                  {
                                                                    "elemento_ref": "/vigas",
                                                                    "nombre": "Vigas",
                                                                    "peso": null
                                                                  }
                                                                ],
                                                                "reglas_evaluacion_ic": {
                                                                  "version_algoritmo": "ic-sieca-v1-prueba",
                                                                  "reglas_anulacion": [],
                                                                  "escala_severidad": {
                                                                    "NO_PRESENTA": 0,
                                                                    "LEVE": 1,
                                                                    "MODERADO": 2,
                                                                    "SEVERO": 4
                                                                  },
                                                                  "agregacion": {
                                                                    "indicadores_de_un_elemento": "MAXIMO",
                                                                    "instancias_repetibles": "MAXIMO",
                                                                    "elementos_ausentes_portantes": 4,
                                                                    "elementos_ausentes_no_portantes": 4
                                                                  },
                                                                  "rangos_estado": {
                                                                    "BUENO": 75,
                                                                    "REGULAR": 50,
                                                                    "MALO": 0
                                                                  }
                                                                }
                                                              }
                                                              """);

      return new VersionFormulario(
          id, codigo, estado, activa, esquema, vinculos, reglas, null, usuarioId, fija, fija);
    } catch (Exception e) {
      throw new IllegalStateException("No fue posible construir la version de prueba", e);
    }
  }

  @Test
  void publicarRechazaPesosParcialesEIdentificaElElementoPendiente() throws Exception {
    UUID id = UUID.randomUUID();
    VersionFormulario borrador = crearEntidad(id, "v1", EstadoVersionFormulario.BORRADOR, false);

    borrador.setReglasEvaluacionIc(
        objectMapper.readTree(
            """
                                                                    {
                                                                      "elementos": [
                                                                        {"elemento_ref": "/vigas"},
                                                                        {"elemento_ref": "/apoyos"}
                                                                      ]
                                                                    }
                                                                    """));

    ConfiguracionIcElemento peso =
        new ConfiguracionIcElemento(
            UUID.randomUUID(), borrador, "/vigas", BigDecimal.ONE, "Vigas", fija);

    when(versiones.findById(id)).thenReturn(Optional.of(borrador));
    when(configuracionesIc.findAllByVersionFormularioId(id)).thenReturn(List.of(peso));

    assertThatThrownBy(() -> service.publicar(id, null, usuarioId))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("/apoyos")
        .satisfies(
            ex -> {
              ApiException error = (ApiException) ex;
              assertThat(error.getCode()).isEqualTo("pesos_incompletos");
              assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            });

    verify(versiones, org.mockito.Mockito.never()).desactivarTodas();
    verify(versiones, org.mockito.Mockito.never()).saveAndFlush(any(VersionFormulario.class));

    assertThat(borrador.getEstado()).isEqualTo(EstadoVersionFormulario.BORRADOR);
    assertThat(borrador.isActiva()).isFalse();
  }

  @Test
  void configurarEscalaConservaElCatalogoYLasDemasReglas() throws Exception {
    UUID id = UUID.randomUUID();
    VersionFormulario borrador = crearEntidad(id, "v1", EstadoVersionFormulario.BORRADOR, false);
    JsonNode original =
        objectMapper.readTree(
            """
                                                {
                                                  "elementos": [{"elemento_ref": "/vigas"}],
                                                  "reglas_evaluacion_ic": {
                                                    "agregacion": {"instancias_repetibles": "MAX"},
                                                    "escala_severidad": {"NO_PRESENTA": 0, "LEVE": null, "MODERADO": null, "SEVERO": null}
                                                  }
                                                }
                                                """);
    borrador.setReglasEvaluacionIc(original);
    when(versiones.findById(id)).thenReturn(Optional.of(borrador));
    when(versiones.save(any(VersionFormulario.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    service.configurarEscala(id, escalaValida(), usuarioId);
    JsonNode guardada = borrador.getReglasEvaluacionIc();
    assertThat(guardada.get("elementos")).isEqualTo(original.get("elementos"));
    assertThat(guardada.at("/reglas_evaluacion_ic/agregacion/instancias_repetibles").asText())
        .isEqualTo("MAX");
    assertThat(guardada.at("/reglas_evaluacion_ic/escala_severidad/SEVERO").decimalValue())
        .isEqualByComparingTo("4");
    assertThat(guardada.at("/reglas_evaluacion_ic/escala_severidad"))
        .isEqualTo(
            objectMapper
                .readTree(guardada.toString())
                .at("/reglas_evaluacion_ic/escala_severidad"));
    assertThat(original.at("/reglas_evaluacion_ic/escala_severidad/SEVERO").isNull()).isTrue();
    assertThat(borrador.getCalibradoPorId()).isEqualTo(usuarioId);
    assertThat(borrador.getCalibradoEn()).isEqualTo(fija);
    verify(versiones).save(borrador);
    verify(configuracionesIc).findAllByVersionFormularioId(id);
    verify(configuracionesIc, org.mockito.Mockito.never())
        .deleteAllByVersionFormularioId(any(UUID.class));
    verify(configuracionesIc, org.mockito.Mockito.never()).save(any(ConfiguracionIcElemento.class));
    verify(configuracionesIc, org.mockito.Mockito.never()).saveAll(any());
    org.mockito.Mockito.verifyNoMoreInteractions(configuracionesIc);
  }

  @Test
  void configurarEscalaRechazaVersionPublicada() {
    UUID id = UUID.randomUUID();
    VersionFormulario publicada = crearEntidad(id, "v1", EstadoVersionFormulario.PUBLICADA, true);
    when(versiones.findById(id)).thenReturn(Optional.of(publicada));
    assertThatThrownBy(() -> service.configurarEscala(id, escalaValida(), usuarioId))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException error = (ApiException) ex;
              assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(error.getCode()).isEqualTo("version_inmutable");
            });
    verify(versiones, org.mockito.Mockito.never()).save(any(VersionFormulario.class));
  }

  @Test
  void configurarEscalaRechazaNivelesIncompletos() {
    UUID id = UUID.randomUUID();
    VersionFormulario borrador = crearEntidad(id, "v1", EstadoVersionFormulario.BORRADOR, false);
    when(versiones.findById(id)).thenReturn(Optional.of(borrador));
    var request =
        new gt.usac.cunoc.sgp.formulario.dto.ConfigurarEscalaIcRequest(
            java.util.Map.of("NO_PRESENTA", BigDecimal.ZERO));
    assertThatThrownBy(() -> service.configurarEscala(id, request, usuarioId))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("escala_incompleta"));
    verify(versiones, org.mockito.Mockito.never()).save(any(VersionFormulario.class));
  }

  @Test
  void configurarEscalaRechazaValoresFueraDeRangoSinModificarLaVersion() {
    UUID id = UUID.randomUUID();
    VersionFormulario borrador = crearEntidad(id, "v1", EstadoVersionFormulario.BORRADOR, false);
    JsonNode anterior = borrador.getReglasEvaluacionIc().deepCopy();
    when(versiones.findById(id)).thenReturn(Optional.of(borrador));
    for (String valor : List.of("-1", "4.01")) {
      var valores = new java.util.HashMap<>(escalaValida().valores());
      valores.put("SEVERO", new BigDecimal(valor));
      var request = new gt.usac.cunoc.sgp.formulario.dto.ConfigurarEscalaIcRequest(valores);
      assertThatThrownBy(() -> service.configurarEscala(id, request, usuarioId))
          .isInstanceOf(ApiException.class)
          .satisfies(
              ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("severidad_invalida"));
    }
    assertThat(borrador.getReglasEvaluacionIc()).isEqualTo(anterior);
    verify(versiones, org.mockito.Mockito.never()).save(any(VersionFormulario.class));
  }

  @Test
  void configurarEscalaExigeCeroParaNoPresenta() {
    UUID id = UUID.randomUUID();
    VersionFormulario borrador = crearEntidad(id, "v1", EstadoVersionFormulario.BORRADOR, false);
    when(versiones.findById(id)).thenReturn(Optional.of(borrador));
    var valores = new java.util.HashMap<>(escalaValida().valores());
    valores.put("NO_PRESENTA", BigDecimal.ONE);
    var request = new gt.usac.cunoc.sgp.formulario.dto.ConfigurarEscalaIcRequest(valores);
    assertThatThrownBy(() -> service.configurarEscala(id, request, usuarioId))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("severidad_invalida"));
    verify(versiones, org.mockito.Mockito.never()).save(any(VersionFormulario.class));
  }

  private gt.usac.cunoc.sgp.formulario.dto.ConfigurarEscalaIcRequest escalaValida() {
    return new gt.usac.cunoc.sgp.formulario.dto.ConfigurarEscalaIcRequest(
        java.util.Map.of(
            "NO_PRESENTA",
            BigDecimal.ZERO,
            "LEVE",
            BigDecimal.ONE,
            "MODERADO",
            BigDecimal.valueOf(2),
            "SEVERO",
            BigDecimal.valueOf(4)));
  }

  @Test
  void publicarRechazaReglasPendientesSinDesactivarLaVersionActiva() {
    UUID id = UUID.randomUUID();
    VersionFormulario borrador = crearEntidad(id, "v1", EstadoVersionFormulario.BORRADOR, false);

    com.fasterxml.jackson.databind.node.ObjectNode reglas =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            borrador.getReglasEvaluacionIc().get("reglas_evaluacion_ic");

    ((com.fasterxml.jackson.databind.node.ObjectNode) reglas.get("agregacion"))
        .putNull("indicadores_de_un_elemento");

    ConfiguracionIcElemento peso =
        new ConfiguracionIcElemento(
            UUID.randomUUID(), borrador, "/vigas", BigDecimal.ONE, "Vigas", fija);

    when(versiones.findById(id)).thenReturn(Optional.of(borrador));
    when(configuracionesIc.findAllByVersionFormularioId(id)).thenReturn(List.of(peso));

    assertThatThrownBy(() -> service.publicar(id, null, usuarioId))
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> {
              assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(error.getCode()).isEqualTo("reglas_ic_invalidas");
              assertThat(error.getMessage()).contains("agregacion.indicadores_de_un_elemento");
            });

    verify(versiones, org.mockito.Mockito.never()).desactivarTodas();
    verify(versiones, org.mockito.Mockito.never()).saveAndFlush(any(VersionFormulario.class));

    assertThat(borrador.getEstado()).isEqualTo(EstadoVersionFormulario.BORRADOR);
    assertThat(borrador.isActiva()).isFalse();
  }

  @Test
  void publicarRechazaConversionBooleanaIncompletaSinDesactivarLaVersionActiva() throws Exception {
    UUID id = UUID.randomUUID();
    VersionFormulario borrador = crearEntidad(id, "v1", EstadoVersionFormulario.BORRADOR, false);

    com.fasterxml.jackson.databind.node.ObjectNode configuracion =
        (com.fasterxml.jackson.databind.node.ObjectNode) borrador.getReglasEvaluacionIc();

    configuracion.set(
        "elementos",
        objectMapper.readTree(
            """
                                                            [
                                                              {
                                                                "elemento_ref": "/otros/senalizacion/informativa",
                                                                "indicadores": ["existe"]
                                                              }
                                                            ]
                                                            """));

    com.fasterxml.jackson.databind.node.ObjectNode reglas =
        (com.fasterxml.jackson.databind.node.ObjectNode) configuracion.get("reglas_evaluacion_ic");

    reglas.set(
        "conversion_indicadores",
        objectMapper.readTree(
            """
                                                            {
                                                              "booleanos": {
                                                                "senal.existe=false": 4
                                                              }
                                                            }
                                                            """));

    ConfiguracionIcElemento peso =
        new ConfiguracionIcElemento(
            UUID.randomUUID(),
            borrador,
            "/otros/senalizacion/informativa",
            BigDecimal.ONE,
            "Senalizacion informativa",
            fija);

    when(versiones.findById(id)).thenReturn(Optional.of(borrador));
    when(configuracionesIc.findAllByVersionFormularioId(id)).thenReturn(List.of(peso));

    assertThatThrownBy(() -> service.publicar(id, null, usuarioId))
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> {
              assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(error.getCode()).isEqualTo("conversiones_ic_invalidas");
              assertThat(error.getMessage()).contains("senal.existe=true");
            });

    verify(versiones, org.mockito.Mockito.never()).desactivarTodas();
    verify(versiones, org.mockito.Mockito.never()).saveAndFlush(any(VersionFormulario.class));
    assertThat(borrador.getEstado()).isEqualTo(EstadoVersionFormulario.BORRADOR);
  }

  @Test
  void publicarRechazaUmbralesDesordenadosSinDesactivarLaVersionActiva() throws Exception {
    UUID id = UUID.randomUUID();
    VersionFormulario borrador = crearEntidad(id, "v1", EstadoVersionFormulario.BORRADOR, false);

    com.fasterxml.jackson.databind.node.ObjectNode configuracion =
        (com.fasterxml.jackson.databind.node.ObjectNode) borrador.getReglasEvaluacionIc();

    configuracion.set(
        "elementos",
        objectMapper.readTree(
            """
                                                            [
                                                              {
                                                                "elemento_ref": "/elementos_no_estructurales/pasamanos_barandal",
                                                                "indicadores": ["golpeado_longitud_m"]
                                                              }
                                                            ]
                                                            """));

    com.fasterxml.jackson.databind.node.ObjectNode reglas =
        (com.fasterxml.jackson.databind.node.ObjectNode) configuracion.get("reglas_evaluacion_ic");

    reglas.set(
        "conversion_indicadores",
        objectMapper.readTree(
            """
                                                            {
                                                              "numericos": {
                                                                "golpeado_longitud_m": {
                                                                  "umbrales_m": {
                                                                    "LEVE": 2,
                                                                    "MODERADO": 1,
                                                                    "SEVERO": 3
                                                                  }
                                                                }
                                                              }
                                                            }
                                                            """));

    ConfiguracionIcElemento peso =
        new ConfiguracionIcElemento(
            UUID.randomUUID(),
            borrador,
            "/elementos_no_estructurales/pasamanos_barandal",
            BigDecimal.ONE,
            "Pasamanos",
            fija);

    when(versiones.findById(id)).thenReturn(Optional.of(borrador));
    when(configuracionesIc.findAllByVersionFormularioId(id)).thenReturn(List.of(peso));

    assertThatThrownBy(() -> service.publicar(id, null, usuarioId))
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> {
              assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(error.getCode()).isEqualTo("conversiones_ic_invalidas");
              assertThat(error.getMessage())
                  .contains("golpeado_longitud_m.umbrales_m")
                  .contains("0 < LEVE < MODERADO < SEVERO");
            });

    verify(versiones, org.mockito.Mockito.never()).desactivarTodas();
    verify(versiones, org.mockito.Mockito.never()).saveAndFlush(any(VersionFormulario.class));
    assertThat(borrador.getEstado()).isEqualTo(EstadoVersionFormulario.BORRADOR);
    assertThat(borrador.isActiva()).isFalse();
  }

  @Test
  void publicarPermiteConversionesCompletasParaLosIndicadoresDelCatalogo() throws Exception {
    UUID id = UUID.randomUUID();
    VersionFormulario borrador = crearEntidad(id, "v1", EstadoVersionFormulario.BORRADOR, false);

    com.fasterxml.jackson.databind.node.ObjectNode configuracion =
        (com.fasterxml.jackson.databind.node.ObjectNode) borrador.getReglasEvaluacionIc();

    configuracion.set(
        "elementos",
        objectMapper.readTree(
            """
                                                            [
                                                              {
                                                                "elemento_ref": "/otros/senalizacion/informativa",
                                                                "indicadores": ["existe", "estado"]
                                                              },
                                                              {
                                                                "elemento_ref": "/elementos_no_estructurales/pasamanos_barandal",
                                                                "indicadores": ["golpeado_longitud_m"]
                                                              }
                                                            ]
                                                            """));

    com.fasterxml.jackson.databind.node.ObjectNode reglas =
        (com.fasterxml.jackson.databind.node.ObjectNode) configuracion.get("reglas_evaluacion_ic");

    reglas.set(
        "conversion_indicadores",
        objectMapper.readTree(
            """
                                                            {
                                                              "booleanos": {
                                                                "senal.existe=true": 0,
                                                                "senal.existe=false": 4
                                                              },
                                                              "enumerados": {
                                                                "estado_bueno_malo": {
                                                                  "BUENO": 0,
                                                                  "MALO": 4
                                                                }
                                                              },
                                                              "numericos": {
                                                                "golpeado_longitud_m": {
                                                                  "umbrales_m": {
                                                                    "LEVE": 1,
                                                                    "MODERADO": 2,
                                                                    "SEVERO": 3
                                                                  }
                                                                }
                                                              }
                                                            }
                                                            """));

    List<ConfiguracionIcElemento> pesos =
        List.of(
            new ConfiguracionIcElemento(
                UUID.randomUUID(),
                borrador,
                "/otros/senalizacion/informativa",
                BigDecimal.ONE,
                "Senalizacion informativa",
                fija),
            new ConfiguracionIcElemento(
                UUID.randomUUID(),
                borrador,
                "/elementos_no_estructurales/pasamanos_barandal",
                BigDecimal.ONE,
                "Pasamanos",
                fija));

    when(versiones.findById(id)).thenReturn(Optional.of(borrador));
    when(configuracionesIc.findAllByVersionFormularioId(id)).thenReturn(pesos);
    when(versiones.saveAndFlush(any(VersionFormulario.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    VersionFormularioResponse respuesta = service.publicar(id, null, usuarioId);

    verify(versiones).desactivarTodas();
    verify(versiones).saveAndFlush(borrador);
    assertThat(respuesta.estado()).isEqualTo(EstadoVersionFormulario.PUBLICADA);
    assertThat(respuesta.activa()).isTrue();
    assertThat(respuesta.publicadoPorId()).isEqualTo(usuarioId);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"RN-IC-05a", "RN-IC-05c"})
  void publicarRechazaReglasCriticasPendientesAntesDeDesactivarLaVersionActual(String reglaId)
      throws Exception {
    UUID id = UUID.randomUUID();
    VersionFormulario borrador = crearEntidad(id, "v1", EstadoVersionFormulario.BORRADOR, false);
    var reglas =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            borrador.getReglasEvaluacionIc().get("reglas_evaluacion_ic");
    var regla = reglas.putArray("reglas_anulacion").addObject();
    regla
        .put("id", reglaId)
        .put("descripcion", "Decision academica pendiente")
        .put("estado_resultante", "MALO")
        .put("pendiente", "Confirmar");
    regla.putArray("elementos").add("/vigas");
    regla.putObject("condicion").put("campo", "existencia").put("igual_a", "AUSENTE");
    when(versiones.findById(id)).thenReturn(Optional.of(borrador));
    when(configuracionesIc.findAllByVersionFormularioId(id))
        .thenReturn(
            List.of(
                new ConfiguracionIcElemento(
                    UUID.randomUUID(), borrador, "/vigas", BigDecimal.ONE, "Vigas", fija)));
    assertThatThrownBy(() -> service.publicar(id, null, usuarioId))
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> {
              assertThat(error.getCode()).isEqualTo("reglas_anulacion_ic_invalidas");
              assertThat(error.getMessage()).contains(reglaId);
            });
    verify(versiones, org.mockito.Mockito.never()).desactivarTodas();
    verify(versiones, org.mockito.Mockito.never()).saveAndFlush(any(VersionFormulario.class));
    assertThat(borrador.getEstado()).isEqualTo(EstadoVersionFormulario.BORRADOR);
  }

  @Test
  void configurarReglasCriticasConservaElRestoDeLaConfiguracion() throws Exception {
    UUID id = UUID.randomUUID();
    VersionFormulario borrador = crearEntidad(id, "v1", EstadoVersionFormulario.BORRADOR, false);
    JsonNode anterior = borrador.getReglasEvaluacionIc().deepCopy();
    JsonNode reglas =
        objectMapper.readTree(
            """
                                                [{"id":"ausencia","descripcion":"Ausencia de viga","estado_resultante":"MALO",
                                                  "pendiente":null,"elementos":["/vigas"],"condicion":{"campo":"existencia","igual_a":"AUSENTE"}}]
                                                """);
    when(versiones.findById(id)).thenReturn(Optional.of(borrador));
    when(versiones.save(any(VersionFormulario.class)))
        .thenAnswer(invocacion -> invocacion.getArgument(0));
    service.configurarReglasCriticas(
        id,
        new gt.usac.cunoc.sgp.formulario.dto.ConfigurarReglasCriticasIcRequest(reglas),
        usuarioId);
    assertThat(borrador.getReglasEvaluacionIc().get("elementos"))
        .isEqualTo(anterior.get("elementos"));
    assertThat(borrador.getReglasEvaluacionIc().at("/reglas_evaluacion_ic/escala_severidad"))
        .isEqualTo(anterior.at("/reglas_evaluacion_ic/escala_severidad"));
    assertThat(borrador.getReglasEvaluacionIc().at("/reglas_evaluacion_ic/reglas_anulacion"))
        .isEqualTo(reglas);
    assertThat(borrador.getCalibradoPorId()).isEqualTo(usuarioId);
    assertThat(anterior.at("/reglas_evaluacion_ic/reglas_anulacion")).isEmpty();
  }

  @Test
  void configurarReglasCriticasRechazaVersionPublicada() {
    UUID id = UUID.randomUUID();
    when(versiones.findById(id))
        .thenReturn(Optional.of(crearEntidad(id, "v1", EstadoVersionFormulario.PUBLICADA, true)));
    assertThatThrownBy(
            () ->
                service.configurarReglasCriticas(
                    id,
                    new gt.usac.cunoc.sgp.formulario.dto.ConfigurarReglasCriticasIcRequest(
                        objectMapper.createArrayNode()),
                    usuarioId))
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    verify(versiones, org.mockito.Mockito.never()).save(any(VersionFormulario.class));
  }

  @Test
  void hu053GuardadosObsoletosNoEjecutanNingunaMutacion() throws Exception {
    UUID id = UUID.randomUUID();
    var version = crearEntidad(id, "v53", EstadoVersionFormulario.BORRADOR, false);
    when(versiones.findByIdForUpdate(id)).thenReturn(Optional.of(version));
    java.util.List<Runnable> operaciones =
        java.util.List.of(
            () -> service.calibrarPesos(id, java.util.List.of(), usuarioId, 1L),
            () -> service.configurarEscala(id, null, usuarioId, 1L),
            () -> service.configurarReglasCriticas(id, null, usuarioId, 1L),
            () -> service.actualizarBorrador(id, null, null, null, "otro", usuarioId, 1L));
    for (var operacion : operaciones) {
      assertThatThrownBy(operacion::run)
          .isInstanceOfSatisfying(
              ApiException.class,
              e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getCode()).isEqualTo("concurrencia_conflicto");
              });
    }
    verify(versiones, never()).save(any());
    verify(versiones, never()).flush();
    verifyNoInteractions(configuracionesIc, mapeos);
  }

  @Test
  void hu053VersionPublicadaNoAceptaGuardadosConVersion() throws Exception {
    UUID id = UUID.randomUUID();
    var version = crearEntidad(id, "v53", EstadoVersionFormulario.PUBLICADA, true);
    when(versiones.findByIdForUpdate(id)).thenReturn(Optional.of(version));
    assertThatThrownBy(() -> service.configurarEscala(id, null, usuarioId, version.getVersion()))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getCode()).isEqualTo("version_inmutable");
            });
    verify(versiones, never()).save(any());
  }
}
