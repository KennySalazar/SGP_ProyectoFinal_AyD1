package gt.usac.cunoc.sgp.formulario.validation;

import static org.assertj.core.api.Assertions.*;

import gt.usac.cunoc.sgp.common.exception.ApiException;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ValidadorActaPdfTest {
  private final ValidadorActaPdf validador = new ValidadorActaPdf();

  @Test
  void aceptaContenidoPdfRealAunqueElMimeDeclaradoSeaGenerico() throws Exception {
    byte[] contenido = pdf();
    assertThat(
            validador.validar(
                new MockMultipartFile(
                    "archivo", "acta.pdf", "application/octet-stream", contenido)))
        .isEqualTo(contenido);
  }

  @Test
  void rechazaTextoRenombradoComoPdf() {
    assertThatThrownBy(
            () ->
                validador.validar(
                    new MockMultipartFile(
                        "archivo", "acta.pdf", "application/pdf", "texto".getBytes())))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("acta_pdf_invalida");
  }

  @Test
  void rechazaCabeceraPdfConContenidoCorrupto() {
    assertThatThrownBy(
            () ->
                validador.validar(
                    new MockMultipartFile("archivo", "%PDF-1.7\nno es PDF".getBytes())))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("acta_pdf_invalida");
  }

  @Test
  void rechazaArchivoVacioONulo() {
    assertThatThrownBy(() -> validador.validar(null)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> validador.validar(new MockMultipartFile("archivo", new byte[0])))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void rechazaPdfSinPaginas() throws Exception {
    try (var documento = new PDDocument();
        var salida = new ByteArrayOutputStream()) {
      documento.save(salida);
      assertThatThrownBy(
              () -> validador.validar(new MockMultipartFile("archivo", salida.toByteArray())))
          .isInstanceOf(ApiException.class);
    }
  }

  @Test
  void rechazaArchivoMayorAlLimite() {
    assertThatThrownBy(
            () ->
                validador.validar(
                    new MockMultipartFile("archivo", new byte[ValidadorActaPdf.MAXIMO_BYTES + 1])))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("acta_demasiado_grande");
  }

  private byte[] pdf() throws Exception {
    try (var documento = new PDDocument();
        var salida = new ByteArrayOutputStream()) {
      documento.addPage(new PDPage());
      documento.save(salida);
      return salida.toByteArray();
    }
  }
}
