package gt.usac.cunoc.sgp.revision.entity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import gt.usac.cunoc.sgp.archivo.model.CategoriaFoto;
import gt.usac.cunoc.sgp.revision.model.EstadoObservacion;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ObservacionRevisionTest {
  private final Revision revision = mock(Revision.class);
  private final UUID id = UUID.randomUUID();
  private final UUID creador = UUID.randomUUID();
  private final Instant fecha = Instant.parse("2026-10-18T12:00:00Z");

  @Test
  void nacePendienteConIdentidadCicloCreadorYFechaDelServidor() {
    UUID instancia = UUID.fromString("01900000-0000-7000-8000-000000000001");
    var observacion =
        ObservacionRevision.paraElemento(
            id, revision, "/tramos", instancia, "Texto", creador, fecha);
    assertThat(observacion.getId()).isEqualTo(id);
    assertThat(observacion.getRevision()).isSameAs(revision);
    assertThat(observacion.getInstanciaElementoId()).isEqualTo(instancia);
    assertThat(observacion.getEstado()).isEqualTo(EstadoObservacion.PENDIENTE);
    assertThat(observacion.getCreadoPorId()).isEqualTo(creador);
    assertThat(observacion.getCreadoEn()).isEqualTo(fecha);
    assertThat(observacion.getResueltoPorId()).isNull();
    assertThat(observacion.getResueltoEn()).isNull();
    assertThat(observacion.getRespuestaAutor()).isNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\n"})
  void rechazaTextoVacioTambienEnLaConstruccionInterna(String texto) {
    assertThatThrownBy(
            () ->
                ObservacionRevision.paraElemento(
                    id, revision, "/campo", null, texto, creador, fecha))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void noPermiteMasDe2000Caracteres() {
    assertThatCode(
            () ->
                ObservacionRevision.paraCategoriaFotografica(
                    id, revision, CategoriaFoto.ACCESO, "x".repeat(2000), creador, fecha))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                ObservacionRevision.paraCategoriaFotografica(
                    id, revision, CategoriaFoto.ACCESO, "x".repeat(2001), creador, fecha))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void noPermiteCrearUnaGeneralComoObservacionPuntual() {
    assertThatThrownBy(
            () ->
                ObservacionRevision.paraElemento(
                    id, revision, null, null, "General", creador, fecha))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                ObservacionRevision.paraCategoriaFotografica(
                    id, revision, null, "General", creador, fecha))
        .isInstanceOf(NullPointerException.class);
  }
}
