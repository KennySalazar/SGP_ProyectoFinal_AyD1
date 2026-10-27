package gt.usac.cunoc.sgp.revision.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.inspeccion.dto.IndiceCondicionPreliminarResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.revision.entity.Revision;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RevisionDetalleServiceTest {
  private final RevisionRepository revisiones = mock(RevisionRepository.class);
  private final VersionFormularioRepository versiones = mock(VersionFormularioRepository.class);
  private final RevisionIndicePreliminarService calculo =
      mock(RevisionIndicePreliminarService.class);
  private final RevisionDetalleService servicio =
      new RevisionDetalleService(revisiones, versiones, calculo);
  private final UUID ciclo = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID versionId = UUID.randomUUID();
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  @BeforeEach
  void preparar() {
    var inspeccion = mock(Inspeccion.class);
    UUID id = UUID.randomUUID();
    when(inspeccion.getId()).thenReturn(id);
    UUID autorId = UUID.randomUUID();
    when(inspeccion.getAutorId()).thenReturn(autorId);
    var revision = mock(Revision.class);
    when(revision.getInspeccion()).thenReturn(inspeccion);
    when(revision.getRevisorId()).thenReturn(actor);
    when(revision.getContenidoEnviado())
        .thenReturn(
            json.createObjectNode()
                .put("inspeccionId", id.toString())
                .put("autorId", autorId.toString())
                .put("puenteId", UUID.randomUUID().toString())
                .put("versionFormularioId", versionId.toString())
                .put("fechaInspeccion", "2026-10-21")
                .set("datos", json.createObjectNode()));
    ((com.fasterxml.jackson.databind.node.ObjectNode) revision.getContenidoEnviado())
        .putNull("identificacionCapturada");
    when(revisiones.findById(ciclo)).thenReturn(Optional.of(revision));
    var version = mock(VersionFormulario.class);
    when(version.getEsquema()).thenReturn(json.createObjectNode().put("type", "object"));
    when(versiones.findById(versionId)).thenReturn(Optional.of(version));
  }

  @Test
  void falloDeConfiguracionMantieneDetalleSinExponerError() {
    when(calculo.calcular(any(), any()))
        .thenThrow(
            new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "configuracion_ic_incompleta",
                "Error",
                "detalle tecnico"));
    var respuesta = servicio.obtener(ciclo, actor);
    assertThat(respuesta.icPreliminar()).isNull();
    assertThat(respuesta.esquemaFormulario().path("type").asText()).isEqualTo("object");
    assertThat(json.valueToTree(respuesta).toString())
        .doesNotContain("detalle tecnico", "configuracion_ic_incompleta");
  }

  @Test
  void falloInesperadoSoloOcultaElIndice() {
    when(calculo.calcular(any(), any())).thenThrow(new IllegalStateException("datos privados"));
    assertThat(servicio.obtener(ciclo, actor).icPreliminar()).isNull();
  }

  @Test
  void calculoCorrectoConservaEtiquetaPreliminar() {
    var indice = new IndiceCondicionPreliminarResponse(new BigDecimal("75"), "BUENO", "PRELIMINAR");
    when(calculo.calcular(any(), any())).thenReturn(indice);
    assertThat(servicio.obtener(ciclo, actor).icPreliminar()).isEqualTo(indice);
  }

  @Test
  void ajenoNoEjecutaCalculo() {
    assertThatThrownBy(() -> servicio.obtener(ciclo, UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    verifyNoInteractions(calculo);
  }

  @Test
  void sinSesionConserva401() {
    assertThatThrownBy(() -> servicio.obtener(ciclo, null))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    verifyNoInteractions(calculo);
  }
}
