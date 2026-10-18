package gt.usac.cunoc.sgp.formulario.validation;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ValidadorActaPdf {
    public static final int MAXIMO_BYTES = 20 * 1024 * 1024;

    public byte[] validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw invalido("Debe adjuntar un acta en PDF con contenido.");
        }
        if (archivo.getSize() > MAXIMO_BYTES) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "acta_demasiado_grande",
                    "Acta demasiado grande",
                    "El acta admite hasta 20 MB.");
        }
        byte[] contenido;
        try (var entrada = archivo.getInputStream()) {
            contenido = entrada.readNBytes(MAXIMO_BYTES + 1);
        } catch (IOException excepcion) {
            throw invalido("No fue posible leer el archivo adjunto.");
        }
        if (contenido.length > MAXIMO_BYTES) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "acta_demasiado_grande",
                    "Acta demasiado grande",
                    "El acta admite hasta 20 MB.");
        }
        if (contenido.length < 5
                || !"%PDF-".equals(new String(contenido, 0, 5, StandardCharsets.US_ASCII))) {
            throw invalido("El contenido real del acta no corresponde a un PDF.");
        }
        try (var fuente = new RandomAccessReadBuffer(contenido);
             var documento = new PDFParser(fuente).parse(false)) {
            if (documento.isEncrypted() || documento.getNumberOfPages() == 0) {
                throw invalido("El acta debe ser un PDF legible, sin cifrado y con al menos una pagina.");
            }
            for (var pagina : documento.getPages()) {
                pagina.getCOSObject();
            }
        } catch (IOException | IllegalArgumentException excepcion) {
            throw invalido("El contenido real del acta no es un PDF valido o requiere una contrasena.");
        }
        return contenido;
    }

    private ApiException invalido(String detalle) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, "acta_pdf_invalida", "Acta PDF invalida", detalle);
    }
}