package gt.usac.cunoc.sgp.inspeccion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.inspeccion.entity.ResultadoIc;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoCondicion;
import gt.usac.cunoc.sgp.inspeccion.model.ResultadoCalculoIc;
import gt.usac.cunoc.sgp.inspeccion.repository.ResultadoIcRepository;
import gt.usac.cunoc.sgp.puente.entity.Puente;
import gt.usac.cunoc.sgp.puente.repository.PuenteRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicacionIndiceCondicionService {

  private final PreparadorEntradaIcService preparador;
  private final CalculoIndiceCondicionService calculador;
  private final ResultadoIcRepository resultados;
  private final PuenteRepository puentes;
  private final AuditoriaWriter auditoria;
  private final JdbcTemplate jdbc;
  private final EntityManager entityManager;
  private final ObjectMapper json;
  private final Clock clock;

  public PublicacionIndiceCondicionService(
      PreparadorEntradaIcService preparador,
      CalculoIndiceCondicionService calculador,
      ResultadoIcRepository resultados,
      PuenteRepository puentes,
      AuditoriaWriter auditoria,
      JdbcTemplate jdbc,
      EntityManager entityManager,
      ObjectMapper json,
      Clock clock) {
    this.preparador = preparador;
    this.calculador = calculador;
    this.resultados = resultados;
    this.puentes = puentes;
    this.auditoria = auditoria;
    this.jdbc = jdbc;
    this.entityManager = entityManager;
    this.json = json;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public ResultadoIc integrarPublicacion(UUID inspeccionId, UUID usuarioId) {
    if (inspeccionId == null || usuarioId == null) {
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "publicacion_ic_invalida",
          "La inspeccion y el usuario responsable son obligatorios.");
    }

    entityManager.flush();

    var idsPuente =
        jdbc.query(
            "SELECT puente_id FROM inspeccion WHERE id = ?",
            (rs, numero) -> rs.getObject("puente_id", UUID.class),
            inspeccionId);

    if (idsPuente.isEmpty()) {
      throw error(
          HttpStatus.NOT_FOUND,
          "inspeccion_no_encontrada",
          "No existe la inspeccion " + inspeccionId);
    }

    Puente puente =
        puentes
            .findByIdForUpdate(idsPuente.get(0))
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.NOT_FOUND,
                        "puente_no_encontrado",
                        "No existe el puente de la inspeccion."));

    InspeccionPublicada inspeccion = cargarInspeccion(inspeccionId);
    if (!puente.getId().equals(inspeccion.puenteId())) {
      throw error(
          HttpStatus.CONFLICT,
          "inspeccion_puente_incongruente",
          "El puente de la inspeccion cambio durante la operacion.");
    }
    if (!"PUBLICADA".equals(inspeccion.estado())
        || inspeccion.publicadaEn() == null
        || inspeccion.eliminada()) {
      throw error(
          HttpStatus.CONFLICT,
          "inspeccion_no_publicada",
          "La inspeccion debe estar publicada y no eliminada dentro de esta transaccion.");
    }
    if (resultados.findPublicacionByInspeccionId(inspeccionId).isPresent()) {
      throw error(
          HttpStatus.CONFLICT,
          "ic_publicacion_existente",
          "La inspeccion ya tiene un resultado definitivo de publicacion.");
    }

    var entrada = preparador.preparar(inspeccion.versionFormularioId(), inspeccion.datos());
    var calculo = calculador.calcular(entrada);
    Instant ahora = clock.instant();
    DecisionEstado decision = resolverDecision(inspeccionId, inspeccion.autorId(), calculo);

    ResultadoIc resultado =
        new ResultadoIc(
            UuidV7Generator.generate(),
            inspeccionId,
            calculo.versionFormularioId(),
            calculo.indiceCalculado(),
            calculo.estadoPorRango(),
            calculo.estadoCalculado(),
            json.valueToTree(calculo.condicionesAnulacion()),
            json.valueToTree(calculo),
            calculo.hashEntrada(),
            calculo.versionAlgoritmo(),
            usuarioId,
            ahora,
            decision.estado(),
            decision.justificacion(),
            decision.autorId(),
            decision.fecha());

    resultados.saveAndFlush(resultado);

    ResultadoIc vigente =
        resultados
            .findPublicacionVigenteByPuenteId(puente.getId())
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.CONFLICT,
                        "ic_vigente_no_encontrado",
                        "No se encontro el resultado definitivo vigente del puente."));

    UUID ultimaPublicadaId =
        jdbc.queryForObject(
            """
                        SELECT id FROM inspeccion
                        WHERE puente_id = ? AND estado = 'PUBLICADA'
                        ORDER BY publicado_en DESC, id DESC
                        LIMIT 1
                        """,
            UUID.class,
            puente.getId());

    if (!vigente.getInspeccionId().equals(ultimaPublicadaId)) {
      throw error(
          HttpStatus.CONFLICT,
          "ic_ultima_publicacion_incompleto",
          "La ultima inspeccion publicada no tiene un resultado definitivo.");
    }

    InspeccionPublicada inspeccionVigente =
        vigente.getInspeccionId().equals(inspeccionId)
            ? inspeccion
            : cargarInspeccion(vigente.getInspeccionId());

    if (inspeccionVigente.eliminada()) {
      throw error(
          HttpStatus.CONFLICT,
          "inspeccion_vigente_eliminada",
          "La inspeccion publicada vigente no puede estar eliminada.");
    }

    ObjectNode anteriores = snapshot(puente);
    String estadoVigente =
        switch (vigente.getEstadoConfirmado()) {
          case BUENO -> "Bueno";
          case REGULAR -> "Regular";
          case MALO -> "Malo";
        };

    boolean cambio =
        !Objects.equals(puente.getEstadoActual(), estadoVigente)
            || puente.getIndiceCondicionActual() == null
            || puente.getIndiceCondicionActual().compareTo(vigente.getIndiceCalculado()) != 0
            || !Objects.equals(
                puente.getFechaUltimaInspeccion(), inspeccionVigente.fechaInspeccion())
            || !Objects.equals(puente.getInspeccionVigenteId(), vigente.getInspeccionId())
            || !Objects.equals(puente.getResultadoIcVigenteId(), vigente.getId())
            || !Objects.equals(
                puente.getInspeccionVigentePublicadaEn(), inspeccionVigente.publicadaEn());

    if (cambio) {
      puente.actualizarEstadoPorPublicacion(
          vigente.getInspeccionId(),
          vigente.getId(),
          vigente.getEstadoConfirmado(),
          vigente.getIndiceCalculado(),
          inspeccionVigente.fechaInspeccion(),
          inspeccionVigente.publicadaEn(),
          ahora);
      puentes.saveAndFlush(puente);
      auditoria.registrarEnTransaccion(
          usuarioId,
          AccionAuditoria.MODIFICAR,
          "puente",
          puente.getId(),
          anteriores,
          snapshot(puente),
          null);
    }

    return resultado;
  }

  private DecisionEstado resolverDecision(
      UUID inspeccionId, UUID autorInspeccionId, ResultadoCalculoIc calculo) {
    var decisiones =
        jdbc.query(
            """
            SELECT estado_confirmado, justificacion_ajuste, realizado_por_id, realizado_en,
                   calculo = CAST(? AS jsonb) AS calculo_vigente
            FROM confirmacion_estado_inspeccion
            WHERE inspeccion_id = ?
            """,
            (rs, numero) -> {
              if (!autorInspeccionId.equals(rs.getObject("realizado_por_id", UUID.class))) {
                throw error(
                    HttpStatus.CONFLICT,
                    "confirmacion_ic_autor_invalido",
                    "La confirmacion no pertenece al autor responsable actual de la inspeccion.");
              }
              if (!rs.getBoolean("calculo_vigente")) {
                throw error(
                    HttpStatus.CONFLICT,
                    "confirmacion_ic_desactualizada",
                    "El calculo cambio desde la confirmacion. El evaluador debe confirmar nuevamente.");
              }
              EstadoCondicion confirmado =
                  EstadoCondicion.valueOf(rs.getString("estado_confirmado"));
              if (confirmado == calculo.estadoCalculado()) {
                return new DecisionEstado(confirmado, null, null, null);
              }
              String justificacion = rs.getString("justificacion_ajuste");
              UUID autorId = rs.getObject("realizado_por_id", UUID.class);
              OffsetDateTime fecha = rs.getObject("realizado_en", OffsetDateTime.class);
              if (justificacion == null || justificacion.isBlank() || fecha == null) {
                throw error(
                    HttpStatus.CONFLICT,
                    "confirmacion_ic_incompleta",
                    "El ajuste requiere justificacion, autor y fecha.");
              }
              return new DecisionEstado(confirmado, justificacion, autorId, fecha.toInstant());
            },
            json.valueToTree(calculo).toString(),
            inspeccionId);
    return decisiones.isEmpty()
        ? new DecisionEstado(calculo.estadoCalculado(), null, null, null)
        : decisiones.get(0);
  }

  private record DecisionEstado(
      EstadoCondicion estado, String justificacion, UUID autorId, Instant fecha) {}

  private InspeccionPublicada cargarInspeccion(UUID id) {
    var encontrados =
        jdbc.query(
            """
                        SELECT puente_id, autor_id, version_formulario_id, estado, fecha_inspeccion,
                               publicado_en, datos, eliminado_en
                        FROM inspeccion WHERE id = ?
                        FOR UPDATE
                        """,
            (rs, numero) -> {
              OffsetDateTime publicadaEn = rs.getObject("publicado_en", OffsetDateTime.class);
              return new InspeccionPublicada(
                  rs.getObject("puente_id", UUID.class),
                  rs.getObject("autor_id", UUID.class),
                  rs.getObject("version_formulario_id", UUID.class),
                  rs.getString("estado"),
                  rs.getObject("fecha_inspeccion", LocalDate.class),
                  publicadaEn == null ? null : publicadaEn.toInstant(),
                  leerDatos(rs.getString("datos")),
                  rs.getObject("eliminado_en") != null);
            },
            id);
    if (encontrados.isEmpty()) {
      throw error(
          HttpStatus.NOT_FOUND, "inspeccion_no_encontrada", "No existe la inspeccion " + id);
    }
    return encontrados.get(0);
  }

  private JsonNode leerDatos(String contenido) {
    try {
      return json.readTree(contenido);
    } catch (JsonProcessingException excepcion) {
      throw new IllegalStateException(
          "Los datos de la inspeccion no contienen JSON valido.", excepcion);
    }
  }

  private ObjectNode snapshot(Puente puente) {
    ObjectNode valor = json.createObjectNode();
    valor.put("id", puente.getId().toString());
    valor.put("estadoActual", puente.getEstadoActual());
    valor.put("indiceCondicionActual", puente.getIndiceCondicionActual());
    valor.put("fechaUltimaInspeccion", cadena(puente.getFechaUltimaInspeccion()));
    valor.put("inspeccionVigenteId", cadena(puente.getInspeccionVigenteId()));
    valor.put("resultadoIcVigenteId", cadena(puente.getResultadoIcVigenteId()));
    valor.put("inspeccionVigentePublicadaEn", cadena(puente.getInspeccionVigentePublicadaEn()));
    return valor;
  }

  private String cadena(Object valor) {
    return valor == null ? null : valor.toString();
  }

  private ApiException error(HttpStatus estado, String codigo, String detalle) {
    return new ApiException(estado, codigo, "Integracion del indice de condicion", detalle);
  }

  private record InspeccionPublicada(
      UUID puenteId,
      UUID autorId,
      UUID versionFormularioId,
      String estado,
      LocalDate fechaInspeccion,
      Instant publicadaEn,
      JsonNode datos,
      boolean eliminada) {}
}
