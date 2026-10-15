/** Esquema JSON tal como llega del backend. */
export type EsquemaJson = Record<string, unknown>;

export type TipoCampo =
  'texto' | 'numero' | 'entero' | 'booleano' | 'fecha' | 'uuid' | 'enumerado' | 'seleccionMultiple';

export interface OpcionCampo {
  valor: string | number;
  /** Etiqueta por omisión; la interfaz la traduce con `formulario.opciones.<valor>` si existe. */
  etiqueta: string;
}

/** Restricción sobre el valor de una propiedad dentro de una condición `if`. */
export interface RestriccionPropiedad {
  const?: unknown;
  enum?: readonly unknown[];
  /** Para listas: debe contener este valor. */
  contiene?: unknown;
  /** El valor debe ser distinto de este. */
  distintoDe?: unknown;
}

export interface Condicion {
  propiedades: Record<string, RestriccionPropiedad>;
}

export interface ConsecuenciaObjeto {
  requeridos: string[];
  /** Propiedades que no pueden existir (`properties: { x: false }`). */
  prohibidos: string[];
  /** `maxProperties: 1`: solo permanece la propiedad que decide la condición. */
  soloDiscriminadores: boolean;
}

export interface ReglaObjeto {
  cuando: Condicion;
  entonces: ConsecuenciaObjeto;
  sino: ConsecuenciaObjeto;
}

/** Regla sobre una lista: si contiene `contiene`, entonces admite como máximo `maximo` valores. */
export interface ReglaLista {
  contiene: unknown;
  maximo: number;
}

interface NodoBase {
  /** Ruta de propiedades separadas por `/`, sin índices: es el `elemento_ref` del dato. */
  ruta: string;
  clave: string;
  titulo: string;
  descripcion?: string;
  /** `readOnly` del esquema: dato precargado del inventario o de la sesión. */
  soloLectura: boolean;
  /** Está en el `required` incondicional de su padre. */
  obligatorio: boolean;
  /** Es obligatorio solo cuando se cumple alguna condición del padre (`then.required`). */
  condicional: boolean;
  /** Participa en el Índice de Condición (`x-sgp-evaluable`). */
  evaluable: boolean;
  fuente?: string;
}

export interface NodoCampo extends NodoBase {
  clase: 'campo';
  tipo: TipoCampo;
  anulable: boolean;
  opciones: OpcionCampo[];
  minimo?: number;
  maximo?: number;
  minimoExclusivo?: number;
  maximoExclusivo?: number;
  longitudMinima?: number;
  longitudMaxima?: number;
  /** Solo para selección múltiple. */
  minimoElementos?: number;
  maximoElementos?: number;
  reglasLista: ReglaLista[];
  sugerencias: string[];
  /**
   * Valor que significa «sin daño» en un indicador de daño (`x-sgp-sin-danos`): `NO_PRESENTA`
   * para una severidad, `false` para «falta elemento», `true` para «junta limpia»… Un campo que lo
   * declara es un indicador; los demás (material, medidas) describen el elemento.
   */
  sinDanos?: unknown;
}

export interface NodoObjeto extends NodoBase {
  clase: 'objeto';
  hijos: NodoFormulario[];
  reglas: ReglaObjeto[];
  /**
   * Está fuera del `required` de su padre: puede omitirse por completo, y solo si se empieza a
   * llenar se exigen sus propios obligatorios (por ejemplo, «Otros (especificar)»).
   */
  opcional: boolean;
}

export interface NodoArreglo extends NodoBase {
  clase: 'arreglo';
  elemento: NodoObjeto;
  /** Cómo se llama una instancia («Tramo», «Pila»), para «Agregar Tramo» o «Tramo 2». */
  nombreElemento: string;
  minimoElementos: number;
  maximoElementos?: number;
}

export type NodoFormulario = NodoCampo | NodoObjeto | NodoArreglo;

export interface EsquemaResuelto {
  titulo: string;
  descripcion?: string;
  raiz: NodoObjeto;
  /** Las secciones del formulario: los hijos de primer nivel de la raíz (objetos o listas repetibles). */
  secciones: (NodoObjeto | NodoArreglo)[];
}
