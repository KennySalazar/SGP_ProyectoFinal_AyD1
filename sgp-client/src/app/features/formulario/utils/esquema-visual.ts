import {
  PreguntaVisual,
  SeccionVisual,
  TipoDatoVisual,
  VinculoVisual,
} from '../models/formulario.models';

export function resolverVinculoParaCampo(
  secClave: string,
  pregClave: string,
  vinculosRaw: unknown,
): string {
  if (!vinculosRaw || typeof vinculosRaw !== 'object') return '';
  const obj = vinculosRaw as Record<string, unknown>;

  const ptrSlash = `/${secClave}/${pregClave}`;
  const ptrDot = `${secClave}.${pregClave}`;

  if (typeof obj[ptrSlash] === 'string') return obj[ptrSlash] as string;
  if (typeof obj[ptrDot] === 'string') return obj[ptrDot] as string;
  if (typeof obj[pregClave] === 'string') return obj[pregClave] as string;

  if (Array.isArray(obj['columnas'])) {
    for (const item of obj['columnas'] as Array<Record<string, unknown>>) {
      if (
        item &&
        (item['puntero'] === ptrSlash ||
          item['puntero'] === ptrDot ||
          item['puntero'] === pregClave) &&
        typeof item['columna'] === 'string'
      ) {
        return item['columna'] as string;
      }
    }
  }

  const inst = obj['instantanea_inventario'];
  if (inst && typeof inst === 'object') {
    const campos = (inst as Record<string, unknown>)['campos'];
    if (campos && typeof campos === 'object') {
      const camposObj = campos as Record<string, unknown>;
      if (typeof camposObj[ptrSlash] === 'string') return camposObj[ptrSlash] as string;
      if (typeof camposObj[ptrDot] === 'string') return camposObj[ptrDot] as string;
    }
  }

  return '';
}

