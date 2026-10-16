package gt.usac.cunoc.sgp.archivo.service;

import java.io.*;

/** Objetos privados. Las claves son opacas y generadas por el servidor; guardar nunca reemplaza. */
public interface AlmacenamientoArchivos {
  void guardar(String clave, InputStream contenido, long tamano, String mime) throws IOException;

  InputStream abrir(String clave) throws IOException;

  boolean existe(String clave) throws IOException;

  void eliminar(String clave) throws IOException;
}
