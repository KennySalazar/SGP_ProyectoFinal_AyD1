package gt.usac.cunoc.sgp.formulario.validation;

/** Error de dominio; el consumidor decide cómo traducirlo al transporte que utilice. */
public class AnclajeInvalidoException extends RuntimeException {
  public enum Motivo {
    RUTA_INVALIDA,
    ELEMENTO_INEXISTENTE,
    REFERENCIA_ESQUEMA_INVALIDA,
    INSTANCIA_REQUERIDA,
    INSTANCIA_NO_ENCONTRADA,
    INSTANCIA_DUPLICADA,
    INSTANCIA_NO_PERMITIDA,
    DATOS_REPETIBLE_INVALIDOS,
    REPETIBLE_ANIDADO_NO_SOPORTADO,
    CATEGORIA_FOTOGRAFICA_INVALIDA
  }

  private final Motivo motivo;

  public AnclajeInvalidoException(Motivo motivo) {
    super("Anclaje inválido: " + motivo.name());
    this.motivo = motivo;
  }

  public Motivo getMotivo() {
    return motivo;
  }
}
