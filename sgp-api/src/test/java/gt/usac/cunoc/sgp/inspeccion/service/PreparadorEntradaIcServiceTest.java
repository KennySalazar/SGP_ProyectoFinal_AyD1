package gt.usac.cunoc.sgp.inspeccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.entity.ConfiguracionIcElemento;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.ConfiguracionIcElementoRepository;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PreparadorEntradaIcServiceTest {

  private static final UUID VERSION_ID = UUID.randomUUID();
  private static final UUID USUARIO_ID = UUID.randomUUID();
  private static final Instant AHORA = Instant.parse("2026-10-14T12:00:00Z");
  private static final String ALGORITMO = "ic-sieca-v1-prueba";

  private final ObjectMapper json = new ObjectMapper();
  private final VersionFormularioRepository versiones = mock(VersionFormularioRepository.class);
  private final ConfiguracionIcElementoRepository configuraciones =
      mock(ConfiguracionIcElementoRepository.class);

  private final PreparadorEntradaIcService service =
      new PreparadorEntradaIcService(versiones, configuraciones);

  @Test
  void utilizaLaVersionPublicadaAunqueYaNoEsteActiva() {
    VersionFormulario version = version(EstadoVersionFormulario.PUBLICADA);
    when(versiones.findById(VERSION_ID)).thenReturn(Optional.of(version));
    when(configuraciones.findAllByVersionFormularioId(VERSION_ID))
        .thenReturn(List.of(peso(version, "/vigas", "2.5")));

    var entrada = service.preparar(VERSION_ID, json.createObjectNode());

    assertThat(entrada.versionFormularioId()).isEqualTo(VERSION_ID);
    assertThat(entrada.codigoFormulario()).isEqualTo("sieca-v1");
    assertThat(entrada.versionAlgoritmo()).isEqualTo(ALGORITMO);
    assertThat(entrada.pesos())
        .containsExactlyEntriesOf(java.util.Map.of("/vigas", new BigDecimal("2.5")));

    verify(configuraciones).findAllByVersionFormularioId(VERSION_ID);
  }

  @Test
  void conservaCopiasIndependientesDeLosDatosYLaConfiguracion() {
    VersionFormulario version = version(EstadoVersionFormulario.PUBLICADA);
    ObjectNode datos = json.createObjectNode().put("observacion", "original");

    when(versiones.findById(VERSION_ID)).thenReturn(Optional.of(version));
    when(configuraciones.findAllByVersionFormularioId(VERSION_ID))
        .thenReturn(List.of(peso(version, "/vigas", "1")));

    var entrada = service.preparar(VERSION_ID, datos);

    datos.put("observacion", "modificada");
    ((ObjectNode) version.getEsquema()).put("title", "modificado");
    ((ObjectNode) version.getReglasEvaluacionIc()).put("formulario", "otro");
    ((ObjectNode) version.getReglasEvaluacionIc().get("reglas_evaluacion_ic"))
        .put("version_algoritmo", "otro");

    assertThat(entrada.datos().get("observacion").asText()).isEqualTo("original");
    assertThat(entrada.esquema().has("title")).isFalse();
    assertThat(entrada.catalogoElementos().get("formulario").asText()).isEqualTo("sieca-v1");
    assertThat(entrada.reglasEvaluacion().get("version_algoritmo").asText()).isEqualTo(ALGORITMO);
    assertThrows(
        UnsupportedOperationException.class, () -> entrada.pesos().put("/otro", BigDecimal.ONE));
  }

  @Test
  void rechazaEntradasInvalidasAntesDeConsultarRepositorios() {
    assertThrows(ApiException.class, () -> service.preparar(null, json.createObjectNode()));
    assertThrows(ApiException.class, () -> service.preparar(VERSION_ID, null));
    assertThrows(ApiException.class, () -> service.preparar(VERSION_ID, json.createArrayNode()));

    verifyNoInteractions(versiones, configuraciones);
  }

  @Test
  void rechazaUnaVersionInexistente() {
    when(versiones.findById(VERSION_ID)).thenReturn(Optional.empty());

    assertThrows(ApiException.class, () -> service.preparar(VERSION_ID, json.createObjectNode()));

    verifyNoInteractions(configuraciones);
  }

  @Test
  void rechazaUnaVersionEnBorrador() {
    when(versiones.findById(VERSION_ID))
        .thenReturn(Optional.of(version(EstadoVersionFormulario.BORRADOR)));

    assertThrows(ApiException.class, () -> service.preparar(VERSION_ID, json.createObjectNode()));

    verifyNoInteractions(configuraciones);
  }

  @Test
  void rechazaUnAlgoritmoDistintoDelRegistradoEnLaVersion() {
    VersionFormulario version = version(EstadoVersionFormulario.PUBLICADA);
    version.setVersionAlgoritmoIc("otro-algoritmo");
    when(versiones.findById(VERSION_ID)).thenReturn(Optional.of(version));

    assertThrows(ApiException.class, () -> service.preparar(VERSION_ID, json.createObjectNode()));

    verifyNoInteractions(configuraciones);
  }

  @Test
  void rechazaUnaVersionSinPesos() {
    VersionFormulario version = version(EstadoVersionFormulario.PUBLICADA);
    when(versiones.findById(VERSION_ID)).thenReturn(Optional.of(version));
    when(configuraciones.findAllByVersionFormularioId(VERSION_ID)).thenReturn(List.of());

    assertThrows(ApiException.class, () -> service.preparar(VERSION_ID, json.createObjectNode()));
  }

  @Test
  void rechazaPesosDuplicados() {
    VersionFormulario version = version(EstadoVersionFormulario.PUBLICADA);
    when(versiones.findById(VERSION_ID)).thenReturn(Optional.of(version));
    when(configuraciones.findAllByVersionFormularioId(VERSION_ID))
        .thenReturn(List.of(peso(version, "/vigas", "1"), peso(version, "/vigas", "2")));

    assertThrows(ApiException.class, () -> service.preparar(VERSION_ID, json.createObjectNode()));
  }

  @Test
  void rechazaPesosNoPositivos() {
    VersionFormulario version = version(EstadoVersionFormulario.PUBLICADA);
    when(versiones.findById(VERSION_ID)).thenReturn(Optional.of(version));

    for (String valor : List.of("0", "-1")) {
      when(configuraciones.findAllByVersionFormularioId(VERSION_ID))
          .thenReturn(List.of(peso(version, "/vigas", valor)));

      assertThrows(ApiException.class, () -> service.preparar(VERSION_ID, json.createObjectNode()));
    }
  }

  private VersionFormulario version(EstadoVersionFormulario estado) {
    ObjectNode esquema = json.createObjectNode().put("type", "object");
    ObjectNode catalogo = json.createObjectNode().put("formulario", "sieca-v1");

    catalogo.putArray("elementos").addObject().put("elemento_ref", "/vigas");
    catalogo.putObject("reglas_evaluacion_ic").put("version_algoritmo", ALGORITMO);

    VersionFormulario version =
        new VersionFormulario(
            VERSION_ID,
            "SIECA-version-historica",
            estado,
            false,
            esquema,
            json.createObjectNode(),
            catalogo,
            null,
            USUARIO_ID,
            AHORA,
            AHORA);

    version.setVersionAlgoritmoIc(ALGORITMO);
    return version;
  }

  private ConfiguracionIcElemento peso(VersionFormulario version, String referencia, String valor) {
    return new ConfiguracionIcElemento(
        UUID.randomUUID(), version, referencia, new BigDecimal(valor), "Peso de prueba", AHORA);
  }
}
