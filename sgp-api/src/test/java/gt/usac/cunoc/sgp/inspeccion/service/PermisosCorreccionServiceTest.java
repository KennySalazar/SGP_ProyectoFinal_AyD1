package gt.usac.cunoc.sgp.inspeccion.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.dto.GuardarBorradorRequest;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.revision.entity.ObservacionRevision;
import gt.usac.cunoc.sgp.revision.entity.Revision;
import gt.usac.cunoc.sgp.revision.model.EstadoObservacion;
import gt.usac.cunoc.sgp.revision.repository.ObservacionRevisionRepository;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PermisosCorreccionServiceTest {
  final ObjectMapper json = new ObjectMapper();
  final RevisionRepository revisiones = mock(RevisionRepository.class);
  final ObservacionRevisionRepository observaciones = mock(ObservacionRevisionRepository.class);
  final Inspeccion inspeccion = mock(Inspeccion.class);
  final Revision ciclo = mock(Revision.class);
  final UUID id = UUID.randomUUID();
  final PermisosCorreccionService servicio =
      new PermisosCorreccionService(revisiones, observaciones);

  @BeforeEach
  void preparar() throws Exception {
    when(inspeccion.getId()).thenReturn(id);
    when(inspeccion.getRolAutorCreacion()).thenReturn("ESTUDIANTE");
    when(inspeccion.getDatos())
        .thenReturn(
            json.readTree(
                "{\"otros\":{\"recomendacion\":\"Antes\",\"descripcion\":\"Conservar\"}}"));
    when(revisiones.findFirstByInspeccionIdOrderByNumeroCicloDesc(id))
        .thenReturn(Optional.of(ciclo));
    when(ciclo.getId()).thenReturn(id);
    when(ciclo.getEstado()).thenReturn("RESUELTA");
    when(ciclo.getVeredicto()).thenReturn("CAMBIOS_SOLICITADOS");
    var obs = mock(ObservacionRevision.class);
    when(obs.getElementoRef()).thenReturn("/otros/recomendacion");
    when(observaciones.findByRevisionIdOrderByCreadoEnAscIdAsc(id)).thenReturn(List.of(obs));
  }

  @Test
  void permiteCambiarSoloElCampoObservado() throws Exception {
    servicio.validar(
        inspeccion,
        new GuardarBorradorRequest(
            1L,
            json.readTree(
                "{\"otros\":{\"recomendacion\":\"Corregida\",\"descripcion\":\"Conservar\"}}"),
            null,
            null,
            null));
  }

  @Test
  void rechazaCambiosEnCamposNoObservados() throws Exception {
    var datos =
        json.readTree("{\"otros\":{\"recomendacion\":\"Corregida\",\"descripcion\":\"Alterada\"}}");
    assertThatThrownBy(
            () ->
                servicio.validar(
                    inspeccion, new GuardarBorradorRequest(1L, datos, null, null, null)))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void bloqueaRetirarCamposNoObservados() throws Exception {
    var datos = json.readTree("{\"otros\":{\"recomendacion\":\"Corregida\"}}");
    assertThatThrownBy(
            () ->
                servicio.validar(
                    inspeccion, new GuardarBorradorRequest(1L, datos, null, null, null)))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void permiteLaSeccionCompletaCuandoFueObservada() throws Exception {
    var obs = mock(ObservacionRevision.class);
    when(obs.getElementoRef()).thenReturn("/otros");
    when(observaciones.findByRevisionIdOrderByCreadoEnAscIdAsc(id)).thenReturn(List.of(obs));
    servicio.validar(
        inspeccion,
        new GuardarBorradorRequest(
            1L, json.readTree("{\"otros\":{\"descripcion\":\"Nueva\"}}"), null, null, null));
  }

  @Test
  void unBorradorSinRevisionesSigueEditable() throws Exception {
    when(revisiones.findFirstByInspeccionIdOrderByNumeroCicloDesc(id)).thenReturn(Optional.empty());
    assertThat(servicio.consultar(inspeccion).restringida()).isFalse();
    servicio.validar(
        inspeccion, new GuardarBorradorRequest(1L, json.readTree("{}"), null, null, null));
  }

  @Test
  void unaObservacionResueltaConservaSuPermisoHastaReenviar() {
    var obs = mock(ObservacionRevision.class);
    when(obs.getElementoRef()).thenReturn("/otros/recomendacion");
    when(obs.getEstado()).thenReturn(EstadoObservacion.RESUELTA);
    when(observaciones.findByRevisionIdOrderByCreadoEnAscIdAsc(id)).thenReturn(List.of(obs));
    assertThat(servicio.consultar(inspeccion).campos()).containsExactly("/otros/recomendacion");
  }

  @Test
  void lasFotografiasNoObservadasQuedanBloqueadas() {
    assertThatThrownBy(() -> servicio.validarFoto(inspeccion, "CAUCE"))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void elPermisoDelTramoNoSeExtiendeAOtraInstancia() throws Exception {
    UUID primero = UUID.randomUUID(), segundo = UUID.randomUUID();
    when(inspeccion.getDatos())
        .thenReturn(
            json.readTree(
                "{\"tramos\":[{\"instancia_id\":\""
                    + primero
                    + "\",\"longitud\":1},{\"instancia_id\":\""
                    + segundo
                    + "\",\"longitud\":2}]}"));
    var obs = mock(ObservacionRevision.class);
    when(obs.getElementoRef()).thenReturn("/tramos/longitud");
    when(obs.getInstanciaElementoId()).thenReturn(segundo);
    when(observaciones.findByRevisionIdOrderByCreadoEnAscIdAsc(id)).thenReturn(List.of(obs));
    assertThat(servicio.consultar(inspeccion).campos()).containsExactly("/tramos/1/longitud");
    var datos =
        json.readTree(
            "{\"tramos\":[{\"instancia_id\":\""
                + primero
                + "\",\"longitud\":3},{\"instancia_id\":\""
                + segundo
                + "\",\"longitud\":2}]}");
    assertThatThrownBy(
            () ->
                servicio.validar(
                    inspeccion, new GuardarBorradorRequest(1L, datos, null, null, null)))
        .isInstanceOf(ApiException.class);
  }
}
