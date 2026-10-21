package gt.usac.cunoc.sgp.foro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.foro.dto.AnclaResponse;
import gt.usac.cunoc.sgp.foro.dto.HiloInspeccionResponse;
import gt.usac.cunoc.sgp.foro.entity.ForoHilo;
import gt.usac.cunoc.sgp.foro.model.CategoriaHilo;
import gt.usac.cunoc.sgp.foro.repository.ForoComentarioRepository;
import gt.usac.cunoc.sgp.foro.repository.ForoHiloRepository;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoInspeccion;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hilo de una inspección publicada (HU-064). Solo una inspección PUBLICADA tiene hilo (RN-FOR-03,
 * RN-INS-05). Se abre al publicarse, después de confirmarse la transacción, y también la primera
 * vez que un usuario autenticado abre el foro de una inspección publicada antes de esta función.
 * Leerlo es público y nunca lo crea.
 *
 * <p>El hilo nace sin comentarios, con el autor de la inspección y la categoría Discusión técnica.
 * El índice único por inspección y la inserción que ignora el conflicto hacen que el evento y la
 * creación perezosa puedan coincidir, o que el evento llegue dos veces, sin duplicar el hilo.
 */
@Service
public class ForoInspeccionService {

  /** Marca la apertura automática en la bitácora (RN-USR-09). */
  static final String PROCESO = "abrir-hilo-inspeccion";

  private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  /** El hilo y si esta llamada fue la que lo creó. */
  public record Apertura(HiloInspeccionResponse hilo, boolean creado) {}

  private final ForoHiloRepository hilos;
  private final ForoComentarioRepository comentarios;
  private final InspeccionRepository inspecciones;
  private final ForoService foro;
  private final AuditoriaWriter auditoria;
  private final ObjectMapper json;
  private final Clock clock;

  public ForoInspeccionService(
      ForoHiloRepository hilos,
      ForoComentarioRepository comentarios,
      InspeccionRepository inspecciones,
      ForoService foro,
      AuditoriaWriter auditoria,
      ObjectMapper json,
      Clock clock) {
    this.hilos = hilos;
    this.comentarios = comentarios;
    this.inspecciones = inspecciones;
    this.foro = foro;
    this.auditoria = auditoria;
    this.json = json;
    this.clock = clock;
  }

  /**
   * El hilo de una inspección publicada con el resumen de sus comentarios anclados. 404 si la
   * inspección no existe, no está publicada o todavía no tiene hilo; nunca lo crea.
   */
  @Transactional(readOnly = true)
  public HiloInspeccionResponse obtener(UUID inspeccionId) {
    publicada(inspeccionId);
    ForoHilo hilo =
        hilos
            .findByInspeccionId(inspeccionId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "hilo_no_encontrado",
                        "Hilo no encontrado",
                        "La inspección todavía no tiene hilo."));
    return respuesta(hilo);
  }

  /**
   * Devuelve el hilo de una inspección publicada y lo crea si aún no existe. Es idempotente:
   * llamarlo de nuevo, o a la vez, devuelve el mismo hilo.
   */
  @Transactional
  public Apertura abrir(UUID inspeccionId, UUID usuarioId) {
    Inspeccion inspeccion = publicada(inspeccionId);
    boolean creado = asegurarHilo(inspeccion, usuarioId, null);
    ForoHilo hilo = hilos.findByInspeccionId(inspeccionId).orElseThrow();
    return new Apertura(respuesta(hilo), creado);
  }

  /**
   * Abre el hilo cuando la publicación ya se confirmó. Corre en su propia transacción: un fallo
   * aquí nunca revierte la publicación (el oyente lo atrapa y lo registra).
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void abrirTrasPublicacion(UUID inspeccionId) {
    asegurarHilo(publicada(inspeccionId), null, PROCESO);
  }

  private boolean asegurarHilo(Inspeccion inspeccion, UUID actor, String proceso) {
    UUID inspeccionId = inspeccion.getId();
    if (hilos.findByInspeccionId(inspeccionId).isPresent()) return false;
    UUID id = UuidV7Generator.generate();
    String titulo = "Inspección del " + FECHA.format(inspeccion.getFechaInspeccion());
    Instant ahora = clock.instant();
    int insertadas =
        hilos.abrirHiloDeInspeccion(
            id,
            inspeccion.getPuente().getId(),
            inspeccionId,
            inspeccion.getAutorId(),
            CategoriaHilo.DISCUSION_TECNICA.name(),
            titulo,
            ahora);
    if (insertadas == 0) return false;
    ObjectNode despues = json.createObjectNode();
    despues.put("puenteId", inspeccion.getPuente().getId().toString());
    despues.put("inspeccionId", inspeccionId.toString());
    despues.put("categoria", CategoriaHilo.DISCUSION_TECNICA.name());
    despues.put("titulo", titulo);
    auditoria.registrarEnTransaccion(
        actor, AccionAuditoria.CREAR, "foro_hilo", id, null, despues, proceso);
    return true;
  }

  private HiloInspeccionResponse respuesta(ForoHilo hilo) {
    List<AnclaResponse> anclas =
        comentarios.contarPorAncla(hilo.getId()).stream()
            .map(
                a ->
                    new AnclaResponse(a.getElementoRef(), a.getInstanciaElementoId(), a.getTotal()))
            .toList();
    return new HiloInspeccionResponse(foro.obtenerHilo(hilo.getId()), anclas);
  }

  /** La inspección publicada de un puente activo; cualquier otra cosa es un 404. */
  private Inspeccion publicada(UUID inspeccionId) {
    return inspecciones
        .findByIdAndEliminadoEnIsNull(inspeccionId)
        .filter(i -> EstadoInspeccion.PUBLICADA.name().equals(i.getEstado()))
        .filter(i -> i.getPuente().isActivo())
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "inspeccion_no_encontrada",
                    "Inspección no encontrada",
                    "La inspección no existe o no está publicada."));
  }
}
