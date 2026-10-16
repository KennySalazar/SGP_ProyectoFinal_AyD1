package gt.usac.cunoc.sgp.archivo.service;

import gt.usac.cunoc.sgp.archivo.model.ImagenProcesada;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.security.*;
import java.util.HexFormat;
import javax.imageio.*;
import org.springframework.stereotype.Service;

@Service
public class ProcesadorImagenes {
  private final InspectorImagenesImageIo inspector;

  public ProcesadorImagenes(InspectorImagenesImageIo inspector) {
    this.inspector = inspector;
  }

  /** Normaliza JPEG y WebP a JPEG RGB sin EXIF; el llamador conserva GPS/fecha por separado. */
  public ImagenProcesada procesar(Path archivo) throws IOException {
    var decodificada = inspector.decodificar(archivo);
    BufferedImage original = escalar(decodificada.imagen(), 1600);
    byte[] bytes = jpeg(original, 0.82f);
    byte[] miniatura = jpeg(escalar(original, 300), 0.8f);
    return new ImagenProcesada(
        bytes, miniatura, "image/jpeg", original.getWidth(), original.getHeight(), checksum(bytes));
  }

  static BufferedImage escalar(BufferedImage fuente, int maximo) {
    double factor =
        Math.min(1.0, (double) maximo / Math.max(fuente.getWidth(), fuente.getHeight()));
    int ancho = Math.max(1, (int) Math.round(fuente.getWidth() * factor)),
        alto = Math.max(1, (int) Math.round(fuente.getHeight() * factor));
    BufferedImage destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = destino.createGraphics();
    try {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, ancho, alto);
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.drawImage(fuente, 0, 0, ancho, alto, null);
    } finally {
      g.dispose();
    }
    return destino;
  }

  private byte[] jpeg(BufferedImage imagen, float calidad) throws IOException {
    var escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
    try (var bytes = new ByteArrayOutputStream();
        var salida = ImageIO.createImageOutputStream(bytes)) {
      escritor.setOutput(salida);
      var parametros = escritor.getDefaultWriteParam();
      parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      parametros.setCompressionQuality(calidad);
      escritor.write(null, new IIOImage(imagen, null, null), parametros);
      salida.flush();
      return bytes.toByteArray();
    } finally {
      escritor.dispose();
    }
  }

  public static String checksum(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
