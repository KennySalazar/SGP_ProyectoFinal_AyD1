package gt.usac.cunoc.sgp.inspeccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.archivo.repository.ArchivoInspeccionRepository;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.entity.MapeoCampoFormulario;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.model.TipoMapeoCampo;
import gt.usac.cunoc.sgp.formulario.repository.MapeoCampoFormularioRepository;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import gt.usac.cunoc.sgp.inspeccion.dto.ExportacionInspeccionResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.RelacionVersionActivaResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.mapper.InspeccionMapper;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.repository.ResultadoIcRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoRepository;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspeccionServiceHu072Test {

  @Mock private InspeccionRepository inspeccionRepository;
  @Mock private VersionFormularioRepository versionFormularioRepository;
  @Mock private PuenteRepository puenteRepository;
  @Mock private gt.usac.cunoc.sgp.puente.repository.AsignacionPuenteRepository asignaciones;
  @Mock private MapeoCampoFormularioRepository mapeoCampoFormularioRepository;
  @Mock private ArchivoInspeccionRepository archivoInspeccionRepository;
  @Mock private OrdenMantenimientoRepository ordenMantenimientoRepository;
  @Mock private ResultadoIcRepository resultadoIcRepository;

  private JsonSchemaValidatorService jsonSchemaValidatorService;
  private InspeccionMapper inspeccionMapper;
  private ObjectMapper objectMapper;
  private InspeccionService service;

  private Puente puente;
  private VersionFormulario versionV1;
  private VersionFormulario versionV2Activa;
  private Inspeccion inspeccionPublicada;
  private UUID inspeccionId;
  private UUID v1Id;
  private UUID v2Id;

  @BeforeEach
  void setUp() {
    jsonSchemaValidatorService = new JsonSchemaValidatorService();
    inspeccionMapper = Mappers.getMapper(InspeccionMapper.class);
    objectMapper = new ObjectMapper();

    service =
        new InspeccionService(
            inspeccionRepository,
            versionFormularioRepository,
            puenteRepository,
            jsonSchemaValidatorService,
            inspeccionMapper,
            objectMapper,
            asignaciones);

    service.setMapeoCampoFormularioRepository(mapeoCampoFormularioRepository);
    service.setArchivoInspeccionRepository(archivoInspeccionRepository);
    service.setOrdenMantenimientoRepository(ordenMantenimientoRepository);

    puente = mock(Puente.class);
    when(puente.getId()).thenReturn(UUID.randomUUID());
    when(puente.getCodigo()).thenReturn("P-100");
    when(puente.getNombre()).thenReturn("Puente Los Esclavos");

    v1Id = UUID.randomUUID();
    ObjectNode esquemaV1 = objectMapper.createObjectNode();
    esquemaV1.put("type", "object");
    versionV1 = mock(VersionFormulario.class);
    when(versionV1.getId()).thenReturn(v1Id);
    when(versionV1.getCodigo()).thenReturn("SIECA-v1");
    when(versionV1.getEsquema()).thenReturn(esquemaV1);

    v2Id = UUID.randomUUID();
    ObjectNode esquemaV2 = objectMapper.createObjectNode();
    esquemaV2.put("type", "object");
    versionV2Activa = mock(VersionFormulario.class);
    when(versionV2Activa.getId()).thenReturn(v2Id);
    when(versionV2Activa.getCodigo()).thenReturn("SIECA-v2");

    inspeccionId = UUID.randomUUID();
    inspeccionPublicada = mock(Inspeccion.class);
    when(inspeccionPublicada.getId()).thenReturn(inspeccionId);
    when(inspeccionPublicada.getPuente()).thenReturn(puente);
    when(inspeccionPublicada.getVersionFormulario()).thenReturn(versionV1);
    when(inspeccionPublicada.getFechaInspeccion()).thenReturn(LocalDate.of(2026, 10, 10));
    when(inspeccionPublicada.getEstado()).thenReturn("PUBLICADA");
    when(inspeccionPublicada.getDatos()).thenReturn(objectMapper.createObjectNode());
  }

  @Test
  @DisplayName("determinarRelacionVersionActiva retorna null si versionInspeccion es null")
  void determinarRelacion_versionNull_retornaNull() {
    RelacionVersionActivaResponse relacion = service.determinarRelacionVersionActiva(null);
    assertThat(relacion).isNull();
  }

  @Test
  @DisplayName(
      "determinarRelacionVersionActiva cuando la versión es la activa retorna esVersionActiva true")
  void determinarRelacion_mismaVersionActiva_retornaEsVersionActivaTrue() {
    when(versionFormularioRepository.findByActivaTrue()).thenReturn(Optional.of(versionV1));

    RelacionVersionActivaResponse relacion = service.determinarRelacionVersionActiva(versionV1);

    assertThat(relacion).isNotNull();
    assertThat(relacion.esVersionActiva()).isTrue();
    assertThat(relacion.codigoVersionInspeccion()).isEqualTo("SIECA-v1");
    assertThat(relacion.codigoVersionActiva()).isEqualTo("SIECA-v1");
    assertThat(relacion.mapeosDeclarados()).isFalse();
    assertThat(relacion.totalEquivalentes()).isZero();
    assertThat(relacion.totalNuevos()).isZero();
    assertThat(relacion.totalEliminados()).isZero();
  }

  @Test
  @DisplayName("determinarRelacionVersionActiva cuando no es la activa y tiene mapeos declarados")
  void determinarRelacion_versionHistoricaConMapeos_retornaMapeosYTotales() {
    when(versionFormularioRepository.findByActivaTrue()).thenReturn(Optional.of(versionV2Activa));

    MapeoCampoFormulario mapeoEquiv = mock(MapeoCampoFormulario.class);
    when(mapeoEquiv.getId()).thenReturn(UUID.randomUUID());
    when(mapeoEquiv.getVersionOrigen()).thenReturn(versionV1);
    when(mapeoEquiv.getVersionDestino()).thenReturn(versionV2Activa);
    when(mapeoEquiv.getElementoRefOrigen()).thenReturn("subestructura.estribo");
    when(mapeoEquiv.getElementoRefDestino()).thenReturn("subestructura.estribo_v2");
    when(mapeoEquiv.getTipo()).thenReturn(TipoMapeoCampo.EQUIVALENTE);
    when(mapeoEquiv.getObservacion()).thenReturn("Renombrado");

    MapeoCampoFormulario mapeoNuevo = mock(MapeoCampoFormulario.class);
    when(mapeoNuevo.getId()).thenReturn(UUID.randomUUID());
    when(mapeoNuevo.getVersionOrigen()).thenReturn(versionV1);
    when(mapeoNuevo.getVersionDestino()).thenReturn(versionV2Activa);
    when(mapeoNuevo.getElementoRefOrigen()).thenReturn(null);
    when(mapeoNuevo.getElementoRefDestino()).thenReturn("superestructura.apoyos_sismicos");
    when(mapeoNuevo.getTipo()).thenReturn(TipoMapeoCampo.NUEVO);
    when(mapeoNuevo.getObservacion()).thenReturn("Campo nuevo en v2");

    when(mapeoCampoFormularioRepository.findAllByVersionOrigenIdAndVersionDestinoId(v1Id, v2Id))
        .thenReturn(List.of(mapeoEquiv, mapeoNuevo));

    RelacionVersionActivaResponse relacion = service.determinarRelacionVersionActiva(versionV1);

    assertThat(relacion).isNotNull();
    assertThat(relacion.esVersionActiva()).isFalse();
    assertThat(relacion.codigoVersionInspeccion()).isEqualTo("SIECA-v1");
    assertThat(relacion.codigoVersionActiva()).isEqualTo("SIECA-v2");
    assertThat(relacion.versionActivaId()).isEqualTo(v2Id);
    assertThat(relacion.mapeosDeclarados()).isTrue();
    assertThat(relacion.totalEquivalentes()).isEqualTo(1);
    assertThat(relacion.totalNuevos()).isEqualTo(1);
    assertThat(relacion.totalEliminados()).isZero();
    assertThat(relacion.mapeos()).hasSize(2);
  }

  @Test
  @DisplayName("exportarInspeccion lanza 404 si la inspección no existe")
  void exportarInspeccion_inexistente_lanza404() {
    UUID id = UUID.randomUUID();
    when(inspeccionRepository.findPublicadaConDetallesById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.exportarInspeccion(id))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("exportarInspeccion retorna exportación sanitizada completa")
  void exportarInspeccion_publicada_retornaEstructuraSanitizada() {
    when(inspeccionRepository.findPublicadaConDetallesById(inspeccionId))
        .thenReturn(Optional.of(inspeccionPublicada));
    when(versionFormularioRepository.findByActivaTrue()).thenReturn(Optional.of(versionV2Activa));

    ExportacionInspeccionResponse exportacion = service.exportarInspeccion(inspeccionId);

    assertThat(exportacion).isNotNull();
    assertThat(exportacion.id()).isEqualTo(inspeccionId);
    assertThat(exportacion.estado()).isEqualTo("PUBLICADA");
    assertThat(exportacion.fechaInspeccion()).isEqualTo(LocalDate.of(2026, 10, 10));
    assertThat(exportacion.puente()).isNotNull();
    assertThat(exportacion.puente().codigo()).isEqualTo("P-100");
    assertThat(exportacion.puente().nombre()).isEqualTo("Puente Los Esclavos");
    assertThat(exportacion.versionFormulario()).isNotNull();
    assertThat(exportacion.versionFormulario().codigo()).isEqualTo("SIECA-v1");
    assertThat(exportacion.fotografias()).isEmpty();
    assertThat(exportacion.propuestasMantenimiento()).isEmpty();
  }
}
