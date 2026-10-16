package gt.usac.cunoc.sgp.inspeccion.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidacionEvidenciaResponseTest {

  @Test
  void conservaUnaCopiaInmutableDeLosFaltantes() {
    var faltante = new FaltanteEvidenciaResponse("CAUCE", 1, 0);
    var lista = new ArrayList<>(List.of(faltante));
    var response = new ValidacionEvidenciaResponse(false, 3, 60, lista);

    lista.clear();

    assertEquals(List.of(faltante), response.faltantes());
    assertThrows(UnsupportedOperationException.class, () -> response.faltantes().clear());
  }

  @Test
  void serializaElContratoDeRespuestaConCodigoDeCategoria() throws Exception {
    var response =
        new ValidacionEvidenciaResponse(
            false, 3, 60, List.of(new FaltanteEvidenciaResponse("CAUCE", 1, 0)));
    var json = new ObjectMapper();
    var esperado =
        json.readTree(
            """
            {
              "valida": false,
              "totalDisponibles": 3,
              "maximoPermitido": 60,
              "faltantes": [{"categoria": "CAUCE", "requeridas": 1, "disponibles": 0}]
            }
            """);

    assertEquals(esperado, json.readTree(json.writeValueAsString(response)));
    assertEquals(response, json.treeToValue(esperado, ValidacionEvidenciaResponse.class));
  }
}
