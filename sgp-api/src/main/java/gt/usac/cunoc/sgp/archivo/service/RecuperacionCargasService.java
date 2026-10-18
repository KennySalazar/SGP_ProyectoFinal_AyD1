package gt.usac.cunoc.sgp.archivo.service;

import gt.usac.cunoc.sgp.archivo.model.ClavesImagen;
import gt.usac.cunoc.sgp.sync.service.OperacionIdempotenteService;
import java.io.*;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RecuperacionCargasService {
  private static final Logger LOG = LoggerFactory.getLogger(RecuperacionCargasService.class);
  private final OperacionIdempotenteService operaciones;
  private final ObjetosImagenService objetos;

  public RecuperacionCargasService(
      OperacionIdempotenteService operaciones, ObjetosImagenService objetos) {
    this.operaciones = operaciones;
    this.objetos = objetos;
  }

  @Scheduled(fixedDelayString = "${app.archivos.recuperacion-intervalo-ms:60000}")
  @SchedulerLock(name = "recuperar-cargas-archivos", lockAtMostFor = "PT10M")
  public void recuperarVencidas() {
    for (var id : operaciones.vencidas()) {
      try {
        operaciones.recuperar(
            id,
            recursos -> {
              if (!"imagen".equals(recursos.path("tipo").asText()))
                throw new IllegalArgumentException("Manifiesto no administrado por fotografías");
              try {
                objetos.eliminar(
                    new ClavesImagen(
                        recursos.path("original").asText(), recursos.path("miniatura").asText()));
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
      } catch (RuntimeException e) {
        LOG.warn("No se pudo recuperar la operación {}; se conservará para reintento", id);
      }
    }
  }
}
