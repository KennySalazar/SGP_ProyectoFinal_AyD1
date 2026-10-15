package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.ConfiguracionIcElementoRepository;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.inspeccion.model.EntradaCalculoIc;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreparadorEntradaIcService {

  private final VersionFormularioRepository versiones;
  private final ConfiguracionIcElementoRepository configuraciones;

  public PreparadorEntradaIcService(
      VersionFormularioRepository versiones, ConfiguracionIcElementoRepository configuraciones) {
    this.versiones = versiones;
    this.configuraciones = configuraciones;
  }

  @Transactional(readOnly = true)
  public EntradaCalculoIc preparar(UUID versionFormularioId, JsonNode datos) {
    if (versionFormularioId == null || datos == null || !datos.isObject()) {
      throw error(
          "entrada_ic_invalida",
          "Se requieren la version del formulario y los datos de la inspeccion.");
    }

    var version =
        versiones
            .findById(versionFormularioId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "version_no_encontrada",
                        "Version no encontrada",
                        "No existe la version " + versionFormularioId));

    if (version.getEstado() != EstadoVersionFormulario.PUBLICADA) {
      throw error(
          "version_ic_no_publicada", "El calculo requiere una version de formulario publicada.");
    }

    JsonNode catalogo = version.getReglasEvaluacionIc();

    if (catalogo == null || !catalogo.isObject()) {
      throw error("configuracion_ic_incompleta", "Falta el catalogo de la version.");
    }

    JsonNode elementos = catalogo.get("elementos");
    JsonNode reglas = catalogo.get("reglas_evaluacion_ic");

    if (elementos == null || !elementos.isArray() || elementos.isEmpty()) {
      throw error("configuracion_ic_incompleta", "Faltan los elementos del catalogo.");
    }

    if (reglas == null || !reglas.isObject()) {
      throw error("configuracion_ic_incompleta", "Faltan las reglas de evaluacion.");
    }

    String formulario = texto(catalogo.get("formulario"), "catalogo.formulario");
    String algoritmo = texto(reglas.get("version_algoritmo"), "version_algoritmo");

    if (!algoritmo.equals(version.getVersionAlgoritmoIc())) {
      throw error(
          "version_ic_incongruente",
          "El algoritmo registrado en la version no coincide con sus reglas de evaluacion.");
    }

    JsonNode esquema = version.getEsquema();

    if (esquema == null || !esquema.isObject()) {
      throw error("configuracion_ic_incompleta", "Falta el esquema del formulario.");
    }

    Map<String, BigDecimal> pesos = new TreeMap<>();

    for (var configuracion : configuraciones.findAllByVersionFormularioId(versionFormularioId)) {
      String referencia = configuracion.getElementoRef();
      BigDecimal peso = configuracion.getPeso();

      if (referencia == null || referencia.isBlank() || peso == null || peso.signum() <= 0) {
        throw error(
            "configuracion_ic_incompleta",
            "Falta una referencia o un peso positivo en la configuracion de " + referencia);
      }

      if (pesos.putIfAbsent(referencia, peso) != null) {
        throw error("configuracion_ic_invalida", "Peso duplicado para " + referencia);
      }
    }

    if (pesos.isEmpty()) {
      throw error("configuracion_ic_incompleta", "La version no tiene pesos configurados.");
    }

    return new EntradaCalculoIc(
        versionFormularioId,
        formulario,
        algoritmo,
        datos.deepCopy(),
        esquema.deepCopy(),
        catalogo.deepCopy(),
        reglas.deepCopy(),
        Map.copyOf(pesos));
  }

  private String texto(JsonNode valor, String campo) {
    if (valor == null || !valor.isTextual() || valor.asText().isBlank()) {
      throw error("configuracion_ic_incompleta", "Falta configurar " + campo);
    }
    return valor.asText();
  }

  private ApiException error(String codigo, String detalle) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        codigo,
        "Configuracion del indice de condicion invalida",
        detalle);
  }
}
