import {
  Condicion,
  ConsecuenciaObjeto,
  EsquemaJson,
  EsquemaResuelto,
  NodoArreglo,
  NodoCampo,
  NodoFormulario,
  NodoObjeto,
  OpcionCampo,
  ReglaLista,
  ReglaObjeto,
  RestriccionPropiedad,
  TipoCampo,
} from './modelos';

const REFERENCIA_LOCAL = /^#\/\$defs\/(.+)$/;
const PROFUNDIDAD_MAXIMA_REFERENCIAS = 32;

function esObjeto(valor: unknown): valor is EsquemaJson {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor);
}

function texto(valor: unknown): string | undefined {
  return typeof valor === 'string' && valor.length > 0 ? valor : undefined;
}

function numero(valor: unknown): number | undefined {
  return typeof valor === 'number' && Number.isFinite(valor) ? valor : undefined;
}

function lista(valor: unknown): unknown[] {
  return Array.isArray(valor) ? valor : [];
}

/** `NO_PRESENTA` → `No presenta`. */
export function humanizar(clave: string): string {
  const palabras = clave.replace(/_/g, ' ').trim().toLowerCase();
  return palabras.charAt(0).toUpperCase() + palabras.slice(1);
}

/**
 * Ordena los hijos como deben verse en pantalla. La base de datos guarda el esquema como JSONB,
 * que reordena las claves de cada objeto, así que el orden viaja en `x-sgp-orden` (una lista). Lo
 * que la lista no nombra va al final, en el orden recibido. Las versiones antiguas, sin la
 * anotación, solo pueden recuperar el orden de las secciones por el número de su título.
 */
function ordenarHijos<T extends { clave: string; titulo: string }>(
  hijos: T[],
  orden: unknown,
  esRaiz: boolean,
): T[] {
  const claves = lista(orden).filter((clave): clave is string => typeof clave === 'string');

  if (claves.length > 0) {
    const posicion = new Map(claves.map((clave, indice) => [clave, indice]));
    const rango = (hijo: T, indice: number) => posicion.get(hijo.clave) ?? claves.length + indice;
    return hijos
      .map((hijo, indice) => ({ hijo, rango: rango(hijo, indice) }))
      .sort((a, b) => a.rango - b.rango)
      .map(({ hijo }) => hijo);
  }

  if (esRaiz) {
    const numeros = hijos.map((hijo) => /^\s*(\d+)\s*[.)]/.exec(hijo.titulo)?.[1]);
    if (hijos.length > 1 && numeros.every((numero) => numero !== undefined)) {
      return hijos
        .map((hijo, indice) => ({ hijo, numero: Number(numeros[indice]) }))
        .sort((a, b) => a.numero - b.numero)
        .map(({ hijo }) => hijo);
    }
  }
  return hijos;
}

/** `3. Tramos` → `Tramos`. */
function quitarNumeracion(titulo: string): string {
  return titulo.replace(/^\s*\d+\s*[.)]\s*/, '');
}

/** Un `enum` sin `type` es válido en JSON Schema: el tipo se deduce de sus valores. */
function inferirTipo(esquema: EsquemaJson): string | undefined {
  const valores = lista(esquema['enum']);
  if (valores.length === 0) return undefined;
  if (valores.every((valor) => typeof valor === 'string')) return 'string';
  if (valores.every((valor) => typeof valor === 'number')) return 'number';
  return undefined;
}

/** Mezcla `extra` sobre `base`; los objetos se combinan en profundidad y lo demás se reemplaza. */
function fusionar(base: EsquemaJson, extra: EsquemaJson): EsquemaJson {
  const resultado: EsquemaJson = { ...base };
  for (const [clave, valor] of Object.entries(extra)) {
    const previo = resultado[clave];
    resultado[clave] = esObjeto(valor) && esObjeto(previo) ? fusionar(previo, valor) : valor;
  }
  return resultado;
}

type BaseNodo = Omit<NodoObjeto, 'clase' | 'hijos' | 'reglas' | 'opcional'>;

class Resolutor {
  constructor(private readonly definiciones: EsquemaJson) {}

  /** Sustituye `$ref` locales a `$defs`; las claves vecinas de `$ref` prevalecen. */
  expandir(esquema: EsquemaJson, profundidad = 0): EsquemaJson {
    const referencia = esquema['$ref'];
    if (typeof referencia !== 'string') return esquema;

    if (profundidad > PROFUNDIDAD_MAXIMA_REFERENCIAS) {
      throw new Error(`Referencia circular en el esquema: ${referencia}`);
    }

    const destino = REFERENCIA_LOCAL.exec(referencia)?.[1];
    const definicion = destino === undefined ? undefined : this.definiciones[destino];
    if (!esObjeto(definicion)) {
      throw new Error(`Referencia no soportada en el esquema: ${referencia}`);
    }

    const resto: EsquemaJson = { ...esquema };
    delete resto['$ref'];
    return fusionar(this.expandir(definicion, profundidad + 1), resto);
  }