export function esquemaASeccionesVisuales(
  esquema: Record<string, unknown> | null | undefined,
  vinculos: Record<string, unknown> | null | undefined = {},
): SeccionVisual[] {
  if (!esquema || typeof esquema !== 'object') return [];
  const properties = esquema['properties'] as Record<string, unknown> | undefined;
  if (!properties || typeof properties !== 'object') return [];

  // Determinar orden de claves de secciones
  let ordenClaves: string[] = [];
  if (Array.isArray(esquema['x-sgp-orden'])) {
    ordenClaves = (esquema['x-sgp-orden'] as unknown[]).filter(
      (k): k is string => typeof k === 'string',
    );
  }

  const todasClaves = Object.keys(properties);

  // Ordenar secciones:
  // 1. Si los títulos contienen número ("1. Identificación", "8. Otros"), ordenar numéricamente por ese número.
  // 2. Si no, respetar 'x-sgp-orden' si existe.
  // 3. De lo contrario, mantener orden natural.
  todasClaves.sort((a, b) => {
    const defA = properties[a] as Record<string, unknown> | undefined;
    const defB = properties[b] as Record<string, unknown> | undefined;
    const titA = (defA?.['title'] as string) || a;
    const titB = (defB?.['title'] as string) || b;
    const matchA = titA.match(/^(\d+)/);
    const matchB = titB.match(/^(\d+)/);
    if (matchA && matchB) {
      const diff = parseInt(matchA[1], 10) - parseInt(matchB[1], 10);
      if (diff !== 0) return diff;
    } else if (matchA) {
      return -1;
    } else if (matchB) {
      return 1;
    }

    if (ordenClaves.length > 0) {
      const idxA = ordenClaves.indexOf(a);
      const idxB = ordenClaves.indexOf(b);
      if (idxA !== -1 && idxB !== -1) return idxA - idxB;
      if (idxA !== -1) return -1;
      if (idxB !== -1) return 1;
    }

    return 0;
  });

  const secciones: SeccionVisual[] = [];

  for (const secClave of todasClaves) {
    const secDefRaw = properties[secClave];
    if (!secDefRaw || typeof secDefRaw !== 'object') continue;
    const secDef = secDefRaw as Record<string, unknown>;

    const titulo = (secDef['title'] as string) || secClave;
    const descripcion = (secDef['description'] as string) || '';
    const esRepetible = secDef['type'] === 'array';

    let propsObj: Record<string, unknown> = {};
    let requiredFields: string[] = [];

    const sourceObj =
      esRepetible && secDef['items'] && typeof secDef['items'] === 'object'
        ? (secDef['items'] as Record<string, unknown>)
        : secDef;

    if (esRepetible && secDef['items'] && typeof secDef['items'] === 'object') {
      const items = secDef['items'] as Record<string, unknown>;
      if (items['properties'] && typeof items['properties'] === 'object') {
        propsObj = items['properties'] as Record<string, unknown>;
      }
      if (Array.isArray(items['required'])) {
        requiredFields = items['required'] as string[];
      }
    } else if (secDef['properties'] && typeof secDef['properties'] === 'object') {
      propsObj = secDef['properties'] as Record<string, unknown>;
      if (Array.isArray(secDef['required'])) {
        requiredFields = secDef['required'] as string[];
      }
    }

    let ordenPreguntas: string[] = [];
    if (Array.isArray(sourceObj['x-sgp-orden'])) {
      ordenPreguntas = (sourceObj['x-sgp-orden'] as unknown[]).filter(
        (k): k is string => typeof k === 'string',
      );
    }

    const todasPregClaves = Object.keys(propsObj);
    if (ordenPreguntas.length > 0) {
      todasPregClaves.sort((a, b) => {
        const idxA = ordenPreguntas.indexOf(a);
        const idxB = ordenPreguntas.indexOf(b);
        if (idxA !== -1 && idxB !== -1) return idxA - idxB;
        if (idxA !== -1) return -1;
        if (idxB !== -1) return 1;
        return 0;
      });
    }

    const preguntas: PreguntaVisual[] = [];
    for (const pregClave of todasPregClaves) {
      const pregDefRaw = propsObj[pregClave];
      if (!pregDefRaw || typeof pregDefRaw !== 'object') continue;
      const p = pregDefRaw as Record<string, unknown>;
      if (!esCampoVisualEditable(p)) continue;

      let tipo: TipoDatoVisual = 'string';
      const rawEnum = p['enum'];
      const rawType = p['type'];
      const format = p['format'];

      if (Array.isArray(rawEnum) && rawEnum.length > 0) {
        tipo = 'select';
      } else if (format === 'date') {
        tipo = 'date';
      } else if (rawType === 'integer' || (Array.isArray(rawType) && rawType.includes('integer'))) {
        tipo = 'integer';
      } else if (rawType === 'number' || (Array.isArray(rawType) && rawType.includes('number'))) {
        tipo = 'number';
      } else if (rawType === 'boolean') {
        tipo = 'boolean';
      } else if (
        Boolean(p['x-sgp-multiline']) ||
        (typeof p['maxLength'] === 'number' && (p['maxLength'] as number) > 200)
      ) {
        tipo = 'multiline';
      } else {
        tipo = 'string';
      }

      const vinculo = resolverVinculoParaCampo(secClave, pregClave, vinculos);

      preguntas.push({
        clave: pregClave,
        titulo: (p['title'] as string) || pregClave,
        descripcion: (p['description'] as string) || '',
        tipo,
        obligatorio: requiredFields.includes(pregClave),
        soloLectura: Boolean(p['readOnly']),
        opciones: Array.isArray(rawEnum) ? (rawEnum.map(String) as string[]) : undefined,
        longitudMinima: typeof p['minLength'] === 'number' ? (p['minLength'] as number) : undefined,
        longitudMaxima: typeof p['maxLength'] === 'number' ? (p['maxLength'] as number) : undefined,
        valorMinimo:
          typeof p['minimum'] === 'number'
            ? (p['minimum'] as number)
            : typeof p['exclusiveMinimum'] === 'number'
              ? (p['exclusiveMinimum'] as number)
              : undefined,
        valorMaximo:
          typeof p['maximum'] === 'number'
            ? (p['maximum'] as number)
            : typeof p['exclusiveMaximum'] === 'number'
              ? (p['exclusiveMaximum'] as number)
              : undefined,
        vinculo,
      });
    }

    secciones.push({
      clave: secClave,
      titulo,
      descripcion,
      esRepetible,
      preguntas,
    });
  }

  return secciones;
}

