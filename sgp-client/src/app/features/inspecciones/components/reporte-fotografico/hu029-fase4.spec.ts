import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, Subject, throwError } from 'rxjs';
import { afterEach, beforeEach, expect, vi } from 'vitest';
import { ejecutarGherkin } from '../../../../../testing/gherkin';
import source from './hu029-fase4.feature';
import es from '../../../../../../public/i18n/es.json';
import { AuthStore } from '../../../../core/services/auth.store';
import {
  FotografiasLocalesService,
  FotografiaPendiente,
} from '../../../../offline/services/fotografias-locales.service';
import { InspeccionApiService } from '../../services/inspeccion-api.service';
import { ProcesadorFotografiasService } from '../../services/procesador-fotografias.service';
import { FotografiaResponse, ValidacionEvidenciaResponse } from '../../models/fotografia.models';
import { ReporteFotograficoComponent } from './reporte-fotografico.component';

let fixture: ComponentFixture<ReporteFotograficoComponent> | undefined;
let c: ReporteFotograficoComponent;
const auth = { user: signal<{ id: string } | null>({ id: 'autor' }) };
const api = {
  listarFotografias: vi.fn(),
  consultarEvidenciaFotografica: vi.fn(),
  contenidoFotografia: vi.fn(),
  adjuntarFotografia: vi.fn(),
  retirarFotografia: vi.fn(),
};
const locales = {
  guardarContexto: vi.fn(),
  pendientes: vi.fn(),
  guardar: vi.fn(),
  confirmar: vi.fn(),
  registrarError: vi.fn(),
};
let diagnostico: ValidacionEvidenciaResponse;
let fotos: FotografiaResponse[];
let cola: FotografiaPendiente[];
let operacion: FotografiaPendiente | undefined;
let miniaturaAnterior: string;
const foto = (): FotografiaResponse => ({
  id: 'foto',
  inspeccionId: 'inspeccion',
  categoria: 'ACCESO',
  latitud: 14,
  longitud: -90,
  capturadoEn: '2026-10-16T12:00:00Z',
  mimeReal: 'image/jpeg',
  tamanoBytes: 4,
  anchoPx: 100,
  altoPx: 100,
});
const pendiente = (): FotografiaPendiente => ({
  id: 'pendiente',
  inspeccionId: 'inspeccion',
  propietarioId: 'autor',
  idempotencyKey: 'clave',
  blob: new Blob(['jpeg']),
  mime: 'image/jpeg',
  sincronizado: false,
  metadatos: {
    id: 'pendiente',
    categoria: 'CAUCE',
    latitud: 14,
    longitud: -90,
    capturadoEn: '2026-10-16T12:00:00Z',
  },
});
const sincronizar = async () => {
  fixture!.detectChanges();
  await fixture!.whenStable();
  fixture!.detectChanges();
};
const texto = () => (fixture!.nativeElement as HTMLElement).textContent ?? '';
const preparar = () => {
  c.borrador.set({
    blob: new Blob(['jpeg'], { type: 'image/jpeg' }),
    latitud: 14,
    longitud: -90,
    capturadoEn: '2026-10-16T12:00:00Z',
    orientacion: 1,
  });
  c.categoria.setValue('CAUCE');
};

beforeEach(async () => {
  auth.user.set({ id: 'autor' });
  Object.values(api).forEach((f) => f.mockReset());
  Object.values(locales).forEach((f) => f.mockReset());
  fotos = [foto()];
  cola = [];
  operacion = undefined;
  diagnostico = { valida: true, totalDisponibles: 1, maximoPermitido: 60, faltantes: [] };
  api.listarFotografias.mockImplementation(() => of(fotos));
  api.consultarEvidenciaFotografica.mockImplementation(() => of(diagnostico));
  api.contenidoFotografia.mockImplementation(() => of(new Blob(['jpeg'])));
  api.retirarFotografia.mockReturnValue(of(undefined));
  locales.guardarContexto.mockResolvedValue(undefined);
  locales.pendientes.mockImplementation(async () => cola);
  locales.guardar.mockImplementation(async (f: FotografiaPendiente) => {
    operacion = f;
    cola = [...cola, f];
  });
  locales.confirmar.mockImplementation(async (id: string) => {
    cola = cola.filter((f) => f.id !== id);
  });
  locales.registrarError.mockImplementation(async (id: string, _autor: string, codigo: string) => {
    cola = cola.map((f) => (f.id === id ? { ...f, ultimoError: codigo } : f));
  });
  let numero = 0;
  vi.spyOn(URL, 'createObjectURL').mockImplementation(() => `blob:foto-${++numero}`);
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
  vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true);
  await TestBed.configureTestingModule({
    imports: [
      ReporteFotograficoComponent,
      TranslocoTestingModule.forRoot({
        langs: { es },
        translocoConfig: { availableLangs: ['es'], defaultLang: 'es' },
      }),
    ],
    providers: [
      { provide: AuthStore, useValue: auth },
      { provide: InspeccionApiService, useValue: api },
      { provide: FotografiasLocalesService, useValue: locales },
      { provide: ProcesadorFotografiasService, useValue: {} },
    ],
  }).compileComponents();
});
afterEach(() => {
  fixture?.destroy();
  fixture = undefined;
  vi.restoreAllMocks();
});

