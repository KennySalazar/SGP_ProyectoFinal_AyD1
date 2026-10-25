package gt.usac.cunoc.sgp.puente.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.entity.AprobacionCalibracion;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.AprobacionCalibracionRepository;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EstadoCalibracionPuenteServiceTest {
  private final PuenteRepository puentes = mock(PuenteRepository.class);
  private final AprobacionCalibracionRepository aprobaciones =
      mock(AprobacionCalibracionRepository.class);
  private final EstadoCalibracionPuenteService servicio =
      new EstadoCalibracionPuenteService(puentes, aprobaciones);
  private final UUID puenteId = UUID.randomUUID();
  private final UUID versionId = UUID.randomUUID();

  private void prepararPuente() {
    var puente = mock(Puente.class);
    when(puentes.findByIdAndActivoTrue(puenteId)).thenReturn(Optional.of(puente));
    when(puente.getVersionFormularioIdIcVigente()).thenReturn(versionId);
  }

  @Test
  void consultaLaAprobacionDeLaVersionDelIcVigenteSinExponerObservaciones() {
    prepararPuente();
    var version = mock(VersionFormulario.class);
    when(version.getId()).thenReturn(versionId);
    var aprobacion =
        new AprobacionCalibracion(
            UUID.randomUUID(),
            version,
            UUID.randomUUID(),
            "Kenny Salazar",
            Instant.parse("2026-10-23T06:50:00Z"),
            "Observacion privada");
    when(aprobaciones.findByVersionFormularioId(versionId)).thenReturn(Optional.of(aprobacion));
    var respuesta = servicio.consultar(puenteId);
    assertThat(respuesta.versionFormularioId()).isEqualTo(versionId);
    assertThat(respuesta.estado()).isEqualTo("APROBADA");
    assertThat(respuesta.aprobadoPorNombre()).isEqualTo("Kenny Salazar");
    assertThat(respuesta.rotuloCalibracion()).isEqualTo("Aprobada por Kenny Salazar el 23/10/2026");
    assertThat(
            new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules()
                .valueToTree(respuesta)
                .has("observaciones"))
        .isFalse();
    verify(aprobaciones).findByVersionFormularioId(versionId);
  }

  @Test
  void muestraProvisionalSiLaVersionDelResultadoNoTieneAprobacion() {
    prepararPuente();
    var respuesta = servicio.consultar(puenteId);
    assertThat(respuesta.versionFormularioId()).isEqualTo(versionId);
    assertThat(respuesta.estado()).isEqualTo("SIN_APROBAR");
    assertThat(respuesta.rotuloCalibracion()).isEqualTo("Calibracion provisional · sin aprobar");
    assertThat(respuesta.aprobadoPorId()).isNull();
  }

  @Test
  void sinIcNoInventaUnaVersionNiConsultaAprobaciones() {
    when(puentes.findByIdAndActivoTrue(puenteId)).thenReturn(Optional.of(mock(Puente.class)));
    assertThat(servicio.consultar(puenteId)).isNull();
    verifyNoInteractions(aprobaciones);
  }

  @Test
  void puenteInactivoOInexistenteResponde404SinRevelarRespaldo() {
    assertThatThrownBy(() -> servicio.consultar(puenteId))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("puente_no_encontrado");
    verifyNoInteractions(aprobaciones);
  }
}
