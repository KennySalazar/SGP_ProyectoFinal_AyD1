import { VersionFormulario } from '../models/formulario.models';

export interface CampoCritico {
  ruta: string[];
  valor: string;
  lista: boolean;
}
export function objetoCritico(valor: unknown): valor is Record<string, unknown> {
  return valor !== null && typeof valor === 'object' && !Array.isArray(valor);
}
export function reglasCriticas(version: VersionFormulario): Record<string, unknown>[] {
  const reglas = version.reglasEvaluacionIc?.['reglas_evaluacion_ic'];
  const lista = objetoCritico(reglas) ? reglas['reglas_anulacion'] : null;
  return Array.isArray(lista) && lista.every(objetoCritico) ? lista : [];
}
export function criticasPendientes(version: VersionFormulario): boolean {
  const reglas = reglasCriticas(version);
  const configuracion = version.reglasEvaluacionIc?.['reglas_evaluacion_ic'];
  const provisional =
    objetoCritico(configuracion) && configuracion['calibracion_provisional'] === true;
  return (
    !reglas.length ||
    reglas.some((regla) => {
      if (regla['pendiente'] == null) return false;
      return !(
        provisional &&
        ['RN-IC-05a', 'RN-IC-05c'].includes(String(regla['id'])) &&
        typeof regla['pendiente'] === 'string' &&
        regla['pendiente'].trim().length > 0
      );
    })
  );
}
export function camposCriticos(
  regla: Record<string, unknown>,
  ruta: string[] = [],
): CampoCritico[] {
  return Object.entries(regla).flatMap(([clave, valor]) => {
    if (['id', 'pendiente', '$comment', 'estado_resultante'].includes(clave)) return [];
    const posicion = [...ruta, clave];
    if (typeof valor === 'string') return [{ ruta: posicion, valor, lista: false }];
    if (Array.isArray(valor) && valor.every((item) => typeof item === 'string'))
      return [{ ruta: posicion, valor: valor.join('\n'), lista: true }];
    return objetoCritico(valor) ? camposCriticos(valor, posicion) : [];
  });
}
export function actualizarCampoCritico(
  regla: Record<string, unknown>,
  campo: CampoCritico,
  texto: string,
): void {
  let destino = regla;
  for (const clave of campo.ruta.slice(0, -1)) {
    const valor = destino[clave];
    if (!objetoCritico(valor)) throw new Error('Ruta de regla invalida');
    destino = valor;
  }
  destino[campo.ruta[campo.ruta.length - 1]] = campo.lista
    ? texto
        .split('\n')
        .map((valor) => valor.trim())
        .filter(Boolean)
    : texto.trim();
}
