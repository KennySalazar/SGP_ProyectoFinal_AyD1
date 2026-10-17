package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionResumenResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.mapper.InspeccionMapper;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoInspeccion;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corrección de una inspección publicada mediante una nueva inspección relacionada, para conservar
 * intacta la evidencia original (RN-INS-07, RN-INS-08).
 */
@Service
public class CorreccionInspeccionService {

  private final InspeccionRepository inspecciones;
  private final UserAccountRepository usuarios;
  private final AuditoriaWriter auditoria;
  private final InspeccionMapper mapper;
  private final ObjectMapper json;
  private final Clock clock;

  public CorreccionInspeccionService(
      InspeccionRepository inspecciones,
      UserAccountRepository usuarios,
      AuditoriaWriter auditoria,
      InspeccionMapper mapper,
      ObjectMapper json,
      Clock clock) {
    this.inspecciones = inspecciones;
    this.usuarios = usuarios;
    this.auditoria = auditoria;
    this.mapper = mapper;
    this.json = json;
    this.clock = clock;
  }

  /**
   * Crea una corrección en BORRADOR de una inspección PUBLICADA. Solo el autor de la original o un
   * Catedrático pueden iniciarla; la corrección queda a nombre de quien la crea (RN-INS-02).
   */
  @Transactional
  public InspeccionResumenResponse crear(
      UUID originalId, String motivo, UUID usuarioId, RoleName rol) {
    if (motivo == null || motivo.isBlank()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "motivo_requerido",
          "Motivo requerido",
          "Una correccion exige indicar el motivo por el que se corrige la inspeccion.");
    }

    Inspeccion original =
        inspecciones
            .findActivaByIdForUpdate(originalId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "inspeccion_no_encontrada",
                        "Inspeccion no encontrada",
                        "No existe la inspeccion " + originalId));

    if (!original.getAutorId().equals(usuarioId) && rol != RoleName.CATEDRATICO) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "correccion_no_autorizada",
          "Acceso denegado",
          "Solo el autor de la inspeccion o un Catedratico pueden corregirla.");
    }
    if (original.estadoActual() != EstadoInspeccion.PUBLICADA) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "inspeccion_no_publicada",
          "Correccion no aplicable",
          original.estadoActual() == EstadoInspeccion.BORRADOR
              ? "Un borrador no se corrige: editelo directamente."
              : "Solo se pueden corregir inspecciones publicadas.");
    }
    if (inspecciones.findCorreccionPublicadaId(originalId).isPresent()) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "inspeccion_superada",
          "Inspeccion superada",
          "La inspeccion ya tiene una correccion publicada; corrija esa correccion.");
    }

    UserAccount autor =
        usuarios
            .findById(usuarioId)
            .orElseThrow(() -> new IllegalStateException("No existe el usuario autenticado"));
    Inspeccion correccion =
        inspecciones.saveAndFlush(
            Inspeccion.correccionDe(
                original,
                usuarioId,
                rol.name(),
                autor.getEmail(),
                motivo.strip(),
                clock.instant()));

    ObjectNode registro =
        json.createObjectNode()
            .put("id", correccion.getId().toString())
            .put("tipo", correccion.getTipo())
            .put("estado", correccion.getEstado())
            .put("supersedeAId", original.getId().toString())
            .put("motivoCorreccion", correccion.getMotivoCorreccion());
    auditoria.registrarEnTransaccion(
        usuarioId, AccionAuditoria.CREAR, "inspeccion", correccion.getId(), null, registro, null);

    return mapper.toResumenResponse(correccion);
  }
}
