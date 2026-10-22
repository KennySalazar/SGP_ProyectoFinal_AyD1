package gt.usac.cunoc.sgp.revision.service;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.inspeccion.dto.IndiceCondicionPreliminarResponse;
import gt.usac.cunoc.sgp.revision.dto.RevisionDetalleResponse;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('CATEDRATICO', 'ESTUDIANTE')")
public class RevisionDetalleService {
  private static final Logger LOGGER = LoggerFactory.getLogger(RevisionDetalleService.class);
  private final RevisionRepository revisiones;
  private final VersionFormularioRepository versiones;
  private final RevisionIndicePreliminarService indicePreliminar;

  public RevisionDetalleService(
      RevisionRepository revisiones,
      VersionFormularioRepository versiones,
      RevisionIndicePreliminarService indicePreliminar) {
    this.revisiones = revisiones;
    this.versiones = versiones;
    this.indicePreliminar = indicePreliminar;
  }

  @Transactional(readOnly = true)
  public RevisionDetalleResponse obtener(UUID revisionId, UUID actor) {
    if (actor == null)
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida.");
    var ciclo = revisiones.findById(revisionId).orElseThrow(this::noEncontrada);
    if ((!actor.equals(ciclo.getRevisorId()) && !actor.equals(ciclo.getInspeccion().getAutorId()))
        || ciclo.getInspeccion().getEliminadoEn() != null) throw noEncontrada();
    JsonNode snapshot = ciclo.getContenidoEnviado();
    UUID inspeccionId = identificador(snapshot, "inspeccionId");
    if (!inspeccionId.equals(ciclo.getInspeccion().getId())) throw contenidoInvalido();
    UUID versionId = identificador(snapshot, "versionFormularioId");
    UUID autorId = identificador(snapshot, "autorId");
    UUID puenteId = identificador(snapshot, "puenteId");
    LocalDate fecha;
    try {
      fecha = LocalDate.parse(snapshot.path("fechaInspeccion").asText());
    } catch (DateTimeParseException e) {
      throw contenidoInvalido();
    }
    if (!snapshot.path("datos").isObject()
        || !(snapshot.path("identificacionCapturada").isObject()
            || snapshot.path("identificacionCapturada").isNull())) throw contenidoInvalido();
    var version = versiones.findById(versionId).orElseThrow(this::contenidoInvalido);
    if (version.getEsquema() == null
        || !version.getEsquema().isObject()
        || version.getEsquema().isEmpty()) throw contenidoInvalido();

    IndiceCondicionPreliminarResponse preliminar = null;
    try {
      preliminar = indicePreliminar.calcular(versionId, snapshot.path("datos").deepCopy());
    } catch (RuntimeException e) {
      LOGGER.warn(
          "IC preliminar de revision no disponible: tipo={}, motivo={}",
          e.getClass().getSimpleName(),
          e instanceof ApiException api ? api.getCode() : "fallo_inesperado_calculo_ic");
    }
    return new RevisionDetalleResponse(
        ciclo.getId(),
        inspeccionId,
        ciclo.getEstado(),
        ciclo.getRevisorId(),
        ciclo.getNumeroCiclo(),
        ciclo.getEnviadaEn(),
        autorId,
        puenteId,
        fecha,
        versionId,
        version.getCodigo(),
        version.getEsquema().deepCopy(),
        snapshot.path("datos").deepCopy(),
        snapshot.path("identificacionCapturada").deepCopy(),
        preliminar,
        "/api/v1/inspecciones/" + inspeccionId + "/fotografias",
        false,
        "/api/v1/revisiones/" + revisionId + "/observaciones");
  }

  private UUID identificador(JsonNode snapshot, String campo) {
    try {
      return UUID.fromString(snapshot.path(campo).asText());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw contenidoInvalido();
    }
  }

  private ApiException contenidoInvalido() {
    return new ApiException(
        HttpStatus.CONFLICT,
        "revision_contenido_invalido",
        "Contenido enviado no disponible",
        "El ciclo no contiene los datos o la versión histórica requeridos.");
  }

  private ApiException noEncontrada() {
    return new ApiException(
        HttpStatus.NOT_FOUND,
        "revision_no_encontrada",
        "Revisión no encontrada",
        "No existe la revisión solicitada o no corresponde al catedrático autenticado.");
  }
}
