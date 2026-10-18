package gt.usac.cunoc.sgp.inspeccion.validation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class ValidadorAnclajeFotograficoTest {
  private static final UUID ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
  private final ObjectMapper json = new ObjectMapper();
  private final ValidadorAnclajeFotografico validador = new ValidadorAnclajeFotografico();
  private final Inspeccion inspeccion = mock(Inspeccion.class);
  private final VersionFormulario version = mock(VersionFormulario.class);
  private ObjectNode datos;
  private JsonNode esquema;

  @BeforeEach
  void preparar() throws Exception {
    esquema =
        json.readTree(
            """
        {"$defs":{"campo":{"type":"string"}},"properties":{
          "grupo":{"type":"object","properties":{"campo":{"$ref":"#/$defs/campo"}}},
          "pilas":{"type":"array","items":{"type":"object","properties":{
            "campo":{"type":"string"},"hijos":{"type":"array","items":{"type":"object"}}}}},
          "opciones":{"type":"array","items":{"type":"string"}},
          "a/b":{"properties":{"c~d":{"type":"string"}}},
          "":{"properties":{"campo":{"type":"string"}}}}}
        """);
    datos = json.createObjectNode();
    datos.putArray("pilas").addObject().put("instancia_id", ID.toString());
    when(inspeccion.getVersionFormulario()).thenReturn(version);
    when(version.getEsquema()).thenReturn(esquema);
    when(inspeccion.getDatos()).thenReturn(datos);
  }

  @Test
  void fotoSinAnclajeSigueSiendoValidaSinConsultarLaInspeccion() {
    validador.validar(null, null, null);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/grupo", "/grupo/campo", "/a~1b/c~0d", "//campo"})
  void conservaCamposContenedoresEscapesYSintaxisAnterior(String ref) {
    validador.validar(inspeccion, ref, null);
  }

  @Test
  void conservaAnclajeAlRepetibleYSusCampos() {
    validador.validar(inspeccion, "/pilas", ID);
    validador.validar(inspeccion, "/pilas/campo", ID);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"/", "grupo.campo", "grupo/campo", "/no_existe", "/a~2b", "/a~"})
  void conservaErrorPublicoCuandoElAnclajeEsInvalido(String ref) {
    exigirError(ref, ID);
  }

  @Test
  void conservaInstanciaObligatoriaYLaProhibicionFueraDeRepetibles() {
    exigirError("/pilas", null);
    exigirError("/grupo", ID);
    exigirError("/pilas", UUID.randomUUID());
  }

  @Test
  void sigueRechazandoDuplicadosDeLaInstanciaSolicitada() {
    datos.withArray("pilas").addObject().put("instancia_id", ID.toString());
    exigirError("/pilas", ID);
  }

  @Test
  void noEndureceLaReglaAnteriorParaOtrosIdentificadoresDuplicados() {
    var otro = UUID.randomUUID();
    var pilas = datos.withArray("pilas");
    pilas.addObject().put("instancia_id", otro.toString());
    pilas.addObject().put("instancia_id", otro.toString());
    validador.validar(inspeccion, "/pilas", ID);
  }

  @Test
  void conservaRechazoDeRepetiblesAnidados() {
    exigirError("/pilas/hijos", ID);
  }

  @Test
  void noCambiaSilenciosamenteLaReglaHistoricaDeArreglosEscalares() {
    datos.putArray("opciones").add("A");
    exigirError("/opciones", null);
    exigirError("/opciones", ID);
  }

  @Test
  void conservaValidacionContraLaVersionDeLaInspeccion() throws Exception {
    when(version.getEsquema()).thenReturn(json.readTree("{\"properties\":{}}"));
    exigirError("/grupo/campo", null);
  }

  @Test
  void rechazaRefLocalInvalidaConElMismoErrorHttp() {
    ((ObjectNode) esquema.path("properties").path("grupo")).put("$ref", "#/$defs/ausente");
    exigirError("/grupo", null);
  }

  private void exigirError(String ref, UUID instancia) {
    assertThatThrownBy(() -> validador.validar(inspeccion, ref, instancia))
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> {
              assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(error.getCode()).isEqualTo("anclaje_fotografico_invalido");
              assertThat(error.getTitle()).isEqualTo("Referencia inválida");
              assertThat(error.getMessage())
                  .isEqualTo(
                      "El elemento o la instancia no pertenecen a la versión y datos de esta inspección.");
            });
  }
}
