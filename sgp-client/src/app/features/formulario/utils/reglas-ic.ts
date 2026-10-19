import { VersionFormulario } from '../models/formulario.models';

export interface CampoReglaIc {
  clave: string;
  ruta: string[];
  grupo: string;
  etiqueta: string;
  tipo: 'numero' | 'agregacion' | 'texto';
  maximo?: number;
  valorFijo?: number;
}
export type ValoresReglasIc = Record<string, number | string | null>;

export function objetoIc(valor: unknown): Record<string, unknown> {
  return valor !== null && typeof valor === 'object' && !Array.isArray(valor)
    ? (valor as Record<string, unknown>)
    : {};
}

export function camposReglasIc(version: VersionFormulario): CampoReglaIc[] {
  const campos: CampoReglaIc[] = [];
  const agregar = (
    ruta: string[],
    grupo: string,
    etiqueta: string,
    tipo: CampoReglaIc['tipo'] = 'numero',
    maximo?: number,
  ) => {
    const clave = JSON.stringify(ruta);
    if (!campos.some((c) => c.clave === clave))
      campos.push({ clave, ruta, grupo, etiqueta, tipo, maximo });
  };
  agregar(['version_algoritmo'], 'general', 'algoritmo', 'texto');
  for (const nombre of ['indicadores_de_un_elemento', 'instancias_repetibles'])
    agregar(['agregacion', nombre], 'agregacion', nombre, 'agregacion');
  for (const nombre of ['elementos_ausentes_portantes', 'elementos_ausentes_no_portantes'])
    agregar(['agregacion', nombre], 'agregacion', nombre, 'numero', 4);
  for (const estado of ['BUENO', 'REGULAR', 'MALO'])
    agregar(['rangos_estado', estado], 'rangos', estado, 'numero', 100);
  const rangos: Record<string, number> = { BUENO: 75, REGULAR: 50, MALO: 0 };
  for (const campo of campos) if (campo.grupo === 'rangos') campo.valorFijo = rangos[campo.ruta[1]];
  const elementos = version.reglasEvaluacionIc['elementos'];
  if (!Array.isArray(elementos)) return campos;
  const booleano = (familia: string, nombre: string) => {
    for (const valor of ['true', 'false'])
      agregar(
        ['conversion_indicadores', 'booleanos', `${familia}.${nombre}=${valor}`],
        'booleanos',
        `${familia}_${nombre}_${valor}`,
        'numero',
        4,
      );
  };
  const enumerado = (familia: string, valores: string[]) => {
    for (const valor of valores)
      agregar(
        ['conversion_indicadores', 'enumerados', familia, valor],
        'categorias',
        `${familia.replaceAll('.', '_')}_${valor}`,
        'numero',
        4,
      );
  };
  for (const entrada of elementos) {
    const elemento = objetoIc(entrada);
    const ref = String(elemento['elemento_ref'] ?? '');
    const indicadores = elemento['indicadores'];
    if (!Array.isArray(indicadores)) continue;
    for (const nombre of indicadores) {
      if (nombre === 'falta_elemento' || nombre === 'junta_limpia') booleano('junta', nombre);
      else if (nombre === 'buen_estado') booleano('losa', nombre);
      else if (nombre === 'existe') {
        if (ref.startsWith('/otros/senalizacion/')) booleano('senal', nombre);
        else if (ref === '/otros/drenajes_aledanos') booleano('drenaje_aledano', nombre);
      } else if (nombre === 'golpeado_longitud_m' || nombre === 'golpeados_cantidad') {
        for (const nivel of ['LEVE', 'MODERADO', 'SEVERO'])
          agregar(
            [
              'conversion_indicadores',
              'numericos',
              nombre,
              nombre === 'golpeado_longitud_m' ? 'umbrales_m' : 'umbrales',
              nivel,
            ],
            nombre,
            nivel,
          );
      } else if (nombre === 'aguas_arriba' || nombre === 'aguas_abajo')
        enumerado('socavacion.nivel', [
          'NO_HAY',
          'SIN_EXPOSICION',
          'CON_EXPOSICION',
          'ASENTAMIENTO_ESTRUCTURA',
        ]);
      else if (nombre === 'condiciones')
        enumerado('condicionesCauce', ['LIMPIO', 'ASOLVADO_SEDIMENTADO', 'EROSIONADO', 'OTRO']);
      else if (nombre === 'estado') {
        if (ref.startsWith('/superestructura/drenaje'))
          enumerado('drenaje.estado', ['LIMPIOS', 'OBSTRUIDOS']);
        else enumerado('estado_bueno_malo', ['BUENO', 'MALO']);
      }
    }
  }
  return campos;
}

