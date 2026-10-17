package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.model.EntradaCalculoIc;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc.DesgloseElemento;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc.DesgloseIndicador;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc.DesgloseInstancia;
import gt.usac.cunoc.sgp.inspeccion.service.ResolverElementosIc.ElementoResuelto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CalculoIndiceCondicionService {

  private final ResolverElementosIc resolver;
  private final ConversorIndicadoresIc conversor;
  private final ConversorMedicionesIc mediciones;
  private final AgregadorCalificacionesIc agregador;
  private final EvaluadorCondicionesCriticasIc condicionesCriticas;
  private final HuellaEntradaIc huella;
  private final MotorPonderacionIc motor;

  public CalculoIndiceCondicionService(
      ResolverElementosIc resolver,
      ConversorIndicadoresIc conversor,
      ConversorMedicionesIc mediciones,
      AgregadorCalificacionesIc agregador,
      EvaluadorCondicionesCriticasIc condicionesCriticas,
      HuellaEntradaIc huella,
      MotorPonderacionIc motor) {
    this.resolver = resolver;
    this.conversor = conversor;
    this.mediciones = mediciones;
    this.agregador = agregador;
    this.condicionesCriticas = condicionesCriticas;
    this.huella = huella;
    this.motor = motor;
  }

  public ResultadoCalculoIc calcular(EntradaCalculoIc entrada) {
    validarEntrada(entrada);

    JsonNode elementos = entrada.catalogoElementos().get("elementos");

    if (elementos == null || !elementos.isArray()) {
      throw error("configuracion_ic_incompleta", "Falta el catalogo de elementos evaluables");
    }

    List<DesgloseElemento> desglose = new ArrayList<>();
    Set<String> referencias = new HashSet<>();

    for (JsonNode definicion : elementos) {
      String referencia = texto(definicion.get("elemento_ref"), "elemento_ref");

      if (!referencias.add(referencia)) {
        throw error(
            "configuracion_ic_invalida", "Elemento duplicado en el catalogo: " + referencia);
      }

      if (excluido(entrada.reglasEvaluacion(), referencia)) {
        continue;
      }

      if (cauceNoAplica(entrada.datos(), referencia)) {
        continue;
      }

      validarRutaEvaluada(entrada.datos(), JsonPointer.compile(referencia), "");

      List<ElementoResuelto> instancias = resolver.resolver(entrada.datos(), referencia);

      if (instancias.isEmpty()) {
        if (definicion.path("repetible").asBoolean(false)) {
          continue;
        }

        throw error("datos_ic_incompletos", "Falta evaluar el elemento " + referencia);
      }

      List<DesgloseInstancia> detalles = new ArrayList<>();
      List<BigDecimal> calificaciones = new ArrayList<>();

      for (ElementoResuelto instancia : instancias) {
        DesgloseInstancia detalle = evaluarInstancia(entrada, definicion, instancia);
        detalles.add(detalle);

        if (detalle.incluida()) {
          calificaciones.add(detalle.calificacion());
        }
      }

      if (calificaciones.isEmpty()) {
        continue;
      }

      BigDecimal peso = entrada.pesos().get(referencia);

      if (peso == null || peso.signum() <= 0) {
        throw error(
            "configuracion_ic_incompleta", "Falta configurar un peso positivo para " + referencia);
      }

      String reglaInstancias =
          texto(
              entrada.reglasEvaluacion().path("agregacion").get("instancias_repetibles"),
              "agregacion.instancias_repetibles");

      BigDecimal calificacion = agregador.agregar(calificaciones, reglaInstancias, referencia);

      desglose.add(
          new DesgloseElemento(
              referencia,
              peso,
              calificacion,
              calificacion.multiply(peso),
              reglaInstancias,
              detalles));
    }

    var anulaciones = condicionesCriticas.evaluar(entrada.datos(), entrada.reglasEvaluacion());

    return motor.calcular(entrada, desglose, anulaciones, huella.calcular(entrada));
  }

  private DesgloseInstancia evaluarInstancia(
      EntradaCalculoIc entrada, JsonNode definicion, ElementoResuelto instancia) {

    JsonNode datos = instancia.datos();
    String existencia = datos.path("existencia").asText("PRESENTE");

    if ("NO_APLICA".equals(existencia)) {
      return detalle(instancia, existencia, false, null, List.of());
    }

    if (!"PRESENTE".equals(existencia) && !"AUSENTE".equals(existencia)) {
      throw error(
          "datos_ic_invalidos",
          "Existencia invalida en " + instancia.rutaDatos() + ": " + existencia);
    }

    if ("AUSENTE".equals(existencia)) {
      String reglaAusencia =
          definicion.path("portante").asBoolean(false)
              ? "elementos_ausentes_portantes"
              : "elementos_ausentes_no_portantes";

      BigDecimal calificacion =
          conversor.leerCalificacion(
              entrada.reglasEvaluacion().path("agregacion").get(reglaAusencia),
              instancia.rutaDatos() + ".existencia=AUSENTE");

      return detalle(
          instancia,
          existencia,
          true,
          calificacion,
          List.of(new DesgloseIndicador("existencia", "AUSENTE", calificacion)));
    }

    JsonNode indicadores = definicion.get("indicadores");

    if (indicadores == null || !indicadores.isArray() || indicadores.isEmpty()) {
      throw error(
          "configuracion_ic_incompleta", "Faltan los indicadores de " + instancia.rutaDatos());
    }

    List<DesgloseIndicador> detalles = new ArrayList<>();
    List<BigDecimal> calificaciones = new ArrayList<>();

    for (JsonNode indicador : indicadores) {
      String nombre = texto(indicador, "indicador");
      JsonNode valor = datos.get(nombre);

      if (omitirIndicador(datos, nombre, valor)) {
        continue;
      }

      if (valor == null || valor.isNull()) {
        throw error(
            "datos_ic_incompletos", "Falta el indicador " + instancia.rutaDatos() + "/" + nombre);
      }

      if (valor.isArray()) {
        if (valor.isEmpty()) {
          throw error(
              "datos_ic_incompletos",
              "El indicador no puede estar vacio: " + instancia.rutaDatos() + "/" + nombre);
        }

        for (JsonNode item : valor) {
          agregarIndicador(entrada, instancia, nombre, item, detalles, calificaciones);
        }
      } else {
        agregarIndicador(entrada, instancia, nombre, valor, detalles, calificaciones);
      }
    }

    String regla =
        texto(
            entrada.reglasEvaluacion().path("agregacion").get("indicadores_de_un_elemento"),
            "agregacion.indicadores_de_un_elemento");

    BigDecimal calificacion = agregador.agregar(calificaciones, regla, instancia.rutaDatos());

    return detalle(instancia, existencia, true, calificacion, detalles);
  }

  private void agregarIndicador(
      EntradaCalculoIc entrada,
      ElementoResuelto instancia,
      String nombre,
      JsonNode valor,
      List<DesgloseIndicador> detalles,
      List<BigDecimal> calificaciones) {

    String referencia = instancia.rutaDatos() + "/" + nombre;
    JsonNode reglas = entrada.reglasEvaluacion();
    JsonNode conversiones = reglas.path("conversion_indicadores");
    BigDecimal calificacion;

    if ("otros".equals(nombre) || "danos".equals(nombre)) {
      calificacion = conversor.convertirSeveridad(valor.get("severidad"), reglas, referencia);
    } else if (valor.isNumber()) {
      JsonNode configuracion = conversiones.path("numericos").path(nombre);
      String campoUmbrales = "golpeado_longitud_m".equals(nombre) ? "umbrales_m" : "umbrales";

      calificacion =
          mediciones.convertir(valor, configuracion.get(campoUmbrales), reglas, referencia);
    } else if (valor.isBoolean()) {
      String familia = familiaBooleano(instancia.rutaDatos(), nombre);
      String clave = familia + "." + nombre + "=" + valor.asText();

      calificacion =
          conversor.leerCalificacion(
              conversiones.path("booleanos").get(clave), referencia + " [" + valor.asText() + "]");
    } else {
      JsonNode escala = reglas.path("escala_severidad");

      if (valor.isTextual() && escala.has(valor.textValue())) {
        calificacion = conversor.convertirSeveridad(valor, reglas, referencia);
      } else {
        String familia = familiaEnumerado(instancia.rutaDatos(), nombre);

        calificacion =
            conversor.convertirCategoria(
                valor, conversiones.path("enumerados").get(familia), referencia);
      }
    }

    detalles.add(new DesgloseIndicador(nombre, valor.toString(), calificacion));
    calificaciones.add(calificacion);
  }

  private boolean omitirIndicador(JsonNode datos, String nombre, JsonNode valor) {
    if (("otros".equals(nombre) || "danos".equals(nombre)) && (valor == null || valor.isNull())) {
      return true;
    }

    if ("estado".equals(nombre) && datos.has("existe") && !datos.path("existe").asBoolean()) {
      return true;
    }

    return datos.path("buen_estado").asBoolean(false)
        && Set.of("asentado", "fisurado", "bache").contains(nombre);
  }

  private String familiaBooleano(String ruta, String nombre) {
    if ("falta_elemento".equals(nombre) || "junta_limpia".equals(nombre)) {
      return "junta";
    }

    if ("buen_estado".equals(nombre)) {
      return "losa";
    }

    if ("existe".equals(nombre)) {
      if (ruta.startsWith("/otros/senalizacion/")) {
        return "senal";
      }

      if ("/otros/drenajes_aledanos".equals(ruta)) {
        return "drenaje_aledano";
      }
    }

    throw error(
        "configuracion_ic_invalida", "Booleano no compatible con sieca-v1: " + ruta + "/" + nombre);
  }

  private String familiaEnumerado(String ruta, String nombre) {
    if ("aguas_arriba".equals(nombre) || "aguas_abajo".equals(nombre)) {
      return "socavacion.nivel";
    }

    if ("condiciones".equals(nombre)) {
      return "condicionesCauce";
    }

    if ("estado".equals(nombre)) {
      return ruta.startsWith("/superestructura/drenaje") ? "drenaje.estado" : "estado_bueno_malo";
    }

    throw error(
        "configuracion_ic_invalida", "Falta una conversion compatible para " + ruta + "/" + nombre);
  }

  private boolean cauceNoAplica(JsonNode datos, String referencia) {
    return referencia.startsWith("/caracteristicas_cauce/")
        && datos.path("caracteristicas_cauce").path("aplica").isBoolean()
        && !datos.path("caracteristicas_cauce").path("aplica").booleanValue();
  }

  private boolean excluido(JsonNode reglas, String referencia) {
    JsonNode excluidos = reglas.get("elementos_excluidos_ic");

    if (excluidos == null) {
      return false;
    }

    if (!excluidos.isArray()) {
      throw error("configuracion_ic_invalida", "elementos_excluidos_ic debe ser una lista");
    }

    for (JsonNode elemento : excluidos) {
      if (referencia.equals(texto(elemento, "elemento excluido"))) {
        return true;
      }
    }

    return false;
  }

  private DesgloseInstancia detalle(
      ElementoResuelto instancia,
      String existencia,
      boolean incluida,
      BigDecimal calificacion,
      List<DesgloseIndicador> indicadores) {

    return new DesgloseInstancia(
        instancia.instanciaId(),
        instancia.rutaDatos(),
        existencia,
        incluida,
        calificacion,
        indicadores);
  }

  private void validarEntrada(EntradaCalculoIc entrada) {
    if (entrada == null
        || entrada.datos() == null
        || !entrada.datos().isObject()
        || entrada.esquema() == null
        || !entrada.esquema().isObject()
        || entrada.catalogoElementos() == null
        || !entrada.catalogoElementos().isObject()
        || entrada.reglasEvaluacion() == null
        || !entrada.reglasEvaluacion().isObject()
        || entrada.pesos() == null) {
      throw error(
          "entrada_ic_invalida",
          "Se requieren datos, esquema, catalogo, reglas y pesos para calcular el IC");
    }

    String formulario = texto(entrada.catalogoElementos().get("formulario"), "catalogo.formulario");

    if (!formulario.equals(entrada.codigoFormulario())) {
      throw error("version_ic_incongruente", "El catalogo no corresponde al formulario indicado");
    }

    String algoritmo =
        texto(entrada.reglasEvaluacion().get("version_algoritmo"), "version_algoritmo");

    if (!algoritmo.equals(entrada.versionAlgoritmo())) {
      throw error("version_ic_incongruente", "Las reglas no corresponden al algoritmo indicado");
    }
  }

  private String texto(JsonNode valor, String referencia) {
    if (valor == null || !valor.isTextual() || valor.textValue().isBlank()) {
      throw error("configuracion_ic_incompleta", "Falta configurar " + referencia);
    }

    return valor.textValue();
  }

  private ApiException error(String codigo, String detalle) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        codigo,
        "Calculo del indice de condicion invalido",
        detalle);
  }

  private void validarRutaEvaluada(JsonNode nodo, JsonPointer pendiente, String ruta) {
    if (nodo == null || nodo.isNull() || nodo.isMissingNode()) {
      throw error("datos_ic_incompletos", "Falta evaluar la ruta " + ruta);
    }

    if (nodo.isArray()) {
      for (int indice = 0; indice < nodo.size(); indice++) {
        validarRutaEvaluada(nodo.get(indice), pendiente, ruta + "/" + indice);
      }

      return;
    }

    if (pendiente.matches()) {
      if (!nodo.isObject()) {
        throw error("datos_ic_invalidos", "El elemento debe ser un objeto: " + ruta);
      }

      return;
    }

    if (!nodo.isObject()) {
      throw error("datos_ic_invalidos", "La ruta evaluada no es un objeto: " + ruta);
    }

    String propiedad = pendiente.getMatchingProperty();

    validarRutaEvaluada(
        nodo.get(propiedad),
        pendiente.tail(),
        ruta + "/" + propiedad.replace("~", "~0").replace("/", "~1"));
  }
}
