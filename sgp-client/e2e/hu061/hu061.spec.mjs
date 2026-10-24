import { test, expect } from '@playwright/test';
import { fixture, user, schema, photos } from './fixture.mjs';

test('precarga, arranca sin red y confirma una publicación durable al reconectar', async ({
  browser,
  request,
}) => {
  await request.get('/__e2e/reset');
  const context = await browser.newContext({ serviceWorkers: 'allow' });
  const page = await context.newPage();
  await page.goto('/__e2e/setup');
  await page.evaluate(
    async ({ fixture, user, schema, photos }) => {
      localStorage.setItem('sgp.offline.user', JSON.stringify(user));
      const db = await new Promise((resolve, reject) => {
        const open = indexedDB.open('sgp_offline', 40);
        open.onupgradeneeded = () => {
          for (const [name, keyPath] of Object.entries({
            puentes: 'id',
            inspecciones: 'id',
            fotos: 'id',
            esquemas_formulario: 'id',
            cola_sync: 'id',
            notificaciones: 'id',
            notificaciones_meta: 'key',
            preparaciones_salida: 'propietarioId',
            solicitudes_salida: 'propietarioId',
            asignaciones_usuario: 'propietarioId',
          })) {
            const store = open.result.createObjectStore(name, { keyPath });
            const indices =
              {
                puentes: ['codigo', 'actualizadoEn'],
                inspecciones: ['puenteId', 'estado', 'actualizadoEn', 'sincronizado'],
                fotos: ['inspeccionId', 'sincronizado'],
                esquemas_formulario: ['version', 'activo'],
                cola_sync: ['tipo', 'entityId', 'creadoEn', 'intentos', 'propietarioId', 'estado'],
                notificaciones: ['leidoEn', 'creadoEn'],
              }[name] ?? [];
            for (const index of indices) store.createIndex(index, index);
          }
        };
        open.onsuccess = () => resolve(open.result);
        open.onerror = () => reject(open.error);
      });
      const tx = db.transaction(['inspecciones', 'esquemas_formulario'], 'readwrite');
      tx.objectStore('inspecciones').put({
        id: fixture.id,
        propietarioId: user.id,
        puenteId: fixture.puenteId,
        estado: 'BORRADOR',
        actualizadoEn: fixture.creadoEn,
        sincronizado: true,
        fotosConfirmadas: photos,
        payload: {
          borrador: fixture,
          versionFormularioId: fixture.versionFormularioId,
          autorId: user.id,
          dispositivoId: fixture.dispositivoId,
        },
      });
      tx.objectStore('esquemas_formulario').put({
        id: fixture.versionFormularioId,
        propietarioId: user.id,
        version: '1',
        activo: true,
        schema,
      });
      await new Promise((resolve, reject) => {
        tx.oncomplete = resolve;
        tx.onerror = () => reject(tx.error);
      });
      db.close();
    },
    { fixture, user, schema, photos },
  );
  await page.goto(`/inspecciones/${fixture.id}/borrador`);
  await expect(page.getByRole('heading', { name: /Puente de prueba/ })).toBeVisible();
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
  await expect
    .poll(() => page.evaluate(() => !!navigator.serviceWorker.controller), { timeout: 45000 })
    .toBe(true);
  expect((await request.get('/index.html')).headers()['cache-control']).toContain('no-store');
  const manifest = await (await request.get('/manifest.webmanifest')).json();
  expect(manifest.display).toBe('standalone');
  expect(manifest.icons).toHaveLength(2);
  await context.setOffline(true);
  await page.reload();
  await expect(page.getByRole('heading', { name: /Puente de prueba/ })).toBeVisible();
  await expect(page.getByText(/IC disponible al sincronizar/)).toBeVisible();
  await page.locator('.btn-accion-final').click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page
    .getByRole('dialog')
    .getByRole('button', { name: /^Publicar inspección$/ })
    .click();
  await expect(page.getByText(/Envío o publicación pendiente de confirmación/)).toBeVisible();
  await page.close();
  const reopened = await context.newPage();
  await reopened.goto(`/inspecciones/${fixture.id}/borrador`);
  await expect(reopened.getByText(/Envío o publicación pendiente de confirmación/)).toBeVisible();
  await context.setOffline(false);
  await reopened.evaluate(() => window.dispatchEvent(new Event('online')));
  await expect(reopened.getByRole('heading', { name: /Inspección publicada/ })).toBeVisible({
    timeout: 20000,
  });
  const result = await (await request.get('/__e2e/resultados')).json();
  expect(result.publicaciones).toHaveLength(1);
  await context.close();
});

test('revalida referencias con el worker y permite posponer una nueva versión', async ({
  page,
  request,
}) => {
  await request.get('/__e2e/version?numero=1');
  await request.get('/__e2e/referencia?nombre=Guatemala');
  await page.goto('/__e2e/setup');
  await page.evaluate(
    (user) => localStorage.setItem('sgp.offline.user', JSON.stringify(user)),
    user,
  );
  await page.goto('/offline');
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
  await expect
    .poll(() => page.evaluate(() => !!navigator.serviceWorker.controller), { timeout: 45000 })
    .toBe(true);
  const departamentos = () =>
    page.evaluate(async () => (await fetch('/api/v1/catalogos/departamentos')).json());
  await expect.poll(departamentos).toEqual([{ id: 'd', nombre: 'Guatemala' }]);
  await request.get('/__e2e/referencia?nombre=ReferenciaActualizada');
  expect(await departamentos()).toEqual([{ id: 'd', nombre: 'Guatemala' }]);
  await expect.poll(departamentos).toEqual([{ id: 'd', nombre: 'ReferenciaActualizada' }]);
  await request.get('/__e2e/version?numero=2');
  await page.evaluate(() =>
    navigator.serviceWorker.controller.postMessage({ action: 'CHECK_FOR_UPDATES', nonce: 7 }),
  );
  await expect(page.getByText(/Hay una nueva versión/)).toBeVisible({ timeout: 20000 });
  await page.getByRole('button', { name: 'Más tarde' }).click();
  await expect(page.getByText(/Hay una nueva versión/)).not.toBeVisible();
  const cached = await page.evaluate(async () => {
    const keys = await caches.keys();
    const urls = await Promise.all(
      keys.map(async (key) =>
        (await (await caches.open(key)).keys()).map((request) => request.url),
      ),
    );
    return urls.flat().filter((url) => url.includes('/api/v1/inspecciones/'));
  });
  expect(cached).toEqual([]);
});
