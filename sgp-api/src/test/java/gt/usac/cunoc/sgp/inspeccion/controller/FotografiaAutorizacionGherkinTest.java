package gt.usac.cunoc.sgp.inspeccion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import gt.usac.cunoc.sgp.archivo.entity.Archivo;
import gt.usac.cunoc.sgp.archivo.entity.ArchivoInspeccion;
import gt.usac.cunoc.sgp.archivo.mapper.FotografiaMapper;
import gt.usac.cunoc.sgp.archivo.model.EstadoArchivo;
import gt.usac.cunoc.sgp.archivo.model.TipoUsoArchivo;
import gt.usac.cunoc.sgp.archivo.repository.ArchivoInspeccionRepository;
import gt.usac.cunoc.sgp.archivo.repository.ArchivoRepository;
import gt.usac.cunoc.sgp.archivo.service.*;
import gt.usac.cunoc.sgp.common.config.SecurityConfiguration;
import gt.usac.cunoc.sgp.common.security.*;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.service.FotografiaService;
import gt.usac.cunoc.sgp.inspeccion.service.ReglasEvidenciaFotografica;
import gt.usac.cunoc.sgp.inspeccion.validation.ValidadorAnclajeFotografico;
import gt.usac.cunoc.sgp.puente.repository.AsignacionPuenteRepository;
import gt.usac.cunoc.sgp.sync.service.OperacionIdempotenteService;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Pruebas HTTP con política real y persistencia aislada; no son pruebas de S3 ni de bloqueos. */
@WebMvcTest(
    controllers = FotografiaController.class,
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({
  gt.usac.cunoc.sgp.inspeccion.service.PermisosCorreccionService.class,
  SecurityConfiguration.class,
  JwtAuthenticationFilter.class,
  AuthRateLimitFilter.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class,
  FotografiaService.class
})
@DisplayName("Característica: autorización HTTP faltante de HU-029")
class FotografiaAutorizacionGherkinTest {
  @Autowired MockMvc mvc;
  @MockBean InspeccionRepository inspecciones;
  @MockBean AsignacionPuenteRepository asignaciones;
  @MockBean gt.usac.cunoc.sgp.revision.repository.RevisionRepository revisiones;
  @MockBean ArchivoRepository archivos;
  @MockBean ArchivoInspeccionRepository vinculos;
  @MockBean RegistroFotografiasInspeccion registro;
  @MockBean FotografiaMapper mapper;
  @MockBean ValidadorAnclajeFotografico anclajes;
  @MockBean ReglasEvidenciaFotografica reglas;
  @MockBean ProcesadorImagenes procesador;
  @MockBean CargasImagenService cargas;
  @MockBean ObjetosImagenService objetos;
  @MockBean AlmacenamientoArchivos almacenamiento;
  @MockBean OperacionIdempotenteService operaciones;
  @MockBean PlatformTransactionManager transacciones;
  @MockBean JwtService jwt;
  @MockBean UserAccountRepository usuarios;
  @MockBean Clock clock;
  @MockBean gt.usac.cunoc.sgp.revision.repository.ObservacionRevisionRepository observaciones;

  private static final UUID INSPECCION = UUID.fromString("019a0000-0000-7000-8000-000000000011");
  private static final UUID FOTO = UUID.fromString("019a0000-0000-7000-8000-000000000012");
  private static final UUID AUTOR = UUID.fromString("019a0000-0000-7000-8000-000000000013");
  private static final UUID AJENO = UUID.fromString("019a0000-0000-7000-8000-000000000014");
  private static final String ORIGINAL = "interno/original.jpg";
  private static final String MINIATURA = "interno/miniatura.jpg";

  /** Lee solo la tabla Ejemplos de este archivo; no introduce un motor Gherkin ni dependencias. */
  static Stream<Arguments> ejemplosGherkin() throws IOException {
    try (var recurso =
        Objects.requireNonNull(
            FotografiaAutorizacionGherkinTest.class.getResourceAsStream(
                "/features/hu029-autorizacion-faltante.feature"))) {
      return new String(recurso.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .map(String::strip)
          .filter(linea -> linea.startsWith("|"))
          .skip(1)
          .map(
              linea ->
                  Arrays.stream(linea.substring(1, linea.length() - 1).split("\\|"))
                      .map(String::strip)
                      .toArray(String[]::new))
          .map(
              celdas -> {
                assertThat(celdas).hasSize(4);
                return Arguments.of(celdas[0], celdas[1], celdas[2], Integer.parseInt(celdas[3]));
              });
    }
  }

  @ParameterizedTest(name = "Dado {0} y {2}; Cuando solicita {1}; Entonces HTTP {3} sin claves")
  @MethodSource("ejemplosGherkin")
  void escenarioGherkin(String actor, String operacion, String condicion, int estado)
      throws Exception {
    var inspeccion = mock(Inspeccion.class);
    when(inspeccion.getAutorId()).thenReturn(AUTOR);
    when(inspeccion.getEstado()).thenReturn("BORRADOR");
    when(inspecciones.findByIdAndEliminadoEnIsNull(INSPECCION)).thenReturn(Optional.of(inspeccion));
    when(inspecciones.findByIdAndEliminadoEnIsNullForUpdate(INSPECCION))
        .thenReturn(Optional.of(inspeccion));
    when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    if (actor.equals("CATEDRATICO_ASIGNADO")) {
      when(inspeccion.getAsignacionPuenteId()).thenReturn(UUID.randomUUID());
      when(asignaciones.existsVigenteParaLecturaFotografica(any(), any(), any(), any()))
          .thenReturn(true);
    }
    if (condicion.equals("FALLO_INTERNO")) {
      var archivo = mock(Archivo.class);
      var vinculo = mock(ArchivoInspeccion.class);
      when(vinculo.getArchivo()).thenReturn(archivo);
      when(archivo.getEstado()).thenReturn(EstadoArchivo.DISPONIBLE);
      when(archivo.getClaveObjetoOriginal()).thenReturn(ORIGINAL);
      when(archivo.getClaveObjetoMiniatura()).thenReturn(MINIATURA);
      when(vinculos.findByInspeccionIdAndArchivoIdAndTipoUso(
              INSPECCION, FOTO, TipoUsoArchivo.FOTO_INSPECCION))
          .thenReturn(Optional.of(vinculo));
      when(almacenamiento.abrir(anyString()))
          .thenThrow(new IOException("s3://bucket-privado/" + ORIGINAL));
    } else assertThat(condicion).isEqualTo("NO_USADO");

    String base = "/api/v1/inspecciones/" + INSPECCION + "/fotografias";
    MockHttpServletRequestBuilder peticion =
        switch (operacion) {
          case "POST" ->
              multipart(base)
                  .file(new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", new byte[] {1}))
                  .file(
                      new MockMultipartFile(
                          "metadatos",
                          "metadatos.json",
                          "application/json",
                          ("{\"id\":\""
                                  + FOTO
                                  + "\",\"categoria\":\"ACCESO\",\"latitud\":14,\"longitud\":-90,\"capturadoEn\":\"2026-10-15T12:00:00Z\"}")
                              .getBytes(StandardCharsets.UTF_8)))
                  .header("Idempotency-Key", "operacion-gherkin");
          case "DELETE" -> delete(base + "/" + FOTO);
          case "ORIGINAL" -> get(base + "/" + FOTO + "/contenido");
          case "MINIATURA" -> get(base + "/" + FOTO + "/miniatura");
          default ->
              throw new IllegalArgumentException("Operación Gherkin desconocida: " + operacion);
        };
    if (!actor.equals("VISITANTE")) {
      var rol =
          actor.startsWith("CATEDRATICO_") || actor.equals("AUTOR")
              ? RoleName.CATEDRATICO
              : RoleName.valueOf(actor);
      UUID id = actor.equals("AUTOR") ? AUTOR : AJENO;
      var sesion =
          new UsernamePasswordAuthenticationToken(
              "actor", null, java.util.List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
      sesion.setDetails(new JwtData("actor@example.com", id, rol, 0));
      peticion.with(authentication(sesion));
    }
    var respuesta =
        mvc.perform(peticion)
            .andExpect(status().is(estado))
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn()
            .getResponse();
    assertThat(
            respuesta.getContentAsString()
                + respuesta.getHeaderNames().stream()
                    .map(nombre -> respuesta.getHeaders(nombre).toString())
                    .toList())
        .doesNotContain("s3://", "bucket-privado", ORIGINAL, MINIATURA, "claveObjeto");
    if (estado == 503) {
      verify(almacenamiento).abrir(operacion.equals("MINIATURA") ? MINIATURA : ORIGINAL);
    } else {
      verifyNoInteractions(
          archivos,
          vinculos,
          registro,
          mapper,
          procesador,
          cargas,
          objetos,
          almacenamiento,
          operaciones,
          anclajes,
          reglas,
          asignaciones);
      if (estado == 401) verifyNoInteractions(inspecciones);
      else assertThat(respuesta.getContentAsString()).contains("inspeccion_no_encontrada");
    }
  }
}
