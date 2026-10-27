package gt.usac.cunoc.sgp.archivo.service;

import gt.usac.cunoc.sgp.archivo.mapper.FotografiaMapper;
import gt.usac.cunoc.sgp.archivo.model.*;
import gt.usac.cunoc.sgp.archivo.repository.ArchivoInspeccionRepository;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true)
public class RegistroFotografiasInspeccionJpa implements RegistroFotografiasInspeccion {
  private final ArchivoInspeccionRepository repositorio;
  private final FotografiaMapper mapper;

  public RegistroFotografiasInspeccionJpa(
      ArchivoInspeccionRepository repositorio, FotografiaMapper mapper) {
    this.repositorio = repositorio;
    this.mapper = mapper;
  }

  public List<FotografiaRegistrada> consultar(UUID inspeccionId) {
    return repositorio
        .findByInspeccionIdAndTipoUsoOrderByCreadoEnAsc(
            inspeccionId, TipoUsoArchivo.FOTO_INSPECCION)
        .stream()
        .map(mapper::toRegistrada)
        .toList();
  }

  public Optional<FotografiaRegistrada> buscar(UUID inspeccionId, UUID fotografiaId) {
    return repositorio
        .findByInspeccionIdAndArchivoIdAndTipoUso(
            inspeccionId, fotografiaId, TipoUsoArchivo.FOTO_INSPECCION)
        .map(mapper::toRegistrada);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void retirar(UUID inspeccionId, UUID fotografiaId, Instant retiradoEn) {
    var vinculo =
        repositorio
            .findByInspeccionIdAndArchivoIdAndTipoUso(
                inspeccionId, fotografiaId, TipoUsoArchivo.FOTO_INSPECCION)
            .orElseThrow(this::noEncontrada);
    if (vinculo.getArchivo().getEstado() == EstadoArchivo.PURGADO) throw noEncontrada();
    vinculo.getArchivo().retirar(Objects.requireNonNull(retiradoEn));
  }

  private ApiException noEncontrada() {
    return new ApiException(
        HttpStatus.NOT_FOUND,
        "fotografia_no_encontrada",
        "Fotografía no encontrada",
        "No existe la fotografía solicitada.");
  }
}
