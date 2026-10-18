package gt.usac.cunoc.sgp.formulario.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.archivo.entity.*;
import gt.usac.cunoc.sgp.archivo.model.*;
import gt.usac.cunoc.sgp.archivo.repository.*;
import gt.usac.cunoc.sgp.archivo.service.AlmacenamientoArchivos;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.entity.*;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.*;
import gt.usac.cunoc.sgp.formulario.validation.ValidadorActaPdf;
import java.io.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;

@ExtendWith(MockitoExtension.class)
class ActaCalibracionServiceTest {
  @Mock private VersionFormularioRepository versiones;
  @Mock private ConfiguracionIcElementoRepository pesos;
  @Mock private ArchivoRepository archivos;
  @Mock private ArchivoActaCalibracionRepository actas;
  @Mock private AlmacenamientoArchivos almacenamiento;
  @Mock private ValidadorActaPdf validador;
  @Mock private AuditoriaWriter auditoria;
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  private final UUID id = UUID.randomUUID();
  private final UUID usuario = UUID.randomUUID();
  private final Instant ahora = Instant.parse("2026-10-18T07:00:00Z");
  private final byte[] contenido = "%PDF-contenido-validado".getBytes();
  private final MockMultipartFile fichero =
      new MockMultipartFile("archivo", "../acta.pdf", "application/pdf", contenido);
  private VersionFormulario version;
  private ActaCalibracionService service;

  @BeforeEach
  void preparar() throws Exception {
    version =
        new VersionFormulario(
            id,
            "v1",
            EstadoVersionFormulario.BORRADOR,
            false,
            json.readTree("{}"),
            json.readTree("{}"),
            json.readTree("{\"elementos\":[{\"elemento_ref\":\"/viga\"}]}"),
            null,
            usuario,
            ahora,
            ahora);
    service =
        new ActaCalibracionService(
            versiones,
            pesos,
            archivos,
            actas,
            almacenamiento,
            validador,
            auditoria,
            json,
            Clock.fixed(ahora, ZoneOffset.UTC));
  }

  @AfterEach
  void limpiar() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private void prepararCarga() {
    when(versiones.findByIdForUpdate(id)).thenReturn(Optional.of(version));
    when(pesos.findAllByVersionFormularioId(id))
        .thenReturn(
            List.of(
                new ConfiguracionIcElemento(
                    UUID.randomUUID(), version, "/viga", BigDecimal.ONE, "Viga", ahora)));
    when(validador.validar(fichero)).thenReturn(contenido);
    TransactionSynchronizationManager.initSynchronization();
  }

  @Test
  void consultaExplicitaSinActa() {
    when(versiones.findById(id)).thenReturn(Optional.of(version));
    var respuesta = service.consultar(id);
    assertThat(respuesta.estado()).isEqualTo("SIN_ACTA");
    assertThat(respuesta.mensaje()).isEqualTo("Sin acta de calibracion");
    assertThat(respuesta.acta()).isNull();
    verifyNoInteractions(almacenamiento);
  }

