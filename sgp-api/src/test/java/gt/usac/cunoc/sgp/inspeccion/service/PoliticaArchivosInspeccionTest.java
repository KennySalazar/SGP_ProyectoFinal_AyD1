package gt.usac.cunoc.sgp.inspeccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.model.ContextoArchivosInspeccion;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PoliticaArchivosInspeccionTest {
  private final UUID autor = UUID.randomUUID();
  private final UUID ajeno = UUID.randomUUID();
  private final PoliticaArchivosInspeccion politica =
      new PoliticaArchivosInspeccion(new ReglasEvidenciaFotografica());

  @Test
  void autorPuedeCargarHastaSesentaYRetirarEnBorrador() {
    var contexto = contexto("BORRADOR");
    assertThatCode(() -> politica.validarCarga(contexto, autor, 59)).doesNotThrowAnyException();
    assertThatCode(() -> politica.validarRetirada(contexto, autor)).doesNotThrowAnyException();
    rechaza(() -> politica.validarCarga(contexto, autor, 60), 422, "limite_fotografias_excedido");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"ENVIADA", "EN_REVISION", "CAMBIOS_SOLICITADOS", "PUBLICADA", "RECHAZADA"})
  void autorLeePeroNoMutaFueraDeBorrador(String estado) {
    var contexto = contexto(estado);
    assertThatCode(() -> politica.validarLectura(contexto, autor, false))
        .doesNotThrowAnyException();
    rechaza(
        () -> politica.validarCarga(contexto, autor, 0),
        409,
        "estado_inspeccion_no_permite_archivos");
    rechaza(
        () -> politica.validarRetirada(contexto, autor),
        409,
        "estado_inspeccion_no_permite_archivos");
  }

  @Test
  void catedraticoAsignadoSoloLeeBorrador() {
    assertThatCode(() -> politica.validarLectura(contexto("BORRADOR"), ajeno, true))
        .doesNotThrowAnyException();
    rechaza(
        () -> politica.validarCarga(contexto("BORRADOR"), ajeno, 0),
        404,
        "inspeccion_no_encontrada");
    rechaza(
        () -> politica.validarRetirada(contexto("BORRADOR"), ajeno),
        404,
        "inspeccion_no_encontrada");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"ENVIADA", "EN_REVISION", "CAMBIOS_SOLICITADOS", "PUBLICADA", "RECHAZADA"})
  void asignacionNoAmpliaLecturaAEstadosNoAcordados(String estado) {
    rechaza(
        () -> politica.validarLectura(contexto(estado), ajeno, true),
        404,
        "inspeccion_no_encontrada");
  }

  @Test
  void actorAjenoNoConoceEstadoNiCapacidad() {
    var publicada = contexto("PUBLICADA");
    rechaza(
        () -> politica.validarLectura(publicada, ajeno, false), 404, "inspeccion_no_encontrada");
    rechaza(() -> politica.validarCarga(publicada, ajeno, 60), 404, "inspeccion_no_encontrada");
    rechaza(() -> politica.validarRetirada(publicada, ajeno), 404, "inspeccion_no_encontrada");
  }

  @Test
  void sesionAusenteProduce401AntesDeConsultarContexto() {
    rechaza(() -> politica.validarLectura(null, null, true), 401, "autenticacion_requerida");
    rechaza(() -> politica.validarCarga(null, null, 60), 401, "autenticacion_requerida");
    rechaza(() -> politica.validarRetirada(null, null), 401, "autenticacion_requerida");
  }

  @Test
  void contextoAusenteOEliminadoNoConcedeAccesoAlAutorNiAlAsignado() {
    var eliminado =
        new ContextoArchivosInspeccion(UUID.randomUUID(), autor, "BORRADOR", Instant.now());
    rechaza(() -> politica.validarLectura(null, autor, false), 404, "inspeccion_no_encontrada");
    rechaza(
        () -> politica.validarLectura(eliminado, autor, false), 404, "inspeccion_no_encontrada");
    rechaza(() -> politica.validarLectura(eliminado, ajeno, true), 404, "inspeccion_no_encontrada");
    rechaza(() -> politica.validarCarga(eliminado, autor, 0), 404, "inspeccion_no_encontrada");
    rechaza(() -> politica.validarRetirada(eliminado, autor), 404, "inspeccion_no_encontrada");
  }

  private ContextoArchivosInspeccion contexto(String estado) {
    return new ContextoArchivosInspeccion(UUID.randomUUID(), autor, estado, null);
  }

  private void rechaza(Runnable accion, int status, String code) {
    assertThatThrownBy(accion::run)
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              assertThat(exception.getStatus().value()).isEqualTo(status);
              assertThat(exception.getCode()).isEqualTo(code);
            });
  }
}
