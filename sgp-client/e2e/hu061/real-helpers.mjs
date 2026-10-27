import { expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';

export const origen = process.env.HU061_API_ORIGIN;
export const pg = 'hu061-aceptacion-postgis';
export const versionId = '0199a000-0000-7000-8000-000000000004';
export const autorId = '0199a000-0000-7000-8000-000000000001';
export const puenteId = '0199a000-0000-7000-8000-000000000003';
export const datos = { vigas: { existencia: 'PRESENTE', dano: 'MODERADO', cortado: 'NO' } };
const clavePrueba = 'Hu061-Prueba-2026!'; // Exclusiva del bootstrap desechable.

export function consulta(sql) {
  return execFileSync(
    'docker',
    [
      'exec',
      pg,
      'psql',
      '-U',
      'hu061_test',
      '-d',
      'hu061_aceptacion',
      '-At',
      '-v',
      'ON_ERROR_STOP=1',
      '-c',
      sql,
    ],
    { encoding: 'utf8' },
  ).trim();
}

export async function login(page, email) {
  await page.goto('/login');
  await page.locator('input[type=email]').fill(email);
  await page.locator('input[type=password]').fill(clavePrueba);
  const respuesta = page.waitForResponse(
    (r) => r.url().endsWith('/api/v1/auth/login') && r.request().method() === 'POST',
  );
  await page.locator('form button[type=submit]').click();
  const res = await respuesta;
  expect(res.status()).toBe(200);
  await expect(page.locator('app-shell')).toBeVisible();
  return (await res.json()).accessToken;
}

// Lectura de diagnóstico: nunca siembra ni modifica IndexedDB.
export async function locales(page, id, idsFotos = []) {
  return page.evaluate(
    async ({ id, idsFotos }) => {
      const db = await new Promise((resolve, reject) => {
        const req = indexedDB.open('sgp_offline');
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
      });
      const obtener = (req) =>
        new Promise((resolve, reject) => {
          req.onsuccess = () => resolve(req.result);
          req.onerror = () => reject(req.error);
        });
      let inspeccion, cola, fotos;
      try {
        const tx = db.transaction(['inspecciones', 'cola_sync', 'fotos']);
        [inspeccion, cola, fotos] = await Promise.all([
          obtener(tx.objectStore('inspecciones').get(id)),
          obtener(tx.objectStore('cola_sync').getAll()),
          obtener(tx.objectStore('fotos').getAll()),
        ]);
      } finally {
        db.close();
      }
      fotos = fotos.filter((f) => f.inspeccionId === id);
      const ids = new Set([
        ...idsFotos,
        ...fotos.map((f) => f.id),
        ...(inspeccion?.fotosConfirmadas ?? []).map((f) => f.id),
      ]);
      const orden = (a, b) => a.id.localeCompare(b.id);
      const resumidas = await Promise.all(
        fotos.map(async ({ blob, ...foto }) => ({
          ...foto,
          tamano: blob.size,
          sha256: [
            ...new Uint8Array(await crypto.subtle.digest('SHA-256', await blob.arrayBuffer())),
          ]
            .map((b) => b.toString(16).padStart(2, '0'))
            .join(''),
        })),
      );
      return {
        inspeccion,
        fotos: resumidas.sort(orden),
        cola: cola.filter((op) => op.entityId === id || ids.has(op.entityId)).sort(orden),
      };
    },
    { id, idsFotos },
  );
}

export async function esperarPrecarga(page, request) {
  await page.evaluate(() => navigator.serviceWorker.ready.then(() => undefined));
  await expect
    .poll(() => page.evaluate(() => !!navigator.serviceWorker.controller), { timeout: 45000 })
    .toBe(true);
  const configuracion = await (await request.get('/ngsw.json')).json();
  const urls = configuracion.assetGroups
    .filter((g) => g.installMode === 'prefetch')
    .flatMap((g) => g.urls);
  await expect
    .poll(
      () =>
        page.evaluate(
          async (urls) =>
            (
              await Promise.all(
                urls.map((url) =>
                  caches.match(new URL(url, location.origin).href, { ignoreVary: true }),
                ),
              )
            ).every(Boolean),
          urls,
        ),
      { timeout: 45000 },
    )
    .toBe(true);
}
