package gt.usac.cunoc.sgp.inspeccion.service;

import gt.usac.cunoc.sgp.archivo.model.EstadoArchivo;
import gt.usac.cunoc.sgp.archivo.service.RegistroFotografiasInspeccion;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.inspeccion.model.ContextoArchivosInspeccion;
import gt.usac.cunoc.sgp.inspeccion.repository.InspeccionRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetiradaFotografiaService {
  private final InspeccionRepository inspecciones;
  private final RegistroFotografiasInspeccion registro;
  private final PoliticaArchivosInspeccion politica;
  private final Clock clock;

  public RetiradaFotografiaService(
      InspeccionRepository inspecciones,
      RegistroFotografiasInspeccion registro,
      PoliticaArchivosInspeccion politica,
      Clock clock) {
    this.inspecciones = inspecciones;
    this.registro = registro;
    this.politica = politica;
    this.clock = clock;
  }

  @Transactional
  public void retirar(UUID inspeccionId, UUID fotografiaId, UUID actorId) {
    if (actorId == null) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "autenticacion_requerida",
          "Autenticación requerida",
          "Se requiere una sesión válida.");
    }
    var inspeccion =
        inspecciones
            .findByIdAndEliminadoEnIsNullForUpdate(inspeccionId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "inspeccion_no_encontrada",
                        "Inspección no encontrada",
                        "No se encontró la inspección solicitada."));
    politica.validarRetirada(
        new ContextoArchivosInspeccion(
            inspeccion.getId(),
            inspeccion.getAutorId(),
            inspeccion.getEstado(),
            inspeccion.getEliminadoEn()),
        actorId);
    registro
        .buscar(inspeccionId, fotografiaId)
        .filter(foto -> foto.estado() != EstadoArchivo.PURGADO)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "fotografia_no_encontrada",
                    "Fotografía no encontrada",
                    "No existe la fotografía solicitada."));
    registro.retirar(inspeccionId, fotografiaId, clock.instant());
  }
}
