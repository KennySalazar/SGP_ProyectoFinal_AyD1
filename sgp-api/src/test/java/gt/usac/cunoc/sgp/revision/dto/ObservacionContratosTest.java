package gt.usac.cunoc.sgp.revision.dto;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.archivo.model.CategoriaFoto;
import gt.usac.cunoc.sgp.revision.model.TipoDestinoObservacion;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ObservacionContratosTest {
  private static ValidatorFactory factory;
  private static Validator validator;
  private static final UUID INSTANCIA = UUID.fromString("01900000-0000-7000-8000-000000000001");

  @BeforeAll
  static void preparar() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void cerrar() {
    factory.close();
  }

  private DestinoObservacion elemento() {
    return new DestinoObservacion(
        TipoDestinoObservacion.ELEMENTO, "/tramos/longitud_m", INSTANCIA, null);
  }

  @Test
  void elementoConservaReferenciaCanonicaEInstancia() throws Exception {
    var solicitud = new CrearObservacionRequest(elemento(), "Texto");
    var json = new ObjectMapper();
    var copia = json.readValue(json.writeValueAsString(solicitud), CrearObservacionRequest.class);
    assertThat(copia).isEqualTo(solicitud);
    assertThat(validator.validate(copia)).isEmpty();
    assertThat(json.readTree(json.writeValueAsString(copia)).path("destino").has("coherente"))
        .isFalse();
  }

  @ParameterizedTest
  @EnumSource(CategoriaFoto.class)
  void categoriaTieneDestinoPropioSinRutaNiInstancia(CategoriaFoto categoria) {
    var destino =
        new DestinoObservacion(TipoDestinoObservacion.CATEGORIA_FOTOGRAFICA, null, null, categoria);
    assertThat(validator.validate(new CrearObservacionRequest(destino, "Texto"))).isEmpty();
  }

  @Test
  void rechazaDestinosAusentesMezcladosOIncompletos() {
    List<DestinoObservacion> destinos =
        Arrays.asList(
            null,
            new DestinoObservacion(null, "/campo", null, null),
            new DestinoObservacion(TipoDestinoObservacion.ELEMENTO, null, null, null),
            new DestinoObservacion(TipoDestinoObservacion.ELEMENTO, " ", null, null),
            new DestinoObservacion(
                TipoDestinoObservacion.ELEMENTO, "/campo", null, CategoriaFoto.ACCESO),
            new DestinoObservacion(TipoDestinoObservacion.CATEGORIA_FOTOGRAFICA, null, null, null),
            new DestinoObservacion(
                TipoDestinoObservacion.CATEGORIA_FOTOGRAFICA,
                "/ACCESO",
                null,
                CategoriaFoto.ACCESO),
            new DestinoObservacion(
                TipoDestinoObservacion.CATEGORIA_FOTOGRAFICA,
                null,
                INSTANCIA,
                CategoriaFoto.ACCESO),
            new DestinoObservacion(
                TipoDestinoObservacion.ELEMENTO, "/" + "x".repeat(300), null, null));
    destinos.forEach(
        destino ->
            assertThat(validator.validate(new CrearObservacionRequest(destino, "Texto")))
                .isNotEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\n\t"})
  void textoObligatorioEnCreacionYEdicion(String texto) {
    assertThat(validator.validate(new CrearObservacionRequest(elemento(), texto))).isNotEmpty();
    assertThat(validator.validate(new EditarObservacionRequest(texto))).isNotEmpty();
  }

  @Test
  void limite2000EnTodosLosContratosDeEscritura() {
    for (int cantidad : new int[] {2000, 2001}) {
      String texto = "x".repeat(cantidad);
      for (Object request :
          List.of(
              new CrearObservacionRequest(elemento(), texto),
              new EditarObservacionRequest(texto),
              new ObservacionGeneralRequest(texto))) {
        assertThat(validator.validate(request).isEmpty()).isEqualTo(cantidad == 2000);
      }
    }
  }

  @Test
  void generalEsSeparadaOpcionalYSinEstadoNiAncla() {
    assertThat(validator.validate(new ObservacionGeneralRequest(null))).isEmpty();
    assertThat(validator.validate(new ObservacionGeneralRequest("General del ciclo"))).isEmpty();
    assertThat(validator.validate(new ObservacionGeneralRequest("  "))).isNotEmpty();
    assertThat(
            Arrays.stream(ObservacionGeneralResponse.class.getRecordComponents())
                .map(c -> c.getName()))
        .containsExactly("revisionId", "texto");
  }

  @Test
  void clienteNoControlaIdentidadesEstadoNiFechas() {
    assertThat(
            Arrays.stream(CrearObservacionRequest.class.getRecordComponents())
                .map(c -> c.getName()))
        .containsExactly("destino", "texto");
    assertThat(
            Arrays.stream(EditarObservacionRequest.class.getRecordComponents())
                .map(c -> c.getName()))
        .containsExactly("texto");
  }

  @Test
  void categoriaInexistenteYGeneralNoSonDestinosPuntuales() {
    var json = new ObjectMapper();
    assertThatThrownBy(() -> json.readValue("{\"tipo\":\"GENERAL\"}", DestinoObservacion.class))
        .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    assertThatThrownBy(
            () ->
                json.readValue(
                    "{\"tipo\":\"CATEGORIA_FOTOGRAFICA\",\"categoriaFotografica\":\"NO_EXISTE\"}",
                    DestinoObservacion.class))
        .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
  }
}