export function valoresReglasIc(version: VersionFormulario): ValoresReglasIc {
  const reglas = objetoIc(version.reglasEvaluacionIc['reglas_evaluacion_ic']);
  return Object.fromEntries(
    camposReglasIc(version).map((campo) => {
      let valor: unknown = reglas;
      for (const parte of campo.ruta) valor = objetoIc(valor)[parte];
      return [campo.clave, typeof valor === 'number' || typeof valor === 'string' ? valor : null];
    }),
  );
}

export function erroresReglasIc(campos: CampoReglaIc[], valores: ValoresReglasIc): string[] {
  const errores = new Set<string>();
  for (const campo of campos) {
    const valor = valores[campo.clave];
    if (campo.tipo === 'texto') {
      if (typeof valor !== 'string' || !valor.trim() || valor.length > 50)
        errores.add('algoritmoInvalido');
    } else if (campo.tipo === 'agregacion') {
      if (valor !== 'MAXIMO' && valor !== 'PROMEDIO') errores.add('agregacionInvalida');
    } else if (
      typeof valor !== 'number' ||
      !Number.isFinite(valor) ||
      valor < 0 ||
      (campo.maximo !== undefined && valor > campo.maximo)
    )
      errores.add('numerosInvalidos');
  }
  const valor = (ruta: string[]) => valores[JSON.stringify(ruta)];
  const bueno = valor(['rangos_estado', 'BUENO']);
  const regular = valor(['rangos_estado', 'REGULAR']);
  const malo = valor(['rangos_estado', 'MALO']);
  if (bueno !== 75 || regular !== 50 || malo !== 0) errores.add('rangosInvalidos');
  for (const grupo of ['golpeado_longitud_m', 'golpeados_cantidad']) {
    const lista = campos.filter((c) => c.grupo === grupo).map((c) => valores[c.clave]);
    if (
      lista.length &&
      (lista.some((v) => typeof v !== 'number' || !Number.isFinite(v)) ||
        Number(lista[0]) <= 0 ||
        Number(lista[1]) <= Number(lista[0]) ||
        Number(lista[2]) <= Number(lista[1]))
    )
      errores.add('umbralesInvalidos');
  }
  return [...errores];
}

export function aplicarReglasIc(
  version: VersionFormulario,
  valores: ValoresReglasIc,
): Record<string, unknown> {
  const configuracion = structuredClone(version.reglasEvaluacionIc);
  const reglas = { ...objetoIc(configuracion['reglas_evaluacion_ic']) };
  configuracion['reglas_evaluacion_ic'] = reglas;
  for (const campo of camposReglasIc(version)) {
    let destino = reglas;
    for (const parte of campo.ruta.slice(0, -1)) {
      const hijo = { ...objetoIc(destino[parte]) };
      destino[parte] = hijo;
      destino = hijo;
    }
    destino[campo.ruta[campo.ruta.length - 1]] = valores[campo.clave];
  }
  return configuracion;
}

export function reglasIcCompletas(version: VersionFormulario): boolean {
  return erroresReglasIc(camposReglasIc(version), valoresReglasIc(version)).length === 0;
}
