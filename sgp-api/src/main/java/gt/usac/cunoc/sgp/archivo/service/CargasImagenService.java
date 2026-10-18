package gt.usac.cunoc.sgp.archivo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.archivo.model.*;
import gt.usac.cunoc.sgp.sync.model.SolicitudOperacion;
import gt.usac.cunoc.sgp.sync.service.OperacionIdempotenteService;
import java.io.IOException;
import org.springframework.stereotype.Service;

@Service
public class CargasImagenService {
  private final OperacionIdempotenteService operaciones;
  private final ObjetosImagenService objetos;
  private final ObjectMapper json;

  public CargasImagenService(
      OperacionIdempotenteService operaciones, ObjetosImagenService objetos, ObjectMapper json) {
    this.operaciones = operaciones;
    this.objetos = objetos;
    this.json = json;
  }

  /**
   * La huella incluye destino, metadatos y checksum. Autorizar antes de preparar. Después, el
   * dominio confirma Archivo/asociación y operaciones.completar en la misma transacción.
   */
  public CargaImagenPreparada preparar(SolicitudOperacion solicitud, ImagenProcesada imagen)
      throws IOException {
    var claves = objetos.nuevasClaves();
    var recursos =
        json.createObjectNode()
            .put("tipo", "imagen")
            .put("original", claves.original())
            .put("miniatura", claves.miniatura());
    var reserva = operaciones.reservar(solicitud, recursos);
    if (reserva.completada()) return new CargaImagenPreparada(reserva, null);
    try {
      objetos.guardar(claves, imagen);
    } catch (IOException | RuntimeException e) {
      try {
        operaciones.compensar(
            reserva,
            () -> {
              try {
                objetos.eliminar(claves);
              } catch (IOException fallo) {
                throw new java.io.UncheckedIOException(fallo);
              }
            });
      } catch (RuntimeException limpieza) {
        e.addSuppressed(limpieza);
      }
      throw e;
    }
    return new CargaImagenPreparada(reserva, claves);
  }
}
