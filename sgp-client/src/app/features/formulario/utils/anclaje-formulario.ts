/** Cadena interna sin escapar, referencia canónica o claves originales separadas por segmento. */
export type RutaAnclajeFormulario = string | readonly string[];

export interface AnclajeFormulario {
  readonly elementoRef: string;
  readonly instanciaElementoId?: string;
}

/**
 * Las cadenas con `/` inicial ya son JSON Pointer: se validan y conservan sin volver a escapar.
 * Las demás cadenas son rutas internas del motor, separadas por `/` y sin escapar. Para una clave
 * con `/` literal se deben proporcionar sus segmentos originales, pues la cadena interna pierde
 * esa distinción. Los segmentos originales `~0` y `~1` son texto literal, no escapes previos.
 *
 * No comprueba la existencia del elemento ni su pertenencia a una instancia: eso corresponde al
 * validador del backend con el esquema histórico y los datos de la inspección.
 */
export function referenciaCanonicaFormulario(ruta: RutaAnclajeFormulario): string {
  if (typeof ruta === 'string' && ruta.startsWith('/')) {
    if (/~(?![01])/u.test(ruta)) throw referenciaInvalida();
    validarSegmentos(
      ruta
        .slice(1)
        .split('/')
        .map((segmento) => segmento.replace(/~1/g, '/').replace(/~0/g, '~')),
    );
    validarLongitud(ruta);
    return ruta;
  }

  const segmentos = typeof ruta === 'string' ? ruta.split('/') : ruta;
  validarSegmentos(segmentos);
  const referencia =
    '/' + segmentos.map((segmento) => segmento.replace(/~/g, '~0').replace(/\//g, '~1')).join('/');
  validarLongitud(referencia);
  return referencia;
}

/** Identidad local del anclaje; no construye un request ni transforma el UUID de la instancia. */
export function crearAnclajeFormulario(
  ruta: RutaAnclajeFormulario,
  instanciaElementoId?: string,
): AnclajeFormulario {
  const elementoRef = referenciaCanonicaFormulario(ruta);
  return instanciaElementoId === undefined ? { elementoRef } : { elementoRef, instanciaElementoId };
}

function validarSegmentos(segmentos: readonly string[]): void {
  if (segmentos.length === 0) throw referenciaInvalida();
  for (const segmento of segmentos) {
    if (segmento.trim().length === 0 || /^\d+$/u.test(segmento) || /\[\d+\]/u.test(segmento)) {
      throw referenciaInvalida();
    }
  }
}

function validarLongitud(referencia: string): void {
  if (referencia.length > 300) throw referenciaInvalida();
}

function referenciaInvalida(): Error {
  return new Error(
    'Referencia de formulario inválida: se requiere una ruta sin índices ni segmentos vacíos, de hasta 300 caracteres.',
  );
}
