package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.archivo.entity.Archivo;
import gt.usac.cunoc.sgp.archivo.entity.ArchivoInspeccion;
import gt.usac.cunoc.sgp.archivo.model.EstadoArchivo;
import gt.usac.cunoc.sgp.archivo.model.TipoUsoArchivo;
import gt.usac.cunoc.sgp.archivo.repository.ArchivoInspeccionRepository;
import gt.usac.cunoc.sgp.archivo.repository.ArchivoRepository;
import gt.usac.cunoc.sgp.archivo.service.AlmacenamientoArchivos;
import gt.usac.cunoc.sgp.archivo.service.ProcesadorImagenes;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.inspeccion.dto.DocumentoInspeccionResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.validation.ValidadorDocumentoPdf;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documentos PDF de una inspección (HU-070, RN-ARC-04, RN-ARC-05, DT-SEC-08, DT-SEC-09). Solo el
 * autor adjunta o retira, y únicamente en borrador; después del envío quedan fijos como las
 * fotografías. El contenido vive en el almacenamiento de objetos; la base solo guarda la
 * referencia.
 */
@Service
public class DocumentoInspeccionService {

  static final int MAXIMO_DOCUMENTOS = 10;

  private static final Logger LOG = LoggerFactory.getLogger(DocumentoInspeccionService.class);
  private static final String NOMBRE_POR_DEFECTO = "documento.pdf";

  private final InspeccionRepository inspecciones;
  private final ArchivoRepository archivos;
  private final ArchivoInspeccionRepository vinculos;
  private final AlmacenamientoArchivos almacenamiento;
  private final ValidadorDocumentoPdf validador;
  private final FotografiaService fotografias;
  private final AuditoriaWriter auditoria;
  private final ObjectMapper json;
  private final Clock clock;

  public DocumentoInspeccionService(
      InspeccionRepository inspecciones,
      ArchivoRepository archivos,
      ArchivoInspeccionRepository vinculos,
      AlmacenamientoArchivos almacenamiento,
      ValidadorDocumentoPdf validador,
      FotografiaService fotografias,
      AuditoriaWriter auditoria,
      ObjectMapper json,
      Clock clock) {
    this.inspecciones = inspecciones;
    this.archivos = archivos;
    this.vinculos = vinculos;
    this.almacenamiento = almacenamiento;
    this.validador = validador;
    this.fotografias = fotografias;
    this.auditoria = auditoria;
    this.json = json;
    this.clock = clock;
  }

  /**
   * Guarda el PDF con un nombre generado por el servidor y registra su referencia en la misma
   * transacción. Si la base falla, se elimina el objeto; si falla el almacenamiento, no queda
   * ningún registro y se puede reintentar.
   */
  @Transactional
  public DocumentoInspeccionResponse adjuntar(
      UUID inspeccionId, UUID actor, MultipartFile fichero) {
    Inspeccion inspeccion = editable(inspeccionId, actor);
    long disponibles =
        vinculos.countByInspeccionIdAndTipoUsoAndArchivo_Estado(
            inspeccionId, TipoUsoArchivo.DOCUMENTO_INSPECCION, EstadoArchivo.DISPONIBLE);
    if (disponibles >= MAXIMO_DOCUMENTOS) {
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "limite_documentos",
          "Una inspección admite hasta " + MAXIMO_DOCUMENTOS + " documentos PDF.");
    }
    byte[] contenido = validador.validar(fichero);

