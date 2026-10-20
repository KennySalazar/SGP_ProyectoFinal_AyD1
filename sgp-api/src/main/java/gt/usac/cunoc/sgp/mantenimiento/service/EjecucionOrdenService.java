package gt.usac.cunoc.sgp.mantenimiento.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.archivo.entity.Archivo;
import gt.usac.cunoc.sgp.archivo.entity.ArchivoEvidenciaMantenimiento;
import gt.usac.cunoc.sgp.archivo.model.ClavesImagen;
import gt.usac.cunoc.sgp.archivo.model.EstadoArchivo;
import gt.usac.cunoc.sgp.archivo.model.ImagenProcesada;
import gt.usac.cunoc.sgp.archivo.model.TipoUsoArchivo;
import gt.usac.cunoc.sgp.archivo.repository.ArchivoEvidenciaMantenimientoRepository;
import gt.usac.cunoc.sgp.archivo.repository.ArchivoRepository;
import gt.usac.cunoc.sgp.archivo.service.AlmacenamientoArchivos;
import gt.usac.cunoc.sgp.archivo.service.ObjetosImagenService;
import gt.usac.cunoc.sgp.archivo.service.ProcesadorImagenes;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.mantenimiento.dto.EjecucionOrdenResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.EvidenciaMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.RegistrarEjecucionRequest;
import gt.usac.cunoc.sgp.mantenimiento.dto.ResponsableDisponibleResponse;
import gt.usac.cunoc.sgp.mantenimiento.dto.RetirarEvidenciaRequest;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimiento;
import gt.usac.cunoc.sgp.mantenimiento.entity.OrdenMantenimientoEjecucion;
import gt.usac.cunoc.sgp.mantenimiento.mapper.OrdenMantenimientoMapper;
import gt.usac.cunoc.sgp.mantenimiento.model.EstadoOrden;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoEjecucionRepository;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoRepository;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Registro de la ejecución de una orden: responsable, fecha y fotografías de evidencia (HU-048,
 * RN-MTO-05, RN-ARC-02, DT-SEC-08). No cambia el estado de la orden; el cierre como ejecutada pasa
 * por {@link TransicionOrdenService}.
 */
@Service
public class EjecucionOrdenService {

  static final long TAMANO_MAXIMO_BYTES = 20L * 1024 * 1024;
  static final int MAXIMO_EVIDENCIAS = 30;
  static final int LONGITUD_MAXIMA_RESPONSABLE = 200;
  static final int LONGITUD_MAXIMA_ELEMENTO = 300;
  static final String ENTIDAD_EJECUCION = "orden_mantenimiento_ejecucion";
  static final String ENTIDAD_ARCHIVO = "archivo";

  private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final int MAXIMO_RESPONSABLES = 200;

  private final OrdenMantenimientoRepository ordenes;
  private final OrdenMantenimientoEjecucionRepository ejecuciones;
  private final ArchivoRepository archivos;
  private final ArchivoEvidenciaMantenimientoRepository evidencias;
  private final UserAccountRepository usuarios;
  private final ProcesadorImagenes procesador;
  private final ObjetosImagenService objetos;
  private final AlmacenamientoArchivos almacenamiento;
  private final AuditoriaWriter auditoria;
  private final OrdenMantenimientoMapper mapper;
  private final ObjectMapper json;
  private final Clock clock;
  private final TransactionTemplate tx;

  public EjecucionOrdenService(
      OrdenMantenimientoRepository ordenes,
      OrdenMantenimientoEjecucionRepository ejecuciones,
      ArchivoRepository archivos,
      ArchivoEvidenciaMantenimientoRepository evidencias,
      UserAccountRepository usuarios,
      ProcesadorImagenes procesador,
      ObjetosImagenService objetos,
      AlmacenamientoArchivos almacenamiento,
      AuditoriaWriter auditoria,
      OrdenMantenimientoMapper mapper,
      ObjectMapper json,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.ordenes = ordenes;
    this.ejecuciones = ejecuciones;
    this.archivos = archivos;
    this.evidencias = evidencias;
    this.usuarios = usuarios;
    this.procesador = procesador;
    this.objetos = objetos;
    this.almacenamiento = almacenamiento;
    this.auditoria = auditoria;
    this.mapper = mapper;
    this.json = json;
    this.clock = clock;
    this.tx = new TransactionTemplate(transactionManager);
  }

