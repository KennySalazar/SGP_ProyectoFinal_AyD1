import { CategoriaFoto } from '../../inspecciones/models/fotografia.models';
import { AnclajeFormulario } from '../utils/anclaje-formulario';

/** Estados de observacion_revision definidos en V9; no representa el estado de un ciclo. */
export type EstadoObservacion = 'PENDIENTE' | 'RESUELTA';

export interface DestinoElementoObservacion {
  readonly tipo: 'ELEMENTO';
  readonly etiqueta: string;
  readonly anclaje: AnclajeFormulario;
}

export interface DestinoGeneralObservacion {
  readonly tipo: 'GENERAL';
}

export interface DestinoCategoriaObservacion {
  readonly tipo: 'CATEGORIA_FOTOGRAFICA';
  readonly categoriaFotografica: CategoriaFoto;
  readonly etiqueta: string;
}
/** Filas antiguas sin destino: visibles, sin atribuirles una ruta o convertirlas en generales. */
export interface DestinoSinAnclajeObservacion {
  readonly tipo: 'SIN_ANCLAJE';
  readonly etiqueta: string;
}
export type DestinoNavegableObservacion = DestinoElementoObservacion | DestinoCategoriaObservacion;
export type DestinoObservacion =
  DestinoNavegableObservacion | DestinoGeneralObservacion | DestinoSinAnclajeObservacion;

interface DatosObservacion {
  /** Identificador estable suministrado por el consumidor; el componente no genera identidades. */
  readonly id: string;
  readonly texto: string;
}

/** Modelo de presentación, no entidad ni contrato HTTP. La observación general no es un pendiente. */
export type ObservacionPresentacion = DatosObservacion &
  (
    | {
        readonly destino: DestinoNavegableObservacion | DestinoSinAnclajeObservacion;
        readonly estado: EstadoObservacion;
      }
    | { readonly destino: DestinoGeneralObservacion; readonly estado: null }
  );

export type ContextoEditorObservacion =
  | { readonly modo: 'CREAR'; readonly destino: DestinoObservacion }
  | { readonly modo: 'EDITAR'; readonly observacion: ObservacionPresentacion };

/** Intención local: el consumidor deberá autorizar y persistir la operación cuando exista el ciclo. */
export type PreparacionObservacion = {
  readonly destino: DestinoObservacion;
  readonly texto: string;
} & ({ readonly modo: 'CREAR' } | { readonly modo: 'EDITAR'; readonly id: string });
