import { Condicion, NodoObjeto, RestriccionPropiedad } from './modelos';

/** Devuelve el valor actual de una propiedad del objeto evaluado. */
export type ObtenerValor = (clave: string) => unknown;

function sinValor(valor: unknown): boolean {
  return valor === undefined || valor === null || valor === '';
}

function cumpleRestriccion(restriccion: RestriccionPropiedad, valor: unknown): boolean {
  // Mientras la propiedad que decide no tenga valor, la condición no se da por cumplida. En JSON
  // Schema una propiedad ausente cumple `properties` por vacuidad; aquí el formulario espera a
  // que el inspector responda antes de mostrar lo que depende de esa respuesta.
  if (sinValor(valor)) return false;
  if ('const' in restriccion && valor !== restriccion.const) return false;
  if (restriccion.enum && !restriccion.enum.includes(valor)) return false;
  if ('contiene' in restriccion) {
    if (!Array.isArray(valor) || !valor.includes(restriccion.contiene)) return false;
  }
  if ('distintoDe' in restriccion && valor === restriccion.distintoDe) return false;
  return true;
}

export function cumpleCondicion(condicion: Condicion, valor: ObtenerValor): boolean {
  return Object.entries(condicion.propiedades).every(([clave, restriccion]) =>
    cumpleRestriccion(restriccion, valor(clave)),
  );
}

/**
 * Decide qué propiedades de un objeto están visibles según sus condiciones `if/then/else`.
 *
 * - Una propiedad exigida por `then.required` solo es visible si alguna de esas condiciones se cumple.
 * - Una propiedad prohibida por `else` (o todas las demás, con `maxProperties: 1`) se oculta cuando
 *   la condición no se cumple.
 * - Una propiedad oculta cuenta como sin valor al evaluar las condiciones que dependen de ella.
 */
export function calcularVisibilidad(
  nodo: NodoObjeto,
  valorBruto: ObtenerValor,
): Map<string, boolean> {
  const visibles = new Map<string, boolean>();
  const enCurso = new Set<string>();

  const esVisible = (clave: string): boolean => {
    const previo = visibles.get(clave);
    if (previo !== undefined) return previo;
    // Una dependencia circular no debería existir; en ese caso no se oculta nada.
    if (enCurso.has(clave)) return true;
    enCurso.add(clave);

    const valorEfectivo: ObtenerValor = (otra) => (esVisible(otra) ? valorBruto(otra) : undefined);

    let exigidaPor = 0;
    let exigidaCumplida = false;
    let permitida = true;

    for (const regla of nodo.reglas) {
      const cumple = cumpleCondicion(regla.cuando, valorEfectivo);
      const decide = clave in regla.cuando.propiedades;
      const aplicada = cumple ? regla.entonces : regla.sino;

      if (regla.entonces.requeridos.includes(clave)) {
        exigidaPor++;
        if (cumple) exigidaCumplida = true;
      }
      if (aplicada.prohibidos.includes(clave)) permitida = false;
      if (aplicada.soloDiscriminadores && !decide) permitida = false;
    }

    enCurso.delete(clave);
    const visible = permitida && (exigidaPor === 0 || exigidaCumplida);
    visibles.set(clave, visible);
    return visible;
  };

  nodo.hijos.forEach((hijo) => esVisible(hijo.clave));
  return visibles;
}
