package gt.usac.cunoc.sgp.inspeccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.formulario.validation.JsonSchemaValidatorService;
import gt.usac.cunoc.sgp.inspeccion.dto.GuardarRespuestasRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.IniciarInspeccionRequest;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionResumenResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.mapper.InspeccionMapper;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InspeccionServiceTest {

  @Mock private InspeccionRepository inspeccionRepository;
  @Mock private VersionFormularioRepository versionFormularioRepository;
  @Mock private PuenteRepository puenteRepository;

  private JsonSchemaValidatorService jsonSchemaValidatorService;
  private InspeccionMapper inspeccionMapper;
  private ObjectMapper objectMapper;
  private InspeccionService service;

  private Puente puenteActivo;
  private VersionFormulario version1Publicada;
  private VersionFormulario version2Activa;
  private VersionFormulario versionBorrador;

  private static final String ESQUEMA_V1 =
      """
      {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "type": "object",
        "additionalProperties": false,
        "required": ["identificacion", "subestructura"],
        "properties": {
          "identificacion": {
            "type": "object",
            "additionalProperties": false,
            "required": ["nombre_puente"],
            "properties": {
              "nombre_puente": { "type": "string" }
            }
          },
          "subestructura": {
            "type": "object",
            "additionalProperties": false,
            "required": ["estribo_entrada"],
            "properties": {
              "estribo_entrada": {
                "type": "object",
                "additionalProperties": false,
                "required": ["material"],
                "properties": {
                  "material": {
                    "type": "string",
                    "enum": ["CONCRETO", "MAMPOSTERIA"]
                  }
                }
              }
            }
          }
        }
      }
      """;

  private static final String ESQUEMA_V2 =
      """
      {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "type": "object",
        "additionalProperties": false,
        "required": ["identificacion", "subestructura", "superestructura_v2"],
        "properties": {
          "identificacion": {
            "type": "object",
            "additionalProperties": false,
            "required": ["nombre_puente", "codigo_nuevo"],
            "properties": {
              "nombre_puente": { "type": "string" },
              "codigo_nuevo": { "type": "string" }
            }
          },
          "subestructura": {
            "type": "object",
            "additionalProperties": false,
            "required": ["estribo_entrada"],
            "properties": {
              "estribo_entrada": {
                "type": "object",
                "additionalProperties": false,
                "required": ["material"],
                "properties": {
                  "material": {
                    "type": "string",
                    "enum": ["CONCRETO", "ACERO_MODERNO"]
                  }
                }
              }
            }
          },
          "superestructura_v2": {
            "type": "object",
            "additionalProperties": false,
            "required": ["vigas"],
            "properties": {
              "vigas": { "type": "string" }
            }
          }
        }
      }
      """;

  @BeforeEach
  void setUp() throws Exception {
    jsonSchemaValidatorService = new JsonSchemaValidatorService();
    inspeccionMapper = Mappers.getMapper(InspeccionMapper.class);
    objectMapper = new ObjectMapper();

    service =
        new InspeccionService(
            inspeccionRepository,
            versionFormularioRepository,
            puenteRepository,
            jsonSchemaValidatorService,
            inspeccionMapper,
            objectMapper);

    UUID puenteId = UUID.randomUUID();
    puenteActivo =
        new Puente(
            puenteId,
            "P-001",
            1,
            null,
            "Puente El Carmen",
            "CA-1",
            java.math.BigDecimal.TEN,
            null,
            UUID.randomUUID(),
            java.time.Instant.now());

    UUID v1Id = UUID.randomUUID();
    version1Publicada =
        new VersionFormulario(
            v1Id,
            "SIECA-2026-v1",
            EstadoVersionFormulario.PUBLICADA,
            false,
            objectMapper.readTree(ESQUEMA_V1),
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            null,
            UUID.randomUUID(),
            java.time.Instant.now(),
            java.time.Instant.now());

    UUID v2Id = UUID.randomUUID();
    version2Activa =
        new VersionFormulario(
            v2Id,
            "SIECA-2026-v2",
            EstadoVersionFormulario.PUBLICADA,
            true,
            objectMapper.readTree(ESQUEMA_V2),
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            null,
            UUID.randomUUID(),
            java.time.Instant.now(),
            java.time.Instant.now());

    UUID vDraftId = UUID.randomUUID();
    versionBorrador =
        new VersionFormulario(
            vDraftId,
            "SIECA-2026-v3-draft",
            EstadoVersionFormulario.BORRADOR,
            false,
            objectMapper.readTree(ESQUEMA_V1),
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            null,
            UUID.randomUUID(),
            java.time.Instant.now(),
            java.time.Instant.now());
  }

  @Test
  void iniciarInspeccion_versionBorrador_rechazada() {
    when(puenteRepository.findById(puenteActivo.getId())).thenReturn(Optional.of(puenteActivo));
    when(versionFormularioRepository.findById(versionBorrador.getId()))
        .thenReturn(Optional.of(versionBorrador));

    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            puenteActivo.getId(),
            versionBorrador.getId(),
            LocalDate.now(),
            "web",
            "Inspector 1",
            null);

    assertThatThrownBy(() -> service.iniciarInspeccion(request, UUID.randomUUID(), "INSPECTOR"))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getCode()).isEqualTo("version_no_utilizable");
            });

    verify(inspeccionRepository, never()).save(any());
  }

  @Test
  void iniciarInspeccion_versionInexistente_rechazada() {
    UUID idInexistente = UUID.randomUUID();
    when(puenteRepository.findById(puenteActivo.getId())).thenReturn(Optional.of(puenteActivo));
    when(versionFormularioRepository.findById(idInexistente)).thenReturn(Optional.empty());

    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            puenteActivo.getId(), idInexistente, LocalDate.now(), "web", "Inspector 1", null);

    assertThatThrownBy(() -> service.iniciarInspeccion(request, UUID.randomUUID(), "INSPECTOR"))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getCode()).isEqualTo("version_no_utilizable");
            });

    verify(inspeccionRepository, never()).save(any());
  }

  @Test
  void iniciarInspeccion_versionPublicada_creaInspeccionEnBorrador() {
    when(puenteRepository.findById(puenteActivo.getId())).thenReturn(Optional.of(puenteActivo));
    when(versionFormularioRepository.findById(version1Publicada.getId()))
        .thenReturn(Optional.of(version1Publicada));
    when(inspeccionRepository.save(any(Inspeccion.class))).thenAnswer(i -> i.getArgument(0));

    IniciarInspeccionRequest request =
        new IniciarInspeccionRequest(
            puenteActivo.getId(),
            version1Publicada.getId(),
            LocalDate.now(),
            "web",
            "Ing. Pérez",
            null);

    InspeccionResumenResponse response =
        service.iniciarInspeccion(request, UUID.randomUUID(), "INSPECTOR");

    assertThat(response).isNotNull();
    assertThat(response.estado()).isEqualTo("BORRADOR");
    assertThat(response.versionFormularioCodigo()).isEqualTo("SIECA-2026-v1");
  }

  @Test
  void guardarRespuestas_seValidaContraVersionDeInspeccionNoContraActiva() throws Exception {
    // Inspección creada con versión 1
    Inspeccion inspeccionV1 =
        new Inspeccion(
            puenteActivo,
            version1Publicada,
            UUID.randomUUID(),
            "INSPECTOR",
            "Ing. Pérez",
            LocalDate.now(),
            "web",
            objectMapper.createObjectNode());

    UUID inspeccionId = inspeccionV1.getId();
    when(inspeccionRepository.findActiveById(inspeccionId)).thenReturn(Optional.of(inspeccionV1));
    when(inspeccionRepository.save(any(Inspeccion.class))).thenAnswer(i -> i.getArgument(0));

    // Datos válidos según versión 1 ("MAMPOSTERIA" es válido en v1 pero no existe en v2)
    // En v2 se exigiría además "codigo_nuevo" y "superestructura_v2"
    JsonNode datosValidosV1 =
        objectMapper.readTree(
            """
            {
              "identificacion": {
                "nombre_puente": "Puente Histórico"
              },
              "subestructura": {
                "estribo_entrada": {
                  "material": "MAMPOSTERIA"
                }
              }
            }
            """);

    InspeccionResumenResponse response =
        service.guardarRespuestas(
            inspeccionId, new GuardarRespuestasRequest(datosValidosV1), UUID.randomUUID());

    assertThat(response).isNotNull();
    assertThat(inspeccionV1.getDatos()).isEqualTo(datosValidosV1);
    verify(inspeccionRepository).save(inspeccionV1);
  }

  @Test
  void guardarRespuestas_borradorAceptaDatosIncompletos() throws Exception {
    Inspeccion inspeccion =
        new Inspeccion(
            puenteActivo,
            version1Publicada,
            UUID.randomUUID(),
            "INSPECTOR",
            "Ing. Pérez",
            LocalDate.now(),
            "web",
            objectMapper.createObjectNode());

    UUID inspeccionId = inspeccion.getId();
    when(inspeccionRepository.findActiveById(inspeccionId)).thenReturn(Optional.of(inspeccion));
    when(inspeccionRepository.save(any(Inspeccion.class))).thenAnswer(i -> i.getArgument(0));

    // Solo se manda identificacion.nombre_puente, omitiendo subestructura por completo
    JsonNode datosIncompletos =
        objectMapper.readTree(
            """
            {
              "identificacion": {
                "nombre_puente": "Puente Incompleto"
              }
            }
            """);

    InspeccionResumenResponse response =
        service.guardarRespuestas(
            inspeccionId, new GuardarRespuestasRequest(datosIncompletos), UUID.randomUUID());

    assertThat(response).isNotNull();
    assertThat(inspeccion.getDatos().path("identificacion").path("nombre_puente").asText())
        .isEqualTo("Puente Incompleto");
  }

  @Test
  void guardarRespuestas_rechazaCamposDesconocidos() throws Exception {
    Inspeccion inspeccion =
        new Inspeccion(
            puenteActivo,
            version1Publicada,
            UUID.randomUUID(),
            "INSPECTOR",
            "Ing. Pérez",
            LocalDate.now(),
            "web",
            objectMapper.createObjectNode());

    UUID inspeccionId = inspeccion.getId();
    when(inspeccionRepository.findActiveById(inspeccionId)).thenReturn(Optional.of(inspeccion));

    JsonNode datosConCampoDesconocido =
        objectMapper.readTree(
            """
            {
              "identificacion": {
                "nombre_puente": "Puente",
                "campo_no_declarado": 999
              }
            }
            """);

    assertThatThrownBy(
            () ->
                service.guardarRespuestas(
                    inspeccionId,
                    new GuardarRespuestasRequest(datosConCampoDesconocido),
                    UUID.randomUUID()))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getErrores()).isNotEmpty();
              assertThat(
                      apiEx.getErrores().stream()
                          .anyMatch(
                              e ->
                                  "identificacion.campo_no_declarado".equals(e.get("campo"))
                                      && e.get("mensaje").contains("no está permitido")))
                  .isTrue();
            });

    verify(inspeccionRepository, never()).save(any());
  }

  @Test
  void guardarRespuestas_rechazaTipoOValorIncorrectoCon422Rfc7807() throws Exception {
    Inspeccion inspeccion =
        new Inspeccion(
            puenteActivo,
            version1Publicada,
            UUID.randomUUID(),
            "INSPECTOR",
            "Ing. Pérez",
            LocalDate.now(),
            "web",
            objectMapper.createObjectNode());

    UUID inspeccionId = inspeccion.getId();
    when(inspeccionRepository.findActiveById(inspeccionId)).thenReturn(Optional.of(inspeccion));

    // material con valor no permitido por el enum
    JsonNode datosInvalidos =
        objectMapper.readTree(
            """
            {
              "subestructura": {
                "estribo_entrada": {
                  "material": "MATERIAL_NO_EXISTENTE"
                }
              }
            }
            """);

    assertThatThrownBy(
            () ->
                service.guardarRespuestas(
                    inspeccionId,
                    new GuardarRespuestasRequest(datosInvalidos),
                    UUID.randomUUID()))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getErrores()).isNotEmpty();
              assertThat(
                      apiEx.getErrores().stream()
                          .anyMatch(
                              e ->
                                  "subestructura.estribo_entrada.material".equals(e.get("campo"))
                                      && e.get("mensaje").contains("Valor no permitido")))
                  .isTrue();
            });

    verify(inspeccionRepository, never()).save(any());
  }

  @Test
  void enviarInspeccion_exigeObligatoriosYPermaneceEnBorrador() throws Exception {
    JsonNode datosIncompletos =
        objectMapper.readTree(
            """
            {
              "identificacion": {
                "nombre_puente": "Puente Los Esclavos"
              }
            }
            """);

    Inspeccion inspeccion =
        new Inspeccion(
            puenteActivo,
            version1Publicada,
            UUID.randomUUID(),
            "INSPECTOR",
            "Ing. Pérez",
            LocalDate.now(),
            "web",
            datosIncompletos);

    UUID inspeccionId = inspeccion.getId();
    when(inspeccionRepository.findActiveById(inspeccionId)).thenReturn(Optional.of(inspeccion));

    // Al intentar enviar sin subestructura (que es obligatoria para enviar)
    assertThatThrownBy(() -> service.enviarInspeccion(inspeccionId, UUID.randomUUID()))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(apiEx.getErrores()).isNotEmpty();
              assertThat(
                      apiEx.getErrores().stream()
                          .anyMatch(e -> "subestructura".equals(e.get("campo"))))
                  .isTrue();
            });

    // La inspección permanece en BORRADOR y no se guarda como ENVIADA
    assertThat(inspeccion.getEstado()).isEqualTo("BORRADOR");
    verify(inspeccionRepository, never()).save(any());
  }

  @Test
  void enviarInspeccion_conDatosCompletos_pasaAEstadoEnviada() throws Exception {
    JsonNode datosCompletos =
        objectMapper.readTree(
            """
            {
              "identificacion": {
                "nombre_puente": "Puente Los Esclavos"
              },
              "subestructura": {
                "estribo_entrada": {
                  "material": "CONCRETO"
                }
              }
            }
            """);

    Inspeccion inspeccion =
        new Inspeccion(
            puenteActivo,
            version1Publicada,
            UUID.randomUUID(),
            "INSPECTOR",
            "Ing. Pérez",
            LocalDate.now(),
            "web",
            datosCompletos);

    UUID inspeccionId = inspeccion.getId();
    when(inspeccionRepository.findActiveById(inspeccionId)).thenReturn(Optional.of(inspeccion));
    when(inspeccionRepository.save(any(Inspeccion.class))).thenAnswer(i -> i.getArgument(0));

    InspeccionResumenResponse response =
        service.enviarInspeccion(inspeccionId, UUID.randomUUID());

    assertThat(response).isNotNull();
    assertThat(response.estado()).isEqualTo("ENVIADA");
    assertThat(inspeccion.getEstado()).isEqualTo("ENVIADA");
    assertThat(inspeccion.getEnviadoDispositivoEn()).isNotNull();
    verify(inspeccionRepository).save(inspeccion);
  }
}
