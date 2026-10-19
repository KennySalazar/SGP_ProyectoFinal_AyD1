package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.databind.JsonNode;
import gt.usac.cunoc.sgp.inspeccion.dto.ProgresoBorradorResponse;
import gt.usac.cunoc.sgp.inspeccion.dto.ProgresoSeccionResponse;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.entity.InspeccionDatoTecnico;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CalculadorProgresoBorrador {

  public ProgresoBorradorResponse calcularProgreso(Inspeccion inspeccion) {
    if (inspeccion == null) {
      return new ProgresoBorradorResponse(0, 0, 0.0, List.of());
    }

    JsonNode datos = inspeccion.getDatos();
    JsonNode idCap = inspeccion.getIdentificacionCapturada();
    Puente puente = inspeccion.getPuente();
    InspeccionDatoTecnico tecnico = inspeccion.getDatoTecnico();

    List<ProgresoSeccionResponse> secciones = new ArrayList<>(8);

    secciones.add(calcularIdentificacion(inspeccion, datos, idCap, puente, tecnico));

    secciones.add(calcularDatosGenerales(datos, tecnico));

    secciones.add(calcularTramos(datos));

    secciones.add(calcularSubestructura(datos));

    secciones.add(calcularSuperestructura(datos));

    secciones.add(calcularElementosNoEstructurales(datos));

    secciones.add(calcularCauce(datos));

    secciones.add(calcularOtros(datos, tecnico));

    int totalRequeridos = 0;
    int totalCompletados = 0;
    for (ProgresoSeccionResponse s : secciones) {
      totalRequeridos += s.camposRequeridos();
      totalCompletados += s.camposCompletados();
    }

    double porcentajeTotal =
        totalRequeridos > 0
            ? BigDecimal.valueOf((totalCompletados * 100.0) / totalRequeridos)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue()
            : 100.0;

    return new ProgresoBorradorResponse(
        totalRequeridos, totalCompletados, porcentajeTotal, secciones);
  }

  private ProgresoSeccionResponse calcularIdentificacion(
      Inspeccion inspeccion,
      JsonNode datos,
      JsonNode idCap,
      Puente puente,
      InspeccionDatoTecnico tecnico) {
    int total = 12;
    int completados = 0;

    if (tieneValor(datos, "/identificacion/nombre_puente")
        || tieneValor(idCap, "/nombre_puente")
        || (puente != null && puente.getNombre() != null)) completados++;

    if (tieneValor(datos, "/identificacion/codigo")
        || tieneValor(idCap, "/codigo")
        || (puente != null && puente.getCodigo() != null)) completados++;

    if (tieneValor(datos, "/identificacion/ruta")
        || tieneValor(idCap, "/ruta")
        || (puente != null && puente.getRuta() != null)) completados++;

    if (tieneValor(datos, "/identificacion/departamento")
        || tieneValor(idCap, "/departamento")
        || (puente != null
            && puente.getMunicipio() != null
            && puente.getMunicipio().getDepartamento() != null)) completados++;

    if (tieneValor(datos, "/identificacion/municipio")
        || tieneValor(idCap, "/municipio")
        || (puente != null
            && puente.getMunicipio() != null
            && puente.getMunicipio().getNombre() != null)) completados++;

    if (tieneValor(datos, "/identificacion/ruta_pavimentada")
        || (tecnico != null && tecnico.getRutaPavimentada() != null)) completados++;

    if (tieneValor(datos, "/identificacion/alineamiento_horizontal")
        || (tecnico != null && tecnico.getAlineamientoHorizontal() != null)) completados++;

    if (tieneValor(datos, "/identificacion/esviaje")
        || (tecnico != null && tecnico.getEsviaje() != null)) completados++;

    if (tieneValor(datos, "/identificacion/coordenadas_utm/norte")
        || tieneValor(idCap, "/coordenadas_utm/norte")
        || inspeccion.getUbicacionInicio() != null) completados++;

    if (tieneValor(datos, "/identificacion/coordenadas_utm/este")
        || tieneValor(idCap, "/coordenadas_utm/este")
        || inspeccion.getUbicacionInicio() != null) completados++;

    if (tieneValor(datos, "/identificacion/nombre_inspector")
        || (inspeccion.getNombreAutorCapturado() != null
            && !inspeccion.getNombreAutorCapturado().isBlank())) completados++;

    if (tieneValor(datos, "/identificacion/fecha_inspeccion")
        || inspeccion.getFechaInspeccion() != null) completados++;

    return crearSeccion("identificacion", "1. Identificación del puente", total, completados);
  }

  private ProgresoSeccionResponse calcularDatosGenerales(
      JsonNode datos, InspeccionDatoTecnico tecnico) {
    int total = 11;
    int completados = 0;

    if (tieneValor(datos, "/datos_generales/longitud_m")
        || (tecnico != null && tecnico.getLongitudM() != null)) completados++;

    if (tieneValor(datos, "/datos_generales/numero_tramos")
        || (tecnico != null && tecnico.getNumeroTramos() != null)) completados++;

    if (tieneValor(datos, "/datos_generales/ancho_rodadura_m")
        || (tecnico != null && tecnico.getAnchoRodaduraM() != null)) completados++;

    if (tieneValor(datos, "/datos_generales/ancho_acera_derecha_m")
        || (tecnico != null && tecnico.getAnchoAceraDerechaM() != null)) completados++;

    if (tieneValor(datos, "/datos_generales/ancho_acera_izquierda_m")
        || (tecnico != null && tecnico.getAnchoAceraIzquierdaM() != null)) completados++;

    if (tieneValor(datos, "/datos_generales/tipologia_puente")
        || (tecnico != null && tecnico.getTipologiaPuente() != null)) completados++;

    if (tieneValor(datos, "/datos_generales/altura_libre_sobre_cauce_m")
        || (tecnico != null && tecnico.getAlturaLibreSobreCauceM() != null)) completados++;

    if (tieneValor(datos, "/datos_generales/tipo_cruce")
        || (tecnico != null && tecnico.getTipoCruce() != null)) completados++;

    if (tieneValor(datos, "/datos_generales/numero_vias_por_sentido")
        || (tecnico != null && tecnico.getNumeroViasPorSentido() != null)) completados++;

    if (tieneValor(datos, "/datos_generales/material_superestructura")
        || (tecnico != null && tecnico.getMaterialSuperestructura() != null)) completados++;

    if (tieneValor(datos, "/datos_generales/material_subestructura")
        || (tecnico != null && tecnico.getMaterialSubestructura() != null)) completados++;

    return crearSeccion("datos_generales", "2. Datos generales", total, completados);
  }

  private ProgresoSeccionResponse calcularTramos(JsonNode datos) {
    int total = 4;
    int completados = 0;

    JsonNode tramosNode = datos != null ? datos.path("tramos") : null;
    if (tramosNode != null && tramosNode.isArray() && !tramosNode.isEmpty()) {
      JsonNode primerTramo = tramosNode.get(0);
      if (tieneValor(primerTramo, "/instancia_id")) completados++;
      if (tieneValor(primerTramo, "/orden")) completados++;
      if (tieneValor(primerTramo, "/longitud_m")) completados++;
      if (tieneValor(primerTramo, "/tipo_seccion")) completados++;
    }

    return crearSeccion("tramos", "3. Tramos", total, completados);
  }

  private ProgresoSeccionResponse calcularSubestructura(JsonNode datos) {
    String[] elementosEstribos = {
      "cortina_superior",
      "cortina_inferior",
      "viga_apoyo",
      "apoyos",
      "columnas",
      "cuerpo_muro_gravedad",
      "aletones/aguas_arriba",
      "aletones/aguas_abajo",
      "socavacion"
    };
    String[] elementosPilas = {
      "viga_cabezal", "columnas", "cuerpo_muro_gravedad", "apoyos", "socavacion"
    };

    int total = elementosEstribos.length * 2 + elementosPilas.length;
    int completados = 0;

    for (String elem : elementosEstribos) {
      if (tieneElemento(datos, "/subestructura/estribo_entrada/" + elem)) completados++;
      if (tieneElemento(datos, "/subestructura/estribo_salida/" + elem)) completados++;
    }
    for (String elem : elementosPilas) {
      if (tieneElementoPilas(datos, elem)) completados++;
    }

    return crearSeccion("subestructura", "4. Sub-estructura", total, completados);
  }

  private ProgresoSeccionResponse calcularSuperestructura(JsonNode datos) {
    String[] elementos = {
      "losa_rodamiento",
      "elemento_portante_concreto",
      "elemento_portante_acero",
      "diafragmas_concreto",
      "diafragmas_acero",
      "estructura_tipo_cercha",
      "drenaje"
    };

    int total = elementos.length;
    int completados = 0;

    for (String elem : elementos) {
      if (tieneElemento(datos, "/superestructura/" + elem)) {
        completados++;
      }
    }

    return crearSeccion("superestructura", "5. Super-estructura", total, completados);
  }

  private ProgresoSeccionResponse calcularElementosNoEstructurales(JsonNode datos) {
    String[] elementos = {
      "pasamanos_barandal",
      "postes_barandal",
      "barreras",
      "junta_entrada",
      "junta_salida",
      "juntas_intermedias",
      "losa_acceso_entrada",
      "losa_acceso_salida",
      "obras_proteccion"
    };

    int total = elementos.length;
    int completados = 0;

    for (String elem : elementos) {
      if (tieneElemento(datos, "/elementos_no_estructurales/" + elem)) {
        completados++;
      }
    }

    return crearSeccion(
        "elementos_no_estructurales", "6. Elementos no estructurales", total, completados);
  }

  private ProgresoSeccionResponse calcularCauce(JsonNode datos) {
    int total = 1;
    int completados = tieneValor(datos, "/caracteristicas_cauce/aplica") ? 1 : 0;
    return crearSeccion(
        "caracteristicas_cauce", "7. Características del cauce", total, completados);
  }

  private ProgresoSeccionResponse calcularOtros(JsonNode datos, InspeccionDatoTecnico tecnico) {
    int total = 4;
    int completados = 0;

    if (tieneValor(datos, "/otros/senalizacion")) completados++;
    if (tieneValor(datos, "/otros/alumbrado_artificial_existe")
        || (tecnico != null && tecnico.getAlumbradoExiste() != null)) completados++;
    if (tieneValor(datos, "/otros/drenajes_aledanos")
        || (tecnico != null && tecnico.getDrenajesAledanosExisten() != null)) completados++;
    if (datos != null && datos.at("/otros/recomendaciones").isObject()) completados++;

    return crearSeccion("otros", "8. Otros", total, completados);
  }

  private boolean tieneElemento(JsonNode datos, String ruta) {
    if (datos == null) return false;
    JsonNode nodo = datos.at(ruta);
    if (nodo.isMissingNode() || nodo.isNull()) return false;
    if (nodo.hasNonNull("existencia")) return true;
    return tieneValor(nodo);
  }

  private boolean tieneElementoPilas(JsonNode datos, String elem) {
    if (datos == null) return false;
    JsonNode pilas = datos.at("/subestructura/pilas");
    if (pilas.isArray() && !pilas.isEmpty()) {
      for (JsonNode pila : pilas) {
        if (tieneElemento(pila, "/" + elem)) return true;
      }
      return false;
    }

    if (pilas.isArray()) return true;
    return false;
  }

  private boolean tieneValor(JsonNode raiz, String puntero) {
    if (raiz == null) return false;
    JsonNode nodo = raiz.at(puntero);
    return tieneValor(nodo);
  }

  private boolean tieneValor(JsonNode nodo) {
    if (nodo == null || nodo.isMissingNode() || nodo.isNull()) {
      return false;
    }
    if (nodo.isTextual()) {
      return !nodo.asText().isBlank();
    }
    if (nodo.isArray()) {
      return !nodo.isEmpty();
    }
    if (nodo.isObject()) {
      return !nodo.isEmpty();
    }
    return true;
  }

  private ProgresoSeccionResponse crearSeccion(
      String seccion, String titulo, int total, int completados) {
    double porcentaje =
        total > 0
            ? BigDecimal.valueOf((completados * 100.0) / total)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue()
            : 100.0;
    return new ProgresoSeccionResponse(
        seccion, titulo, total, completados, porcentaje, completados >= total);
  }
}