    UUID id = UuidV7Generator.generate();
    String clave = "documento-" + inspeccionId + "-" + id + ".pdf";
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int estado) {
            if (estado == STATUS_ROLLED_BACK) {
              eliminarObjeto(clave);
            }
          }
        });
    try {
      almacenamiento.guardar(
          clave, new ByteArrayInputStream(contenido), contenido.length, "application/pdf");
    } catch (IOException e) {
      throw error(
          HttpStatus.SERVICE_UNAVAILABLE,
          "almacenamiento_no_disponible",
          "No fue posible guardar el documento. Intente nuevamente.");
    }

    Instant ahora = clock.instant();
    String nombre = nombreSeguro(fichero.getOriginalFilename());
    Archivo archivo =
        new Archivo(
            id,
            TipoUsoArchivo.DOCUMENTO_INSPECCION,
            null,
            nombre,
            id + ".pdf",
            clave,
            null,
            "application/pdf",
            contenido.length,
            ProcesadorImagenes.checksum(contenido),
            null,
            null,
            null,
            null,
            actor,
            ahora);
    archivos.saveAndFlush(archivo);
    ArchivoInspeccion vinculo = new ArchivoInspeccion(archivo, inspeccion, null, null, ahora);
    vinculos.saveAndFlush(vinculo);
    auditoria.registrarEnTransaccion(
        actor,
        AccionAuditoria.CREAR,
        "archivo",
        id,
        null,
        json.createObjectNode()
            .put("tipoUso", TipoUsoArchivo.DOCUMENTO_INSPECCION.name())
            .put("inspeccionId", inspeccionId.toString())
            .put("nombreOriginal", nombre)
            .put("tamanoBytes", contenido.length),
        null);
    return aRespuesta(vinculo);
  }

  /** Documentos disponibles, con las mismas reglas de lectura que las fotografías. */
  @Transactional(readOnly = true)
  public List<DocumentoInspeccionResponse> listar(UUID inspeccionId, UUID actor, RoleName rol) {
    fotografias.autorizarLectura(inspeccionId, actor, rol);
    return vinculos
        .findByInspeccionIdAndTipoUsoOrderByCreadoEnAsc(
            inspeccionId, TipoUsoArchivo.DOCUMENTO_INSPECCION)
        .stream()
        .filter(v -> v.getArchivo().getEstado() == EstadoArchivo.DISPONIBLE)
        .map(this::aRespuesta)
        .toList();
  }

  /** Contenido para la descarga, con el nombre original saneado; nunca la ruta del objeto. */
  @Transactional(readOnly = true)
  public Descarga descargar(UUID inspeccionId, UUID documentoId, UUID actor, RoleName rol) {
    Archivo archivo = consultarDisponible(inspeccionId, documentoId, actor, rol);
    try {
      return new Descarga(almacenamiento.abrir(archivo.getClaveObjetoOriginal()), nombre(archivo));
    } catch (IOException e) {
      throw error(
          HttpStatus.SERVICE_UNAVAILABLE,
          "almacenamiento_no_disponible",
          "No fue posible recuperar el documento. Intente nuevamente.");
    }
  }

  /** Retira un documento de un borrador propio; deja de listarse y de descargarse. */
  @Transactional
  public void retirar(UUID inspeccionId, UUID documentoId, UUID actor) {
    editable(inspeccionId, actor);
    ArchivoInspeccion vinculo =
        vinculos
            .findByInspeccionIdAndArchivoIdAndTipoUso(
                inspeccionId, documentoId, TipoUsoArchivo.DOCUMENTO_INSPECCION)
            .filter(v -> v.getArchivo().getEstado() == EstadoArchivo.DISPONIBLE)
            .orElseThrow(DocumentoInspeccionService::noEncontrado);
    Archivo archivo = vinculo.getArchivo();
    archivo.retirar(clock.instant());
    archivos.saveAndFlush(archivo);
    auditoria.registrarEnTransaccion(
        actor,
        AccionAuditoria.CAMBIAR_ESTADO,
        "archivo",
        documentoId,
        json.createObjectNode().put("estado", EstadoArchivo.DISPONIBLE.name()),
        json.createObjectNode()
            .put("estado", EstadoArchivo.RETIRADO.name())
            .put("inspeccionId", inspeccionId.toString()),
        null);
  }

  /** Contenido del documento y nombre con que se entrega. */
  public record Descarga(InputStream contenido, String nombre) {}

  private Archivo consultarDisponible(
      UUID inspeccionId, UUID documentoId, UUID actor, RoleName rol) {
    fotografias.autorizarLectura(inspeccionId, actor, rol);
    return vinculos
        .findByInspeccionIdAndArchivoIdAndTipoUso(
            inspeccionId, documentoId, TipoUsoArchivo.DOCUMENTO_INSPECCION)
        .map(ArchivoInspeccion::getArchivo)
        .filter(a -> a.getEstado() == EstadoArchivo.DISPONIBLE)
        .orElseThrow(DocumentoInspeccionService::noEncontrado);
  }

  /** Bloquea la inspección y exige que sea un borrador del propio actor. */
  private Inspeccion editable(UUID inspeccionId, UUID actor) {
    if (actor == null) {
      throw error(
          HttpStatus.UNAUTHORIZED, "autenticacion_requerida", "Se requiere una sesión válida.");
    }
    Inspeccion inspeccion =
        inspecciones
            .findByIdAndEliminadoEnIsNullForUpdate(inspeccionId)
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.NOT_FOUND,
                        "inspeccion_no_encontrada",
                        "Inspección no encontrada."));
    if (!inspeccion.getAutorId().equals(actor)) {
      throw error(
          HttpStatus.FORBIDDEN,
          "documento_no_autorizado",
          "Solo el autor de la inspección puede adjuntar o retirar documentos.");
    }
    if (!"BORRADOR".equals(inspeccion.getEstado())) {
      throw error(
          HttpStatus.CONFLICT,
          "estado_inspeccion_no_permite_archivos",
          "Los documentos solo se modifican en un borrador; después del envío quedan fijos.");
    }
    return inspeccion;
  }

  private DocumentoInspeccionResponse aRespuesta(ArchivoInspeccion vinculo) {
    Archivo archivo = vinculo.getArchivo();
    return new DocumentoInspeccionResponse(
        archivo.getId(),
        vinculo.getInspeccion().getId(),
        nombre(archivo),
        archivo.getTamanoBytes(),
        archivo.getSubidoPorId(),
        archivo.getConfirmadoEn());
  }

  private static String nombre(Archivo archivo) {
    return archivo.getNombreOriginal() == null ? NOMBRE_POR_DEFECTO : archivo.getNombreOriginal();
  }

  /** Quita la ruta y los caracteres de control del nombre del cliente y asegura la extensión. */
  static String nombreSeguro(String nombre) {
    if (nombre == null) {
      return NOMBRE_POR_DEFECTO;
    }
    String seguro = nombre.replace('\\', '/');
    seguro = seguro.substring(seguro.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").strip();
    if (seguro.isEmpty()) {
      return NOMBRE_POR_DEFECTO;
    }
    if (!seguro.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
      seguro = seguro + ".pdf";
    }
    return seguro.length() <= 255 ? seguro : seguro.substring(seguro.length() - 255);
  }

  private void eliminarObjeto(String clave) {
    try {
      almacenamiento.eliminar(clave);
    } catch (IOException | RuntimeException e) {
      LOG.error("No fue posible eliminar el documento tras el rollback: {}", clave, e);
    }
  }

  private static ApiException noEncontrado() {
    return error(HttpStatus.NOT_FOUND, "documento_no_encontrado", "Documento no encontrado.");
  }

  private static ApiException error(HttpStatus status, String codigo, String detalle) {
    return new ApiException(status, codigo, "Documento de inspección", detalle);
  }
}
