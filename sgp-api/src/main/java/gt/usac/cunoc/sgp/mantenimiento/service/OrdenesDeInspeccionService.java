package gt.usac.cunoc.sgp.mantenimiento.service;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.entity.Inspeccion;
import gt.usac.cunoc.sgp.inspeccion.model.EstadoInspeccion;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import gt.usac.cunoc.sgp.mantenimiento.dto.OrdenMantenimientoResponse;
import gt.usac.cunoc.sgp.mantenimiento.mapper.OrdenMantenimientoMapper;
import gt.usac.cunoc.sgp.mantenimiento.repository.OrdenMantenimientoRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta las órdenes que originó una inspección publicada, para mostrarlas junto a ella (HU-046).
 * Un borrador o una inspección en revisión no es visible para nadie más que su autor y su revisor
 * (RN-INS-05), así que aquí responde 404 como si no existiera.
 */
@Service
public class OrdenesDeInspeccionService {

  private final InspeccionRepository inspecciones;
  private final OrdenMantenimientoRepository ordenes;
  private final OrdenMantenimientoMapper mapper;

  public OrdenesDeInspeccionService(
      InspeccionRepository inspecciones,
      OrdenMantenimientoRepository ordenes,
      OrdenMantenimientoMapper mapper) {
    this.inspecciones = inspecciones;
    this.ordenes = ordenes;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public List<OrdenMantenimientoResponse> listar(UUID inspeccionId) {
    Inspeccion inspeccion =
        inspecciones
            .findByIdAndEliminadoEnIsNull(inspeccionId)
            .filter(i -> i.estadoActual() == EstadoInspeccion.PUBLICADA)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "inspeccion_no_encontrada",
                        "Inspección no encontrada",
                        "No existe la inspección publicada solicitada."));
    return ordenes.findByInspeccionOrigenIdOrderByCreadoEnAscIdAsc(inspeccion.getId()).stream()
        .map(mapper::toResponse)
        .toList();
  }
}