export function seccionesVisualesAEsquema(
  secciones: SeccionVisual[],
  esquemaBase: Record<string, unknown> | null | undefined,
  vinculosExistentes: Record<string, unknown> | null | undefined,
): { esquema: Record<string, unknown>; vinculos: Record<string, unknown> } {
  const esquema: Record<string, unknown> = esquemaBase
    ? JSON.parse(JSON.stringify(esquemaBase))
    : {};

  if (!esquema['$schema']) {
    esquema['$schema'] = 'https://json-schema.org/draft/2020-12/schema';
  }
  if (!esquema['type']) {
    esquema['type'] = 'object';
  }
  if (esquema['additionalProperties'] === undefined) {
    esquema['additionalProperties'] = false;
  }

  const vinculos: Record<string, unknown> = vinculosExistentes
    ? JSON.parse(JSON.stringify(vinculosExistentes))
    : {};

  const propiedadesActuales = (
    esquema['properties'] && typeof esquema['properties'] === 'object'
      ? (esquema['properties'] as Record<string, unknown>)
      : {}
  ) as Record<string, Record<string, unknown>>;

  const nuevasPropiedades: Record<string, unknown> = {};
  const nuevasRequeridas: string[] = [];

  for (const sec of secciones) {
    nuevasRequeridas.push(sec.clave);
    const secBase = propiedadesActuales[sec.clave] || {};

    const esquemaBasePreguntas = sec.esRepetible
      ? ((secBase['items'] as Record<string, unknown>) ?? {})
      : secBase;
    const propsBase =
      (esquemaBasePreguntas['properties'] as Record<string, unknown> | undefined) || {};

    const propiedadesNoEditables = Object.fromEntries(
      Object.entries(propsBase).filter(([, valor]) => {
        return (
          valor !== null &&
          typeof valor === 'object' &&
          !esCampoVisualEditable(valor as Record<string, unknown>)
        );
      }),
    );
    const propsObj: Record<string, unknown> = { ...propiedadesNoEditables };
    const ordenPreguntas =
      sec.preguntas.length > 0
        ? sec.preguntas.map((pregunta) => pregunta.clave)
        : esquemaBasePreguntas['x-sgp-orden'];
    const requeridosBase = new Set(
      Array.isArray(esquemaBasePreguntas['required']) ? esquemaBasePreguntas['required'] : [],
    );
    const reqList: string[] = Object.keys(propiedadesNoEditables).filter((clave) =>
      requeridosBase.has(clave),
    );

    for (const p of sec.preguntas) {
      const pBase: Record<string, unknown> = (propsBase[p.clave] as Record<string, unknown>)
        ? { ...(propsBase[p.clave] as Record<string, unknown>) }
        : {};

      pBase['title'] = p.titulo;
      if (p.descripcion && p.descripcion.trim().length > 0) {
        pBase['description'] = p.descripcion.trim();
      } else {
        delete pBase['description'];
      }

      if (p.soloLectura) {
        pBase['readOnly'] = true;
      } else {
        delete pBase['readOnly'];
      }

      switch (p.tipo) {
        case 'select':
          pBase['type'] = 'string';
          pBase['enum'] =
            p.opciones && p.opciones.length > 0 ? p.opciones.map((op) => op.trim()) : ['OPCION_1'];
          delete pBase['format'];
          delete pBase['minLength'];
          delete pBase['maxLength'];
          delete pBase['minimum'];
          delete pBase['maximum'];
          delete pBase['exclusiveMinimum'];
          delete pBase['exclusiveMaximum'];
          delete pBase['x-sgp-multiline'];
          break;

        case 'date':
          pBase['type'] = 'string';
          pBase['format'] = 'date';
          delete pBase['enum'];
          delete pBase['minimum'];
          delete pBase['maximum'];
          delete pBase['exclusiveMinimum'];
          delete pBase['exclusiveMaximum'];
          delete pBase['x-sgp-multiline'];
          break;

        case 'multiline':
          pBase['type'] = 'string';
          pBase['x-sgp-multiline'] = true;
          delete pBase['enum'];
          delete pBase['format'];
          delete pBase['minimum'];
          delete pBase['maximum'];
          delete pBase['exclusiveMinimum'];
          delete pBase['exclusiveMaximum'];
          if (p.longitudMinima != null) pBase['minLength'] = p.longitudMinima;
          else delete pBase['minLength'];
          if (p.longitudMaxima != null) pBase['maxLength'] = p.longitudMaxima;
          else delete pBase['maxLength'];
          break;

        case 'integer':
          pBase['type'] = 'integer';
          delete pBase['enum'];
          delete pBase['format'];
          delete pBase['minLength'];
          delete pBase['maxLength'];
          delete pBase['x-sgp-multiline'];
          if (p.valorMinimo != null) pBase['minimum'] = p.valorMinimo;
          else delete pBase['minimum'];
          if (p.valorMaximo != null) pBase['maximum'] = p.valorMaximo;
          else delete pBase['maximum'];
          break;

        case 'number':
          pBase['type'] = 'number';
          delete pBase['enum'];
          delete pBase['format'];
          delete pBase['minLength'];
          delete pBase['maxLength'];
          delete pBase['x-sgp-multiline'];
          if (p.valorMinimo != null) pBase['minimum'] = p.valorMinimo;
          else delete pBase['minimum'];
          if (p.valorMaximo != null) pBase['maximum'] = p.valorMaximo;
          else delete pBase['maximum'];
          break;

        case 'boolean':
          pBase['type'] = 'boolean';
          delete pBase['enum'];
          delete pBase['format'];
          delete pBase['minLength'];
          delete pBase['maxLength'];
          delete pBase['minimum'];
          delete pBase['maximum'];
          delete pBase['exclusiveMinimum'];
          delete pBase['exclusiveMaximum'];
          delete pBase['x-sgp-multiline'];
          break;

        case 'string':
        default:
          pBase['type'] = 'string';
          delete pBase['enum'];
          delete pBase['format'];
          delete pBase['minimum'];
          delete pBase['maximum'];
          delete pBase['exclusiveMinimum'];
          delete pBase['exclusiveMaximum'];
          delete pBase['x-sgp-multiline'];
          if (p.longitudMinima != null) pBase['minLength'] = p.longitudMinima;
          else delete pBase['minLength'];
          if (p.longitudMaxima != null) pBase['maxLength'] = p.longitudMaxima;
          else delete pBase['maxLength'];
          break;
      }

      propsObj[p.clave] = pBase;
      if (p.obligatorio) {
        reqList.push(p.clave);
      }

      const vinculoKey = `${sec.clave}.${p.clave}`;
      if (p.vinculo && p.vinculo.trim().length > 0) {
        vinculos[vinculoKey] = p.vinculo.trim();
      } else {
        delete vinculos[vinculoKey];
        delete vinculos[p.clave];
      }
    }

    if (sec.esRepetible) {
      nuevasPropiedades[sec.clave] = {
        ...secBase,
        title: sec.titulo,
        ...(sec.descripcion ? { description: sec.descripcion } : {}),
        type: 'array',
        items: {
          type: 'object',
          additionalProperties: false,
          required: reqList,
          'x-sgp-orden': ordenPreguntas,
          properties: propsObj,
        },
      };
    } else {
      nuevasPropiedades[sec.clave] = {
        ...secBase,
        title: sec.titulo,
        ...(sec.descripcion ? { description: sec.descripcion } : {}),
        type: 'object',
        additionalProperties: false,
        required: reqList,
        'x-sgp-orden': ordenPreguntas,
        properties: propsObj,
      };
    }
  }

  esquema['properties'] = nuevasPropiedades;
  esquema['required'] = nuevasRequeridas;
  esquema['x-sgp-orden'] = secciones.map((s) => s.clave);

  return { esquema, vinculos };
}

