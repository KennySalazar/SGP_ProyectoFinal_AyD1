import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';

const origen = process.env.HU061_API_ORIGIN;
const pg = 'hu061-aceptacion-postgis';
const versionId = '0199a000-0000-7000-8000-000000000004';
const clavePrueba = 'Hu061-Prueba-2026!'; // Solo para el bootstrap de la base desechable.
const datos = { vigas: { existencia: 'PRESENTE', dano: 'MODERADO', cortado: 'NO' } };

function consulta(sql) {
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

async function locales(page, id) {
  return page.evaluate(async (id) => {
    const db = await new Promise((resolve, reject) => {
      const req = indexedDB.open('sgp_offline');
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
    const tx = db.transaction(['inspecciones', 'cola_sync']);
    const obtener = (req) =>
      new Promise((resolve, reject) => {
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
      });
    const [inspeccion, cola] = await Promise.all([
      obtener(tx.objectStore('inspecciones').get(id)),
      obtener(tx.objectStore('cola_sync').getAll()),
    ]);
    db.close();
    return { inspeccion, cola: cola.filter((op) => op.entityId === id) };
  }, id);
}

async function login(page, email) {
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

for (const rol of ['CATEDRATICO', 'ESTUDIANTE']) {
  test(`CA4/CA7 backend real: ${rol}`, async ({ browser }, testInfo) => {
    test.skip(!origen, 'Requiere Spring Boot aislado y real-fixture.sql en hu061_aceptacion');
    expect(origen).toBe('http://127.0.0.1:18090');
    expect(consulta('SELECT current_database()')).toBe('hu061_aceptacion');
    const context = await browser.newContext({ serviceWorkers: 'allow' });
    try {
      const page = await context.newPage();
      let ultimoToken;
      context.on('response', async (res) => {
        if (/\/api\/v1\/auth\/(login|refresh)$/.test(res.url()) && res.status() === 200) {
          const cuerpo = await res.json().catch(() => null);
          if (cuerpo?.accessToken) ultimoToken = cuerpo.accessToken;
        }
      });
      const email =
        rol === 'ESTUDIANTE' ? 'estudiante-hu061@example.test' : 'catedratico-hu061@example.test';
      const usuarioId =
        rol === 'ESTUDIANTE'
          ? '0199a000-0000-7000-8000-000000000002'
          : '0199a000-0000-7000-8000-000000000001';
      const puenteId =
        rol === 'ESTUDIANTE'
          ? '0199a000-0000-7000-8000-000000000013'
          : '0199a000-0000-7000-8000-000000000003';
      ultimoToken = await login(page, email);
      expect(
        (await context.cookies()).some((c) => c.name === 'SGP_REFRESH_TOKEN' && c.httpOnly),
      ).toBe(true);
      const headers = { Authorization: `Bearer ${ultimoToken}` };
      const id = randomUUID();
      let res = await context.request.post('/api/v1/inspecciones', {
        headers: { ...headers, 'Idempotency-Key': id },
        data: {
          id,
          puenteId,
          versionFormularioId: versionId,
          dispositivoId: 'hu061-real',
          fechaInspeccion: new Date().toISOString().slice(0, 10),
          latitud: 14.481,
          longitud: -90.615,
          confirmarDatosPermanentes: false,
        },
      });
      expect(res.status(), await res.text()).toBe(201);
      res = await context.request.put(`/api/v1/inspecciones/${id}/borrador`, {
        headers: { ...headers, 'Idempotency-Key': randomUUID() },
        data: { version: 0, datos },
      });
      expect(res.status(), await res.text()).toBe(200);
      const imagen = await readFile('public/icons/icon-192.png');
      for (const categoria of ['ACCESO', 'SUPERESTRUCTURA', 'SUBESTRUCTURA', 'CAUCE']) {
        const fotoId = randomUUID();
        res = await context.request.post(`/api/v1/inspecciones/${id}/fotografias`, {
          headers: { ...headers, 'Idempotency-Key': fotoId },
          multipart: {
            metadatos: {
              name: 'metadatos.json',
              mimeType: 'application/json',
              buffer: Buffer.from(
                JSON.stringify({
                  id: fotoId,
                  categoria,
                  latitud: 14.481,
                  longitud: -90.615,
                  capturadoEn: new Date().toISOString(),
                }),
              ),
            },
            archivo: { name: 'foto.png', mimeType: 'image/png', buffer: imagen },
          },
        });
        expect(res.status(), await res.text()).toBe(201);
      }
      await page.goto(`/inspecciones/${id}/borrador`);
      await expect(page.locator('.btn-accion-final')).toBeEnabled();
      await expect
        .poll(async () => (await locales(page, id)).inspeccion?.fotosConfirmadas?.length)
        .toBe(4);
      const configuracion = await (await context.request.get('/ngsw.json')).json();
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
              configuracion.assetGroups
                .filter((g) => g.installMode === 'prefetch')
                .flatMap((g) => g.urls),
            ),
          { timeout: 45000 },
        )
        .toBe(true);
      await context.setOffline(true);
      await page.evaluate(() => window.dispatchEvent(new Event('offline')));
      await page.locator('.btn-accion-final').click();
      await page
        .getByRole('dialog')
        .getByRole('button', {
          name: rol === 'ESTUDIANTE' ? /^Enviar a revisión$/ : /^Publicar inspección$/,
        })
        .click();
      await expect(page.getByText(/Envío o publicación pendiente de confirmación/)).toBeVisible();
      const pendiente = await locales(page, id);
      expect(pendiente.inspeccion.estado).toBe('BORRADOR');
      expect(pendiente.inspeccion.intencionFinal.estado).toBe('PENDIENTE');
      const final = pendiente.cola.find((op) =>
        ['ENVIAR_INSPECCION', 'PUBLICAR_INSPECCION'].includes(op.operacion),
      );
      expect(final.propietarioId).toBe(usuarioId);
      expect(consulta(`SELECT estado FROM inspeccion WHERE id='${id}'`)).toBe('BORRADOR');
      if (rol === 'CATEDRATICO') {
        await page.getByRole('button', { name: 'Cerrar sesion' }).click();
        await expect(page).toHaveURL(/\/login$/);
        expect(await page.evaluate(() => localStorage.getItem('sgp.offline.user'))).toBeNull();
        expect((await locales(page, id)).cola).toEqual(pendiente.cola);
      } else {
        const vencimiento = JSON.parse(
          Buffer.from(ultimoToken.split('.')[1], 'base64url').toString(),
        ).exp;
        await expect
          .poll(() => Date.now(), { timeout: 10000 })
          .toBeGreaterThan(vencimiento * 1000 + 1000);
      }
      const accion = rol === 'ESTUDIANTE' ? 'enviar' : 'publicar';
      const respuestaFinal = page.waitForResponse(
        (r) => r.url().endsWith(`/inspecciones/${id}/${accion}`) && r.request().method() === 'POST',
      );
      await context.setOffline(false);
      if (rol === 'CATEDRATICO') {
        await login(page, email);
      } else {
        const vencido = await context.request.get('/api/v1/auth/me', {
          headers: { Authorization: `Bearer ${ultimoToken}` },
        });
        expect(vencido.status()).toBe(401);
      }
      await page.evaluate(() => window.dispatchEvent(new Event('online')));
      const confirmacion = await respuestaFinal;
      expect(confirmacion.status(), await confirmacion.text()).toBe(200);
      expect(confirmacion.request().headers()['idempotency-key']).toBe(final.idempotencyKey);
      const cuerpo = await confirmacion.text();
      const estado = rol === 'ESTUDIANTE' ? 'ENVIADA' : 'PUBLICADA';
      await expect
        .poll(() => consulta(`SELECT estado FROM inspeccion WHERE id='${id}'`))
        .toBe(estado);
      await expect
        .poll(async () => (await locales(page, id)).inspeccion?.intencionFinal?.estado)
        .toBe('CONFIRMADA');
      const sincronizada = await locales(page, id);
      expect(sincronizada.cola).toEqual([]);
      expect(sincronizada.inspeccion.fotosConfirmadas).toHaveLength(4);
      expect(sincronizada.inspeccion.payload.borrador.datos.vigas).toEqual(datos.vigas);
      const conteos = () =>
        JSON.parse(
          consulta(
            `SELECT json_build_object('inspecciones',(SELECT count(*) FROM inspeccion WHERE id='${id}'),'historial',(SELECT count(*) FROM historial_estado_inspeccion WHERE inspeccion_id='${id}'),'ic',(SELECT count(*) FROM resultado_ic WHERE inspeccion_id='${id}' AND tipo='PUBLICACION'),'fotos',(SELECT count(*) FROM archivo_inspeccion WHERE inspeccion_id='${id}'),'reservasFinales',(SELECT count(*) FROM operacion_idempotente WHERE usuario_id='${usuarioId}' AND clave='${final.idempotencyKey}'))`,
          ),
        );
      const antesReplay = conteos();
      expect(antesReplay).toEqual({
        inspecciones: 1,
        historial: rol === 'ESTUDIANTE' ? 1 : 2,
        ic: rol === 'ESTUDIANTE' ? 0 : 1,
        fotos: 4,
        reservasFinales: 1,
      });
      const replay = await context.request.post(`/api/v1/inspecciones/${id}/${accion}`, {
        headers: {
          Authorization: `Bearer ${ultimoToken}`,
          'Idempotency-Key': final.idempotencyKey,
        },
      });
      expect(replay.status()).toBe(200);
      expect(replay.headers()['idempotent-replay']).toBe('true');
      expect(await replay.text()).toBe(cuerpo);
      expect(conteos()).toEqual(antesReplay);
      await testInfo.attach('resultado-real.json', {
        body: JSON.stringify({
          rol,
          id,
          estado,
          propietarioId: usuarioId,
          idempotencyKey: final.idempotencyKey,
          antesReplay,
          despuesReplay: conteos(),
          replay: replay.status(),
          cookieHttpOnly: true,
        }),
        contentType: 'application/json',
      });
    } finally {
      await context.close();
    }
  });
}
