package gt.usac.cunoc.sgp.inspeccion.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.entity.*;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.*;
import gt.usac.cunoc.sgp.inspeccion.entity.*;
import gt.usac.cunoc.sgp.inspeccion.model.*;
import gt.usac.cunoc.sgp.inspeccion.repository.*;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;

class RecalculoIcMotorTest {
  private final ObjectMapper json = new ObjectMapper();
  private final UUID inspeccionId = UUID.randomUUID(), usuarioId = UUID.randomUUID();
  private final UUID anteriorId = UUID.randomUUID(), nuevaId = UUID.randomUUID();
  private final VersionFormularioRepository versiones = mock(VersionFormularioRepository.class);
  private final ConfiguracionIcElementoRepository pesos =
      mock(ConfiguracionIcElementoRepository.class);
  private final ResultadoIcRepository resultados = mock(ResultadoIcRepository.class);
  private final InspeccionRepository inspecciones = mock(InspeccionRepository.class);
  private final AuditoriaWriter auditoria = mock(AuditoriaWriter.class);
  private RecalculoIcService servicio;
  private PreparadorEntradaIcService preparador;
  private CalculoIndiceCondicionService calculador;
  private JsonNode datos;

  @BeforeEach
  void preparar() throws Exception {
    datos = cargar("datos");
    var resolver = new ResolverElementosIc();
    var agregador = new AgregadorCalificacionesIc();
    var conversor = new ConversorIndicadoresIc(agregador);
    calculador =
        new CalculoIndiceCondicionService(
            resolver,
            conversor,
            new ConversorMedicionesIc(conversor),
            agregador,
            new EvaluadorCondicionesCriticasIc(resolver),
            new HuellaEntradaIc(),
            new MotorPonderacionIc(agregador));
    preparador = new PreparadorEntradaIcService(versiones, pesos);
    VersionFormulario anterior = version(anteriorId, "v1");
    VersionFormulario nueva = version(nuevaId, "v2");
    when(versiones.findById(anteriorId)).thenReturn(Optional.of(anterior));
    when(versiones.findById(nuevaId)).thenReturn(Optional.of(nueva));
    when(versiones.findByActivaTrue()).thenReturn(Optional.of(nueva));
    when(pesos.findAllByVersionFormularioId(anteriorId))
        .thenReturn(List.of(peso(anterior, "/vigas", 1), peso(anterior, "/losa", 1)));
    when(pesos.findAllByVersionFormularioId(nuevaId))
        .thenReturn(List.of(peso(nueva, "/vigas", 3), peso(nueva, "/losa", 1)));
    var inspeccion = mock(Inspeccion.class);
    when(inspeccion.getEstado()).thenReturn("PUBLICADA");
    when(inspeccion.getPublicadoEn()).thenReturn(Instant.parse("2026-10-20T20:00:00Z"));
    when(inspeccion.getDatos()).thenReturn(datos);
    when(inspecciones.findByIdAndEliminadoEnIsNullForUpdate(inspeccionId))
        .thenReturn(Optional.of(inspeccion));
    servicio =
        new RecalculoIcService(
            inspecciones,
            resultados,
            versiones,
            mock(UserAccountRepository.class),
            preparador,
            calculador,
            auditoria,
            json,
            Clock.systemUTC());
    var calculo = calculador.calcular(preparador.preparar(anteriorId, datos));
    var original =
        new ResultadoIc(
            UUID.randomUUID(),
            inspeccionId,
            anteriorId,
            calculo.indiceCalculado(),
            calculo.estadoPorRango(),
            calculo.estadoCalculado(),
            json.createArrayNode(),
            json.valueToTree(calculo),
            calculo.hashEntrada(),
            calculo.versionAlgoritmo(),
            usuarioId,
            Instant.now());
    when(resultados.findPublicacionByInspeccionId(inspeccionId)).thenReturn(Optional.of(original));
  }

  @Test
  void mismoMotorCalcula50OriginalY25RecalculoConLosDatosIntactos() {
    JsonNode antes = datos.deepCopy();
    var original = resultados.findPublicacionByInspeccionId(inspeccionId).orElseThrow();
    var recalculo = servicio.recalcular(inspeccionId, usuarioId);
    assertThat(original.getIndiceCalculado()).isEqualByComparingTo("50");
    assertThat(recalculo.indiceCalculado()).isEqualByComparingTo("25");
    assertThat(recalculo.estadoCalculado()).isEqualTo(EstadoCondicion.MALO);
    assertThat(recalculo.hashEntrada()).isNotEqualTo(original.getHashEntrada());
    assertThat(datos).isEqualTo(antes);
    assertThat(original.getTipo()).isEqualTo("PUBLICACION");
  }

  @Test
  void nuevosElementosSinDatosNoInventanUnaCalificacionNiGuardanResultado() {
    ((com.fasterxml.jackson.databind.node.ObjectNode) datos).remove("losa");
    assertThatThrownBy(() -> servicio.recalcular(inspeccionId, usuarioId))
        .isInstanceOf(ApiException.class);
    verify(resultados, never()).saveAndFlush(any());
    verifyNoInteractions(auditoria);
  }

  private VersionFormulario version(UUID id, String codigo) throws Exception {
    var v = mock(VersionFormulario.class);
    when(v.getId()).thenReturn(id);
    when(v.getCodigo()).thenReturn(codigo);
    when(v.getEstado()).thenReturn(EstadoVersionFormulario.PUBLICADA);
    when(v.getEsquema()).thenReturn(cargar("esquema"));
    when(v.getReglasEvaluacionIc()).thenReturn(cargar("catalogo"));
    when(v.getVersionAlgoritmoIc()).thenReturn("ic-hu071-prueba-v1");
    return v;
  }

  private ConfiguracionIcElemento peso(VersionFormulario v, String ref, int valor) {
    return new ConfiguracionIcElemento(
        UUID.randomUUID(), v, ref, BigDecimal.valueOf(valor), "Peso HU071", Instant.now());
  }

  private JsonNode cargar(String nombre) throws Exception {
    try (var entrada =
        new ClassPathResource("inspeccion/hu071/" + nombre + ".json").getInputStream()) {
      return json.readTree(entrada);
    }
  }
}
