package gt.usac.cunoc.sgp.archivo.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

class AlmacenamientoS3Test {
  @Test
  void consultaNoConfundeDenegacionConAusenciaYNoCambiaLaPoliticaDelBucket() throws Exception {
    var cliente = mock(S3Client.class);
    var almacenamiento = new AlmacenamientoS3(cliente, "privado");
    when(cliente.headObject(any(HeadObjectRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(404).build());
    assertThat(almacenamiento.existe("foto.jpg")).isFalse();
    when(cliente.headObject(any(HeadObjectRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(403).build());
    assertThatThrownBy(() -> almacenamiento.existe("foto.jpg")).isInstanceOf(IOException.class);
    var peticion = ArgumentCaptor.forClass(PutObjectRequest.class);
    almacenamiento.guardar("foto.jpg", new ByteArrayInputStream(new byte[] {1}), 1, "image/jpeg");
    verify(cliente).putObject(peticion.capture(), any(RequestBody.class));
    assertThat(peticion.getValue().ifNoneMatch()).isEqualTo("*");
    assertThat(peticion.getValue().acl()).isNull();
    assertThat(peticion.getValue().bucket()).isEqualTo("privado");
    assertThat(peticion.getValue().key()).isEqualTo("foto.jpg");
  }
}