  @Transactional(readOnly = true)
  public EjecucionOrdenResponse obtenerEjecucion(UUID ordenId) {
    exigirOrden(ordenId);
    OrdenMantenimientoEjecucion ejecucion =
        ejecuciones
            .findById(ordenId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ejecucion_no_encontrada",
                        "Ejecución no encontrada",
                        "La orden " + ordenId + " todavía no ha iniciado su ejecución."));
    return aRespuesta(ejecucion);
  }

  /**
   * Registra la fecha de ejecución y el responsable de una orden EN_EJECUCION. La fecha puede
   * quedar pendiente hasta el cierre; el responsable no.
   */
  @Transactional
  public EjecucionOrdenResponse registrarEjecucion(
      UUID ordenId, RegistrarEjecucionRequest request, UUID usuarioId) {
    if (request == null) {
      throw invalida("responsable", "Indique la fecha de ejecución y el responsable.");
    }
    bloquearEnEjecucion(ordenId);
    String nombre = limpiar(request.responsableNombre());
    if (nombre != null && nombre.length() > LONGITUD_MAXIMA_RESPONSABLE) {
      throw invalida(
          "responsableNombre",
          "El nombre del responsable no puede exceder "
              + LONGITUD_MAXIMA_RESPONSABLE
              + " caracteres.");
    }
    if (request.responsableUsuarioId() == null && nombre == null) {
      throw invalida(
          "responsable",
          "Indique un usuario del sistema o el nombre de la persona o empresa responsable.");
    }
    if (request.responsableUsuarioId() != null
        && !usuarios
            .findById(request.responsableUsuarioId())
            .map(UserAccount::isActive)
            .orElse(false)) {
      throw invalida("responsableUsuarioId", "El usuario responsable no existe o está inactivo.");
    }

    OrdenMantenimientoEjecucion ejecucion = exigirEjecucion(ordenId);
    if (request.fechaEjecucion() != null) {
      validarFecha(request.fechaEjecucion(), ejecucion.getIniciadoEn());
    }

    ObjectNode antes = instantanea(ejecucion);
    ejecucion.registrar(
        request.fechaEjecucion(), request.responsableUsuarioId(), nombre, clock.instant());
    try {
      ejecuciones.saveAndFlush(ejecucion);
    } catch (DataIntegrityViolationException e) {
      throw invalida(
          "fechaEjecucion",
          "La fecha de ejecución no puede ser anterior al día en que se inició la ejecución.");
    }
    auditoria.registrarEnTransaccion(
        usuarioId,
        AccionAuditoria.MODIFICAR,
        ENTIDAD_EJECUCION,
        ordenId,
        antes,
        instantanea(ejecucion),
        null);
    return aRespuesta(ejecucion);
  }

  /** Usuarios activos que pueden figurar como responsables de la intervención. */
  @Transactional(readOnly = true)
  public List<ResponsableDisponibleResponse> listarResponsables() {
    return usuarios
        .findByRolYEstado(null, "ACTIVO", PageRequest.of(0, MAXIMO_RESPONSABLES, Sort.by("email")))
        .stream()
        .map(u -> new ResponsableDisponibleResponse(u.getId(), u.getEmail(), u.getRole().getName()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<EvidenciaMantenimientoResponse> listarEvidencias(UUID ordenId) {
    exigirOrden(ordenId);
    return evidencias
        .findByOrdenMantenimientoIdAndArchivo_EstadoOrderByCreadoEnAsc(
            ordenId, EstadoArchivo.DISPONIBLE)
        .stream()
        .map(mapper::toEvidenciaResponse)
        .toList();
  }

  /**
   * Valida el contenido real de la imagen (JPEG o WebP), la normaliza con miniatura, la guarda con
   * un nombre generado por el servidor y la liga a la orden. Si el almacenamiento falla responde
   * 503 y no registra nada.
   */
  public EvidenciaMantenimientoResponse adjuntarEvidencia(
      UUID ordenId, String elementoRef, MultipartFile fichero, UUID usuarioId) {
    String elemento = limpiar(elementoRef);
    if (elemento != null && elemento.length() > LONGITUD_MAXIMA_ELEMENTO) {
      throw invalida(
          "elementoRef",
          "La referencia del elemento no puede exceder "
              + LONGITUD_MAXIMA_ELEMENTO
              + " caracteres.");
    }
    tx.executeWithoutResult(t -> validarCapacidad(exigirEnEjecucion(exigirOrden(ordenId))));
    if (fichero == null || fichero.isEmpty()) {
      throw evidenciaInvalida("Seleccione una fotografía de evidencia.");
    }
    if (fichero.getSize() > TAMANO_MAXIMO_BYTES) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "evidencia_demasiado_grande",
          "Evidencia demasiado grande",
          "La fotografía de evidencia no puede exceder 20 MB.");
    }

    Path temporal = null;
    ClavesImagen claves = null;
    try {
      temporal = Files.createTempFile("sgp-evidencia-", ".tmp");
      fichero.transferTo(temporal);
      ImagenProcesada imagen = procesador.procesar(temporal);
      claves = objetos.nuevasClaves();
      objetos.guardar(claves, imagen);
      final ClavesImagen guardadas = claves;
      EvidenciaMantenimientoResponse respuesta =
          tx.execute(t -> registrarEvidencia(ordenId, elemento, imagen, guardadas, usuarioId));
      claves = null;
      return respuesta;
    } catch (IOException e) {
      throw almacenamientoNoDisponible("No se pudo almacenar la fotografía de evidencia.");
    } finally {
      if (claves != null) {
        try {
          objetos.eliminar(claves);
        } catch (IOException | RuntimeException e) {
          /* Un objeto huérfano no tiene vínculo de negocio y no se sirve. */
        }
      }
      if (temporal != null) {
        try {
          Files.deleteIfExists(temporal);
        } catch (IOException e) {
          temporal.toFile().deleteOnExit();
        }
      }
    }
  }

  /** Retira una evidencia subida por error; deja de contar para el cierre. */
  @Transactional
  public void retirarEvidencia(
      UUID ordenId, UUID evidenciaId, RetirarEvidenciaRequest request, UUID usuarioId) {
    String motivo = request == null ? null : limpiar(request.motivo());
    if (motivo == null) {
      throw invalida("motivo", "El motivo del retiro es obligatorio.");
    }
    if (motivo.length() > TransicionOrdenService.LONGITUD_MAXIMA_MOTIVO) {
      throw invalida(
          "motivo",
          "El motivo no puede exceder "
              + TransicionOrdenService.LONGITUD_MAXIMA_MOTIVO
              + " caracteres.");
    }
    bloquearEnEjecucion(ordenId);
    ArchivoEvidenciaMantenimiento evidencia = exigirEvidencia(ordenId, evidenciaId);
    Archivo archivo = evidencia.getArchivo();
    if (archivo.getEstado() != EstadoArchivo.DISPONIBLE) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "evidencia_retirada",
          "Evidencia retirada",
          "La evidencia ya fue retirada.");
    }
    archivo.retirar(clock.instant(), motivo);
    archivos.saveAndFlush(archivo);
    auditoria.registrarEnTransaccion(
        usuarioId,
        AccionAuditoria.CAMBIAR_ESTADO,
        ENTIDAD_ARCHIVO,
        evidenciaId,
        json.createObjectNode().put("estado", EstadoArchivo.DISPONIBLE.name()),
        json.createObjectNode()
            .put("estado", EstadoArchivo.RETIRADO.name())
            .put("motivo", motivo)
            .put("ordenId", ordenId.toString()),
        null);
  }

  /** Contenido de una evidencia disponible, servido solo por el extremo autorizado. */
  public InputStream contenidoEvidencia(UUID ordenId, UUID evidenciaId, boolean miniatura) {
    String clave =
        tx.execute(
            t -> {
              Archivo archivo = exigirEvidencia(ordenId, evidenciaId).getArchivo();
              if (archivo.getEstado() != EstadoArchivo.DISPONIBLE) {
                throw evidenciaNoEncontrada();
              }
              return miniatura
                  ? archivo.getClaveObjetoMiniatura()
                  : archivo.getClaveObjetoOriginal();
            });
    try {
      return almacenamiento.abrir(clave);
    } catch (IOException e) {
      throw almacenamientoNoDisponible("No se pudo recuperar la fotografía de evidencia.");
    }
  }

  /**
   * Valida que la fecha no sea futura ni anterior al día en que se inició la ejecución, ambos en
   * hora de Guatemala.
   */
  void validarFecha(LocalDate fecha, Instant iniciadoEn) {
    LocalDate hoy = LocalDate.now(clock.withZone(TransicionOrdenService.ZONA_GUATEMALA));
    if (fecha.isAfter(hoy)) {
      throw invalida("fechaEjecucion", "La fecha de ejecución no puede ser futura.");
    }
    LocalDate inicio = iniciadoEn.atZone(TransicionOrdenService.ZONA_GUATEMALA).toLocalDate();
    if (fecha.isBefore(inicio)) {
      throw invalida(
          "fechaEjecucion",
          "La fecha de ejecución no puede ser anterior al inicio de la ejecución ("
              + FORMATO_FECHA.format(inicio)
              + ").");
    }
  }

  private EvidenciaMantenimientoResponse registrarEvidencia(
      UUID ordenId,
      String elementoRef,
      ImagenProcesada imagen,
      ClavesImagen claves,
      UUID usuarioId) {
    validarCapacidad(bloquearEnEjecucion(ordenId));
    Instant ahora = clock.instant();
    Archivo archivo =
        new Archivo(
            UuidV7Generator.generate(),
            TipoUsoArchivo.EVIDENCIA_MANTENIMIENTO,
            null,
            null,
            claves.original(),
            claves.original(),
            claves.miniatura(),
            imagen.mimeReal(),
            imagen.original().length,
            imagen.checksumSha256(),
            imagen.anchoPx(),
            imagen.altoPx(),
            null,
            null,
            usuarioId,
            ahora);
    archivos.saveAndFlush(archivo);
    ArchivoEvidenciaMantenimiento evidencia =
        evidencias.saveAndFlush(
            new ArchivoEvidenciaMantenimiento(archivo, ordenId, elementoRef, ahora));

    ObjectNode despues =
        json.createObjectNode()
            .put("tipoUso", TipoUsoArchivo.EVIDENCIA_MANTENIMIENTO.name())
            .put("estado", EstadoArchivo.DISPONIBLE.name())
            .put("ordenId", ordenId.toString());
    if (elementoRef != null) {
      despues.put("elementoRef", elementoRef);
    }
    auditoria.registrarEnTransaccion(
        usuarioId, AccionAuditoria.CREAR, ENTIDAD_ARCHIVO, archivo.getId(), null, despues, null);
    return mapper.toEvidenciaResponse(evidencia);
  }

  private void validarCapacidad(OrdenMantenimiento orden) {
    if (evidencias.countByOrdenMantenimientoIdAndArchivo_Estado(
            orden.getId(), EstadoArchivo.DISPONIBLE)
        >= MAXIMO_EVIDENCIAS) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "limite_evidencias",
          "Límite de evidencias",
          "Una orden admite hasta " + MAXIMO_EVIDENCIAS + " fotografías de evidencia.");
    }
  }

  private OrdenMantenimiento bloquearEnEjecucion(UUID ordenId) {
    return exigirEnEjecucion(
        ordenes.findByIdForUpdate(ordenId).orElseThrow(() -> ordenNoEncontrada(ordenId)));
  }

  private OrdenMantenimiento exigirOrden(UUID ordenId) {
    return ordenes.findById(ordenId).orElseThrow(() -> ordenNoEncontrada(ordenId));
  }

  /** Lo ejecutado es inmutable; antes de iniciar no hay ejecución que registrar. */
  private static OrdenMantenimiento exigirEnEjecucion(OrdenMantenimiento orden) {
    if (orden.getEstado() == EstadoOrden.EJECUTADA) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "ejecucion_inmutable",
          "Orden ejecutada",
          "La orden ya fue ejecutada; su fecha, responsable y evidencia no se pueden modificar.");
    }
    if (orden.getEstado() != EstadoOrden.EN_EJECUCION) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "orden_no_en_ejecucion",
          "Orden no en ejecución",
          "La orden está "
              + orden.getEstado().name()
              + "; solo se registra la ejecución de una orden EN_EJECUCION.");
    }
    return orden;
  }

  private OrdenMantenimientoEjecucion exigirEjecucion(UUID ordenId) {
    return ejecuciones
        .findById(ordenId)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.CONFLICT,
                    "ejecucion_no_iniciada",
                    "Ejecución no iniciada",
                    "La orden no tiene registro de ejecución."));
  }

  private ArchivoEvidenciaMantenimiento exigirEvidencia(UUID ordenId, UUID evidenciaId) {
    return evidencias
        .findByOrdenMantenimientoIdAndArchivoId(ordenId, evidenciaId)
        .orElseThrow(EjecucionOrdenService::evidenciaNoEncontrada);
  }

  private EjecucionOrdenResponse aRespuesta(OrdenMantenimientoEjecucion ejecucion) {
    String email =
        ejecucion.getResponsableUsuarioId() == null
            ? null
            : usuarios
                .findById(ejecucion.getResponsableUsuarioId())
                .map(UserAccount::getEmail)
                .orElse(null);
    return mapper.toEjecucionResponse(ejecucion, email);
  }

  private ObjectNode instantanea(OrdenMantenimientoEjecucion ejecucion) {
    ObjectNode nodo = json.createObjectNode();
    nodo.put(
        "fechaEjecucion",
        ejecucion.getFechaEjecucion() == null ? null : ejecucion.getFechaEjecucion().toString());
    nodo.put(
        "responsableUsuarioId",
        ejecucion.getResponsableUsuarioId() == null
            ? null
            : ejecucion.getResponsableUsuarioId().toString());
    nodo.put("responsableNombre", ejecucion.getResponsableNombre());
    return nodo;
  }

  private static String limpiar(String valor) {
    return valor == null || valor.isBlank() ? null : valor.strip();
  }

  private static ApiException ordenNoEncontrada(UUID ordenId) {
    return new ApiException(
        HttpStatus.NOT_FOUND,
        "orden_no_encontrada",
        "Orden no encontrada",
        "No existe la orden de mantenimiento " + ordenId);
  }

  private static ApiException evidenciaNoEncontrada() {
    return new ApiException(
        HttpStatus.NOT_FOUND,
        "evidencia_no_encontrada",
        "Evidencia no encontrada",
        "No existe la evidencia solicitada.");
  }

  private static ApiException evidenciaInvalida(String mensaje) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "contenido_fotografico_invalido",
        "Imagen inválida",
        mensaje);
  }

  private static ApiException almacenamientoNoDisponible(String mensaje) {
    return new ApiException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "almacenamiento_no_disponible",
        "Almacenamiento no disponible",
        mensaje + " Intente de nuevo más tarde.");
  }

  private static ApiException invalida(String campo, String mensaje) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "solicitud_invalida",
        "Solicitud invalida",
        mensaje,
        List.of(Map.of("campo", campo, "mensaje", mensaje)));
  }
}
