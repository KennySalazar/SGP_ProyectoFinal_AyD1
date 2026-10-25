package gt.usac.cunoc.sgp.foro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.foro.dto.ComentarioResponse;
import gt.usac.cunoc.sgp.foro.dto.CrearComentarioRequest;
import gt.usac.cunoc.sgp.foro.dto.CrearHiloRequest;
import gt.usac.cunoc.sgp.foro.dto.HiloResponse;
import gt.usac.cunoc.sgp.foro.entity.ForoComentario;
import gt.usac.cunoc.sgp.foro.entity.ForoHilo;
import gt.usac.cunoc.sgp.foro.mapper.ForoMapper;
import gt.usac.cunoc.sgp.foro.model.CategoriaHilo;
import gt.usac.cunoc.sgp.foro.repository.ForoAutorRepository;
import gt.usac.cunoc.sgp.foro.repository.ForoAutorRepository.Autor;
import gt.usac.cunoc.sgp.foro.repository.ForoComentarioRepository;
import gt.usac.cunoc.sgp.foro.repository.ForoComentarioRepository.TotalPorHilo;
import gt.usac.cunoc.sgp.foro.repository.ForoHiloRepository;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import gt.usac.cunoc.sgp.usuario.model.RoleName;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Foro por puente (HU-063): hilos permanentes clasificados por categoría (RN-FOR-01, RN-FOR-06) y
 * comentarios con un solo nivel de respuesta (RN-FOR-04). Lo puede leer cualquiera; para participar
 * hace falta una sesión (RN-FOR-09), lo que decide el controlador.
 *
 * <p>Los comentarios del hilo de una inspección publicada pueden anclarse a un campo o elemento
 * (HU-064, RN-FOR-02). Ese hilo solo existe para inspecciones PUBLICADAS (RN-FOR-03), un estado
 * terminal que la base de datos no deja borrar.
 *
 * <p>Lo que se ve de un comentario eliminado u oculto depende de quién lee: el Catedrático y el
 * Administrador moderan el foro y ven el texto oculto con su motivo (HU-065, RN-FOR-08).
 */
@Service
public class ForoService {

  private final ForoHiloRepository hilos;
  private final ForoComentarioRepository comentarios;
  private final ForoAutorRepository autores;
  private final PuenteRepository puentes;
  private final InspeccionRepository inspecciones;
  private final AnclajeComentarioForo anclajes;
  private final LimiteTasaForo limites;
  private final AvisosForo avisos;
  private final AuditoriaWriter auditoria;
  private final ForoMapper mapper;
  private final ObjectMapper json;
  private final Clock clock;

  public ForoService(
      ForoHiloRepository hilos,
      ForoComentarioRepository comentarios,
      ForoAutorRepository autores,
      PuenteRepository puentes,
      InspeccionRepository inspecciones,
      AnclajeComentarioForo anclajes,
      LimiteTasaForo limites,
      AvisosForo avisos,
      AuditoriaWriter auditoria,
      ForoMapper mapper,
      ObjectMapper json,
      Clock clock) {
    this.hilos = hilos;
    this.comentarios = comentarios;
    this.autores = autores;
    this.puentes = puentes;
    this.inspecciones = inspecciones;
    this.anclajes = anclajes;
    this.limites = limites;
    this.avisos = avisos;
    this.auditoria = auditoria;
    this.mapper = mapper;
    this.json = json;
    this.clock = clock;
  }

  /** Hilos propios de un puente, del de actividad más reciente al más antiguo. */
  @Transactional(readOnly = true)
  public Page<HiloResponse> listarHilosDePuente(UUID puenteId, int pagina, int tamanio) {
    ContenidoForo.validarPaginacion(pagina, tamanio);
    puenteActivo(puenteId);
    Page<ForoHilo> resultado =
        hilos.findByPuenteIdAndInspeccionIdIsNull(
            puenteId,
            PageRequest.of(
                pagina, tamanio, Sort.by(Sort.Order.desc("actualizadoEn"), Sort.Order.desc("id"))));
    List<ForoHilo> filas = resultado.getContent();
    Map<UUID, Long> totales =
        filas.isEmpty()
            ? Map.of()
            : comentarios.contarPorHilos(filas.stream().map(ForoHilo::getId).toList()).stream()
                .collect(Collectors.toMap(TotalPorHilo::getHiloId, TotalPorHilo::getTotal));
    Map<UUID, Autor> autoresPorId = autoresDe(filas.stream().map(ForoHilo::getAutorId).toList());
    return resultado.map(
        h ->
            mapper.toHiloResponse(
                h, autoresPorId.get(h.getAutorId()), totales.getOrDefault(h.getId(), 0L)));
  }

