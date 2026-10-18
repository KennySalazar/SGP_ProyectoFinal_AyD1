package gt.usac.cunoc.sgp.inspeccion.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.dto.FaltanteEvidenciaResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class ReglasEvidenciaFotograficaTest {

  // Propuesta académica suministrada explícitamente; no es configuración de producción.
  private static final Map<String, Integer> MINIMOS_PROPUESTOS =
      Map.of("ACCESO", 1, "SUPERESTRUCTURA", 1, "SUBESTRUCTURA", 1, "CAUCE", 1);

  private final ReglasEvidenciaFotografica reglas = new ReglasEvidenciaFotografica();

  @Test
  void sinFotografiasInformaTodasLasCategoriasFaltantes() {
    var resultado = reglas.evaluar(Map.of(), MINIMOS_PROPUESTOS);

    assertFalse(resultado.valida());
    assertEquals(0, resultado.totalDisponibles());
    assertEquals(60, resultado.maximoPermitido());
    assertEquals(
        List.of(
            new FaltanteEvidenciaResponse("ACCESO", 1, 0),
            new FaltanteEvidenciaResponse("CAUCE", 1, 0),
            new FaltanteEvidenciaResponse("SUBESTRUCTURA", 1, 0),
            new FaltanteEvidenciaResponse("SUPERESTRUCTURA", 1, 0)),
        resultado.faltantes());
  }

  @Test
  void unaFotoEnCadaCategoriaCumpleSinExigirOtra() {
    var resultado = reglas.evaluar(evidenciaCompleta(), MINIMOS_PROPUESTOS);

    assertTrue(resultado.valida());
    assertEquals(4, resultado.totalDisponibles());
    assertTrue(resultado.faltantes().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ACCESO", "SUPERESTRUCTURA", "SUBESTRUCTURA", "CAUCE"})
  void otraNoSustituyeUnaCategoriaObligatoria(String categoria) {
    var conteos = new HashMap<>(evidenciaCompleta());
    conteos.remove(categoria);
    conteos.put("OTRA", 20L);

    var resultado = reglas.evaluar(conteos, MINIMOS_PROPUESTOS);

    assertFalse(resultado.valida());
    assertEquals(23, resultado.totalDisponibles());
    assertEquals(List.of(new FaltanteEvidenciaResponse(categoria, 1, 0)), resultado.faltantes());
  }

  @Test
  void conteoCeroEnCategoriaObligatoriaInformaFaltante() {
    var conteos = new HashMap<>(evidenciaCompleta());
    conteos.put("CAUCE", 0L);

    var resultado = reglas.evaluar(conteos, MINIMOS_PROPUESTOS);

    assertFalse(resultado.valida());
    assertEquals(3, resultado.totalDisponibles());
    assertEquals(List.of(new FaltanteEvidenciaResponse("CAUCE", 1, 0)), resultado.faltantes());
  }

  @Test
  void conservaLosMinimosParametrizadosAnteUnaDecisionAcademicaDistinta() {
    var minimos = new HashMap<>(MINIMOS_PROPUESTOS);
    minimos.put("ACCESO", 2);

    var resultado = reglas.evaluar(evidenciaCompleta(), minimos);

    assertFalse(resultado.valida());
    assertEquals(List.of(new FaltanteEvidenciaResponse("ACCESO", 2, 1)), resultado.faltantes());
  }

  @ParameterizedTest
  @CsvSource({"59,true", "60,true", "61,false"})
  void evaluaElMaximoIncluyendoOtra(long total, boolean valida) {
    var conteos = new HashMap<>(evidenciaCompleta());
    conteos.put("OTRA", total - 4);

    var resultado = reglas.evaluar(conteos, MINIMOS_PROPUESTOS);

    assertEquals(valida, resultado.valida());
    assertEquals(total, resultado.totalDisponibles());
    assertTrue(resultado.faltantes().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 4, 59})
  void permiteUnaNuevaFotoMientrasHayaCapacidad(long disponibles) {
    assertDoesNotThrow(() -> reglas.validarCapacidadParaNuevaFotografia(disponibles));
  }

  @ParameterizedTest
  @ValueSource(longs = {60, 61, Long.MAX_VALUE})
  void rechazaUnaNuevaFotoAlAlcanzarElLimite(long disponibles) {
    var exception =
        assertThrows(
            ApiException.class, () -> reglas.validarCapacidadParaNuevaFotografia(disponibles));

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatus());
    assertEquals("limite_fotografias_excedido", exception.getCode());
  }

  @Test
  void politicaAusenteNoSeInterpretaComoEvidenciaValida() {
    assertThrows(NullPointerException.class, () -> reglas.evaluar(evidenciaCompleta(), null));
    assertThrows(
        IllegalArgumentException.class, () -> reglas.evaluar(evidenciaCompleta(), Map.of()));
  }

  @Test
  void conteoAusenteNoSeInterpretaComoInventarioVacio() {
    assertThrows(NullPointerException.class, () -> reglas.evaluar(null, MINIMOS_PROPUESTOS));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void rechazaMinimosNoPositivos(int minimo) {
    assertThrows(
        IllegalArgumentException.class,
        () -> reglas.evaluar(evidenciaCompleta(), Map.of("ACCESO", minimo)));
  }

  @Test
  void rechazaConteosNegativos() {
    assertThrows(
        IllegalArgumentException.class,
        () -> reglas.evaluar(Map.of("ACCESO", -1L), MINIMOS_PROPUESTOS));
    assertThrows(
        IllegalArgumentException.class, () -> reglas.validarCapacidadParaNuevaFotografia(-1));
  }

  @Test
  void rechazaConteosYMinimosNulos() {
    var conteos = new HashMap<String, Long>();
    conteos.put("ACCESO", null);
    var minimos = new HashMap<String, Integer>();
    minimos.put("ACCESO", null);

    assertThrows(IllegalArgumentException.class, () -> reglas.evaluar(conteos, MINIMOS_PROPUESTOS));
    assertThrows(
        IllegalArgumentException.class, () -> reglas.evaluar(evidenciaCompleta(), minimos));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void rechazaCodigosVaciosEnConteosYMinimos(String categoria) {
    assertThrows(
        IllegalArgumentException.class,
        () -> reglas.evaluar(Map.of(categoria, 1L), MINIMOS_PROPUESTOS));
    assertThrows(
        IllegalArgumentException.class,
        () -> reglas.evaluar(evidenciaCompleta(), Map.of(categoria, 1)));
  }

  @Test
  void rechazaCodigosNulosEnConteosYMinimos() {
    var conteos = new HashMap<String, Long>();
    conteos.put(null, 1L);
    var minimos = new HashMap<String, Integer>();
    minimos.put(null, 1);

    assertThrows(IllegalArgumentException.class, () -> reglas.evaluar(conteos, MINIMOS_PROPUESTOS));
    assertThrows(
        IllegalArgumentException.class, () -> reglas.evaluar(evidenciaCompleta(), minimos));
  }

  @Test
  void evitaQueUnDesbordamientoDelTotalAutoriceEvidencia() {
    assertThrows(
        IllegalArgumentException.class,
        () -> reglas.evaluar(Map.of("ACCESO", Long.MAX_VALUE, "OTRA", 1L), MINIMOS_PROPUESTOS));
  }

  private Map<String, Long> evidenciaCompleta() {
    return Map.of("ACCESO", 1L, "SUPERESTRUCTURA", 1L, "SUBESTRUCTURA", 1L, "CAUCE", 1L);
  }
}
