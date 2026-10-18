package gt.usac.cunoc.sgp.archivo.service;

import static org.assertj.core.api.Assertions.*;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ImagenesYAlmacenamientoTest {
  @TempDir Path directorio;
  final InspectorImagenesImageIo inspector = new InspectorImagenesImageIo();

  @Test
  void jpegYWebpSeInspeccionanPorContenidoYSeGeneraMiniaturaProporcional() throws Exception {
    var entrada = directorio.resolve("imagen.sin-extension");
    ImageIO.write(
        new BufferedImage(2400, 1200, BufferedImage.TYPE_INT_RGB), "jpeg", entrada.toFile());
    assertThat(inspector.inspeccionar(entrada).mimeReal()).isEqualTo("image/jpeg");
    var procesada = new ProcesadorImagenes(inspector).procesar(entrada);
    assertThat(procesada.anchoPx()).isEqualTo(1600);
    assertThat(procesada.altoPx()).isEqualTo(800);
    var mini = ImageIO.read(new ByteArrayInputStream(procesada.miniatura()));
    assertThat(mini.getWidth()).isEqualTo(300);
    assertThat(mini.getHeight()).isEqualTo(150);
    var webp = directorio.resolve("imagen.jpg");
    try (var recurso = getClass().getResourceAsStream("/archivo/imagen.webp")) {
      Files.copy(recurso, webp);
    }
    assertThat(inspector.inspeccionar(webp).mimeReal()).isEqualTo("image/webp");
    var pequena = new ProcesadorImagenes(inspector).procesar(webp);
    assertThat(pequena.anchoPx()).isEqualTo(40);
    assertThat(ImageIO.read(new ByteArrayInputStream(pequena.miniatura())).getWidth())
        .isEqualTo(40);
  }

  @Test
  void rechazaContenidoFalsoPngTruncadoYExcesoDeBytes() throws Exception {
    var ruta = directorio.resolve("falso.jpg");
    Files.writeString(ruta, "no es imagen");
    assertThatThrownBy(() -> inspector.inspeccionar(ruta)).isInstanceOf(ApiException.class);
    ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", ruta.toFile());
    assertThatThrownBy(() -> inspector.inspeccionar(ruta)).isInstanceOf(ApiException.class);
    ImageIO.write(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB), "jpeg", ruta.toFile());
    var bytes = Files.readAllBytes(ruta);
    Files.write(ruta, java.util.Arrays.copyOf(bytes, bytes.length / 2));
    assertThatThrownBy(() -> inspector.inspeccionar(ruta)).isInstanceOf(ApiException.class);
    try (var f = new RandomAccessFile(ruta.toFile(), "rw")) {
      f.setLength(20L * 1024 * 1024 + 1);
    }
    assertThatThrownBy(() -> inspector.inspeccionar(ruta)).isInstanceOf(ApiException.class);
  }

  @Test
  void rechazaDimensionesQueExcedenElLimiteAntesDeDecodificar() throws Exception {
    var ruta = directorio.resolve("enorme.jpg");
    ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "jpeg", ruta.toFile());
    var bytes = Files.readAllBytes(ruta);
    boolean cambiado = false;
    for (int i = 0; i < bytes.length - 9; i++) {
      if ((bytes[i] & 255) == 255 && (bytes[i + 1] & 255) == 192) {
        bytes[i + 5] = (byte) 255;
        bytes[i + 6] = (byte) 255;
        bytes[i + 7] = (byte) 255;
        bytes[i + 8] = (byte) 255;
        cambiado = true;
        break;
      }
    }
    assertThat(cambiado).isTrue();
    Files.write(ruta, bytes);
    assertThatThrownBy(() -> inspector.inspeccionar(ruta)).isInstanceOf(ApiException.class);
  }

  @Test
  void localNoReemplazaNiPermiteTraversalYEliminaSinFallarSiNoExiste() throws Exception {
    var storage = new AlmacenamientoLocal(directorio.resolve("objetos"));
    storage.guardar("uno.jpg", new ByteArrayInputStream(new byte[] {1, 2}), 2, "image/jpeg");
    assertThat(storage.existe("uno.jpg")).isTrue();
    assertThatThrownBy(
            () ->
                storage.guardar(
                    "uno.jpg", new ByteArrayInputStream(new byte[] {3}), 1, "image/jpeg"))
        .isInstanceOf(IOException.class);
    try (var in = storage.abrir("uno.jpg")) {
      assertThat(in.readAllBytes()).containsExactly(1, 2);
    }
    assertThatThrownBy(() -> storage.abrir("../secreto"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                storage.guardar(
                    "dos.jpg", new ByteArrayInputStream(new byte[] {1}), 2, "image/jpeg"))
        .isInstanceOf(IOException.class);
    assertThat(storage.existe("dos.jpg")).isFalse();
    storage.eliminar("uno.jpg");
    storage.eliminar("uno.jpg");
    assertThat(storage.existe("uno.jpg")).isFalse();
  }

  @Test
  void falloDeMiniaturaCompensaOriginalSinEliminarOtroObjeto() throws Exception {
    var real = new AlmacenamientoLocal(directorio);
    var almacenamiento =
        new AlmacenamientoArchivos() {
          public void guardar(String clave, InputStream in, long tamano, String mime)
              throws IOException {
            if (clave.contains("mini")) throw new IOException("fallo simulado");
            real.guardar(clave, in, tamano, mime);
          }

          public InputStream abrir(String clave) throws IOException {
            return real.abrir(clave);
          }

          public boolean existe(String clave) throws IOException {
            return real.existe(clave);
          }

          public void eliminar(String clave) throws IOException {
            real.eliminar(clave);
          }
        };
    var servicio = new ObjetosImagenService(almacenamiento);
    var claves = servicio.nuevasClaves();
    var entrada = directorio.resolve("entrada.jpg");
    ImageIO.write(new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB), "jpeg", entrada.toFile());
    var imagen = new ProcesadorImagenes(inspector).procesar(entrada);
    assertThatThrownBy(() -> servicio.guardar(claves, imagen)).isInstanceOf(IOException.class);
    assertThat(real.existe(claves.original())).isFalse();
    var otro = new ObjetosImagenService(real);
    var repetidas = otro.nuevasClaves();
    otro.guardar(repetidas, imagen);
    assertThatThrownBy(() -> otro.guardar(repetidas, imagen)).isInstanceOf(IOException.class);
    assertThat(real.existe(repetidas.original())).isTrue();
    assertThat(real.existe(repetidas.miniatura())).isTrue();
  }
}
