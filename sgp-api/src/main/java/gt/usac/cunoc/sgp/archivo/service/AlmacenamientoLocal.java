package gt.usac.cunoc.sgp.archivo.service;

import java.io.*;
import java.nio.file.*;

public class AlmacenamientoLocal implements AlmacenamientoArchivos {
  private final Path raiz;
  private final boolean permisosPosix;

  public AlmacenamientoLocal(Path directorio) throws IOException {
    Files.createDirectories(directorio);
    if (Files.isSymbolicLink(directorio))
      throw new IOException("Directorio de almacenamiento no permitido");
    raiz = directorio.toRealPath();
    permisosPosix =
        Files.getFileStore(raiz)
            .supportsFileAttributeView(java.nio.file.attribute.PosixFileAttributeView.class);
  }

  private Path destino(String clave) {
    return raiz.resolve(ClaveObjeto.validar(clave));
  }

  public void guardar(String clave, InputStream contenido, long tamano, String mime)
      throws IOException {
    if (tamano <= 0) throw new IOException("Tamaño de objeto inválido");
    Path destino = destino(clave), temporal = raiz.resolve(clave + ".pendiente");
    if (permisosPosix) {
      Files.createFile(
          temporal,
          java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
              java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
    } else {
      Files.createFile(temporal);
    }
    try {
      long escritos = 0;
      try (var salida = Files.newOutputStream(temporal)) {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = contenido.read(buffer)) != -1) {
          escritos += n;
          if (escritos > tamano) throw new IOException("Longitud de objeto excedida");
          salida.write(buffer, 0, n);
        }
      }
      if (escritos != tamano) throw new IOException("Longitud de objeto diferente a la declarada");
      Files.createLink(destino, temporal);
    } finally {
      Files.deleteIfExists(temporal);
    }
  }

  public InputStream abrir(String clave) throws IOException {
    return Files.newInputStream(destino(clave), LinkOption.NOFOLLOW_LINKS);
  }

  public boolean existe(String clave) {
    return Files.isRegularFile(destino(clave), LinkOption.NOFOLLOW_LINKS);
  }

  public void eliminar(String clave) throws IOException {
    Files.deleteIfExists(destino(clave));
    Files.deleteIfExists(raiz.resolve(ClaveObjeto.validar(clave) + ".pendiente"));
  }
}
