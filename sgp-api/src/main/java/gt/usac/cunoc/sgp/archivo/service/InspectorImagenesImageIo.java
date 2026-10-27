package gt.usac.cunoc.sgp.archivo.service;

import gt.usac.cunoc.sgp.archivo.model.ImagenInspeccionada;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class InspectorImagenesImageIo implements InspectorImagenes {
  static final long MAX_BYTES = 20L * 1024 * 1024;
  static final long MAX_PIXELES = 40_000_000L;

  record Decodificada(BufferedImage imagen, String mime) {}

  public ImagenInspeccionada inspeccionar(Path archivoTemporal) {
    var d = decodificar(archivoTemporal);
    return new ImagenInspeccionada(d.mime(), d.imagen().getWidth(), d.imagen().getHeight());
  }

  Decodificada decodificar(Path archivo) {
    try {
      long tamano = Files.size(archivo);
      if (tamano == 0 || tamano > MAX_BYTES) throw invalida();
      try (ImageInputStream entrada = ImageIO.createImageInputStream(archivo.toFile())) {
        if (entrada == null) throw invalida();
        var lectores = ImageIO.getImageReaders(entrada);
        if (!lectores.hasNext()) throw invalida();
        ImageReader lector = lectores.next();
        try {
          String formato = lector.getFormatName().toLowerCase(Locale.ROOT);
          String mime =
              switch (formato) {
                case "jpeg", "jpg" -> "image/jpeg";
                case "webp" -> "image/webp";
                default -> throw invalida();
              };
          lector.setInput(entrada, true, true);
          int ancho = lector.getWidth(0), alto = lector.getHeight(0);
          if (ancho <= 0 || alto <= 0 || (long) ancho * alto > MAX_PIXELES) throw invalida();
          lector.addIIOReadWarningListener(
              (source, warning) -> {
                throw invalida();
              });
          BufferedImage imagen = lector.read(0);
          if (imagen == null) throw invalida();
          return new Decodificada(imagen, mime);
        } finally {
          lector.dispose();
        }
      }
    } catch (IOException | IllegalArgumentException e) {
      throw invalida();
    }
  }

  private ApiException invalida() {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "contenido_fotografico_invalido",
        "Imagen inválida",
        "Se requiere una imagen JPEG/WebP íntegra y dentro de los límites de procesamiento.");
  }
}
