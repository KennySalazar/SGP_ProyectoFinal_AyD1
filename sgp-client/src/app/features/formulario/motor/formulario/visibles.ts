import { FormGroup } from '@angular/forms';
import { NodoFormulario, NodoObjeto } from '../esquema/modelos';
import { ConjuntoOcultos } from './valor';

/** Hijos de un objeto que se muestran: los que no oculta una condición ni son identificadores. */
export function hijosVisibles(
  nodo: NodoObjeto,
  grupo: FormGroup,
  ocultos: ConjuntoOcultos,
): NodoFormulario[] {
  return nodo.hijos.filter((hijo) => {
    const control = grupo.controls[hijo.clave];
    return (
      control !== undefined &&
      !ocultos.has(control) &&
      !(hijo.clase === 'campo' && hijo.tipo === 'uuid')
    );
  });
}
