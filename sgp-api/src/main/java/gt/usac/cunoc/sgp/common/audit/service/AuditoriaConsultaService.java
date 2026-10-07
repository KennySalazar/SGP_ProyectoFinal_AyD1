package gt.usac.cunoc.sgp.common.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.audit.dto.AuditoriaDetalleResponse;
import gt.usac.cunoc.sgp.common.audit.dto.AuditoriaResponse;
import gt.usac.cunoc.sgp.common.audit.dto.CambioAuditoria;
import gt.usac.cunoc.sgp.common.audit.entity.Auditoria;
import gt.usac.cunoc.sgp.common.audit.mapper.AuditoriaMapper;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.repository.AuditoriaRepository;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.EmailNormalizer;
import gt.usac.cunoc.sgp.usuario.entity.UserAccount;
import gt.usac.cunoc.sgp.usuario.repository.UserAccountRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditoriaConsultaService {

  private final AuditoriaRepository repositorio;
  private final AuditoriaMapper mapper;
  private final UserAccountRepository usuarios;

  public AuditoriaConsultaService(
      AuditoriaRepository repositorio, AuditoriaMapper mapper, UserAccountRepository usuarios) {
    this.repositorio = repositorio;
    this.mapper = mapper;
    this.usuarios = usuarios;
  }

  public Page<AuditoriaResponse> consultar(
      UUID usuarioId,
      String entidad,
      String accion,
      Instant desde,
      Instant hasta,
      int pagina,
      int tamanio) {
    return consultar(usuarioId, null, entidad, accion, desde, hasta, pagina, tamanio);
  }

  public Page<AuditoriaResponse> consultar(
      UUID usuarioId,
      String usuarioEmail,
      String entidad,
      String accion,
      Instant desde,
      Instant hasta,
      int pagina,
      int tamanio) {

    if (pagina < 0 || tamanio < 1 || tamanio > 100) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "paginacion_invalida",
          "Datos de auditoria invalidos",
          "La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.");
    }

    AccionAuditoria filtroAccion = convertirAccion(accion);
    if (desde != null && hasta != null && desde.isAfter(hasta)) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "rango_invalido",
          "Datos de auditoria invalidos",
          "La fecha inicial debe ser anterior o igual a la fecha final.");
    }

    var pageable =
        PageRequest.of(
            pagina, tamanio, Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id")));

    Specification<Auditoria> filtros = Specification.where(null);
    UUID actor = usuarioId;
    if (usuarioEmail != null && !usuarioEmail.isBlank()) {
      var usuario = usuarios.findByEmail(EmailNormalizer.normalize(usuarioEmail));
      if (usuario.isEmpty() || (actor != null && !actor.equals(usuario.get().getId()))) {
        return Page.empty(pageable);
      }
      actor = usuario.get().getId();
    }
    UUID filtroUsuario = actor;
    if (filtroUsuario != null)
      filtros =
          filtros.and(
              (raiz, consulta, criterio) -> criterio.equal(raiz.get("usuarioId"), filtroUsuario));
    if (entidad != null && !entidad.isBlank())
      filtros =
          filtros.and((raiz, consulta, criterio) -> criterio.equal(raiz.get("entidad"), entidad));
    if (filtroAccion != null)
      filtros =
          filtros.and(
              (raiz, consulta, criterio) -> criterio.equal(raiz.get("accion"), filtroAccion));
    if (desde != null)
      filtros =
          filtros.and(
              (raiz, consulta, criterio) ->
                  criterio.greaterThanOrEqualTo(raiz.get("creadoEn"), desde));
    if (hasta != null)
      filtros =
          filtros.and(
              (raiz, consulta, criterio) ->
                  criterio.lessThanOrEqualTo(raiz.get("creadoEn"), hasta));

    var registros = repositorio.findAll(filtros, pageable);
    var ids =
        registros.stream()
            .map(Auditoria::getUsuarioId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<UUID, String> correos = new LinkedHashMap<>();
    if (!ids.isEmpty()) {
      usuarios
          .findAllById(ids)
          .forEach(usuario -> correos.put(usuario.getId(), usuario.getEmail()));
    }
    return registros.map(
        registro -> mapper.toResponse(registro, correos.get(registro.getUsuarioId())));
  }

  public AuditoriaDetalleResponse detalle(UUID id) {
    Auditoria registro =
        repositorio
            .findById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "auditoria_no_encontrada",
                        "Registro no encontrado",
                        "El registro de auditoría no existe."));
    Map<String, JsonNode> anteriores = new LinkedHashMap<>();
    Map<String, JsonNode> posteriores = new LinkedHashMap<>();
    aplanar("", registro.getValoresAnteriores(), anteriores);
    aplanar("", registro.getValoresPosteriores(), posteriores);
    List<CambioAuditoria> cambios = new ArrayList<>();
    for (String campo : posteriores.keySet()) {
      if (!anteriores.containsKey(campo)
          || !Objects.equals(anteriores.get(campo), posteriores.get(campo))) {
        cambios.add(
            new CambioAuditoria(
                campo, textoLegible(anteriores.get(campo)), textoLegible(posteriores.get(campo))));
      }
    }
    for (String campo : anteriores.keySet()) {
      if (!posteriores.containsKey(campo)) {
        cambios.add(new CambioAuditoria(campo, textoLegible(anteriores.get(campo)), null));
      }
    }
    String correo =
        registro.getUsuarioId() == null
            ? null
            : usuarios.findById(registro.getUsuarioId()).map(UserAccount::getEmail).orElse(null);
    AuditoriaResponse resumen = mapper.toResponse(registro, correo);
    return new AuditoriaDetalleResponse(
        resumen.id(),
        resumen.usuarioId(),
        resumen.accion(),
        resumen.entidad(),
        resumen.entidadId(),
        resumen.procesoAutomatico(),
        resumen.creadoEn(),
        cambios,
        correo);
  }

  private void aplanar(String prefijo, JsonNode nodo, Map<String, JsonNode> destino) {
    if (nodo == null) return;
    if (nodo.isObject() && !nodo.isEmpty()) {
      nodo.fields()
          .forEachRemaining(
              campo ->
                  aplanar(
                      prefijo.isEmpty() ? campo.getKey() : prefijo + "." + campo.getKey(),
                      campo.getValue(),
                      destino));
    } else if (nodo.isArray() && !nodo.isEmpty()) {
      for (int i = 0; i < nodo.size(); i++) aplanar(prefijo + "[" + i + "]", nodo.get(i), destino);
    } else {
      destino.put(prefijo.isEmpty() ? "valor" : prefijo, nodo);
    }
  }

  private String textoLegible(JsonNode nodo) {
    if (nodo == null || nodo.isNull()) return null;
    if (nodo.isBoolean()) return nodo.asBoolean() ? "Sí" : "No";
    if (nodo.isArray()) return "Lista vacía";
    if (nodo.isObject()) return "Sin campos";
    return nodo.asText();
  }

  private AccionAuditoria convertirAccion(String accion) {
    if (accion == null) {
      return null;
    }
    try {
      return AccionAuditoria.valueOf(accion);
    } catch (IllegalArgumentException excepcion) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "accion_invalida",
          "Datos de auditoria invalidos",
          "La acción debe ser CREAR, MODIFICAR, CAMBIAR_ESTADO o MODERAR.");
    }
  }
}
