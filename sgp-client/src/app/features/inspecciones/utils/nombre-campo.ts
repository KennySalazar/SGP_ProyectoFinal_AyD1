import { EsquemaJson, NodoFormulario, NodoObjeto } from '../../formulario/motor/esquema/modelos';
import { humanizar, resolverEsquema } from '../../formulario/motor/esquema/resolutor';

export function nombreCampo(campo: string, esquema: EsquemaJson | null): string {
  const partes = campo
    .replace(/\[(\d+)\]/g, '/$1')
    .split(/[./]/)
    .filter(Boolean);
  if (partes[0] === 'datos') partes.shift();
  let nodo: NodoFormulario | undefined;
  if (esquema) {
    try {
      nodo = resolverEsquema(esquema).raiz;
    } catch {
      nodo = undefined;
    }
  }
  const etiquetas: string[] = [];
  for (const parte of partes) {
    if (/^\d+$/.test(parte)) {
      etiquetas.push(`Elemento ${Number(parte) + 1}`);
      if (nodo?.clase === 'arreglo') nodo = nodo.elemento;
      continue;
    }
    const objeto: NodoObjeto | undefined =
      nodo?.clase === 'arreglo' ? nodo.elemento : nodo?.clase === 'objeto' ? nodo : undefined;
    const hijo: NodoFormulario | undefined = objeto?.hijos.find((n) => n.clave === parte);
    etiquetas.push(hijo?.titulo || humanizar(parte));
    nodo = hijo;
  }
  return etiquetas.join(' > ') || 'Campo pendiente';
}

export function mensajeCampo(campo: string, mensaje: string, esquema: EsquemaJson | null): string {
  const nombre = nombreCampo(campo, esquema);
  return mensaje.includes(campo) && campo.length > 0
    ? mensaje.split(campo).join(nombre)
    : `${nombre}: ${mensaje || 'Revisa este campo antes de enviar.'}`;
}
