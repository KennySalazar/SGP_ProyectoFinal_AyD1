package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.archivo.entity.*;
import gt.usac.cunoc.sgp.archivo.mapper.FotografiaMapper;
import gt.usac.cunoc.sgp.archivo.model.*;
import gt.usac.cunoc.sgp.archivo.repository.*;
import gt.usac.cunoc.sgp.archivo.service.*;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.dto.*;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.inspeccion.validation.ValidadorAnclajeFotografico;
import gt.usac.cunoc.sgp.puente.repository.AsignacionPuenteRepository;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import gt.usac.cunoc.sgp.sync.model.SolicitudOperacion;
import gt.usac.cunoc.sgp.sync.service.*;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.locationtech.jts.geom.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FotografiaService {
  private final InspeccionRepository inspecciones;
  private final AsignacionPuenteRepository asignaciones;
  private final RevisionRepository revisiones;
  private final ArchivoRepository archivos;
  private final ArchivoInspeccionRepository vinculos;
  private final RegistroFotografiasInspeccion registroFotografias;
  private final FotografiaMapper mapper;
  private final ValidadorAnclajeFotografico anclajes;
  private final ReglasEvidenciaFotografica reglasEvidencia;
  private final ProcesadorImagenes procesador;
  private final CargasImagenService cargas;
  private final ObjetosImagenService objetos;
  private final AlmacenamientoArchivos almacenamiento;
  private final OperacionIdempotenteService operaciones;
  private final ObjectMapper json;
  private final Clock clock;
  private final TransactionTemplate tx;

  public FotografiaService(
      InspeccionRepository inspecciones,
      ArchivoRepository archivos,
      ArchivoInspeccionRepository vinculos,
      RegistroFotografiasInspeccion registroFotografias,
      FotografiaMapper mapper,
      ValidadorAnclajeFotografico anclajes,
      ReglasEvidenciaFotografica reglasEvidencia,
      ProcesadorImagenes procesador,
      CargasImagenService cargas,
      ObjetosImagenService objetos,
      AlmacenamientoArchivos almacenamiento,
      OperacionIdempotenteService operaciones,
      ObjectMapper json,
      Clock clock,
      AsignacionPuenteRepository asignaciones,
      RevisionRepository revisiones,
      PlatformTransactionManager tm) {
    this.inspecciones = inspecciones;
    this.asignaciones = asignaciones;
    this.revisiones = revisiones;
    this.archivos = archivos;
    this.vinculos = vinculos;
    this.registroFotografias = registroFotografias;
    this.mapper = mapper;
    this.anclajes = anclajes;
    this.reglasEvidencia = reglasEvidencia;
    this.procesador = procesador;
    this.cargas = cargas;
    this.objetos = objetos;
    this.almacenamiento = almacenamiento;
    this.operaciones = operaciones;
    this.json = json;
    this.clock = clock;
    this.tx = new TransactionTemplate(tm);
  }

  public FotografiaResponse adjuntar(
      UUID inspeccionId,
      UUID actor,
      AdjuntarFotografiaRequest request,
      MultipartFile fichero,
      String clave) {
    tx.executeWithoutResult(t -> autorizar(inspeccionId, actor, false));
    CategoriaFoto categoria = validar(request, clave);
    if (fichero.isEmpty())
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "contenido_fotografico_invalido",
          "La fotografía no puede estar vacía.");
    if (fichero.getSize() > 20L * 1024 * 1024)
      throw error(
          HttpStatus.PAYLOAD_TOO_LARGE,
          "carga_demasiado_grande",
          "El archivo debe contener una imagen de hasta 20 MB.");
    Path temporal = null;
    CargaImagenPreparada preparada = null;
    boolean confirmada = false;
    try {
      temporal = Files.createTempFile("sgp-fotografia-", ".tmp");
      fichero.transferTo(temporal);
      byte[] bytes = Files.readAllBytes(temporal);
      var huella =
          json.createObjectNode()
              .put("inspeccionId", inspeccionId.toString())
              .put("checksum", ProcesadorImagenes.checksum(bytes));
      huella.set("metadatos", json.valueToTree(request));
      var solicitud =
          new SolicitudOperacion(
              actor,
              clave,
              "POST",
              "/api/v1/inspecciones/" + inspeccionId + "/fotografias",
              HuellaSolicitud.calcular(huella),
              "archivo",
              request.id());
      var previa = operaciones.buscarCompletada(solicitud);
      if (previa.isPresent()) return json.convertValue(previa.get(), FotografiaResponse.class);
      tx.executeWithoutResult(
          t -> {
            var i = autorizar(inspeccionId, actor, false);
            borrador(i);
            anclajes.validar(i, request.elementoRef(), request.instanciaElementoId());
            validarCapacidad(inspeccionId);
          });
      var imagen = procesador.procesar(temporal);
      preparada = cargas.preparar(solicitud, imagen);
      if (preparada.reserva().completada())
        return json.convertValue(preparada.reserva().respuesta(), FotografiaResponse.class);
      final var carga = preparada;
      var respuesta =
          tx.execute(
              t -> {
                var inspeccion = autorizar(inspeccionId, actor, true);
                borrador(inspeccion);
                anclajes.validar(inspeccion, request.elementoRef(), request.instanciaElementoId());
                validarCapacidad(inspeccionId);
                operaciones.bloquearEntidad("archivo", request.id());
                if (archivos.existsById(request.id()))
                  throw error(
                      HttpStatus.CONFLICT,
                      "fotografia_identidad_conflictiva",
                      "El identificador de fotografía ya está registrado.");
                var punto =
                    new GeometryFactory(new PrecisionModel(), 4326)
                        .createPoint(new Coordinate(request.longitud(), request.latitud()));
                var archivo =
                    new Archivo(
                        request.id(),
                        TipoUsoArchivo.FOTO_INSPECCION,
                        categoria,
                        null,
                        carga.claves().original(),
                        carga.claves().original(),
                        carga.claves().miniatura(),
                        imagen.mimeReal(),
                        imagen.original().length,
                        imagen.checksumSha256(),
                        imagen.anchoPx(),
                        imagen.altoPx(),
                        punto,
                        request.capturadoEn(),
                        actor,
                        clock.instant());
                archivos.saveAndFlush(archivo);
                var vinculo =
                    new ArchivoInspeccion(
                        archivo,
                        inspeccion,
                        request.elementoRef(),
                        request.instanciaElementoId(),
                        clock.instant());
                vinculos.saveAndFlush(vinculo);
                var dto = mapper.toResponse(vinculo);
                operaciones.completar(carga.reserva(), json.valueToTree(dto), 201, request.id());
                return dto;
              });
      confirmada = true;
      return respuesta;
    } catch (IOException e) {
      throw error(
          HttpStatus.SERVICE_UNAVAILABLE,
          "almacenamiento_no_disponible",
          "No se pudo almacenar la fotografía. Puede reintentar con la misma operación.");
    } finally {
      if (preparada != null && !preparada.reserva().completada() && !confirmada) {
        try {
          final var pendiente = preparada;
          operaciones.compensar(
              pendiente.reserva(),
              () -> {
                try {
                  objetos.eliminar(pendiente.claves());
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
        } catch (RuntimeException e) {
          /* El manifiesto duradero permite recuperar este intento tras vencer. */
        }
      }
      if (temporal != null)
        try {
          Files.deleteIfExists(temporal);
        } catch (IOException e) {
          temporal.toFile().deleteOnExit();
        }
    }
  }

  private void validarCapacidad(UUID inspeccionId) {
    reglasEvidencia.validarCapacidadParaNuevaFotografia(
        vinculos.countByInspeccionIdAndTipoUsoAndArchivo_Estado(
            inspeccionId, TipoUsoArchivo.FOTO_INSPECCION, EstadoArchivo.DISPONIBLE));
  }

  private CategoriaFoto validar(AdjuntarFotografiaRequest r, String clave) {
    if (r == null
        || r.id() == null
        || r.id().version() != 7
        || r.id().variant() != 2
        || clave == null
        || clave.isBlank()
        || clave.length() > 100)
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "fotografia_metadatos_invalidos",
          "Se requieren UUID v7 y clave idempotente válidos.");
    if (r.latitud() == null
        || r.longitud() == null
        || !Double.isFinite(r.latitud())
        || !Double.isFinite(r.longitud())
        || Math.abs(r.latitud()) > 90
        || Math.abs(r.longitud()) > 180
        || r.capturadoEn() == null
        || r.capturadoEn().isAfter(clock.instant().plusSeconds(300)))
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "fotografia_metadatos_invalidos",
          "La fotografía requiere ubicación y fecha de captura válidas.");
    try {
      return CategoriaFoto.valueOf(r.categoria());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "categoria_fotografia_invalida",
          "Seleccione una categoría fotográfica válida.");
    }
  }

  private Inspeccion autorizar(UUID id, UUID actor, boolean bloquear) {
    if (actor == null)
      throw error(
          HttpStatus.UNAUTHORIZED, "autenticacion_requerida", "Se requiere una sesión válida.");
    var i =
        (bloquear
                ? inspecciones.findByIdAndEliminadoEnIsNullForUpdate(id)
                : inspecciones.findByIdAndEliminadoEnIsNull(id))
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.NOT_FOUND,
                        "inspeccion_no_encontrada",
                        "Inspección no encontrada."));
    if (!i.getAutorId().equals(actor))
      throw error(HttpStatus.NOT_FOUND, "inspeccion_no_encontrada", "Inspección no encontrada.");
    return i;
  }

  private void borrador(Inspeccion i) {
    if (!"BORRADOR".equals(i.getEstado()))
      throw error(
          HttpStatus.CONFLICT,
          "estado_inspeccion_no_permite_archivos",
          "Solo se pueden modificar fotografías de un borrador propio.");
  }

  public void retirar(UUID id, UUID fotoId, UUID actor) {
    tx.executeWithoutResult(
        t -> {
          var inspeccion = autorizar(id, actor, true);
          borrador(inspeccion);
          registroFotografias.retirar(id, fotoId, clock.instant());
        });
  }

  public List<FotografiaResponse> listar(UUID id, UUID actor) {
    return listar(id, actor, null);
  }

  public List<FotografiaResponse> listar(UUID id, UUID actor, RoleName rol) {
    return tx.execute(
        t -> {
          autorizarLectura(id, actor, rol);
          return vinculos
              .findByInspeccionIdAndTipoUsoOrderByCreadoEnAsc(id, TipoUsoArchivo.FOTO_INSPECCION)
              .stream()
              .filter(v -> v.getArchivo().getEstado() == EstadoArchivo.DISPONIBLE)
              .map(mapper::toResponse)
              .toList();
        });
  }

  public InputStream contenido(UUID id, UUID fotoId, UUID actor, boolean miniatura) {
    return contenido(id, fotoId, actor, null, miniatura);
  }

  public InputStream contenido(UUID id, UUID fotoId, UUID actor, RoleName rol, boolean miniatura) {
    String clave =
        tx.execute(
            t -> {
              autorizarLectura(id, actor, rol);
              var v =
                  vinculos
                      .findByInspeccionIdAndArchivoIdAndTipoUso(
                          id, fotoId, TipoUsoArchivo.FOTO_INSPECCION)
                      .filter(x -> x.getArchivo().getEstado() == EstadoArchivo.DISPONIBLE)
                      .orElseThrow(
                          () ->
                              error(
                                  HttpStatus.NOT_FOUND,
                                  "fotografia_no_encontrada",
                                  "Fotografía no encontrada."));
              return miniatura
                  ? v.getArchivo().getClaveObjetoMiniatura()
                  : v.getArchivo().getClaveObjetoOriginal();
            });
    try {
      return almacenamiento.abrir(clave);
    } catch (IOException e) {
      throw error(
          HttpStatus.SERVICE_UNAVAILABLE,
          "almacenamiento_no_disponible",
          "No se pudo recuperar la fotografía.");
    }
  }

  private ApiException error(HttpStatus status, String code, String detail) {
    return new ApiException(status, code, "Fotografía de inspección", detail);
  }

  private void autorizarLectura(UUID id, UUID actor, RoleName rol) {
    if (actor == null)
      throw error(
          HttpStatus.UNAUTHORIZED, "autenticacion_requerida", "Se requiere una sesión válida.");
    var i =
        inspecciones
            .findByIdAndEliminadoEnIsNull(id)
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.NOT_FOUND,
                        "inspeccion_no_encontrada",
                        "Inspección no encontrada."));
    if ("PUBLICADA".equals(i.getEstado()) || i.getAutorId().equals(actor)) return;
    if ("EN_REVISION".equals(i.getEstado())) {
      if (rol != RoleName.CATEDRATICO)
        throw error(
            HttpStatus.FORBIDDEN,
            "lectura_revision_no_permitida",
            "La lectura de una inspección en revisión requiere ser su autor o su catedrático revisor.");
      if (revisiones.existsByInspeccionIdAndRevisorIdAndEstado(id, actor, "EN_CURSO")) return;
      throw error(HttpStatus.NOT_FOUND, "inspeccion_no_encontrada", "Inspección no encontrada.");
    }
    if (rol == RoleName.CATEDRATICO
        && Set.of("BORRADOR", "ENVIADA", "EN_REVISION").contains(i.getEstado())
        && i.getAsignacionPuenteId() != null
        && asignaciones.existsVigenteParaLecturaFotografica(
            i.getAsignacionPuenteId(), i.getAutorId(), i.getPuente().getId(), actor)) return;
    throw error(HttpStatus.NOT_FOUND, "inspeccion_no_encontrada", "Inspección no encontrada.");
  }
}
