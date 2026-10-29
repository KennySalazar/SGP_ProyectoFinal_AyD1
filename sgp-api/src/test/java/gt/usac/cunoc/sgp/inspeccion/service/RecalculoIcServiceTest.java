package gt.usac.cunoc.sgp.inspeccion.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.entity.ResultadoIc;
import gt.usac.cunoc.sgp.inspeccion.model.*;
import gt.usac.cunoc.sgp.inspeccion.repository.*;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class RecalculoIcServiceTest {
  private final InspeccionRepository inspecciones = mock(InspeccionRepository.class);
  private final ResultadoIcRepository resultados = mock(ResultadoIcRepository.class);
  private final VersionFormularioRepository versiones = mock(VersionFormularioRepository.class);
  private final UserAccountRepository usuarios = mock(UserAccountRepository.class);
  private final PreparadorEntradaIcService preparador = mock(PreparadorEntradaIcService.class);
  private final CalculoIndiceCondicionService calculador =
      mock(CalculoIndiceCondicionService.class);
  private final AuditoriaWriter auditoria = mock(AuditoriaWriter.class);
  private final ObjectMapper json = new ObjectMapper();
  private static final Instant AHORA = Instant.parse("2026-10-24T20:00:00Z");
  private final RecalculoIcService servicio =
      new RecalculoIcService(
          inspecciones,
          resultados,
          versiones,
          usuarios,
          preparador,
          calculador,
          auditoria,
          json,
          Clock.fixed(AHORA, ZoneOffset.UTC));
  private final UUID id = UUID.randomUUID(),
      versionId = UUID.randomUUID(),
      actor = UUID.randomUUID();
  private final Inspeccion inspeccion = mock(Inspeccion.class);
  private final VersionFormulario vigente = mock(VersionFormulario.class);
  private ResultadoIc original;
  private ResultadoCalculoIc calculo;

  @BeforeEach
  void preparar() {
    original = resultado(false, UUID.randomUUID());
    when(inspecciones.findByIdAndEliminadoEnIsNullForUpdate(id))
        .thenReturn(Optional.of(inspeccion));
    when(inspecciones.findByIdAndEliminadoEnIsNull(id)).thenReturn(Optional.of(inspeccion));
    when(inspeccion.getEstado()).thenReturn("PUBLICADA");
    when(inspeccion.getPublicadoEn()).thenReturn(AHORA.minusSeconds(300));
    when(inspeccion.getDatos()).thenReturn(json.createObjectNode().put("dato", "guardado"));
    when(resultados.findPublicacionByInspeccionId(id)).thenReturn(Optional.of(original));
    when(versiones.findByActivaTrue()).thenReturn(Optional.of(vigente));
    when(vigente.getId()).thenReturn(versionId);
    when(vigente.getEstado()).thenReturn(EstadoVersionFormulario.PUBLICADA);
    when(vigente.getCodigo()).thenReturn("v2");
    when(versiones.findById(versionId)).thenReturn(Optional.of(vigente));
    var usuario = mock(UserAccount.class);
    when(usuario.getNombreCompleto()).thenReturn("Kenny Salazar");
    when(usuarios.findById(actor)).thenReturn(Optional.of(usuario));
    calculo =
        new ResultadoCalculoIc(
            versionId,
            "sieca-v1",
            "alg-v1",
            "a".repeat(64),
            BigDecimal.valueOf(25),
            EstadoCondicion.MALO,
            EstadoCondicion.MALO,
            BigDecimal.ONE,
            BigDecimal.ONE,
            List.of(),
            List.of());
    when(calculador.calcular(any())).thenReturn(calculo);
  }

  @Test
  void guardaRecalculoConActorFechaYConfiguracionSinModificarElOriginal() {
    var respuesta = servicio.recalcular(id, actor);
    assertThat(respuesta.tipo()).isEqualTo("RECALCULO");
    assertThat(respuesta.versionConfiguracionId()).isEqualTo(versionId);
    assertThat(respuesta.solicitadoPorId()).isEqualTo(actor);
    assertThat(respuesta.solicitadoPorNombre()).isEqualTo("Kenny Salazar");
    assertThat(respuesta.calculadoEn()).isEqualTo(AHORA);
    assertThat(respuesta.rotulo()).isEqualTo("Recálculo del IC · 24/10/2026 14:00");
    verify(preparador).preparar(versionId, inspeccion.getDatos());
    verify(resultados)
        .saveAndFlush(
            argThat(
                r ->
                    "RECALCULO".equals(r.getTipo())
                        && r.getEstadoConfirmado() == r.getEstadoCalculado()));
    verify(auditoria)
        .registrarEnTransaccion(
            eq(actor),
            any(),
            eq("resultado_ic"),
            eq(respuesta.id()),
            isNull(),
            argThat(
                n ->
                    n.get("inspeccionId").asText().equals(id.toString())
                        && n.get("versionConfiguracionId").asText().equals(versionId.toString())),
            isNull());
    verify(inspecciones, never()).save(any());
    assertThat(original.getTipo()).isEqualTo("PUBLICACION");
  }

  @Test
  void repetidoDevuelveElExistenteSinSobrescribirSolicitanteNiFechaNiAuditoria() {
    ResultadoIc anterior = resultado(true, UUID.randomUUID());
    when(resultados.findRecalculo(id, versionId, calculo.hashEntrada(), calculo.versionAlgoritmo()))
        .thenReturn(Optional.of(anterior));
    var respuesta = servicio.recalcular(id, actor);
    assertThat(respuesta.id()).isEqualTo(anterior.getId());
    assertThat(respuesta.solicitadoPorId()).isEqualTo(anterior.getSolicitadoPorId());
    assertThat(respuesta.calculadoEn()).isEqualTo(anterior.getCalculadoEn());
    verify(resultados, never()).saveAndFlush(any());
    verifyNoInteractions(auditoria);
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORRADOR", "ENVIADA", "EN_REVISION", "RECHAZADA"})
  void rechazaNoPublicadasAntesDeCalcular(String estado) {
    when(inspeccion.getEstado()).thenReturn(estado);
    assertThatThrownBy(() -> servicio.recalcular(id, actor))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    verifyNoInteractions(preparador, calculador, auditoria);
    verify(resultados, never()).saveAndFlush(any());
  }

  @Test
  void rechazaPublicadaSinFecha() {
    when(inspeccion.getPublicadoEn()).thenReturn(null);
    assertThatThrownBy(() -> servicio.recalcular(id, actor)).isInstanceOf(ApiException.class);
    verifyNoInteractions(calculador, auditoria);
  }

  @Test
  void rechazaInspeccionInexistente() {
    when(inspecciones.findByIdAndEliminadoEnIsNullForUpdate(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> servicio.recalcular(id, actor))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void rechazaConfiguracionNoVigente() {
    when(versiones.findByActivaTrue()).thenReturn(Optional.empty());
    assertThatThrownBy(() -> servicio.recalcular(id, actor)).isInstanceOf(ApiException.class);
    verifyNoInteractions(preparador, calculador, auditoria);
  }

  @Test
  void rechazaConfiguracionBorrador() {
    when(vigente.getEstado()).thenReturn(EstadoVersionFormulario.BORRADOR);
    assertThatThrownBy(() -> servicio.recalcular(id, actor)).isInstanceOf(ApiException.class);
    verifyNoInteractions(preparador, calculador, auditoria);
  }

  @Test
  void errorDeCalculoNoGuardaNada() {
    when(calculador.calcular(any()))
        .thenThrow(
            new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "configuracion_ic_incompleta",
                "IC",
                "Faltan pesos"));
    assertThatThrownBy(() -> servicio.recalcular(id, actor)).isInstanceOf(ApiException.class);
    verify(resultados, never()).saveAndFlush(any());
    verifyNoInteractions(auditoria);
  }

  @Test
  void noCreaUnRecalculoSinResultadoOriginal() {
    when(resultados.findPublicacionByInspeccionId(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> servicio.recalcular(id, actor)).isInstanceOf(ApiException.class);
    verifyNoInteractions(preparador, calculador, auditoria);
  }

  private ResultadoIc resultado(boolean recalculo, UUID usuario) {
    var dato = json.createObjectNode();
    if (recalculo)
      return ResultadoIc.recalculo(
          UUID.randomUUID(),
          id,
          versionId,
          BigDecimal.valueOf(50),
          EstadoCondicion.REGULAR,
          EstadoCondicion.REGULAR,
          json.createArrayNode(),
          dato,
          "a".repeat(64),
          "alg-v1",
          usuario,
          AHORA.minusSeconds(120));
    return new ResultadoIc(
        UUID.randomUUID(),
        id,
        versionId,
        BigDecimal.valueOf(50),
        EstadoCondicion.REGULAR,
        EstadoCondicion.REGULAR,
        json.createArrayNode(),
        dato,
        "b".repeat(64),
        "alg-v1",
        usuario,
        AHORA.minusSeconds(300));
  }
}
