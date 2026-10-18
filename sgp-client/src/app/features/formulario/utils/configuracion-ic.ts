import { reglasIcCompletas } from './reglas-ic';
import { VersionFormulario } from '../models/formulario.models';
export const NIVELES_SEVERIDAD = ['NO_PRESENTA', 'LEVE', 'MODERADO', 'SEVERO'] as const;
export type NivelSeveridad = (typeof NIVELES_SEVERIDAD)[number];
export type EscalaIcEditable = Record<NivelSeveridad, number | null>;
export interface PesoIcEditable {
  elementoRef: string;
  descripcion: string | null;
  peso: number | null;
}
function objeto(valor: unknown): Record<string, unknown> {
  return valor !== null && typeof valor === 'object' && !Array.isArray(valor)
    ? (valor as Record<string, unknown>)
    : {};
}
export function elementosCalibracion(version: VersionFormulario): PesoIcEditable[] {
  const elementos = version.reglasEvaluacionIc?.['elementos'];
  const guardados = new Map(version.pesos.map((p) => [p.elementoRef, p]));
  const resultado = new Map<string, PesoIcEditable>();
  if (Array.isArray(elementos)) {
    for (const entrada of elementos) {
      const elemento = objeto(entrada);
      const ref = elemento['elemento_ref'];
      if (typeof ref !== 'string' || !ref.trim() || resultado.has(ref)) continue;
      const guardado = guardados.get(ref);
      resultado.set(ref, {
        elementoRef: ref,
        descripcion:
          guardado?.descripcion ??
          (typeof elemento['nombre'] === 'string' ? elemento['nombre'] : null),
        peso: guardado?.peso ?? null,
      });
    }
  }
  for (const peso of version.pesos) {
    if (!resultado.has(peso.elementoRef))
      resultado.set(peso.elementoRef, {
        elementoRef: peso.elementoRef,
        descripcion: peso.descripcion ?? null,
        peso: peso.peso,
      });
  }
  return [...resultado.values()];
}
export function escalaCalibracion(version: VersionFormulario): EscalaIcEditable {
  const reglas = objeto(version.reglasEvaluacionIc?.['reglas_evaluacion_ic']);
  const escala = objeto(reglas['escala_severidad']);
  const valor = (nivel: NivelSeveridad) =>
    typeof escala[nivel] === 'number' ? (escala[nivel] as number) : null;
  return {
    NO_PRESENTA: valor('NO_PRESENTA'),
    LEVE: valor('LEVE'),
    MODERADO: valor('MODERADO'),
    SEVERO: valor('SEVERO'),
  };
}
export function pesoValido(peso: number | null): peso is number {
  return typeof peso === 'number' && Number.isFinite(peso) && peso > 0;
}
export function escalaValida(escala: EscalaIcEditable): boolean {
  return (
    escala.NO_PRESENTA === 0 &&
    NIVELES_SEVERIDAD.every((nivel) => {
      const valor = escala[nivel];
      return typeof valor === 'number' && Number.isFinite(valor) && valor >= 0 && valor <= 4;
    })
  );
}
export function configuracionIcCompleta(version: VersionFormulario): boolean {
  const elementos = elementosCalibracion(version);
  const guardados = new Map(version.pesos.map((p) => [p.elementoRef, p.peso]));
  return (
    elementos.length > 0 &&
    elementos.every((e) => pesoValido(guardados.get(e.elementoRef) ?? null)) &&
    escalaValida(escalaCalibracion(version)) &&
    reglasIcCompletas(version)
  );
}
