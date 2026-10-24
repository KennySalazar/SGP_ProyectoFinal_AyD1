import { test, expect, chromium } from '@playwright/test';
import { fixture, user, schema, photos } from './fixture.mjs';

for (const rol of ['CATEDRATICO', 'ESTUDIANTE']) {
  test(`precarga, reabre offline y sincroniza tras recuperar sesión: ${rol}`, async ({
    browser,
    request,
  }) => {
    await request.get(`/__e2e/reset?rol=${rol}`);
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
                  cola_sync: [
                    'tipo',
                    'entityId',
                    'creadoEn',
                    'intentos',
                    'propietarioId',
                    'estado',
                  ],
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
      { fixture: { ...fixture, rolAutor: rol }, user: { ...user, role: rol }, schema, photos },
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
    // Controller/ready no implica que Angular haya terminado de precargar todos los recursos.
    const configuracion = await (await request.get('/ngsw.json')).json();
    const precarga = configuracion.assetGroups
      .filter((grupo) => grupo.installMode === 'prefetch')
      .flatMap((grupo) => grupo.urls);
    await expect
      .poll(
        () =>
          page.evaluate(async (urls) => {
            const respuestas = await Promise.all(
              urls.map((url) =>
                caches.match(new URL(url, location.origin).href, { ignoreVary: true }),
              ),
            );
            return respuestas.every(Boolean);
          }, precarga),
        { timeout: 45000 },
      )
      .toBe(true);
    await context.setOffline(true);
    await page.reload();
    await expect(page.getByRole('heading', { name: /Puente de prueba/ })).toBeVisible();
    await expect(page.getByText(/IC disponible al sincronizar/)).toBeVisible();
    await page.locator('.btn-accion-final').click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page
      .getByRole('dialog')
      .getByRole('button', {
        name: rol === 'ESTUDIANTE' ? /^Enviar a revisión$/ : /^Publicar inspección$/,
      })
      .click();
    await expect(page.getByText(/Envío o publicación pendiente de confirmación/)).toBeVisible();
    await page.close();
    const reopened = await context.newPage();
    await reopened.goto(`/inspecciones/${fixture.id}/borrador`);
    await expect(reopened.getByText(/Envío o publicación pendiente de confirmación/)).toBeVisible();
    const pendientes = () =>
      reopened.evaluate(async () => {
        const db = await new Promise((resolve, reject) => {
          const open = indexedDB.open('sgp_offline');
          open.onsuccess = () => resolve(open.result);
          open.onerror = () => reject(open.error);
        });
        const items = await new Promise((resolve, reject) => {
          const req = db.transaction('cola_sync').objectStore('cola_sync').getAll();
          req.onsuccess = () => resolve(req.result);
          req.onerror = () => reject(req.error);
        });
        db.close();
        return items.map(({ id, idempotencyKey, propietarioId }) => ({
          id,
          idempotencyKey,
          propietarioId,
        }));
      });
    const antes = await pendientes();
    expect(antes.length).toBeGreaterThan(0);
    if (rol === 'CATEDRATICO') {
      await reopened.getByRole('button', { name: 'Cerrar sesion' }).click();
      await expect(reopened).toHaveURL(/\/login$/);
      expect(await pendientes()).toEqual(antes);
      expect(await reopened.evaluate(() => localStorage.getItem('sgp.offline.user'))).toBeNull();
    }
    // Expiración del servidor de prueba: exige login, sin autorizarse con el perfil offline.
    await request.get('/__e2e/sesion?activa=false');
    await context.setOffline(false);
    if (rol === 'ESTUDIANTE') await reopened.reload();
    await reopened.evaluate(() => window.dispatchEvent(new Event('online')));
    await expect(reopened.locator('input[type=email]')).toBeVisible();
    expect(await pendientes()).toEqual(antes);
    expect((await (await request.get('/__e2e/resultados')).json()).publicaciones).toEqual([]);
    await reopened.locator('input[type=email]').fill(user.email);
    await reopened.locator('input[type=password]').fill('solo-fixture-hu061');
    await reopened.locator('form button[type=submit]').click();
    await expect
      .poll(async () => (await (await request.get('/__e2e/resultados')).json()).estado)
      .toBe(rol === 'ESTUDIANTE' ? 'ENVIADA' : 'PUBLICADA');
    await reopened.goto(`/inspecciones/${fixture.id}/borrador`);
    await expect(
      reopened.getByRole('heading', {
        name: rol === 'ESTUDIANTE' ? /Inspección enviada a revisión/ : /Inspección publicada/,
      }),
    ).toBeVisible({
      timeout: 20000,
    });
    const result = await (await request.get('/__e2e/resultados')).json();
    expect(result.publicaciones).toHaveLength(1);
    expect(antes.some((op) => op.idempotencyKey === result.publicaciones[0])).toBe(true);
    expect(await pendientes()).toEqual([]);
    await context.close();
  });
}

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
  const urls = ['/api/v1/formularios/activo', '/api/v1/catalogos/departamentos/d/municipios'];
  const referencias = await page.evaluate(
    async (urls) => Promise.all(urls.map(async (url) => (await fetch(url)).json())),
    urls,
  );
  await expect
    .poll(() =>
      page.evaluate(
        async (urls) => Promise.all(urls.map(async (url) => (await fetch(url)).json())),
        urls,
      ),
    )
    .toEqual(referencias);
  await page.context().setOffline(true);
  expect(
    await page.evaluate(
      async (urls) => Promise.all(urls.map(async (url) => (await fetch(url)).json())),
      urls,
    ),
  ).toEqual(referencias);
  await page.context().setOffline(false);
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