async function abrir(actor: string, estado: string) {
  auth.user.set({ id: actor });
  fixture = TestBed.createComponent(ReporteFotograficoComponent);
  c = fixture.componentInstance;
  fixture.componentRef.setInput('inspeccion', {
    id: 'inspeccion',
    puenteId: 'puente',
    autorId: 'autor',
    estado,
  });
  await sincronizar();
  await vi.waitFor(() => expect(c.cargando()).toBe(false));
  await sincronizar();
  miniaturaAnterior = c.confirmadas()[0]?.miniatura ?? '';
}

ejecutarGherkin(source, 'HU-029 fase 4: panel fotográfico', [
  {
    patron: /^un diagnóstico con 1 fotografía disponible y faltante en CAUCE$/,
    ejecutar: () => {
      diagnostico = {
        ...diagnostico,
        valida: false,
        faltantes: [{ categoria: 'CAUCE', requeridas: 1, disponibles: 0 }],
      };
    },
  },
  {
    patron: /^un diagnóstico con 60 fotografías disponibles$/,
    ejecutar: () => {
      diagnostico.totalDisponibles = 60;
    },
  },
  {
    patron: /^una fotografía local pendiente$/,
    ejecutar: () => {
      cola = [pendiente()];
    },
  },
  { patron: /^el autor abre el panel en BORRADOR$/, ejecutar: () => abrir('autor', 'BORRADOR') },
  { patron: /^"([^"]+)" abre el panel en "([^"]+)"$/, ejecutar: abrir },
  {
    patron: /^el contador muestra 1 de 60 y el faltante Cauce$/,
    ejecutar: () => {
      expect(texto()).toContain('Fotografías disponibles: 1 de 60.');
      expect(texto()).toContain('Cauce:');
      expect(texto()).toContain('0 disponibles de 1 requeridas.');
    },
  },
  {
    patron: /^el pendiente se muestra separado sin aumentar la evidencia$/,
    ejecutar: () => {
      expect(texto()).toContain('1 fotografías guardadas');
      expect(texto()).toContain('No cuentan como evidencia disponible');
      expect(c.evidencia()?.totalDisponibles).toBe(1);
    },
  },
  {
    patron: /^adjunta una fotografía que el servidor confirma$/,
    ejecutar: async () => {
      preparar();
      fotos = [...fotos, { ...foto(), id: 'nueva', categoria: 'CAUCE' }];
      diagnostico = { ...diagnostico, totalDisponibles: 2, valida: true, faltantes: [] };
      api.adjuntarFotografia.mockReturnValue(of(fotos[1]));
      await c.adjuntar();
      await sincronizar();
    },
  },
  {
    patron: /^consulta nuevamente la lista y la evidencia$/,
    ejecutar: () => {
      expect(api.listarFotografias).toHaveBeenCalledTimes(2);
      expect(api.consultarEvidenciaFotografica).toHaveBeenCalledTimes(2);
    },
  },
  {
    patron: /^muestra 2 disponibles sin pendientes y libera la miniatura anterior$/,
    ejecutar: () => {
      expect(c.evidencia()?.totalDisponibles).toBe(2);
      expect(c.pendientes()).toEqual([]);
      expect(c.confirmadas()).toHaveLength(2);
      expect(URL.revokeObjectURL).toHaveBeenCalledWith(miniaturaAnterior);
    },
  },
  {
    patron: /^solicita retirar una fotografía$/,
    ejecutar: async () => {
      const boton = Array.from(
        (fixture!.nativeElement as HTMLElement).querySelectorAll('button'),
      ).find((b) => b.textContent?.trim() === 'Retirar fotografía');
      expect(boton).toBeDefined();
      boton!.click();
      await sincronizar();
    },
  },
  {
    patron: /^muestra el diálogo de confirmación sin enviar DELETE$/,
    ejecutar: () => {
      expect(document.body.textContent).toContain('Confirmar retirada de fotografía');
      expect(document.body.textContent).toContain('dejará de estar disponible');
      expect(c.fotoPorRetirar()?.id).toBe('foto');
      expect(api.retirarFotografia).not.toHaveBeenCalled();
    },
  },
  {
    patron: /^cancela la retirada$/,
    ejecutar: async () => {
      c.cancelarRetirada();
      await sincronizar();
      expect(c.fotoPorRetirar()).toBeNull();
    },
  },
  {
    patron: /^conserva la fotografía sin enviar DELETE$/,
    ejecutar: () => {
      expect(c.confirmadas()).toHaveLength(1);
      expect(api.retirarFotografia).not.toHaveBeenCalled();
      expect(locales.confirmar).not.toHaveBeenCalled();
    },
  },
  {
    patron: /^el servidor confirma la retirada$/,
    ejecutar: async () => {
      fotos = [];
      diagnostico = {
        ...diagnostico,
        valida: false,
        totalDisponibles: 0,
        faltantes: [{ categoria: 'ACCESO', requeridas: 1, disponibles: 0 }],
      };
      await c.confirmarRetirada();
      await sincronizar();
      expect(api.retirarFotografia).toHaveBeenCalledWith('inspeccion', 'foto');
    },
  },
  {
    patron: /^desaparece la fotografía y se libera su miniatura$/,
    ejecutar: () => {
      expect(c.confirmadas()).toEqual([]);
      expect(c.fotoPorRetirar()).toBeNull();
      expect(URL.revokeObjectURL).toHaveBeenCalledWith(miniaturaAnterior);
      expect(c.retiradaConfirmada()).toBe(true);
    },
  },
  {
    patron: /^muestra el nuevo faltante de Acceso$/,
    ejecutar: () => {
      expect(c.evidencia()?.totalDisponibles).toBe(0);
      expect(texto()).toContain('Acceso:');
      expect(api.consultarEvidenciaFotografica).toHaveBeenCalledTimes(2);
    },
  },
  {
    patron: /^la retirada falla con estado (\d+) y código "([^"]*)"$/,
    ejecutar: async (status, code) => {
      api.retirarFotografia.mockReturnValue(
        throwError(() => ({ status: Number(status), error: { code } })),
      );
      await c.confirmarRetirada();
      await sincronizar();
    },
  },
  {
    patron: /^mantiene la fotografía y muestra "([^"]+)" en el diálogo$/,
    ejecutar: (mensaje) => {
      expect(c.confirmadas()).toHaveLength(1);
      expect(c.errorRetirada()).toBe(mensaje);
      expect(document.body.querySelector('[role="dialog"] [role="alert"]')).not.toBeNull();
      expect(URL.revokeObjectURL).not.toHaveBeenCalledWith(miniaturaAnterior);
      if (mensaje === 'estado') expect(c.puedeModificar()).toBe(false);
    },
  },
  {
    patron: /^confirma la retirada sin conexión$/,
    ejecutar: async () => {
      vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);
      await c.confirmarRetirada();
      await sincronizar();
    },
  },
  {
    patron: /^muestra un error de retirada en el diálogo$/,
    ejecutar: () => {
      expect(c.errorRetirada()).toBe('retirada');
      expect(c.fotoPorRetirar()).not.toBeNull();
    },
  },
  {
    patron: /^limpia fotografías evidencia y pendientes sin borrar el almacenamiento local$/,
    ejecutar: () => {
      expect(c.confirmadas()).toEqual([]);
      expect(c.evidencia()).toBeNull();
      expect(c.pendientes()).toEqual([]);
      expect(locales.confirmar).not.toHaveBeenCalled();
      expect(URL.revokeObjectURL).toHaveBeenCalledWith(miniaturaAnterior);
    },
  },
  {
    patron: /^muestra "([^"]+)" en el diálogo sin permitir edición$/,
    ejecutar: (mensaje) => {
      expect(c.errorRetirada()).toBe(mensaje);
      expect(c.puedeModificar()).toBe(false);
    },
  },
  {
    patron: /^la carga falla con estado (\d+) y código "([^"]*)"$/,
    ejecutar: async (status, code) => {
      preparar();
      api.adjuntarFotografia.mockReturnValue(
        throwError(() => ({ status: Number(status), error: { code } })),
      );
      await c.adjuntar();
      await sincronizar();
    },
  },
  {
    patron: /^conserva el blob el UUID y la clave y registra "([^"]+)"$/,
    ejecutar: (mensaje) => {
      const p = c.pendientes()[0];
      expect(p.id).toBe(operacion!.id);
      expect(p.blob).toBe(operacion!.blob);
      expect(p.idempotencyKey).toBe(operacion!.idempotencyKey);
      expect(p.ultimoError).toBe(mensaje);
      expect(locales.registrarError).toHaveBeenCalledWith(p.id, 'autor', mensaje);
      expect(c.error()).toBe(mensaje);
    },
  },
  {
    patron: /^no confirma ni reenvía automáticamente la fotografía$/,
    ejecutar: () => {
      expect(locales.confirmar).not.toHaveBeenCalled();
      expect(api.adjuntarFotografia).toHaveBeenCalledTimes(1);
      if (c.pendientes()[0].ultimoError !== 'limite')
        expect(c.puedeReintentar(c.pendientes()[0])).toBe(false);
    },
  },
  {
    patron: /^no permite reintentar con el inventario lleno$/,
    ejecutar: async () => {
      const p = c.pendientes()[0];
      expect(c.puedeReintentar(p)).toBe(false);
      await c.reintentar(p);
      expect(api.adjuntarFotografia).toHaveBeenCalledTimes(1);
    },
  },
  {
    patron: /^el servidor informa 59 disponibles al actualizar$/,
    ejecutar: async () => {
      diagnostico = { ...diagnostico, totalDisponibles: 59 };
      await c.actualizar();
      await sincronizar();
      expect(c.puedeReintentar(c.pendientes()[0])).toBe(true);
    },
  },
  {
    patron: /^reintenta la carga pendiente con éxito$/,
    ejecutar: async () => {
      api.adjuntarFotografia.mockReturnValue(of(foto()));
      await c.reintentar(c.pendientes()[0]);
      await sincronizar();
    },
  },
  {
    patron: /^conserva exactamente la operación original al reintentar$/,
    ejecutar: () => {
      expect(api.adjuntarFotografia.mock.calls[1]).toEqual(api.adjuntarFotografia.mock.calls[0]);
      expect(c.pendientes()).toEqual([]);
      expect(locales.confirmar).toHaveBeenCalledWith(operacion!.id, 'autor');
    },
  },
  {
    patron: /^consulta fotografías y evidencia remotas$/,
    ejecutar: () => {
      expect(api.listarFotografias).toHaveBeenCalledWith('inspeccion');
      expect(api.consultarEvidenciaFotografica).toHaveBeenCalledWith('inspeccion');
    },
  },
  {
    patron: /^no permite captura carga reintento ni retirada$/,
    ejecutar: async () => {
      expect(c.puedeModificar()).toBe(false);
      expect(
        (fixture!.nativeElement as HTMLElement).querySelector('input[type="file"]'),
      ).toBeNull();
      preparar();
      await c.adjuntar();
      await c.abrirCamara();
      await c.reintentar(pendiente());
      c.solicitarRetirada(foto());
      await c.confirmarRetirada();
      expect(api.adjuntarFotografia).not.toHaveBeenCalled();
      expect(api.retirarFotografia).not.toHaveBeenCalled();
      expect(c.fotoPorRetirar()).toBeNull();
    },
  },
  {
    patron: /^no lee ni guarda contexto o cola local$/,
    ejecutar: () => {
      expect(locales.guardarContexto).not.toHaveBeenCalled();
      expect(locales.pendientes).not.toHaveBeenCalled();
      expect(locales.guardar).not.toHaveBeenCalled();
      expect(c.pendientes()).toEqual([]);
    },
  },
  {
    patron: /^puede solicitar el original a la API protegida$/,
    ejecutar: async () => {
      vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
      await c.abrirOriginal(foto());
      expect(api.contenidoFotografia).toHaveBeenLastCalledWith('inspeccion', 'foto');
    },
  },
  {
    patron: /^comienza una consulta que finaliza después de cerrar sesión$/,
    ejecutar: async () => {
      const tardia = new Subject<FotografiaResponse[]>();
      api.listarFotografias.mockReturnValue(tardia);
      const consulta = c.actualizar();
      c.previsualizacion.set('blob:previa');
      auth.user.set(null);
      await sincronizar();
      tardia.next([foto()]);
      tardia.complete();
      await consulta;
      await sincronizar();
    },
  },
  {
    patron: /^quedan vacías las fotografías evidencia y previsualizaciones$/,
    ejecutar: () => {
      expect(c.confirmadas()).toEqual([]);
      expect(c.evidencia()).toBeNull();
      expect(c.previsualizacion()).toBeNull();
      expect(c.pendientes()).toEqual([]);
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:previa');
    },
  },
  {
    patron: /^falla la actualización de evidencia$/,
    ejecutar: async () => {
      api.consultarEvidenciaFotografica.mockReturnValue(throwError(() => ({ status: 500 })));
      await c.actualizar();
      await sincronizar();
    },
  },
  {
    patron: /^no muestra diagnóstico válido ni totales inventados$/,
    ejecutar: () => {
      expect(c.evidencia()).toBeNull();
      expect(c.error()).toBe('lectura');
      expect(texto()).toContain('No se pudo confirmar el diagnóstico');
      expect(texto()).not.toContain('cumple los mínimos');
      expect(texto()).not.toContain('Fotografías disponibles:');
    },
  },
]);