  @Test
  void adjuntaObjetoPrivadoYRegistraAuditoria() throws Exception {
    prepararCarga();
    var respuesta = service.adjuntar(id, fichero, usuario);
    assertThat(respuesta.estado()).isEqualTo("CON_ACTA");
    assertThat(respuesta.acta().nombreOriginal()).isEqualTo("acta.pdf");
    assertThat(respuesta.acta().subidoPorId()).isEqualTo(usuario);
    assertThat(respuesta.acta().subidoEn()).isEqualTo(ahora);
    verify(almacenamiento)
        .guardar(
            startsWith("acta-" + id), any(), eq((long) contenido.length), eq("application/pdf"));
    verify(actas).saveAndFlush(any(ArchivoActaCalibracion.class));
    verify(auditoria)
        .registrarEnTransaccion(
            eq(usuario),
            any(),
            eq("version_formulario"),
            eq(id),
            argThat(nodo -> "SIN_ACTA".equals(nodo.path("estado").asText())),
            argThat(nodo -> "CON_ACTA".equals(nodo.path("estado").asText())),
            anyString());
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(
            sincronizacion ->
                sincronizacion.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    verify(almacenamiento, never()).eliminar(anyString());
  }

  @Test
  void reemplazaElActaConservandoElArchivoAnteriorRetirado() throws Exception {
    prepararCarga();
    Archivo anterior = archivo();
    when(actas.findFirstByVersionFormularioIdAndArchivoEstadoOrderByCreadoEnDescArchivoIdDesc(
            id, EstadoArchivo.DISPONIBLE))
        .thenReturn(Optional.of(new ArchivoActaCalibracion(anterior, version, ahora)));
    service.adjuntar(id, fichero, usuario);
    assertThat(anterior.getEstado()).isEqualTo(EstadoArchivo.RETIRADO);
    verify(archivos).saveAndFlush(anterior);
    verify(almacenamiento, never()).eliminar(eq(anterior.getClaveObjetoOriginal()));
  }

  @Test
  void rollbackLimpiaElObjetoNuevoSiFallaLaBaseDeDatos() throws Exception {
    prepararCarga();
    when(archivos.saveAndFlush(any())).thenThrow(new IllegalStateException("Fallo persistencia"));
    assertThatThrownBy(() -> service.adjuntar(id, fichero, usuario))
        .isInstanceOf(IllegalStateException.class);
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(
            sincronizacion ->
                sincronizacion.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    verify(almacenamiento).eliminar(startsWith("acta-" + id));
    verifyNoInteractions(auditoria);
  }

  @Test
  void errorDeAlmacenamientoNoGuardaMetadatos() throws Exception {
    prepararCarga();
    doThrow(new IOException("No disponible"))
        .when(almacenamiento)
        .guardar(anyString(), any(), anyLong(), anyString());
    assertThatThrownBy(() -> service.adjuntar(id, fichero, usuario))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("almacenamiento_no_disponible");
    verifyNoInteractions(archivos, auditoria);
  }

  @Test
  void versionPublicadaPermiteAdjuntarActaSinModificarPesosNiReglas() {
    prepararCarga();
    version.setEstado(EstadoVersionFormulario.PUBLICADA);
    var reglas = version.getReglasEvaluacionIc().deepCopy();
    service.adjuntar(id, fichero, usuario);
    assertThat(version.getEstado()).isEqualTo(EstadoVersionFormulario.PUBLICADA);
    assertThat(version.getReglasEvaluacionIc()).isEqualTo(reglas);
    verify(pesos, never()).save(any());
    verify(pesos, never()).deleteAllByVersionFormularioId(any());
  }

  @Test
  void exigePesosConfiguradosAntesDelActa() {
    when(versiones.findByIdForUpdate(id)).thenReturn(Optional.of(version));
    assertThatThrownBy(() -> service.adjuntar(id, fichero, usuario))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("pesos_incompletos");
    verifyNoInteractions(almacenamiento, validador);
  }

  @Test
  void descargaVerificaTamanoYChecksumSinExponerLaClave() throws Exception {
    when(versiones.findById(id)).thenReturn(Optional.of(version));
    Archivo archivo = archivo();
    when(actas.findFirstByVersionFormularioIdAndArchivoEstadoOrderByCreadoEnDescArchivoIdDesc(
            id, EstadoArchivo.DISPONIBLE))
        .thenReturn(Optional.of(new ArchivoActaCalibracion(archivo, version, ahora)));
    when(almacenamiento.abrir(archivo.getClaveObjetoOriginal()))
        .thenReturn(new ByteArrayInputStream(contenido));
    assertThat(service.descargar(id)).isEqualTo(contenido);
    when(almacenamiento.abrir(archivo.getClaveObjetoOriginal()))
        .thenReturn(new ByteArrayInputStream(new byte[1]));
    assertThatThrownBy(() -> service.descargar(id))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("acta_no_disponible");
  }

  @Test
  void descargaSinActaRespondeNoEncontrada() {
    when(versiones.findById(id)).thenReturn(Optional.of(version));
    assertThatThrownBy(() -> service.descargar(id))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("acta_no_encontrada");
  }

  @Test
  void versionNoExistenteRespondeNoEncontrada() {
    assertThatThrownBy(() -> service.consultar(id))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("version_no_encontrada");
    assertThatThrownBy(() -> service.adjuntar(id, fichero, usuario))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("version_no_encontrada");
  }

  @Test
  void usaClavesCompatiblesConElAlmacenamientoLocalReal(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path directorio) throws Exception {
    prepararCarga();
    var local = new gt.usac.cunoc.sgp.archivo.service.AlmacenamientoLocal(directorio);
    service =
        new ActaCalibracionService(
            versiones,
            pesos,
            archivos,
            actas,
            local,
            validador,
            auditoria,
            json,
            Clock.fixed(ahora, ZoneOffset.UTC));
    var respuesta = service.adjuntar(id, fichero, usuario);
    String clave = "acta-" + id + "-" + respuesta.acta().archivoId() + ".pdf";
    assertThat(local.existe(clave)).isTrue();
    try (var entrada = local.abrir(clave)) {
      assertThat(entrada.readAllBytes()).isEqualTo(contenido);
    }
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(
            sincronizacion ->
                sincronizacion.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    assertThat(local.existe(clave)).isFalse();
  }

  private Archivo archivo() throws Exception {
    return new Archivo(
        UUID.randomUUID(),
        TipoUsoArchivo.ACTA_CALIBRACION,
        null,
        "acta.pdf",
        "objeto.pdf",
        "privado/objeto.pdf",
        null,
        "application/pdf",
        contenido.length,
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido)),
        null,
        null,
        null,
        null,
        usuario,
        ahora);
  }
}