  construirNodo(
    entrada: unknown,
    clave: string,
    rutaPadre: string,
    obligatorio: boolean,
  ): NodoFormulario {
    if (!esObjeto(entrada)) {
      throw new Error(`El esquema de «${clave}» no es un objeto`);
    }

    const esquema = this.expandir(entrada);
    const ruta = rutaPadre ? `${rutaPadre}/${clave}` : clave;
    const tipos = lista(esquema['type']);
    const anulable = tipos.includes('null');
    const principal =
      tipos.find((tipo) => tipo !== 'null') ?? (esquema['type'] as unknown) ?? inferirTipo(esquema);

    const base = {
      ruta,
      clave,
      titulo: texto(esquema['title']) ?? humanizar(clave),
      descripcion: texto(esquema['description']),
      soloLectura: esquema['readOnly'] === true,
      obligatorio,
      condicional: false,
      evaluable: esquema['x-sgp-evaluable'] === true,
      fuente: texto(esquema['x-sgp-fuente']),
    };

    if (principal === 'object' || (principal === undefined && esObjeto(esquema['properties']))) {
      return this.construirObjeto(esquema, base);
    }
    if (principal === 'array') {
      return this.construirLista(esquema, base);
    }
    return this.construirCampo(esquema, base, principal, anulable);
  }

  private construirObjeto(esquema: EsquemaJson, base: BaseNodo): NodoObjeto {
    const requeridos = new Set(lista(esquema['required']));
    const propiedades = esObjeto(esquema['properties']) ? esquema['properties'] : {};

    const hijos = ordenarHijos(
      Object.entries(propiedades).map(([clave, hijo]) =>
        this.construirNodo(hijo, clave, base.ruta, requeridos.has(clave)),
      ),
      esquema['x-sgp-orden'],
      base.ruta === '',
    );
    const reglas = this.reglasDeObjeto(esquema);

    // Lo exigido solo por una condición no es obligatorio a secas: depende de ella.
    for (const regla of reglas) {
      for (const clave of regla.entonces.requeridos) {
        const hijo = hijos.find((candidato) => candidato.clave === clave);
        if (hijo) hijo.condicional = true;
      }
    }

    const opcional = base.ruta !== '' && !base.obligatorio;
    return { ...base, clase: 'objeto', hijos, reglas, opcional };
  }

  private construirLista(esquema: EsquemaJson, base: BaseNodo): NodoFormulario {
    const elementos = esObjeto(esquema['items']) ? this.expandir(esquema['items']) : {};

    if (elementos['type'] === 'object') {
      // Una instancia de la lista siempre existe: sus obligatorios se exigen desde que se agrega.
      const elemento = this.construirObjeto(elementos, { ...base, obligatorio: true });
      const arreglo: NodoArreglo = {
        ...base,
        clase: 'arreglo',
        elemento,
        nombreElemento: texto(elementos['title']) ?? quitarNumeracion(base.titulo),
        minimoElementos: numero(esquema['minItems']) ?? 0,
        maximoElementos: numero(esquema['maxItems']),
      };
      return arreglo;
    }

    // Lista de valores simples: selección múltiple.
    const campo = this.construirCampoDeValores(elementos, base, 'seleccionMultiple', false);
    campo.minimoElementos = numero(esquema['minItems']);
    campo.maximoElementos = numero(esquema['maxItems']);
    campo.reglasLista = this.reglasDeLista(esquema);
    if ('x-sgp-sin-danos' in esquema) campo.sinDanos = esquema['x-sgp-sin-danos'];
    return campo;
  }

  private construirCampo(
    esquema: EsquemaJson,
    base: BaseNodo,
    principal: unknown,
    anulable: boolean,
  ): NodoCampo {
    let tipo: TipoCampo;
    switch (principal) {
      case 'boolean':
        tipo = 'booleano';
        break;
      case 'integer':
        tipo = 'entero';
        break;
      case 'number':
        tipo = 'numero';
        break;
      case 'string':
        tipo =
          esquema['format'] === 'date' ? 'fecha' : esquema['format'] === 'uuid' ? 'uuid' : 'texto';
        break;
      default:
        throw new Error(`Tipo no soportado en «${base.ruta}»: ${String(principal)}`);
    }

    return this.construirCampoDeValores(esquema, base, tipo, anulable);
  }

