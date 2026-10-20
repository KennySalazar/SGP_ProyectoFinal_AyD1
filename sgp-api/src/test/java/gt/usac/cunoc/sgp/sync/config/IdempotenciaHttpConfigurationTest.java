package gt.usac.cunoc.sgp.sync.config;

import static org.assertj.core.api.Assertions.*;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

class IdempotenciaHttpConfigurationTest {
  @Test
  void documentaContratoSinDuplicarEncabezadosNiCambiarLecturas() {
    var crear =
        new Operation()
            .responses(
                new ApiResponses().addApiResponse("201", new ApiResponse().description("Creada")));
    var consultar =
        new Operation()
            .responses(
                new ApiResponses().addApiResponse("200", new ApiResponse().description("Listado")));
    var api =
        new OpenAPI()
            .paths(
                new Paths()
                    .addPathItem(
                        "/api/v1/inspecciones", new PathItem().post(crear).get(consultar)));
    var personalizar = new IdempotenciaHttpConfiguration().contratoIdempotencia();
    personalizar.customise(api);
    personalizar.customise(api);
    assertThat(crear.getParameters()).hasSize(1);
    assertThat(crear.getParameters().getFirst().getName()).isEqualTo("Idempotency-Key");
    assertThat(crear.getResponses()).containsKeys("201", "409", "422");
    assertThat(crear.getResponses().get("409").getHeaders()).containsKey("Retry-After");
    assertThat(crear.getResponses().get("422").getContent())
        .containsKey("application/problem+json");
    assertThat(crear.getResponses().get("201").getHeaders()).containsKey("Idempotent-Replay");
    assertThat(consultar.getParameters()).isNull();
    assertThat(consultar.getResponses()).containsOnlyKeys("200");
  }
}
