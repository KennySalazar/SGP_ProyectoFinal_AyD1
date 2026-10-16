package gt.usac.cunoc.sgp.archivo.service;

final class ClaveObjeto {
  private ClaveObjeto() {}

  static String validar(String clave) {
    if (clave == null || !clave.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,199}") || clave.contains(".."))
      throw new IllegalArgumentException("Clave de objeto inválida");
    return clave;
  }
}
