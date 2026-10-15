package gt.usac.cunoc.sgp.formulario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.formulario.entity.ConfiguracionIcElemento;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.ConfiguracionIcElementoRepository;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VersionFormularioAuditoriaSnapshotProviderTest {

  private final VersionFormularioRepository versiones = mock(VersionFormularioRepository.class);

  private final ConfiguracionIcElementoRepository configuracionesIc =
      mock(ConfiguracionIcElementoRepository.class);

  private final VersionFormularioAuditoriaSnapshotProvider provider =
      new VersionFormularioAuditoriaSnapshotProvider(versiones, configuracionesIc);

  @Test
  void capturaPesosOrdenadosEscalaYDatosDeCalibracion() throws Exception {
    UUID id = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();
    Instant fecha = Instant.parse("2026-10-14T07:00:00Z");

    VersionFormulario version = mock(VersionFormulario.class);
    when(version.getId()).thenReturn(id);
    when(version.getCalibradoPorId()).thenReturn(usuarioId);
    when(version.getCalibradoEn()).thenReturn(fecha);

    ObjectNode reglas =
        (ObjectNode)
            new ObjectMapper()
                .readTree(
                    """
                                        {
                                          "reglas_evaluacion_ic": {
                                            "escala_severidad": {"SEVERO": 4}
                                          }
                                        }
                                        """);

    when(version.getReglasEvaluacionIc()).thenReturn(reglas);
    when(versiones.findById(id)).thenReturn(Optional.of(version));

    ConfiguracionIcElemento vigas =
        new ConfiguracionIcElemento(
            UUID.randomUUID(), version, "/vigas", BigDecimal.ONE, "Vigas", fecha);

    ConfiguracionIcElemento apoyos =
        new ConfiguracionIcElemento(
            UUID.randomUUID(), version, "/apoyos", BigDecimal.valueOf(2), "Apoyos", fecha);

    when(configuracionesIc.findAllByVersionFormularioId(id)).thenReturn(List.of(vigas, apoyos));

    var snapshot =
        (VersionFormularioAuditoriaSnapshotProvider.VersionFormularioSnapshot) provider.cargar(id);

    assertThat(snapshot.id()).isEqualTo(id);
    assertThat(snapshot.calibradoPorId()).isEqualTo(usuarioId);
    assertThat(snapshot.calibradoEn()).isEqualTo(fecha);
    assertThat(snapshot.pesos())
        .extracting(VersionFormularioAuditoriaSnapshotProvider.PesoSnapshot::elementoRef)
        .containsExactly("/apoyos", "/vigas");

    assertThat(snapshot.pesos().get(0).peso()).isEqualByComparingTo("2");
    assertThat(snapshot.pesos().get(0).descripcion()).isEqualTo("Apoyos");

    ((ObjectNode) reglas.at("/reglas_evaluacion_ic/escala_severidad")).put("SEVERO", 1);

    assertThat(
            snapshot
                .reglasEvaluacionIc()
                .at("/reglas_evaluacion_ic/escala_severidad/SEVERO")
                .asInt())
        .isEqualTo(4);
  }

  @Test
  void identificadorNuloNoConsultaRepositorios() {
    assertThat(provider.cargar(null)).isNull();

    verifyNoInteractions(versiones, configuracionesIc);
  }

  @Test
  void versionInexistenteNoConsultaPesos() {
    UUID id = UUID.randomUUID();
    when(versiones.findById(id)).thenReturn(Optional.empty());

    assertThat(provider.cargar(id)).isNull();

    verifyNoInteractions(configuracionesIc);
  }
}
