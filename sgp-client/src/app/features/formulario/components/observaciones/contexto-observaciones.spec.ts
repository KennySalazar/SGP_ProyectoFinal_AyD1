import { FormArray, FormControl, FormGroup } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { anclajeDeControl } from './contexto-observaciones';

describe('Anclajes del control histórico', () => {
  it('conserva claves originales con escapes y no interpreta puntos como separadores', () => {
    const campo = new FormControl('dato');
    new FormGroup({ 'sección/~': new FormGroup({ 'clave.punto': campo }) });
    expect(anclajeDeControl(campo)).toEqual({ elementoRef: '/sección~1~0/clave.punto' });
  });
  it('conserva el UUID de la instancia local aunque cambie su posición', () => {
    const campo = new FormControl(10);
    const instancia = new FormGroup({
      instancia_id: new FormControl('019ABC00-0000-7000-8000-000000000001'),
      longitud: campo,
    });
    const lista = new FormArray([instancia]);
    new FormGroup({ tramos: lista });
    expect(anclajeDeControl(campo)).toEqual({
      elementoRef: '/tramos/longitud',
      instanciaElementoId: '019ABC00-0000-7000-8000-000000000001',
    });
    lista.insert(
      0,
      new FormGroup({ instancia_id: new FormControl('otro'), longitud: new FormControl(1) }),
    );
    expect(anclajeDeControl(campo)?.instanciaElementoId).toBe(
      '019ABC00-0000-7000-8000-000000000001',
    );
    expect(anclajeDeControl(instancia)?.elementoRef).toBe('/tramos');
    expect(anclajeDeControl(lista)).toBeNull();
  });
  it('no admite instancias sin UUID ni repetibles anidados', () => {
    const campo = new FormControl('');
    new FormGroup({ lista: new FormArray([new FormGroup({ campo })]) });
    expect(anclajeDeControl(campo)).toBeNull();
    const anidado = new FormControl('');
    new FormGroup({
      exterior: new FormArray([
        new FormGroup({
          instancia_id: new FormControl('id1'),
          interior: new FormArray([
            new FormGroup({ instancia_id: new FormControl('id2'), anidado }),
          ]),
        }),
      ]),
    });
    expect(anclajeDeControl(anidado)).toBeNull();
  });
  it('mantiene arreglos escalares como campos sin identidad de instancia', () => {
    const campo = new FormControl(['uno', 'dos']);
    new FormGroup({ seleccion: campo });
    expect(anclajeDeControl(campo)).toEqual({ elementoRef: '/seleccion' });
  });
});
