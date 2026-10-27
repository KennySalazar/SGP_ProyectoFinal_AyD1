package gt.usac.cunoc.sgp.notificacion.service;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.notificacion.dto.NotificacionResponse;
import gt.usac.cunoc.sgp.notificacion.entity.Notificacion;
import gt.usac.cunoc.sgp.notificacion.mapper.NotificacionMapper;
import gt.usac.cunoc.sgp.notificacion.repository.NotificacionRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio transversal de avisos dentro de la aplicación (HU-040, RN-USR-09, DT-SEC-04, DT-SEC-12).
 * Único punto de entrada para notificar usuarios en la aplicación.
 */
@Service
public class NotificacionService {

  private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

  private static final Pattern PATRON_TOKEN_O_SECRETO =
      Pattern.compile(
          "(eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}|(?i)bearer\\s+[A-Za-z0-9._~+/-]+=*)");
  private static final Pattern PATRON_CALIFICACION =
      Pattern.compile("(?i)\\b(calificaci[oó]n|nota)\\s*[:=]?\\s*\\d+");

  private final NotificacionRepository notificaciones;
  private final NotificacionMapper mapper;
  private final Clock clock;

  public NotificacionService(
      NotificacionRepository notificaciones, @Nullable NotificacionMapper mapper, Clock clock) {
    this.notificaciones = notificaciones;
    this.mapper = mapper != null ? mapper : NotificacionService::toResponseFallback;
    this.clock = clock;
  }

  private static NotificacionResponse toResponseFallback(Notificacion n) {
    if (n == null) {
      return null;
    }
    return new NotificacionResponse(
        n.getId(),
        n.getTipo(),
        n.getTitulo(),
        n.getMensaje(),
        n.getEntidad(),
        n.getEntidadId(),
        n.getClaveEvento(),
        n.getLeidoEn(),
        n.getCreadoEn(),
        n.isLeida());
  }

  /**
   * Registra un aviso para un usuario. Si ya existe un aviso con la misma clave de evento, no se
   * duplica y la operación que lo originó no falla.
   */
  @Transactional
  public void notificar(
      UUID usuarioId,
      String tipo,
      String titulo,
      String mensaje,
      String entidad,
      UUID entidadId,
      String claveEvento) {
    Objects.requireNonNull(usuarioId, "usuarioId no puede ser nulo");
    Objects.requireNonNull(tipo, "tipo no puede ser nulo");
    Objects.requireNonNull(titulo, "titulo no puede ser nulo");
    Objects.requireNonNull(mensaje, "mensaje no puede ser nulo");
    Objects.requireNonNull(claveEvento, "claveEvento no puede ser nulo");

    validarEntidadCoherente(entidad, entidadId);
    validarSinDatosSensibles(titulo, mensaje);

    if (notificaciones.existsByUsuarioIdAndClaveEvento(usuarioId, claveEvento.strip())) {
      log.debug("Aviso ya existente para usuarioId={} claveEvento={}", usuarioId, claveEvento);
      return;
    }

    notificaciones.insertarSiNoExiste(
        new Notificacion(
            UuidV7Generator.generate(),
            usuarioId,
            tipo.strip(),
            titulo.strip(),
            mensaje.strip(),
            entidad != null ? entidad.strip() : null,
            entidadId,
            claveEvento.strip(),
            clock.instant()));
  }

  /** Alias para compatibilidad con código existente. */
  @Transactional
  public void registrar(
      UUID usuarioId,
      String tipo,
      String titulo,
      String mensaje,
      String entidad,
      UUID entidadId,
      String claveEvento) {
    notificar(usuarioId, tipo, titulo, mensaje, entidad, entidadId, claveEvento);
  }

  /** Lista los avisos del usuario autenticado ordenados de más reciente a más antiguo. */
  @Transactional(readOnly = true)
  public Page<NotificacionResponse> listar(
      UUID usuarioId, boolean soloNoLeidos, int pagina, int tamanio) {
    validarPaginacion(pagina, tamanio);
    var pageable = PageRequest.of(pagina, tamanio);

    if (soloNoLeidos) {
      return notificaciones
          .findByUsuarioIdAndLeidoEnIsNullOrderByCreadoEnDesc(usuarioId, pageable)
          .map(mapper::toResponse);
    }
    return notificaciones
        .findByUsuarioIdOrderByCreadoEnDesc(usuarioId, pageable)
        .map(mapper::toResponse);
  }

  /** Obtiene un aviso específico asegurando que pertenezca al usuario (DT-SEC-04). */
  @Transactional(readOnly = true)
  public NotificacionResponse obtener(UUID id, UUID usuarioId) {
    return notificaciones
        .findByIdAndUsuarioId(id, usuarioId)
        .map(mapper::toResponse)
        .orElseThrow(this::notificacionNoEncontrada);
  }

  /** Cuenta el número de avisos no leídos del usuario. */
  @Transactional(readOnly = true)
  public long contarNoLeidas(UUID usuarioId) {
    return notificaciones.countByUsuarioIdAndLeidoEnIsNull(usuarioId);
  }

  /** Marca un aviso como leído si pertenece al usuario (DT-SEC-04). */
  @Transactional
  public NotificacionResponse marcarLeida(UUID id, UUID usuarioId) {
    Notificacion notificacion =
        notificaciones
            .findByIdAndUsuarioId(id, usuarioId)
            .orElseThrow(this::notificacionNoEncontrada);

    notificacion.marcarLeida(clock.instant());
    return mapper.toResponse(notificaciones.save(notificacion));
  }

  /** Marca todos los avisos no leídos del usuario como leídos. */
  @Transactional
  public int marcarTodasLeidas(UUID usuarioId) {
    return notificaciones.marcarTodasLeidas(usuarioId, clock.instant());
  }

  private void validarEntidadCoherente(String entidad, UUID entidadId) {
    boolean tieneEntidad = entidad != null && !entidad.isBlank();
    boolean tieneId = entidadId != null;
    if ((tieneEntidad && !tieneId) || (!tieneEntidad && tieneId)) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "entidad_notificacion_incoherente",
          "Entidad incoherente",
          "La entidad y el identificador de la entidad deben proporcionarse juntos o ambos ser nulos.");
    }
  }

  private void validarSinDatosSensibles(String titulo, String mensaje) {
    if (titulo != null
        && (PATRON_TOKEN_O_SECRETO.matcher(titulo).find()
            || PATRON_CALIFICACION.matcher(titulo).find())) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "aviso_con_datos_sensibles",
          "Datos sensibles no permitidos",
          "Los avisos no deben incluir calificaciones, tokens ni datos sensibles (DT-SEC-12).");
    }
    if (mensaje != null
        && (PATRON_TOKEN_O_SECRETO.matcher(mensaje).find()
            || PATRON_CALIFICACION.matcher(mensaje).find())) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "aviso_con_datos_sensibles",
          "Datos sensibles no permitidos",
          "Los avisos no deben incluir calificaciones, tokens ni datos sensibles (DT-SEC-12).");
    }
  }

  private void validarPaginacion(int pagina, int tamanio) {
    if (pagina < 0 || tamanio < 1 || tamanio > 100) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "paginacion_invalida",
          "Paginación inválida",
          "La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.");
    }
  }

  private ApiException notificacionNoEncontrada() {
    return new ApiException(
        HttpStatus.NOT_FOUND,
        "notificacion_no_encontrada",
        "Aviso no encontrado",
        "No existe un aviso con el identificador proporcionado.");
  }
}
