package gt.usac.cunoc.sgp.formulario.validation;

import static gt.usac.cunoc.sgp.formulario.validation.AnclajeInvalidoException.Motivo.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ValidadorAnclajeFormularioTest {
  private static final UUID PILA = UUID.fromString("01900000-0000-7000-8000-000000000001");
  private static final UUID TRAMO = UUID.fromString("01900000-0000-7000-8000-000000000002");
  private static final UUID OTRO = UUID.fromString("01900000-0000-7000-8000-000000000003");
  private final ObjectMapper json = new ObjectMapper();
  private final ValidadorAnclajeFormulario validador = new ValidadorAnclajeFormulario();
  private JsonNode esquema;
  private ObjectNode datos;

  @BeforeEach
  void preparar() throws Exception {
    try (var recurso = getClass().getResourceAsStream("/formulario/sieca-v1/esquema.schema.json")) {
      esquema = json.readTree(recurso);
    }
    datos = json.createObjectNode();
    datos
        .putObject("subestructura")
        .putArray("pilas")
        .addObject()
        .put("instancia_id", PILA.toString());
    datos.putArray("tramos").addObject().put("instancia_id", TRAMO.toString());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/identificacion/fecha_inspeccion",
        "/subestructura",
        "/subestructura/estribo_entrada",
        "/subestructura/estribo_entrada/socavacion/aguas_arriba",
        "/otros"
      })
  void admiteCamposSeccionesEvaluablesYContenedoresAunqueNoTenganRespuesta(String ref) {
    assertThatCode(() -> validador.validar(esquema, datos, ref, null)).doesNotThrowAnyException();
  }

  @Test
  void laExistenciaDelAnclaNoExigeDatosFueraDeRepetibles() {
    assertThatCode(() -> validador.validar(esquema, null, "/subestructura", null))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/no_existe", "/subestructura/no_existe", "/SUBESTRUCTURA", "/ACCESO"})
  void rechazaRutasInexistentesOCategoriasInventadas(String ref) {
    exigir(ELEMENTO_INEXISTENTE, esquema, datos, ref, null);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "/",
        "subestructura/estribo_entrada",
        "subestructura.estribo_entrada",
        "//subestructura",
        "/subestructura/",
        "/subestructura//estribo_entrada",
        "/a~",
        "/a~2b",
        "/a~xb",
        "/a\n~x"
      })
  void rechazaRutasMalFormadas(String ref) {
    exigir(RUTA_INVALIDA, esquema, datos, ref, null);
  }

  @Test
  void rechazaReferenciaMayorDe300Caracteres() {
    exigir(RUTA_INVALIDA, esquema, datos, "/" + "a".repeat(300), null);
  }

  @Test
  void resuelveEscapesDePropiedadesYReferenciasLocalesSinDobleDecodificacion() throws Exception {
    var historico =
        json.readTree(
            """
        {"$defs":{"grupo/especial":{"properties":{"elemento~uno":{"type":"string"},
          "literal~1":{"type":"string"}}}},
         "properties":{"grupo/especial":{"$ref":"#/$defs/grupo~1especial"}}}
        """);
    validador.validar(historico, datos, "/grupo~1especial/elemento~0uno", null);
    validador.validar(historico, datos, "/grupo~1especial/literal~01", null);
    exigir(ELEMENTO_INEXISTENTE, historico, datos, "/grupo~1especial/literal~1", null);
  }

  @Test
  void utilizaExclusivamenteLaVersionHistoricaProporcionada() throws Exception {
    var anterior = json.readTree("{\"properties\":{\"anterior\":{\"type\":\"string\"}}}");
    var posterior = json.readTree("{\"properties\":{\"nuevo\":{\"type\":\"string\"}}}");
    datos.put("nuevo", "No basta con que exista en los datos");
    validador.validar(posterior, datos, "/nuevo", null);
    exigir(ELEMENTO_INEXISTENTE, anterior, datos, "/nuevo", null);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/subestructura/pilas", "/subestructura/pilas/socavacion/aguas_arriba"})
  void aceptaInstanciaDePilaYCamposDentroDeElla(String ref) {
    validador.validar(esquema, datos, ref, PILA);
  }

  @Test
  void aceptaInstanciaDeTramo() {
    validador.validar(esquema, datos, "/tramos", TRAMO);
    validador.validar(esquema, datos, "/tramos/longitud_m", TRAMO);
  }

  @Test
  void rechazaInstanciaInexistente() {
    exigir(INSTANCIA_NO_ENCONTRADA, esquema, datos, "/tramos", OTRO);
  }

  @Test
  void noBuscaElUuidEnOtroRepetible() {
    exigir(INSTANCIA_NO_ENCONTRADA, esquema, datos, "/subestructura/pilas", TRAMO);
    exigir(INSTANCIA_NO_ENCONTRADA, esquema, datos, "/tramos/longitud_m", PILA);
  }

  @Test
  void rechazaDuplicadosDelUuidSolicitado() {
    datos.withArray("tramos").addObject().put("instancia_id", TRAMO.toString());
    exigir(INSTANCIA_DUPLICADA, esquema, datos, "/tramos", TRAMO);
  }

  @Test
  void rechazaDuplicadosAunqueNoSeanElUuidSolicitado() {
    var tramos = datos.withArray("tramos");
    tramos.addObject().put("instancia_id", OTRO.toString());
    tramos.addObject().put("instancia_id", OTRO.toString());
    exigir(INSTANCIA_DUPLICADA, esquema, datos, "/tramos", TRAMO);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/tramos", "/tramos/longitud_m", "/subestructura/pilas"})
  void exigeInstanciaInclusoAlAnclarAlRepetible(String ref) {
    exigir(INSTANCIA_REQUERIDA, esquema, datos, ref, null);
  }

  @Test
  void rechazaInstanciaParaUnElementoNoRepetible() {
    exigir(INSTANCIA_NO_PERMITIDA, esquema, datos, "/subestructura/estribo_entrada", PILA);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/tramos/0/longitud_m", "/tramos/0", "/tramos[0]"})
  void noAdmiteIndicesComoIdentidad(String ref) {
    exigir(ELEMENTO_INEXISTENTE, esquema, datos, ref, TRAMO);
  }

  @Test
  void losArreglosEscalaresSonCamposSinInstancia() {
    String ref = "/caracteristicas_cauce/estado_cauce/condiciones";
    validador.validar(esquema, datos, ref, null);
    exigir(INSTANCIA_NO_PERMITIDA, esquema, datos, ref, PILA);
    exigir(ELEMENTO_INEXISTENTE, esquema, datos, ref + "/0", null);
  }

  @Test
  void admiteArregloEscalarDentroDeUnRepetibleConUnaSolaInstancia() throws Exception {
    var historico =
        json.readTree(
            """
        {"properties":{"tramos":{"type":"array","items":{"type":"object",
          "properties":{"opciones":{"type":"array","items":{"type":"string"}}}}}}}
        """);
    validador.validar(historico, datos, "/tramos/opciones", TRAMO);
  }

  @Test
  void rechazaRepetiblesAnidadosSinIntentarBuscarGlobalmente() throws Exception {
    var historico =
        json.readTree(
            """
        {"properties":{"tramos":{"type":"array","items":{"type":"object",
          "properties":{"hijos":{"type":"array","items":{"type":"object",
            "properties":{"campo":{"type":"string"}}}}}}}}}
        """);
    exigir(REPETIBLE_ANIDADO_NO_SOPORTADO, historico, datos, "/tramos/hijos", TRAMO);
    exigir(REPETIBLE_ANIDADO_NO_SOPORTADO, historico, datos, "/tramos/hijos/campo", TRAMO);
  }

  @Test
  void rechazaArreglosDeArreglos() throws Exception {
    var historico =
        json.readTree(
            """
        {"properties":{"lista":{"type":"array","items":{"type":"array",
          "items":{"type":"object"}}}}}
        """);
    exigir(REPETIBLE_ANIDADO_NO_SOPORTADO, historico, datos, "/lista", TRAMO);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"https://ejemplo.com/esquema", "#/$defs/ausente", "#/$defs/a~2", "#/ciclo"})
  void rechazaReferenciasExternasInexistentesMalEscapadasOCiclicas(String referencia)
      throws Exception {
    var historico =
        json.readTree(
            """
        {"ciclo":{"$ref":"#/ciclo"},"properties":{"campo":{}}}
        """);
    ((ObjectNode) historico.path("properties").path("campo")).put("$ref", referencia);
    exigir(REFERENCIA_ESQUEMA_INVALIDA, historico, datos, "/campo", null);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"tramos\":null}",
        "{\"tramos\":{}}",
        "{\"tramos\":[{}]}",
        "{\"tramos\":[{\"instancia_id\":\"no-uuid\"}]}"
      })
  void rechazaDatosRepetiblesInvalidos(String contenido) throws Exception {
    exigir(DATOS_REPETIBLE_INVALIDOS, esquema, json.readTree(contenido), "/tramos", TRAMO);
  }

  @Test
  void noModificaEsquemaNiDatosAlAceptarORechazar() {
    JsonNode esquemaAntes = esquema.deepCopy(), datosAntes = datos.deepCopy();
    validador.validar(esquema, datos, "/tramos/longitud_m", TRAMO);
    exigir(INSTANCIA_NO_ENCONTRADA, esquema, datos, "/tramos", OTRO);
    assertThat(esquema).isEqualTo(esquemaAntes);
    assertThat(datos).isEqualTo(datosAntes);
  }

  private void exigir(
      AnclajeInvalidoException.Motivo motivo,
      JsonNode historico,
      JsonNode contenido,
      String ref,
      UUID instancia) {
    assertThatThrownBy(() -> validador.validar(historico, contenido, ref, instancia))
        .isInstanceOfSatisfying(
            AnclajeInvalidoException.class,
            error -> assertThat(error.getMotivo()).isEqualTo(motivo));
  }
}
