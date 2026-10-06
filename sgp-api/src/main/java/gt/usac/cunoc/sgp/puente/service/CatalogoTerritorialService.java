package gt.usac.cunoc.sgp.puente.service;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.puente.dto.DepartamentoResponse;
import gt.usac.cunoc.sgp.puente.dto.MunicipioResponse;
import gt.usac.cunoc.sgp.puente.mapper.PuenteMapper;
import gt.usac.cunoc.sgp.puente.repository.DepartamentoRepository;
import gt.usac.cunoc.sgp.puente.repository.MunicipioRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class CatalogoTerritorialService {

  private final DepartamentoRepository departamentoRepository;
  private final MunicipioRepository municipioRepository;
  private final PuenteMapper puenteMapper;

  public CatalogoTerritorialService(
      DepartamentoRepository departamentoRepository,
      MunicipioRepository municipioRepository,
      PuenteMapper puenteMapper) {
    this.departamentoRepository = departamentoRepository;
    this.municipioRepository = municipioRepository;
    this.puenteMapper = puenteMapper;
  }

  @PreAuthorize("permitAll()")
  public Page<DepartamentoResponse> listarDepartamentos(int pagina, int tamanio) {
    Pageable pageable = crearPaginacion(pagina, tamanio);

    return departamentoRepository
        .findByActivoTrue(pageable)
        .map(puenteMapper::toDepartamentoResponse);
  }

  public Page<MunicipioResponse> listarMunicipios(UUID departamentoId, int pagina, int tamanio) {
    Pageable pageable = crearPaginacion(pagina, tamanio);

    departamentoRepository
        .findByIdAndActivoTrue(departamentoId)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "departamento_no_encontrado",
                    "Departamento no encontrado",
                    "El departamento indicado no existe o no está activo."));

    return municipioRepository
        .findByDepartamentoIdAndActivoTrue(departamentoId, pageable)
        .map(puenteMapper::toMunicipioResponse);
  }

  private Pageable crearPaginacion(int pagina, int tamanio) {
    if (pagina < 0 || tamanio < 1 || tamanio > 100) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "paginacion_invalida",
          "Paginación inválida",
          "La página debe ser mayor o igual a 0 y el tamaño debe estar entre 1 y 100.");
    }

    return PageRequest.of(pagina, tamanio, Sort.by(Sort.Order.asc("nombre"), Sort.Order.asc("id")));
  }
}
