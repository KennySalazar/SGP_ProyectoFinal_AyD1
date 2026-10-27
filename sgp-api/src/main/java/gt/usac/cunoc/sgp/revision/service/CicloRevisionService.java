package gt.usac.cunoc.sgp.revision.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.notificacion.service.NotificacionService;
import gt.usac.cunoc.sgp.puente.repository.AsignacionPuenteRepository;
import gt.usac.cunoc.sgp.revision.entity.Revision;
import gt.usac.cunoc.sgp.revision.model.EstadoObservacion;
import gt.usac.cunoc.sgp.revision.repository.ObservacionRevisionRepository;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Crea el snapshot y el ciclo que nacen en cada envío de una inspección estudiantil. */
@Service
public class CicloRevisionService {

  private static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");

  private final RevisionRepository revisiones;
  private final ObservacionRevisionRepository observaciones;
  private final AsignacionPuenteRepository asignaciones;
  private final NotificacionService notificaciones;
  private final ObjectMapper json;
  private final Clock clock;

  public CicloRevisionService(
      RevisionRepository revisiones,
      ObservacionRevisionRepository observaciones,
      AsignacionPuenteRepository asignaciones,
      NotificacionService notificaciones,
      ObjectMapper json,
      Clock clock) {
    this.revisiones = revisiones;
    this.observaciones = observaciones;
    this.asignaciones = asignaciones;
    this.notificaciones = notificaciones;
    this.json = json;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Revision crearParaEnvio(Inspeccion inspeccion) {
    var anterior =
        revisiones.findFirstByInspeccionIdOrderByNumeroCicloDesc(inspeccion.getId()).orElse(null);
    if (anterior != null) {
      if (!"RESUELTA".equals(anterior.getEstado())
          || !"CAMBIOS_SOLICITADOS".equals(anterior.getVeredicto()))
        throw new ApiException(
            HttpStatus.CONFLICT,
            "ciclo_no_reenviable",
            "Reenvío no permitido",
            "El último ciclo debe estar resuelto con cambios solicitados.");
      if (observaciones.countByRevisionIdAndEstado(anterior.getId(), EstadoObservacion.PENDIENTE)
          > 0)
        throw new ApiException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "observaciones_pendientes",
            "Observaciones pendientes",
            "Debe resolver todas las observaciones del último ciclo antes de reenviar.");
    }
    if (inspeccion.getAsignacionPuenteId() == null) {
      if (anterior != null)
        throw new ApiException(
            HttpStatus.CONFLICT,
            "revision_sin_asignacion",
            "Asignación requerida",
            "El ciclo no cuenta con una asignación académica para reenviar.");
      return null;
    }

    LocalDate hoy = LocalDate.now(clock.withZone(ZONA_GUATEMALA));
    var asignacion =
        asignaciones
            .findVigentePorId(inspeccion.getAsignacionPuenteId(), inspeccion.getAutorId(), hoy)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.FORBIDDEN,
                        "asignacion_no_vigente",
                        "Asignación no vigente",
                        "La asignación del puente fue revocada, la inscripción no está activa o el curso finalizó."));

    Instant ahora = clock.instant();
    ObjectNode contenido = json.createObjectNode();
    contenido.put("inspeccionId", inspeccion.getId().toString());
    contenido.put("puenteId", inspeccion.getPuente().getId().toString());
    contenido.put("versionFormularioId", inspeccion.getVersionFormulario().getId().toString());
    contenido.put("fechaInspeccion", inspeccion.getFechaInspeccion().toString());
    contenido.put("autorId", inspeccion.getAutorId().toString());
    contenido.set(
        "identificacionCapturada",
        inspeccion.getIdentificacionCapturada() == null
            ? json.nullNode()
            : inspeccion.getIdentificacionCapturada().deepCopy());
    contenido.set(
        "datos",
        inspeccion.getDatos() == null ? json.createObjectNode() : inspeccion.getDatos().deepCopy());

    Revision revision =
        revisiones.save(
            new Revision(
                UuidV7Generator.generate(),
                inspeccion,
                revisiones.ultimoCiclo(inspeccion.getId()) + 1,
                anterior == null ? asignacion.getCatedraticoId() : anterior.getRevisorId(),
                ahora,
                inspeccion.getVersion() == null ? 0 : inspeccion.getVersion(),
                contenido,
                hash(contenido)));

    notificaciones.registrar(
        revision.getRevisorId(),
        "INSPECCION_ENVIADA",
        "Inspección enviada para revisión",
        "Un estudiante envió la inspección del puente " + inspeccion.getPuente().getCodigo() + ".",
        anterior == null ? "inspeccion" : "revision",
        anterior == null ? inspeccion.getId() : revision.getId(),
        "revision-enviada:" + revision.getId());
    return revision;
  }

  private String hash(ObjectNode contenido) {
    try {
      byte[] bytes = json.writeValueAsString(contenido).getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException | JsonProcessingException ex) {
      throw new IllegalStateException(
          "No fue posible calcular la huella del contenido enviado", ex);
    }
  }
}
