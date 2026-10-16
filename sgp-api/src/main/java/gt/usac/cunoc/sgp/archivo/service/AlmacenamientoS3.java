package gt.usac.cunoc.sgp.archivo.service;

import java.io.*;
import java.nio.file.NoSuchFileException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

public class AlmacenamientoS3 implements AlmacenamientoArchivos {
  private final S3Client cliente;
  private final String bucket;

  public AlmacenamientoS3(S3Client cliente, String bucket) {
    this.cliente = cliente;
    this.bucket = bucket;
  }

  public void guardar(String clave, InputStream contenido, long tamano, String mime)
      throws IOException {
    if (tamano <= 0) throw new IOException("Tamaño de objeto inválido");
    try {
      cliente.putObject(
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(ClaveObjeto.validar(clave))
              .contentType(mime)
              .ifNoneMatch("*")
              .build(),
          RequestBody.fromInputStream(contenido, tamano));
    } catch (RuntimeException e) {
      throw new IOException("No se pudo guardar el objeto", e);
    }
  }

  public InputStream abrir(String clave) throws IOException {
    try {
      return cliente.getObject(
          GetObjectRequest.builder().bucket(bucket).key(ClaveObjeto.validar(clave)).build());
    } catch (S3Exception e) {
      if (e.statusCode() == 404) throw new NoSuchFileException(clave);
      throw new IOException("No se pudo abrir el objeto", e);
    } catch (RuntimeException e) {
      throw new IOException("No se pudo abrir el objeto", e);
    }
  }

  public boolean existe(String clave) throws IOException {
    try {
      cliente.headObject(
          HeadObjectRequest.builder().bucket(bucket).key(ClaveObjeto.validar(clave)).build());
      return true;
    } catch (S3Exception e) {
      if (e.statusCode() == 404) return false;
      throw new IOException("No se pudo consultar el objeto", e);
    } catch (RuntimeException e) {
      throw new IOException("No se pudo consultar el objeto", e);
    }
  }

  public void eliminar(String clave) throws IOException {
    try {
      cliente.deleteObject(
          DeleteObjectRequest.builder().bucket(bucket).key(ClaveObjeto.validar(clave)).build());
    } catch (RuntimeException e) {
      throw new IOException("No se pudo eliminar el objeto", e);
    }
  }
}
