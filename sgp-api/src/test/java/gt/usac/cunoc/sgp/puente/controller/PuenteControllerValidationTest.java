package gt.usac.cunoc.sgp.puente.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.usac.cunoc.sgp.puente.exception.PuenteExceptionHandler;
import gt.usac.cunoc.sgp.puente.service.PuenteService;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PuenteControllerValidationTest {

  private PuenteService puenteService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    puenteService = mock(PuenteService.class);

    mockMvc =
        MockMvcBuilders.standaloneSetup(new PuenteController(puenteService))
            .setControllerAdvice(new PuenteExceptionHandler())
            .build();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("solicitudesInvalidas")
  void rechazaDatosInvalidosConProblemDetail(String escenario, String solicitud, String campo)
      throws Exception {

    mockMvc
        .perform(post("/api/v1/puentes").contentType(MediaType.APPLICATION_JSON).content(solicitud))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.type").isNotEmpty())
        .andExpect(jsonPath("$.title").isNotEmpty())
        .andExpect(jsonPath("$.detail").isNotEmpty())
        .andExpect(jsonPath("$.instance").value("/api/v1/puentes"))
        .andExpect(jsonPath("$..campo", hasItem(campo)));

    verifyNoInteractions(puenteService);
  }

  private static Stream<Arguments> solicitudesInvalidas() {
    String solicitud =
        """
                {
                  "nombre": "Puente de prueba",
                  "departamentoId": "019a0000-0000-7000-8000-000000000001",
                  "municipioId": "019a0000-0000-7000-8000-000000000002",
                  "ruta": "CA-1",
                  "latitud": 14.8,
                  "longitud": -91.5,
                  "confirmarCercania": false
                }
                """;

    return Stream.of(
        Arguments.of("Ruta faltante", solicitud.replace("\"ruta\": \"CA-1\",", ""), "ruta"),
        Arguments.of(
            "Ruta vacia", solicitud.replace("\"ruta\": \"CA-1\"", "\"ruta\": \"   \""), "ruta"),
        Arguments.of(
            "Latitud fuera de rango",
            solicitud.replace("\"latitud\": 14.8", "\"latitud\": 91.0"),
            "latitud"),
        Arguments.of(
            "Municipio faltante",
            solicitud.replace("\"municipioId\": \"019a0000-0000-7000-8000-000000000002\",", ""),
            "municipioId"));
  }
}
