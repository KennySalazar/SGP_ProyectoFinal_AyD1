package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.dto.GuardarBorradorRequest;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.revision.repository.ObservacionRevisionRepository;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PermisosCorreccionService {
  public record Permisos(boolean restringida, List<String> campos, List<String> categorias) {}

  private final RevisionRepository revisiones;
  private final ObservacionRevisionRepository observaciones;

  public PermisosCorreccionService(
      RevisionRepository revisiones, ObservacionRevisionRepository observaciones) {
    this.revisiones = revisiones;
    this.observaciones = observaciones;
  }

  public Permisos consultar(Inspeccion inspeccion) {
    if (!"ESTUDIANTE".equals(inspeccion.getRolAutorCreacion()))
      return new Permisos(false, List.of(), List.of());
    var ciclo =
        revisiones.findFirstByInspeccionIdOrderByNumeroCicloDesc(inspeccion.getId()).orElse(null);
    if (ciclo == null
        || !"RESUELTA".equals(ciclo.getEstado())
        || !"CAMBIOS_SOLICITADOS".equals(ciclo.getVeredicto()))
      return new Permisos(false, List.of(), List.of());
    var lista = observaciones.findByRevisionIdOrderByCreadoEnAscIdAsc(ciclo.getId());
    List<String> campos = new ArrayList<>();
    List<String> categorias = new ArrayList<>();
    for (var obs : lista) {
      if (obs.getElementoRef() != null)
        resolver(
            inspeccion.getDatos(),
            obs.getElementoRef().substring(1).split("/"),
            0,
            "",
            obs.getInstanciaElementoId(),
            campos);
      if (obs.getCategoriaFotografica() != null)
        categorias.add(obs.getCategoriaFotografica().name());
    }
    return new Permisos(
        true, campos.stream().distinct().toList(), categorias.stream().distinct().toList());
  }

  private void resolver(
      JsonNode nodo,
      String[] partes,
      int indice,
      String ruta,
      UUID instancia,
      List<String> salida) {
    if (nodo != null && nodo.isArray() && instancia != null) {
      for (int i = 0; i < nodo.size(); i++) {
        if (instancia.toString().equals(nodo.get(i).path("instancia_id").asText()))
          resolver(nodo.get(i), partes, indice, ruta + "/" + i, null, salida);
      }
      return;
    }
    if (indice == partes.length) {
      salida.add(ruta);
      return;
    }
    String clave = partes[indice].replace("~1", "/").replace("~0", "~");
    resolver(
        nodo == null ? null : nodo.path(clave),
        partes,
        indice + 1,
        ruta + "/" + partes[indice],
        instancia,
        salida);
  }

  public void validar(Inspeccion inspeccion, GuardarBorradorRequest request) {
    var permisos = consultar(inspeccion);
    if (!permisos.restringida() || request == null) return;
    if (request.datos() != null) validarInstancias(inspeccion.getDatos(), request.datos());
    if (request.datos() != null)
      comparar(inspeccion.getDatos(), request.datos(), "", permisos.campos());
    if (request.identificacionCapturada() != null)
      comparar(
          inspeccion.getIdentificacionCapturada(),
          request.identificacionCapturada(),
          "/identificacion",
          permisos.campos());
    if (request.fechaInspeccion() != null
        && !Objects.equals(request.fechaInspeccion(), inspeccion.getFechaInspeccion())
        && !permitido("/identificacion/fecha_inspeccion", permisos.campos()))
      throw fueraDeObservacion();
    if (request.acompanantes() != null) {
      var actuales =
          inspeccion.getAcompanantes().stream()
              .map(
                  a ->
                      List.of(
                          Objects.toString(a.getUsuarioId(), ""),
                          Objects.toString(a.getNombre(), ""),
                          Objects.toString(a.getNumeroColegiado(), "")))
              .toList();
      var nuevos =
          request.acompanantes().stream()
              .map(
                  a ->
                      List.of(
                          Objects.toString(a.usuarioId(), ""),
                          Objects.toString(a.nombre(), ""),
                          Objects.toString(a.numeroColegiado(), "")))
              .toList();
      if (!actuales.equals(nuevos)) throw fueraDeObservacion();
    }
  }

  private void validarInstancias(JsonNode antes, JsonNode despues) {
    if (antes == null || despues == null) return;
    if (antes.isObject() && despues.isObject()) {
      if (antes.has("instancia_id")
          && !Objects.equals(antes.get("instancia_id"), despues.get("instancia_id")))
        throw fueraDeObservacion();
      antes
          .fieldNames()
          .forEachRemaining(clave -> validarInstancias(antes.path(clave), despues.path(clave)));
    } else if (antes.isArray()
        && despues.isArray()
        && antes.size() > 0
        && antes.get(0).isObject()) {
      if (antes.size() != despues.size()) throw fueraDeObservacion();
      for (int i = 0; i < antes.size(); i++) validarInstancias(antes.get(i), despues.get(i));
    }
  }

  public void validarFoto(Inspeccion inspeccion, String categoria) {
    var permisos = consultar(inspeccion);
    if (permisos.restringida() && !permisos.categorias().contains(categoria))
      throw fueraDeObservacion();
  }

  private boolean permitido(String ruta, List<String> campos) {
    return campos.stream().anyMatch(c -> ruta.equals(c) || ruta.startsWith(c + "/"));
  }

  private void comparar(JsonNode antes, JsonNode despues, String ruta, List<String> campos) {
    if (permitido(ruta, campos)) return;
    if (antes == null || antes.isNull() || antes.isMissingNode()) {
      if (despues == null || despues.isNull() || despues.isMissingNode()) return;
    }
    if (Objects.equals(antes, despues)) return;
    if (antes != null
        && despues != null
        && antes.isNumber()
        && despues.isNumber()
        && antes.decimalValue().compareTo(despues.decimalValue()) == 0) return;
    if ((antes != null && antes.isObject()) || (despues != null && despues.isObject())) {
      Set<String> claves = new HashSet<>();
      if (antes != null) antes.fieldNames().forEachRemaining(claves::add);
      if (despues != null) despues.fieldNames().forEachRemaining(claves::add);
      for (String clave : claves)
        comparar(
            antes == null ? null : antes.path(clave),
            despues == null ? null : despues.path(clave),
            ruta + "/" + clave.replace("~", "~0").replace("/", "~1"),
            campos);
      return;
    }
    if (antes != null
        && despues != null
        && antes.isArray()
        && despues.isArray()
        && antes.size() == despues.size()) {
      for (int i = 0; i < antes.size(); i++)
        comparar(antes.get(i), despues.get(i), ruta + "/" + i, campos);
      return;
    }
    throw fueraDeObservacion();
  }

  private ApiException fueraDeObservacion() {
    return new ApiException(
        HttpStatus.CONFLICT,
        "campo_no_observado",
        "Correccion de inspeccion",
        "Solo se pueden modificar los campos o categorias de fotografias observados por el catedratico.");
  }
}
