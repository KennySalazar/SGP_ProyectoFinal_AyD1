import { test, expect } from '@playwright/test';
import { consulta, locales } from './real-helpers.mjs';
import {
  validarEntorno,
  prepararOffline,
  verificarSincronizacion,
  contar,
  escrituras,
} from './hu062-helpers.mjs';

test.use({
  serviceWorkers: 'allow',
  screenshot: 'only-on-failure',
  actionTimeout: 15000,
  geolocation: { latitude: 14.481, longitude: -90.615 },
  permissions: ['geolocation'],
  timezoneId: 'America/Guatemala',
});

test.describe('HU-062 fase 1 / Spring Boot real', () => {
  test.setTimeout(120000);
  test.beforeAll(validarEntorno);

  test('sin conexión: crea datos y fotos, conserva claves y recarga mediante el worker', async ({
    page,
    context,
    request,
  }, testInfo) => {
    const { id, pendiente, antes, remotasAntes } = await prepararOffline(page, context, request);
    const nav = await page.reload();
    expect(nav.fromServiceWorker()).toBe(true);
    await expect(page.getByText(/Envío o publicación pendiente de confirmación/)).toBeVisible();
    await expect.poll(() => page.evaluate(() => navigator.onLine)).toBe(false);
    const despues = await locales(
      page,
      id,
      pendiente.fotos.map((f) => f.id),
    );
    expect(despues).toEqual(pendiente);
    expect(await escrituras(request)).toEqual(antes);
    expect(contar()).toBe(remotasAntes);
    expect(consulta(`SELECT count(*) FROM archivo_inspeccion WHERE inspeccion_id='${id}'`)).toBe(
      '0',
    );
    await testInfo.attach('offline.json', {
      body: JSON.stringify(
        { id, recargaServiceWorker: true, escriturasRemotas: 0, antes: pendiente, despues },
        null,
        2,
      ),
      contentType: 'application/json',
    });
  });

  test('red normal: sincroniza creación, respuestas, cuatro fotos y publicación sin duplicados', async ({
    page,
    context,
    request,
  }, testInfo) => {
    let token;
    context.on('response', async (res) => {
      if (/\/api\/v1\/auth\/(login|refresh)$/.test(res.url()) && res.status() === 200) {
        token = (await res.json().catch(() => null))?.accessToken ?? token;
      }
    });
    const estado = await prepararOffline(page, context, request);
    const { id, pendiente } = estado;
    const respuestaFinal = page.waitForResponse(
      (r) => r.url().endsWith(`/inspecciones/${id}/publicar`) && r.request().method() === 'POST',
    );
    await context.setOffline(false);
    await page.evaluate(() => window.dispatchEvent(new Event('online')));
    const confirmacion = await respuestaFinal;
    const { enviadas } = await verificarSincronizacion(
      page,
      context,
      request,
      testInfo,
      estado,
      confirmacion,
      token,
    );
    // Cada fila de cola se entregó exactamente una vez en el perfil normal.
    expect(enviadas).toHaveLength(pendiente.cola.length);
    expect(enviadas.map((r) => r.clave).sort()).toEqual(
      pendiente.cola.map((op) => op.idempotencyKey).sort(),
    );
    expect(enviadas.at(0).ruta).toBe('/api/v1/inspecciones');
    expect(enviadas.at(-1).ruta).toBe(`/api/v1/inspecciones/${id}/publicar`);
  });
});
