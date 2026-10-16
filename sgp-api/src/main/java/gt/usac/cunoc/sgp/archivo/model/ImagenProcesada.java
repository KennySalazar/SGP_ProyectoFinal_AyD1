package gt.usac.cunoc.sgp.archivo.model;

public record ImagenProcesada(
    byte[] original,
    byte[] miniatura,
    String mimeReal,
    int anchoPx,
    int altoPx,
    String checksumSha256) {
  public ImagenProcesada {
    original = original.clone();
    miniatura = miniatura.clone();
  }

  @Override
  public byte[] original() {
    return original.clone();
  }

  @Override
  public byte[] miniatura() {
    return miniatura.clone();
  }
}