function esCampoVisualEditable(esquema: Record<string, unknown>): boolean {
  if (typeof esquema['$ref'] === 'string') return false;

  const tipo = esquema['type'];
  const tipos = Array.isArray(tipo) ? tipo : [tipo];
  return !tipos.some((valor) => valor === 'object' || valor === 'array');
}

export function extraerVinculosVisuales(
  vinculosRaw: unknown,
  secciones: SeccionVisual[] = [],
): VinculoVisual[] {
  const resultado: VinculoVisual[] = [];
  const yaAgregados = new Set<string>();

  if (vinculosRaw && typeof vinculosRaw === 'object') {
    const obj = vinculosRaw as Record<string, unknown>;

    if (Array.isArray(obj['columnas'])) {
      for (const item of obj['columnas'] as Array<Record<string, unknown>>) {
        if (item && typeof item['puntero'] === 'string' && typeof item['columna'] === 'string') {
          const ptr = item['puntero'] as string;
          yaAgregados.add(ptr);
          yaAgregados.add(ptr.startsWith('/') ? ptr.substring(1).replace(/\//g, '.') : ptr);
          resultado.push({
            id: `col_${ptr}`,
            campoRef: ptr,
            columnaDestino: item['columna'] as string,
            tipo: 'Columna técnica',
            descripcion: (item['nota'] as string) || (item['origen'] as string) || '',
          });
        }
      }
    }

    const inst = obj['instantanea_inventario'];
    if (inst && typeof inst === 'object') {
      const campos = (inst as Record<string, unknown>)['campos'];
      if (campos && typeof campos === 'object') {
        for (const [k, v] of Object.entries(campos as Record<string, unknown>)) {
          if (typeof v === 'string') {
            yaAgregados.add(k);
            yaAgregados.add(k.startsWith('/') ? k.substring(1).replace(/\//g, '.') : k);
            resultado.push({
              id: `inv_${k}`,
              campoRef: k,
              columnaDestino: v,
              tipo: 'Inventario puente',
              descripcion: 'Datos de inventario del puente',
            });
          }
        }
      }
    }

    const clavesIgnoradas = new Set([
      '$comment',
      'formulario',
      'columnas',
      'instantanea_inventario',
      'repetibles',
      'derivados_no_persistidos',
    ]);
    for (const [k, v] of Object.entries(obj)) {
      if (!clavesIgnoradas.has(k) && typeof v === 'string') {
        if (!yaAgregados.has(k)) {
          yaAgregados.add(k);
          resultado.push({
            id: `flat_${k}`,
            campoRef: k,
            columnaDestino: v,
            tipo: 'Directo',
          });
        }
      }
    }
  }

  // Complementar con cualquier vínculo declarado en las preguntas
  for (const sec of secciones) {
    for (const p of sec.preguntas) {
      if (p.vinculo && p.vinculo.trim().length > 0) {
        const ptrSlash = `/${sec.clave}/${p.clave}`;
        const ptrDot = `${sec.clave}.${p.clave}`;
        if (!yaAgregados.has(ptrSlash) && !yaAgregados.has(ptrDot)) {
          yaAgregados.add(ptrSlash);
          resultado.push({
            id: `preg_${sec.clave}_${p.clave}`,
            campoRef: ptrSlash,
            columnaDestino: p.vinculo.trim(),
            tipo: 'Directo',
            descripcion: `${sec.titulo} > ${p.titulo}`,
          });
        }
      }
    }
  }

  return resultado;
}

export function vinculosVisualesAObjeto(
  vinculos: VinculoVisual[],
  vinculosOriginalesRaw?: unknown,
): Record<string, unknown> {
  const base: Record<string, unknown> =
    vinculosOriginalesRaw && typeof vinculosOriginalesRaw === 'object'
      ? JSON.parse(JSON.stringify(vinculosOriginalesRaw))
      : {};

  const esEstructuraCompleja =
    Array.isArray(base['columnas']) ||
    (base['instantanea_inventario'] && typeof base['instantanea_inventario'] === 'object');

  if (esEstructuraCompleja) {
    const columnasNuevas: Array<Record<string, unknown>> = [];
    const camposInventario: Record<string, string> = {};

    for (const v of vinculos) {
      if (v.tipo === 'Inventario puente') {
        camposInventario[v.campoRef] = v.columnaDestino;
      } else {
        const item: Record<string, unknown> = {
          puntero: v.campoRef,
          columna: v.columnaDestino,
        };
        if (v.descripcion && v.descripcion.trim().length > 0) {
          item['nota'] = v.descripcion.trim();
        }
        columnasNuevas.push(item);
      }
      base[v.campoRef] = v.columnaDestino;
    }

    base['columnas'] = columnasNuevas;
    if (Object.keys(camposInventario).length > 0) {
      base['instantanea_inventario'] = {
        columna: 'inspeccion.identificacion_capturada',
        campos: camposInventario,
      };
    }

    return base;
  }

  const resultado: Record<string, unknown> = {};
  const columnas: Array<{ puntero: string; columna: string; tipo?: string }> = [];

  for (const v of vinculos) {
    resultado[v.campoRef] = v.columnaDestino;
    columnas.push({
      puntero: v.campoRef,
      columna: v.columnaDestino,
      tipo: v.tipo,
    });
  }

  resultado['columnas'] = columnas;
  return resultado;
}
