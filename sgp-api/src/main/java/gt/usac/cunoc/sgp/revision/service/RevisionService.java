package gt.usac.cunoc.sgp.revision.service;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.dto.InspeccionResumenResponse;
import gt.usac.cunoc.sgp.inspeccion.mapper.InspeccionMapper;
import gt.usac.cunoc.sgp.inspeccion.service.TransicionInspeccionService;
import gt.usac.cunoc.sgp.revision.dto.BorradorEstudianteResponse;
import gt.usac.cunoc.sgp.revision.dto.ContadorRevisionesResponse;
import gt.usac.cunoc.sgp.revision.dto.RevisionBandejaResponse;
import gt.usac.cunoc.sgp.revision.dto.RevisionResueltaResponse;
import gt.usac.cunoc.sgp.revision.entity.Revision;
import gt.usac.cunoc.sgp.revision.repository.RevisionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevisionService {

  private static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");
  private static final Set<String> ESTADOS = Set.of("ENVIADA", "EN_REVISION");
  private static final Instant INICIO = Instant.parse("2000-01-01T00:00:00Z");
  private static final Instant FIN = Instant.parse("2100-01-01T00:00:00Z");

  private final RevisionRepository revisiones;
  private final TransicionInspeccionService transiciones;
  private final InspeccionMapper inspeccionMapper;
  private final Clock clock;

  public RevisionService(
      RevisionRepository revisiones,
      TransicionInspeccionService transiciones,
      InspeccionMapper inspeccionMapper,
      Clock clock) {
    this.revisiones = revisiones;
    this.transiciones = transiciones;
    this.inspeccionMapper = inspeccionMapper;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Page<RevisionBandejaResponse> listarBandeja(
      UUID revisorId,
      String estado,
      String puente,
      String estudiante,
      String curso,
      LocalDate fechaDesde,
      LocalDate fechaHasta,
      int pagina,
      int tamanio) {
    validarPaginacion(pagina, tamanio);
    String estadoNormalizado = texto(estado).toUpperCase();
    if (!estadoNormalizado.isEmpty() && !ESTADOS.contains(estadoNormalizado)) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "estado_invalido",
          "Estado inválido",
          "La bandeja solo admite los estados ENVIADA y EN_REVISION.");
    }
    if (fechaDesde != null && fechaHasta != null && fechaHasta.isBefore(fechaDesde)) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "rango_fechas_invalido",
          "Rango de fechas inválido",
          "La fecha final no puede ser anterior a la fecha inicial.");
    }

    Instant desde =
        fechaDesde == null ? INICIO : fechaDesde.atStartOfDay(ZONA_GUATEMALA).toInstant();
    Instant hasta =
        fechaHasta == null ? FIN : fechaHasta.plusDays(1).atStartOfDay(ZONA_GUATEMALA).toInstant();
    LocalDate hoy = LocalDate.now(clock.withZone(ZONA_GUATEMALA));

    return revisiones
        .buscarBandeja(
            revisorId,
            estadoNormalizado,
            texto(puente),
            texto(estudiante),
            texto(curso),
            desde,
            hasta,
            PageRequest.of(pagina, tamanio))
        .map(fila -> respuesta(fila, hoy));
  }

  @Transactional(readOnly = true)
  public Page<RevisionResueltaResponse> listarResueltas(UUID revisorId, int pagina, int tamanio) {
    validarPaginacion(pagina, tamanio);
    return revisiones.buscarResueltas(revisorId, PageRequest.of(pagina, tamanio));
  }

  @Transactional(readOnly = true)
  public ContadorRevisionesResponse contarPendientes(UUID revisorId) {
    return new ContadorRevisionesResponse(revisiones.contarPendientes(revisorId));
  }

  @Transactional(readOnly = true)
  public Page<BorradorEstudianteResponse> listarBorradores(
      UUID revisorId, int pagina, int tamanio) {
    validarPaginacion(pagina, tamanio);
    return revisiones
        .buscarBorradores(revisorId, PageRequest.of(pagina, tamanio))
        .map(
            fila ->
                new BorradorEstudianteResponse(
                    fila.getInspeccionId(),
                    fila.getPuenteId(),
                    fila.getPuenteCodigo(),
                    fila.getPuenteNombre(),
                    fila.getEstudianteId(),
                    fila.getEstudianteEmail(),
                    fila.getEstudianteNombre(),
                    fila.getCursoId(),
                    fila.getCursoNombre(),
                    fila.getPeriodo(),
                    fila.getSeccion(),
                    fila.getUltimaActividadEn()));
  }

  @Transactional
  public InspeccionResumenResponse iniciar(UUID revisionId, UUID revisorId) {
    Revision revision;
    try {
      revision = revisiones.findByIdForUpdate(revisionId).orElseThrow(() -> revisionNoEncontrada());
    } catch (PessimisticLockingFailureException exception) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "revision_en_proceso",
          "Revisión en proceso",
          "Otra petición está iniciando esta revisión.");
    }
    if (!revision.getRevisorId().equals(revisorId)) {
      throw revisionNoEncontrada();
    }

    if ("EN_CURSO".equals(revision.getEstado())
        && "EN_REVISION".equals(revision.getInspeccion().getEstado())) {
      return inspeccionMapper.toResumenResponse(revision.getInspeccion());
    }
    if (!"PENDIENTE".equals(revision.getEstado())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "revision_no_iniciable",
          "Revisión no iniciable",
          "El ciclo de revisión ya fue resuelto.");
    }

    InspeccionResumenResponse respuesta =
        transiciones.iniciarRevision(revision.getInspeccion().getId(), revisorId, revision.getId());
    revision.iniciar(clock.instant());
    revisiones.saveAndFlush(revision);
    return respuesta;
  }

  private RevisionBandejaResponse respuesta(
      RevisionRepository.RevisionBandejaProjection fila, LocalDate hoy) {
    LocalDate enviada = fila.getEnviadaEn().atZone(ZONA_GUATEMALA).toLocalDate();
    long dias = Math.max(0, ChronoUnit.DAYS.between(enviada, hoy));
    return new RevisionBandejaResponse(
        fila.getRevisionId(),
        fila.getInspeccionId(),
        fila.getPuenteId(),
        fila.getPuenteCodigo(),
        fila.getPuenteNombre(),
        fila.getEstudianteId(),
        fila.getEstudianteEmail(),
        fila.getEstudianteNombre(),
        fila.getCursoId(),
        fila.getCursoNombre(),
        fila.getPeriodo(),
        fila.getSeccion(),
        fila.getNumeroCiclo(),
        fila.getEnviadaEn(),
        fila.getIniciadaEn(),
        dias,
        dias > 15,
        fila.getEstadoInspeccion(),
        fila.getEstadoRevision());
  }

  private String texto(String valor) {
    return valor == null ? "" : valor.trim();
  }

  private void validarPaginacion(int pagina, int tamanio) {
    if (pagina < 0 || tamanio < 1 || tamanio > 100) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "paginacion_invalida",
          "Paginación inválida",
          "La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.");
    }
  }

  private ApiException revisionNoEncontrada() {
    return new ApiException(
        HttpStatus.NOT_FOUND,
        "revision_no_encontrada",
        "Revisión no encontrada",
        "No existe la revisión solicitada o no corresponde al catedrático autenticado.");
  }
}