  /**
   * Abre un hilo en un puente activo con su primer comentario. Valida todo y responde un único 422
   * con el detalle. Respeta el límite de tasa (429) y avisa a los mencionados y, si es una alerta
   * sobre un puente en estado Malo, a los Catedráticos y Administradores (HU-066).
   */
  @Transactional
  public HiloResponse crearHilo(UUID puenteId, CrearHiloRequest request, UUID usuarioId) {
    Puente puente = puenteActivo(puenteId);
    limites.exigirParaHilo(usuarioId);
    List<Map<String, String>> errores = new ArrayList<>();
    String titulo = ContenidoForo.limpiarTitulo(request.titulo());
    if (titulo == null) {
      errores.add(ContenidoForo.error("titulo", "El título es obligatorio."));
    } else if (titulo.length() > ContenidoForo.LONGITUD_MAXIMA_TITULO) {
      errores.add(
          ContenidoForo.error(
              "titulo",
              "El título no puede exceder "
                  + ContenidoForo.LONGITUD_MAXIMA_TITULO
                  + " caracteres."));
    }
    CategoriaHilo categoria = categoria(request.categoria(), errores);
    String comentario = ContenidoForo.limpiarComentario(request.comentario());
    validarComentario(comentario, "comentario", errores);
    if (!errores.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "hilo_invalido",
          "Hilo inválido",
          "El hilo contiene datos inválidos.",
          errores);
    }

