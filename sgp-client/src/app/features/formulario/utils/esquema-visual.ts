import { PreguntaVisual, SeccionVisual, TipoDatoVisual } from '../models/formulario.models';

export function esquemaASeccionesVisuales(
  esquema: Record<string, unknown> | null | undefined,
  vinculos: Record<string, unknown> | null | undefined = {},
): SeccionVisual[] {
  if (!esquema || typeof esquema !== 'object') return [];
  const properties = esquema['properties'];
  if (!properties || typeof properties !== 'object') return [];

  const vinculosObj = (vinculos && typeof vinculos === 'object' ? vinculos : {}) as Record<
    string,
    string
  >;
  const secciones: SeccionVisual[] = [];

  for (const [secClave, secDefRaw] of Object.entries(properties)) {
    if (!secDefRaw || typeof secDefRaw !== 'object') continue;
    const secDef = secDefRaw as Record<string, unknown>;

    const titulo = (secDef['title'] as string) || secClave;
    const descripcion = (secDef['description'] as string) || '';
    const esRepetible = secDef['type'] === 'array';

    let propsObj: Record<string, unknown> = {};
    let requiredFields: string[] = [];

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

    const preguntas: PreguntaVisual[] = [];
    for (const [pregClave, pregDefRaw] of Object.entries(propsObj)) {
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

      const vinculoKey = `${secClave}.${pregClave}`;
      const vinculo = vinculosObj[vinculoKey] || vinculosObj[pregClave] || '';

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
        properties: propsObj,
      };
    }
  }

  esquema['properties'] = nuevasPropiedades;
  esquema['required'] = nuevasRequeridas;

  return { esquema, vinculos };
}

function esCampoVisualEditable(esquema: Record<string, unknown>): boolean {
  if (typeof esquema['$ref'] === 'string') return false;

  const tipo = esquema['type'];
  const tipos = Array.isArray(tipo) ? tipo : [tipo];
  return !tipos.some((valor) => valor === 'object' || valor === 'array');
}
