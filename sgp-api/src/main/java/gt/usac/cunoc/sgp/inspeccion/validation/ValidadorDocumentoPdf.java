package gt.usac.cunoc.sgp.inspeccion.validation;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Valida el tipo real de un documento de inspección (DT-SEC-08, RN-ARC-04): la firma binaria {@code
 * %PDF-} y que PDFBox lo pueda leer, sin confiar en la extensión ni en el tipo declarado.
 */
@Component
public class ValidadorDocumentoPdf {

  public static final int MAXIMO_BYTES = 20 * 1024 * 1024;

  /** Devuelve el contenido ya validado; 413 si excede 20 MB y 422 si no es un PDF legible. */
  public byte[] validar(MultipartFile archivo) {
    if (archivo == null || archivo.isEmpty()) {
      throw invalido("Seleccione un documento PDF con contenido.");
    }
    if (archivo.getSize() > MAXIMO_BYTES) {
      throw demasiadoGrande();
    }
    byte[] contenido;
    try (var entrada = archivo.getInputStream()) {
      contenido = entrada.readNBytes(MAXIMO_BYTES + 1);
    } catch (IOException excepcion) {
      throw invalido("No fue posible leer el documento adjunto.");
    }
    if (contenido.length > MAXIMO_BYTES) {
      throw demasiadoGrande();
    }
    if (contenido.length < 5
        || !"%PDF-".equals(new String(contenido, 0, 5, StandardCharsets.US_ASCII))) {
      throw invalido("El contenido real del archivo no corresponde a un documento PDF.");
    }
    try (var fuente = new RandomAccessReadBuffer(contenido);
        var documento = new PDFParser(fuente).parse(false)) {
      if (documento.isEncrypted() || documento.getNumberOfPages() == 0) {
        throw invalido("El documento debe ser un PDF legible, sin contraseña y con páginas.");
      }
    } catch (IOException | IllegalArgumentException excepcion) {
      throw invalido("El archivo no es un PDF válido o requiere una contraseña.");
    }
    return contenido;
  }

  private static ApiException demasiadoGrande() {
    return new ApiException(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "documento_demasiado_grande",
        "Documento demasiado grande",
        "Cada documento PDF admite hasta 20 MB.");
  }

  private static ApiException invalido(String detalle) {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "documento_pdf_invalido",
        "Documento PDF inválido",
        detalle);
  }
}