    Instant ahora = clock.instant();
    ForoHilo hilo =
        hilos.save(
            new ForoHilo(
                UuidV7Generator.generate(), puenteId, null, usuarioId, categoria, titulo, ahora));
    auditoria.registrarEnTransaccion(
        usuarioId, AccionAuditoria.CREAR, "foro_hilo", hilo.getId(), null, instantanea(hilo), null);
    avisos.avisarMenciones(hilo, registrarComentario(hilo, comentario, null, usuarioId));
    avisos.avisarAlerta(hilo, puente);
    return mapper.toHiloResponse(hilo, autoresDe(List.of(usuarioId)).get(usuarioId), 1L);
  }

  /**
   * Un comentario, visto por quien lee: lo usa quien abre el aviso de una mención para llegar a su
   * hilo (HU-066). 404 si no existe o su puente está inactivo.
   */
  @Transactional(readOnly = true)
  public ComentarioResponse obtenerComentario(UUID comentarioId, RoleName lector) {
    ForoComentario comentario =
        comentarios
            .findById(comentarioId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "comentario_no_encontrado",
                        "Comentario no encontrado",
                        "El comentario no existe."));
    hiloVisible(comentario.getHiloId());
    return mapper.toComentarioResponse(
        comentario,
        autoresDe(List.of(comentario.getAutorId())).get(comentario.getAutorId()),
        esModerador(lector));
  }

  @Transactional(readOnly = true)
  public HiloResponse obtenerHilo(UUID hiloId) {
    ForoHilo hilo = hiloVisible(hiloId);
    long total =
        comentarios.contarPorHilos(List.of(hiloId)).stream()
            .mapToLong(TotalPorHilo::getTotal)
            .sum();
    return mapper.toHiloResponse(
        hilo, autoresDe(List.of(hilo.getAutorId())).get(hilo.getAutorId()), total);
  }

  /**
   * Comentarios y respuestas de un hilo en una sola lista, de los más antiguos a los más nuevos.
   * Una respuesta siempre llega después de su comentario principal, así que quien la muestra puede
   * agruparla bajo él a medida que carga páginas.
   */
  public Page<ComentarioResponse> listarComentarios(UUID hiloId, int pagina, int tamanio) {
    return listarComentarios(hiloId, pagina, tamanio, FiltroComentarios.NINGUNO);
  }

  /** Si quien lee modera el foro: ve los comentarios ocultos y su motivo (RN-FOR-08). */
  private static boolean esModerador(RoleName rol) {
    return rol == RoleName.CATEDRATICO || rol == RoleName.ADMINISTRADOR;
  }

  /** Los comentarios de un campo o elemento; sin {@code elementoRef}, todos los del hilo. */
  public Page<ComentarioResponse> listarComentarios(
      UUID hiloId, int pagina, int tamanio, String elementoRef, UUID instanciaElementoId) {
    return listarComentarios(
        hiloId, pagina, tamanio, FiltroComentarios.porElemento(elementoRef, instanciaElementoId));
  }

  /** Los comentarios con el filtro pedido, vistos por alguien que no modera el foro. */
  public Page<ComentarioResponse> listarComentarios(
      UUID hiloId, int pagina, int tamanio, FiltroComentarios filtro) {
    return listarComentariosPara(hiloId, pagina, tamanio, filtro, null);
  }

  /**
   * Los comentarios de un hilo, más antiguos primero, con el filtro pedido. Con {@code elementoRef}
   * solo los anclados a ese campo o elemento: sin instancia, los del elemento sin instancia; con
   * ella, los de esa instancia. Con {@code seccion} los de esa sección y todo lo que contiene; con
   * {@code soloGenerales}, los que no están anclados. Dos filtros a la vez se rechazan con 422.
   *
   * <p>{@code lector} es el rol de quien lee, o nulo si es un visitante. Los eliminados y ocultos
   * siguen en la lista para no romper el hilo, sin su texto salvo los ocultos para un moderador.
   */
  @Transactional(readOnly = true)
  public Page<ComentarioResponse> listarComentariosPara(
      UUID hiloId, int pagina, int tamanio, FiltroComentarios filtro, RoleName lector) {
    ContenidoForo.validarPaginacion(pagina, tamanio);
    String ref = ContenidoForo.limpiarReferencia(filtro.elementoRef());
    String seccion = ContenidoForo.limpiarReferencia(filtro.seccion());
    validarFiltro(ref, filtro.instanciaElementoId(), seccion, filtro.soloGenerales());
    ForoHilo hilo = hiloVisible(hiloId);
    PageRequest paginacion =
        PageRequest.of(pagina, tamanio, Sort.by(Sort.Order.asc("creadoEn"), Sort.Order.asc("id")));
    Page<ForoComentario> resultado;
    if (filtro.soloGenerales()) {
      resultado = comentarios.findByHiloIdAndElementoRefIsNull(hilo.getId(), paginacion);
    } else if (seccion != null) {
      String raiz = "/" + seccion;
      resultado =
          comentarios.findBySeccion(
              hilo.getId(), raiz, ContenidoForo.escaparLike(raiz) + "/%", paginacion);
    } else if (ref == null) {
      resultado = comentarios.findByHiloId(hilo.getId(), paginacion);
    } else if (filtro.instanciaElementoId() == null) {
      resultado =
          comentarios.findByHiloIdAndElementoRefAndInstanciaElementoIdIsNull(
              hilo.getId(), ref, paginacion);
    } else {
      resultado =
          comentarios.findByHiloIdAndElementoRefAndInstanciaElementoId(
              hilo.getId(), ref, filtro.instanciaElementoId(), paginacion);
    }
    Map<UUID, Autor> autoresPorId =
        autoresDe(resultado.getContent().stream().map(ForoComentario::getAutorId).toList());
    boolean moderador = esModerador(lector);
    return resultado.map(
        c -> mapper.toComentarioResponse(c, autoresPorId.get(c.getAutorId()), moderador));
  }

  private void validarFiltro(String ref, UUID instancia, String seccion, boolean soloGenerales) {
    List<Map<String, String>> errores = new ArrayList<>();
    if (ref == null && instancia != null) {
      errores.add(
          ContenidoForo.error(
              "elementoRef", "La instancia exige indicar el elemento al que pertenece."));
    }
    int filtros = (ref != null ? 1 : 0) + (seccion != null ? 1 : 0) + (soloGenerales ? 1 : 0);
    if (filtros > 1) {
      errores.add(
          ContenidoForo.error(
              "filtro", "Usa un solo filtro a la vez: un elemento, una sección o los generales."));
    }
    if (seccion != null && (seccion.contains("/") || seccion.length() > 100)) {
      errores.add(
          ContenidoForo.error(
              "seccion", "La sección es la clave de una sola sección, sin barras (hasta 100)."));
    }
    if (!errores.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "filtro_invalido",
          "Filtro inválido",
          errores.get(0).get("mensaje"),
          errores);
    }
  }

  /**
   * Agrega un comentario a un hilo, o una respuesta si indica el comentario principal. En el hilo
   * de una inspección el comentario principal puede anclarse a un campo o elemento; la respuesta
   * hereda el ancla de su comentario.
   */
  @Transactional
  public ComentarioResponse crearComentario(
      UUID hiloId, CrearComentarioRequest request, UUID usuarioId) {
    ForoHilo hilo = hiloVisible(hiloId);
    limites.exigirParaComentario(usuarioId);
    List<Map<String, String>> errores = new ArrayList<>();
    String contenido = ContenidoForo.limpiarComentario(request.contenido());
    validarComentario(contenido, "contenido", errores);
    String elementoRef = ContenidoForo.limpiarReferencia(request.elementoRef());
    UUID instanciaElementoId = request.instanciaElementoId();
    if (request.padreId() != null) {
      var padre = comentarios.findByIdAndHiloId(request.padreId(), hiloId);
      if (padre.isEmpty()) {
        errores.add(
            ContenidoForo.error(
                "padreId", "El comentario al que responde no existe en este hilo."));
      } else if (padre.get().getPadreId() != null) {
        errores.add(
            ContenidoForo.error(
                "padreId",
                "Solo se puede responder a un comentario principal, no a una respuesta."));
      }
      if (elementoRef != null || instanciaElementoId != null) {
        errores.add(
            ContenidoForo.error(
                "elementoRef",
                "Una respuesta toma el elemento del comentario al que responde; no lleva ancla"
                    + " propia."));
      }
      elementoRef = padre.map(ForoComentario::getElementoRef).orElse(null);
      instanciaElementoId = padre.map(ForoComentario::getInstanciaElementoId).orElse(null);
    } else {
      validarAncla(hilo, elementoRef, instanciaElementoId, errores);
    }
    if (!errores.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "comentario_invalido",
          "Comentario inválido",
          "El comentario contiene datos inválidos.",
          errores);
    }
    if (hilo.getCerradoEn() != null) throw hiloCerrado();
    ForoComentario comentario =
        registrarComentario(
            hilo, contenido, request.padreId(), elementoRef, instanciaElementoId, usuarioId);
    avisos.avisarMenciones(hilo, comentario);
    return mapper.toComentarioResponse(
        comentario, autoresDe(List.of(usuarioId)).get(usuarioId), false);
  }

  /**
   * Un ancla exige el hilo de una inspección y un elemento que exista en el esquema con que se
   * levantó esa inspección (RN-FOR-02). Sin ancla no hay nada que comprobar.
   */
  private void validarAncla(
      ForoHilo hilo,
      String elementoRef,
      UUID instanciaElementoId,
      List<Map<String, String>> errores) {
    if (elementoRef == null && instanciaElementoId == null) return;
    if (elementoRef == null) {
      errores.add(
          ContenidoForo.error(
              "elementoRef", "La instancia exige indicar el elemento al que pertenece."));
    } else if (hilo.getInspeccionId() == null) {
      errores.add(
          ContenidoForo.error(
              "elementoRef",
              "Solo los comentarios del hilo de una inspección pueden anclarse a un elemento."));
    } else {
      Inspeccion inspeccion =
          inspecciones
              .findByIdAndEliminadoEnIsNull(hilo.getInspeccionId())
              .orElseThrow(ForoService::hiloNoEncontrado);
      anclajes.validar(inspeccion, elementoRef, instanciaElementoId, errores);
    }
  }

  private ForoComentario registrarComentario(
      ForoHilo hilo, String contenido, UUID padreId, UUID usuarioId) {
    return registrarComentario(hilo, contenido, padreId, null, null, usuarioId);
  }

  private ForoComentario registrarComentario(
      ForoHilo hilo,
      String contenido,
      UUID padreId,
      String elementoRef,
      UUID instanciaElementoId,
      UUID usuarioId) {
    Instant ahora = clock.instant();
    // Si un moderador lo cerró después de leerlo, el hilo ya no admite el comentario (RN-FOR-08).
    if (hilos.registrarActividad(hilo.getId(), ahora) == 0) throw hiloCerrado();
    ForoComentario comentario =
        comentarios.save(
            new ForoComentario(
                UuidV7Generator.generate(),
                hilo.getId(),
                usuarioId,
                padreId,
                elementoRef,
                instanciaElementoId,
                contenido,
                ahora));
    auditoria.registrarEnTransaccion(
        usuarioId,
        AccionAuditoria.CREAR,
        "foro_comentario",
        comentario.getId(),
        null,
        instantanea(comentario),
        null);
    return comentario;
  }

  private void validarComentario(
      String contenido, String campo, List<Map<String, String>> errores) {
    if (contenido == null) {
      errores.add(ContenidoForo.error(campo, "El comentario es obligatorio."));
    } else if (contenido.length() > ContenidoForo.LONGITUD_MAXIMA_COMENTARIO) {
      errores.add(
          ContenidoForo.error(
              campo,
              "El comentario no puede exceder "
                  + ContenidoForo.LONGITUD_MAXIMA_COMENTARIO
                  + " caracteres."));
    }
  }

  private CategoriaHilo categoria(String valor, List<Map<String, String>> errores) {
    String texto = valor == null ? "" : valor.strip();
    try {
      return CategoriaHilo.valueOf(texto);
    } catch (IllegalArgumentException e) {
      errores.add(
          ContenidoForo.error(
              "categoria",
              texto.isEmpty()
                  ? "La categoría es obligatoria."
                  : "La categoría debe ser una de: "
                      + Arrays.stream(CategoriaHilo.values())
                          .map(Enum::name)
                          .collect(Collectors.joining(", "))
                      + "."));
      return null;
    }
  }

  private Puente puenteActivo(UUID puenteId) {
    return puentes
        .findById(puenteId)
        .filter(Puente::isActivo)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "puente_no_encontrado",
                    "Puente no encontrado",
                    "El puente no existe o está inactivo."));
  }

  /** El hilo de un puente activo; si no existe o el puente está inactivo, 404. */
  private ForoHilo hiloVisible(UUID hiloId) {
    ForoHilo hilo = hilos.findById(hiloId).orElseThrow(ForoService::hiloNoEncontrado);
    puenteActivo(hilo.getPuenteId());
    return hilo;
  }

  private static ApiException hiloCerrado() {
    return new ApiException(
        HttpStatus.CONFLICT,
        "hilo_cerrado",
        "Hilo cerrado",
        "El hilo está cerrado y ya no admite comentarios.");
  }

  private static ApiException hiloNoEncontrado() {
    return new ApiException(
        HttpStatus.NOT_FOUND, "hilo_no_encontrado", "Hilo no encontrado", "El hilo no existe.");
  }

  private Map<UUID, Autor> autoresDe(Collection<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    return autores.buscarPorIds(new HashSet<>(ids)).stream()
        .collect(Collectors.toMap(Autor::getId, autor -> autor));
  }

  private ObjectNode instantanea(ForoHilo hilo) {
    ObjectNode nodo = json.createObjectNode();
    nodo.put("puenteId", hilo.getPuenteId().toString());
    nodo.put("categoria", hilo.getCategoria().name());
    nodo.put("titulo", hilo.getTitulo());
    return nodo;
  }

  private ObjectNode instantanea(ForoComentario comentario) {
    ObjectNode nodo = json.createObjectNode();
    nodo.put("hiloId", comentario.getHiloId().toString());
    if (comentario.getPadreId() != null) {
      nodo.put("padreId", comentario.getPadreId().toString());
    }
    if (comentario.getElementoRef() != null) {
      nodo.put("elementoRef", comentario.getElementoRef());
    }
    if (comentario.getInstanciaElementoId() != null) {
      nodo.put("instanciaElementoId", comentario.getInstanciaElementoId().toString());
    }
    nodo.put("contenido", comentario.getContenido());
    return nodo;
  }
}