test('instala la PWA y la lanza sin red desde un perfil persistente', async ({
  request,
}, testInfo) => {
  await request.get('/__e2e/reset');
  const perfil = testInfo.outputPath('perfil-instalacion');
  const manifestId = 'http://127.0.0.1:4173/';
  let context = await chromium.launchPersistentContext(perfil, {
    channel: 'chromium',
    headless: true,
    baseURL: 'http://127.0.0.1:4173',
  });
  let instalada = false;
  try {
    let page = context.pages()[0];
    await page.goto('/__e2e/setup');
    await page.evaluate(
      (user) => localStorage.setItem('sgp.offline.user', JSON.stringify(user)),
      user,
    );
    await page.goto('/offline');
    await page.evaluate(() => navigator.serviceWorker.ready.then(() => true));
    await expect.poll(() => page.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true);
    let cdp = await context.newCDPSession(page);
    const requisitos = await cdp.send('Page.getInstallabilityErrors');
    expect(requisitos.installabilityErrors).toEqual([]);
    await cdp.send('PWA.install', { manifestId, installUrlOrBundleUrl: manifestId });
    instalada = true;
    // Equivale a elegir «Abrir como ventana» en la instalación de escritorio.
    await cdp.send('PWA.changeAppUserSettings', { manifestId, displayMode: 'standalone' });
    const instaladaEnChrome = await cdp.send('PWA.getOsAppState', { manifestId });
    await testInfo.attach('instalacion.json', {
      body: JSON.stringify({ requisitos, instaladaEnChrome }),
      contentType: 'application/json',
    });
    await context.close();
    context = await chromium.launchPersistentContext(perfil, {
      channel: 'chromium',
      headless: true,
      baseURL: 'http://127.0.0.1:4173',
    });
    await context.setOffline(true);
    page = context.pages()[0];
    cdp = await context.newCDPSession(page);
    const nuevaPagina = context.waitForEvent('page');
    await cdp.send('PWA.launch', { manifestId });
    const app = await nuevaPagina;
    await expect(app.locator('app-shell')).toBeVisible();
    expect(await app.evaluate(() => matchMedia('(display-mode: standalone)').matches)).toBe(true);
    expect(await app.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true);
    await expect(app.getByText('Sin conexion', { exact: true })).toBeVisible();
    await app.screenshot({ path: testInfo.outputPath('pwa-instalada-offline.png') });
  } finally {
    if (instalada) {
      const cdp = await context.newCDPSession(context.pages()[0]);
      await cdp.send('PWA.uninstall', { manifestId });
    }
    await context.close();
  }
});

test('carga inicial fría en 3G simulada inferior a cinco segundos', async ({
  browser,
  request,
}, testInfo) => {
  await request.get('/__e2e/sesion?activa=false');
  const perfil = { downloadBps: 1600000 / 8, uploadBps: 750000 / 8, latencyMs: 150, cpu: 4 };
  const muestras = [];
  for (let muestra = 0; muestra < 3; muestra++) {
    const context = await browser.newContext({
      serviceWorkers: 'allow',
      viewport: { width: 1365, height: 768 },
    });
    try {
      const page = await context.newPage();
      const cdp = await context.newCDPSession(page);
      await cdp.send('Network.enable');
      await cdp.send('Network.setCacheDisabled', { cacheDisabled: true });
      await cdp.send('Network.emulateNetworkConditions', {
        offline: false,
        latency: perfil.latencyMs,
        downloadThroughput: perfil.downloadBps,
        uploadThroughput: perfil.uploadBps,
        connectionType: 'cellular3g',
      });
      await cdp.send('Emulation.setCPUThrottlingRate', { rate: perfil.cpu });
      await page.goto('/', { waitUntil: 'domcontentloaded' });
      await expect(page.locator('input[type=email]')).toBeVisible({ timeout: 15000 });
      await expect(page.locator('form button[type=submit]')).toBeVisible();
      muestras.push(
        await page.evaluate(() => ({
          interfazListaMs: performance.now(),
          navegacion: performance.getEntriesByType('navigation')[0].toJSON(),
          pinturas: performance.getEntriesByType('paint').map((entry) => entry.toJSON()),
          recursos: performance.getEntriesByType('resource').map((entry) => ({
            url: entry.name,
            transferSize: entry.transferSize,
            encodedBodySize: entry.encodedBodySize,
            decodedBodySize: entry.decodedBodySize,
          })),
        })),
      );
    } finally {
      await context.close();
    }
  }
  await testInfo.attach('medicion-3g.json', {
    body: JSON.stringify({ perfil, navegador: browser.version(), muestras }, null, 2),
    contentType: 'application/json',
  });
  for (const muestra of muestras) expect(muestra.interfazListaMs).toBeLessThan(5000);
});
