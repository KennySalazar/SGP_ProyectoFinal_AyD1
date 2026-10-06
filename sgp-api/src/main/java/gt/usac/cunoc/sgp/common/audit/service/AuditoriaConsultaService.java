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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  public AuditoriaConsultaService(AuditoriaRepository repositorio, AuditoriaMapper mapper) {
    this.repositorio = repositorio;
    this.mapper = mapper;
  }

  public Page<AuditoriaResponse> consultar(
      UUID usuarioId,
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
    if (usuarioId != null)
      filtros =
          filtros.and(
              (raiz, consulta, criterio) -> criterio.equal(raiz.get("usuarioId"), usuarioId));
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

    return repositorio.findAll(filtros, pageable).map(mapper::toResponse);
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
    Map<String, String> anteriores = new LinkedHashMap<>();
    Map<String, String> posteriores = new LinkedHashMap<>();
    aplanar("", registro.getValoresAnteriores(), anteriores);
    aplanar("", registro.getValoresPosteriores(), posteriores);
    List<CambioAuditoria> cambios = new ArrayList<>();
    for (String campo : posteriores.keySet()) {
      if (!java.util.Objects.equals(anteriores.get(campo), posteriores.get(campo))) {
        cambios.add(new CambioAuditoria(campo, anteriores.get(campo), posteriores.get(campo)));
      }
    }
    for (String campo : anteriores.keySet()) {
      if (!posteriores.containsKey(campo)) {
        cambios.add(new CambioAuditoria(campo, anteriores.get(campo), null));
      }
    }
    AuditoriaResponse resumen = mapper.toResponse(registro);
    return new AuditoriaDetalleResponse(
        resumen.id(),
        resumen.usuarioId(),
        resumen.accion(),
        resumen.entidad(),
        resumen.entidadId(),
        resumen.procesoAutomatico(),
        resumen.creadoEn(),
        cambios);
  }

  private void aplanar(String prefijo, JsonNode nodo, Map<String, String> destino) {
    if (nodo == null || nodo.isNull()) return;
    if (nodo.isObject()) {
      nodo.fields()
          .forEachRemaining(
              campo ->
                  aplanar(
                      prefijo.isEmpty() ? campo.getKey() : prefijo + "." + campo.getKey(),
                      campo.getValue(),
                      destino));
    } else if (nodo.isArray()) {
      for (int i = 0; i < nodo.size(); i++) aplanar(prefijo + "[" + i + "]", nodo.get(i), destino);
    } else {
      destino.put(prefijo, nodo.asText());
    }
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
