import { test, expect } from '@playwright/test';
import { fixture, user, schema } from './fixture.mjs';

test('HU-057/HU-060: captura con worker, reapertura offline, diagnóstico y eliminación segura', async ({
  browser,
  request,
}) => {
  await request.get('/__e2e/reset');
  const context = await browser.newContext({
    serviceWorkers: 'allow',
    permissions: ['geolocation'],
    geolocation: { latitude: 14, longitude: -90 },
  });
  let page = await context.newPage();
  try {
    await page.goto('/__e2e/setup');
    await page.evaluate(
      async ({ fixture, user, schema }) => {
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
              for (const index of {
                puentes: ['codigo', 'actualizadoEn'],
                inspecciones: ['puenteId', 'estado', 'actualizadoEn', 'sincronizado'],
                fotos: ['inspeccionId', 'sincronizado'],
                esquemas_formulario: ['version', 'activo'],
                cola_sync: ['tipo', 'entityId', 'creadoEn', 'intentos', 'propietarioId', 'estado'],
                notificaciones: ['leidoEn', 'creadoEn'],
              }[name] ?? [])
                store.createIndex(index, index);
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
          tx.onerror = reject;
        });
        db.close();
      },
      { fixture, user, schema },
    );
    await page.goto(`/inspecciones/${fixture.id}/borrador`);
    await expect(page.getByRole('heading', { name: /Puente de prueba/ })).toBeVisible();
    await expect.poll(() => page.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true);
    const manifest = await (await request.get('/ngsw.json')).json();
    const prefetched = manifest.assetGroups
      .filter((g) => g.installMode === 'prefetch')
      .flatMap((g) => g.urls);
    await expect
      .poll(() =>
        page.evaluate(async (urls) => {
          for (const url of urls) if (!(await caches.match(url))) return false;
          return true;
        }, prefetched),
      )
      .toBe(true);
    await context.setOffline(true);
    await page.evaluate(() => {
      // Fuente de cámara simulada explícitamente; el compresor y worker son los de producción.
      navigator.mediaDevices.getUserMedia = async () => {
        const canvas = document.createElement('canvas');
        canvas.width = 2400;
        canvas.height = 1800;
        const ctx = canvas.getContext('2d');
        ctx.fillStyle = '#426b87';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        return canvas.captureStream(1);
      };
    });
    const reporte = page.locator('app-reporte-fotografico');
    await reporte.getByRole('button', { name: 'Abrir cámara', exact: true }).click();
    await expect
      .poll(() => reporte.locator('video').evaluate((v) => v.videoWidth))
      .toBeGreaterThan(0);
    const worker = page.waitForEvent('worker');
    await reporte.getByRole('button', { name: 'Capturar con GPS', exact: true }).click();
    expect((await worker).url()).toMatch(/worker-.*\.js/);
    await expect(reporte.getByAltText('Previsualización de la fotografía')).toBeVisible();
    await reporte.getByRole('combobox').click();
    await page.getByRole('option', { name: 'ACCESO', exact: true }).click();
    await reporte.getByRole('button', { name: 'Adjuntar fotografía', exact: true }).click();
    await expect(
      reporte.getByRole('heading', { name: 'Pendientes en este dispositivo' }),
    ).toBeVisible();
    const snapshot = async (pagina) =>
      pagina.evaluate(async () => {
        const db = await new Promise((resolve) => {
          const r = indexedDB.open('sgp_offline');
          r.onsuccess = () => resolve(r.result);
        });
        const tx = db.transaction(['fotos', 'cola_sync']);
        const leer = (tabla) =>
          new Promise((resolve) => {
            const r = tx.objectStore(tabla).getAll();
            r.onsuccess = () => resolve(r.result);
          });
        const [fotos, cola] = await Promise.all([leer('fotos'), leer('cola_sync')]);
        db.close();
        const foto = fotos[0];
        if (!foto) return null;
        const bitmap = await createImageBitmap(foto.blob);
        const dimensiones = { ancho: bitmap.width, alto: bitmap.height };
        bitmap.close();
        return {
          ...dimensiones,
          id: foto.id,
          metadatos: foto.metadatos,
          clave: foto.idempotencyKey,
          colaClave: cola.find((op) => op.entityId === foto.id)?.idempotencyKey,
          size: foto.blob.size,
          mime: foto.blob.type,
          hash: Array.from(
            new Uint8Array(await crypto.subtle.digest('SHA-256', await foto.blob.arrayBuffer())),
          ),
        };
      });
    const antes = await snapshot(page);
    expect(antes.size).toBeGreaterThan(0);
    expect(antes.size).toBeLessThanOrEqual(400 * 1024);
    expect(antes.mime).toMatch(/^image\/(webp|jpeg)$/);
    expect(Math.max(antes.ancho, antes.alto)).toBe(1600);
    expect(antes.id[14]).toBe('7');
    expect(antes.metadatos.latitud).toBe(14);
    expect(antes.clave).toBe(antes.colaClave);
    await page.close();
    page = await context.newPage();
    await page.goto(`/inspecciones/${fixture.id}/borrador`);
    await expect(
      page.getByRole('heading', { name: 'Pendientes en este dispositivo' }),
    ).toBeVisible();
    expect(await snapshot(page)).toEqual(antes);
    await page.goto('/offline');
    await expect(page.getByText(antes.id, { exact: false })).toBeVisible();
    await expect(page.getByText('Todavía no se ha intentado', { exact: false })).toBeVisible();
    await page.goto(`/inspecciones/${fixture.id}/borrador`);
    await page.getByRole('button', { name: 'Eliminar fotografía local', exact: true }).click();
    await page
      .getByRole('dialog')
      .getByRole('button', { name: 'Eliminar fotografía local', exact: true })
      .click();
    await expect.poll(() => snapshot(page)).toBeNull();
  } finally {
    await context.close().catch(() => {});
  }
});