  private construirCampoDeValores(
    esquema: EsquemaJson,
    base: BaseNodo,
    tipo: TipoCampo,
    anulable: boolean,
  ): NodoCampo {
    const prohibido = esObjeto(esquema['not']) ? esquema['not']['const'] : undefined;
    const opciones: OpcionCampo[] = lista(esquema['enum'])
      .filter(
        (valor): valor is string | number =>
          (typeof valor === 'string' || typeof valor === 'number') && valor !== prohibido,
      )
      .map((valor) => ({ valor, etiqueta: humanizar(String(valor)) }));

    const tieneOpciones = opciones.length > 0;
    const esMultiple = tipo === 'seleccionMultiple';
    const tipoFinal: TipoCampo = tieneOpciones && !esMultiple ? 'enumerado' : tipo;

    return {
      ...base,
      clase: 'campo',
      tipo: tipoFinal,
      anulable,
      opciones,
      minimo: numero(esquema['minimum']),
      maximo: numero(esquema['maximum']),
      minimoExclusivo: numero(esquema['exclusiveMinimum']),
      maximoExclusivo: numero(esquema['exclusiveMaximum']),
      longitudMinima: numero(esquema['minLength']),
      longitudMaxima: numero(esquema['maxLength']),
      reglasLista: [],
      sugerencias: lista(esquema['examples']).filter(
        (valor): valor is string => typeof valor === 'string',
      ),
      ...('x-sgp-sin-danos' in esquema ? { sinDanos: esquema['x-sgp-sin-danos'] } : {}),
    };
  }

  /** Reúne `if/then/else` del propio objeto y de cada elemento de `allOf`. */
  private reglasDeObjeto(esquema: EsquemaJson): ReglaObjeto[] {
    const candidatos: EsquemaJson[] = [esquema, ...lista(esquema['allOf']).filter(esObjeto)];
    const reglas: ReglaObjeto[] = [];

    for (const candidato of candidatos) {
      const condicion = candidato['if'];
      if (!esObjeto(condicion)) continue;

      reglas.push({
        cuando: this.condicion(condicion),
        entonces: this.consecuencia(candidato['then']),
        sino: this.consecuencia(candidato['else']),
      });
    }

    return reglas;
  }

  private reglasDeLista(esquema: EsquemaJson): ReglaLista[] {
    const condicion = esquema['if'];
    const entonces = esquema['then'];
    if (!esObjeto(condicion) || !esObjeto(entonces)) return [];

    const contiene = esObjeto(condicion['contains']) ? condicion['contains']['const'] : undefined;
    const maximo = numero(entonces['maxItems']);
    return contiene !== undefined && maximo !== undefined ? [{ contiene, maximo }] : [];
  }

  private condicion(esquema: EsquemaJson): Condicion {
    const expandido = this.expandir(esquema);
    const propiedades: Record<string, RestriccionPropiedad> = {};

    for (const [clave, valor] of Object.entries(
      esObjeto(expandido['properties']) ? expandido['properties'] : {},
    )) {
      if (!esObjeto(valor)) continue;
      const restriccion: RestriccionPropiedad = {};
      if ('const' in valor) restriccion.const = valor['const'];
      if (Array.isArray(valor['enum'])) restriccion.enum = valor['enum'];
      if (esObjeto(valor['contains']) && 'const' in valor['contains']) {
        restriccion.contiene = valor['contains']['const'];
      }
      if (esObjeto(valor['not']) && 'const' in valor['not']) {
        restriccion.distintoDe = valor['not']['const'];
      }
      propiedades[clave] = restriccion;
    }

    return { propiedades };
  }

  private consecuencia(esquema: unknown): ConsecuenciaObjeto {
    const consecuencia: ConsecuenciaObjeto = {
      requeridos: [],
      prohibidos: [],
      soloDiscriminadores: false,
    };
    if (!esObjeto(esquema)) return consecuencia;

    consecuencia.requeridos = lista(esquema['required']).filter(
      (valor): valor is string => typeof valor === 'string',
    );
    if (esObjeto(esquema['properties'])) {
      consecuencia.prohibidos = Object.entries(esquema['properties'])
        .filter(([, valor]) => valor === false)
        .map(([clave]) => clave);
    }
    consecuencia.soloDiscriminadores = esquema['maxProperties'] === 1;
    return consecuencia;
  }
}

/** Convierte el esquema recibido del backend en el árbol de nodos que usa el motor. */
export function resolverEsquema(esquema: EsquemaJson): EsquemaResuelto {
  const definiciones = esObjeto(esquema['$defs']) ? esquema['$defs'] : {};
  const resolutor = new Resolutor(definiciones);

  const raiz = resolutor.construirNodo(esquema, '', '', false);
  if (raiz.clase !== 'objeto') {
    throw new Error('El esquema del formulario debe ser un objeto');
  }

  // Una sección puede ser un objeto o una lista repetible (por ejemplo, «3. Tramos»).
  const secciones = raiz.hijos.filter(
    (hijo): hijo is NodoObjeto | NodoArreglo => hijo.clase !== 'campo',
  );

  return {
    titulo: texto(esquema['title']) ?? 'Formulario',
    descripcion: texto(esquema['description']),
    raiz,
    secciones,
  };
}
