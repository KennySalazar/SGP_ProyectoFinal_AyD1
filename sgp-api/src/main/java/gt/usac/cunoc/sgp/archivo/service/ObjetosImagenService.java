package gt.usac.cunoc.sgp.archivo.service;

import gt.usac.cunoc.sgp.archivo.model.*;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ObjetosImagenService {
  private final AlmacenamientoArchivos almacenamiento;

  public ObjetosImagenService(AlmacenamientoArchivos almacenamiento) {
    this.almacenamiento = almacenamiento;
  }

  public ClavesImagen nuevasClaves() {
    String prefijo = UuidV7Generator.generate() + "-" + UUID.randomUUID();
    return new ClavesImagen(prefijo + ".jpg", prefijo + "-mini.jpg");
  }

  /** Las claves deben reservarse antes de escribir y ser exclusivas de este intento. */
  public void guardar(ClavesImagen claves, ImagenProcesada imagen) throws IOException {
    var escritos = new ArrayList<String>();
    try {
      guardarVerificado(claves.original(), imagen.original(), escritos);
      guardarVerificado(claves.miniatura(), imagen.miniatura(), escritos);
    } catch (IOException | RuntimeException e) {
      for (String clave : escritos) {
        try {
          almacenamiento.eliminar(clave);
        } catch (IOException limpieza) {
          e.addSuppressed(limpieza);
        }
      }
      throw e;
    }
  }

  private void guardarVerificado(String clave, byte[] bytes, List<String> escritos)
      throws IOException {
    almacenamiento.guardar(clave, new ByteArrayInputStream(bytes), bytes.length, "image/jpeg");
    escritos.add(clave);
    try (var entrada = almacenamiento.abrir(clave)) {
      byte[] leidos = entrada.readNBytes(bytes.length + 1);
      if (!ProcesadorImagenes.checksum(bytes).equals(ProcesadorImagenes.checksum(leidos)))
        throw new IOException("El objeto almacenado no coincide con el contenido procesado");
    }
  }

  public void eliminar(ClavesImagen claves) throws IOException {
    IOException fallo = null;
    for (String clave : new String[] {claves.original(), claves.miniatura()}) {
      try {
        almacenamiento.eliminar(clave);
      } catch (IOException e) {
        if (fallo == null) fallo = e;
        else fallo.addSuppressed(e);
      }
    }
    if (fallo != null) throw fallo;
  }
}
