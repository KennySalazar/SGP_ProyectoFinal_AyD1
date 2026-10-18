package gt.usac.cunoc.sgp.sync.service;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class HuellaSolicitudTest {
  @Test
  void ordenDeCamposNoCambiaHuellaPeroDestinoChecksumYMetadatosSi() throws Exception {
    var json = new ObjectMapper();
    var a =
        json.readTree(
            "{\"destino\":\"uno\",\"checksum\":\"abc\",\"meta\":{\"categoria\":\"ACCESO\",\"lat\":14}}");
    var b =
        json.readTree(
            "{\"meta\":{\"lat\":14,\"categoria\":\"ACCESO\"},\"checksum\":\"abc\",\"destino\":\"uno\"}");
    assertThat(HuellaSolicitud.calcular(a)).isEqualTo(HuellaSolicitud.calcular(b));
    for (String campo : new String[] {"destino", "checksum", "meta"}) {
      var cambiado = a.deepCopy();
      ((com.fasterxml.jackson.databind.node.ObjectNode) cambiado).put(campo, "diferente");
      assertThat(HuellaSolicitud.calcular(cambiado)).isNotEqualTo(HuellaSolicitud.calcular(a));
    }
  }
}
