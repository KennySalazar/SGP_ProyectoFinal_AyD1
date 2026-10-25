import { test, expect } from '@playwright/test';
import { consulta, locales, datos } from './real-helpers.mjs';
import {
  validarEntorno,
  prepararOffline,
  verificarSincronizacion,
  estadoRed,
  configurar,
  esperarBarrera,
  liberar,
} from './hu062-helpers.mjs';

test.use({
  serviceWorkers: 'allow',
  screenshot: 'only-on-failure',
  actionTimeout: 15000,
  geolocation: { latitude: 14.481, longitude: -90.615 },
  permissions: ['geolocation'],
  timezoneId: 'America/Guatemala',
});

// Solo excluye campos que legítimamente cambian con el intento; no escribe en Dexie.
async function comprobarPendientes(page, estado, clave) {
  const actual = await locales(
    page,
    estado.id,
    estado.pendiente.fotos.map((f) => f.id),
  );
  expect(actual.inspeccion.intencionFinal.estado).toBe('PENDIENTE');
  expect(actual.inspeccion.payload.borrador.datos.vigas).toEqual(datos.vigas);
  expect(actual.fotos).toEqual(estado.pendiente.fotos);
  const operacion = actual.cola.find((op) => op.idempotencyKey === clave);
  const original = estado.pendiente.cola.find((op) => op.idempotencyKey === clave);
  expect(operacion).toBeDefined();
  expect(operacion.id).toBe(original.id);
  expect(operacion.entityId).toBe(original.entityId);
  expect(operacion.idempotencyKey).toBe(original.idempotencyKey);
  for (const op of actual.cola) {
    expect(estado.pendiente.cola.find((p) => p.id === op.id)?.idempotencyKey).toBe(
      op.idempotencyKey,
    );
  }
  return actual;
}

test.describe('HU-062 fase 2 / Spring Boot real', () => {
  test.setTimeout(180000);
  test.beforeAll(validarEntorno);
  for (const [perfil, titulo] of [
    ['latencia', '1500 ms por sentido, pendientes hasta confirmar'],
    ['antes', 'dos fallos antes del backend y recuperación automática'],
    ['respuesta', 'confirmación perdida después de commit y replay seguro'],
  ]) {
    test(titulo, async ({ page, context, request }, testInfo) => {
      let token;
      const fallosNavegador = [];
      const snapshots = [];
      context.on('response', async (res) => {
        if (/\/api\/v1\/auth\/(login|refresh)$/.test(res.url()) && res.status() === 200)
          token = (await res.json().catch(() => null))?.accessToken ?? token;
      });
      const estado = await prepararOffline(page, context, request);
      const creacion = estado.pendiente.cola.find((op) => op.operacion === 'CREAR_INSPECCION');
      page.on('requestfailed', (req) => {
        if (req.url().endsWith('/api/v1/inspecciones') && req.method() === 'POST')
          fallosNavegador.push({
            clave: req.headers()['idempotency-key'],
            error: req.failure()?.errorText,
          });
      });
      await configurar(request, perfil, estado.id);
      try {
        const final = page.waitForResponse(
          (r) =>
            r.url().endsWith(`/inspecciones/${estado.id}/publicar`) &&
            r.request().method() === 'POST',
          { timeout: 120000 },
        );
        // Evita rechazo sin observador si una aserción de diagnóstico falla antes de await final.
        final.catch(() => {});
        await context.setOffline(false);
        await page.evaluate(() => window.dispatchEvent(new Event('online')));
        if (perfil === 'latencia') {
          await esperarBarrera(request, 'peticion-foto');
          let red = await estadoRed(request);
          expect(red.peticionMs).toBe(1500);
          expect(red.respuestaMs).toBe(1500);
          const evento = red.eventos.find((e) => e.barrera === 'peticion-foto');
          snapshots.push({
            etapa: 'antes de enviar foto',
            local: await comprobarPendientes(page, estado, evento.clave),
          });
          expect(
            consulta(`SELECT count(*) FROM archivo_inspeccion WHERE inspeccion_id='${estado.id}'`),
          ).toBe('0');
          await liberar(request, 'peticion-foto');
          await esperarBarrera(request, 'respuesta-foto');
          red = await estadoRed(request);
          const respuesta = red.eventos.find(
            (e) => e.tipo === 'backend-confirmado' && e.clave === evento.clave,
          );
          expect(respuesta.status).toBe(201);
          snapshots.push({
            etapa: 'foto remota confirmada, respuesta retenida',
            local: await comprobarPendientes(page, estado, evento.clave),
          });
          expect(
            consulta(`SELECT count(*) FROM archivo_inspeccion WHERE inspeccion_id='${estado.id}'`),
          ).toBe('1');
          await liberar(request, 'respuesta-foto');
        } else if (perfil === 'antes') {
          for (const intento of [1, 2, 3]) {
            await esperarBarrera(request, `antes-${intento}`);
            const local = await comprobarPendientes(page, estado, creacion.idempotencyKey);
            expect(local.cola.find((op) => op.id === creacion.id).intentos).toBe(intento);
            expect(local.cola.map((op) => op.id)).toEqual(estado.pendiente.cola.map((op) => op.id));
            expect(consulta(`SELECT count(*) FROM inspeccion WHERE id='${estado.id}'`)).toBe('0');
            snapshots.push({ etapa: `antes del intento ${intento}`, local });
            await liberar(request, `antes-${intento}`);
          }
        } else {
          await esperarBarrera(request, 'respuesta-perdida');
          expect(consulta(`SELECT count(*) FROM inspeccion WHERE id='${estado.id}'`)).toBe('1');
          snapshots.push({
            etapa: 'commit real sin confirmación local',
            local: await comprobarPendientes(page, estado, creacion.idempotencyKey),
          });
          await liberar(request, 'respuesta-perdida');
          await esperarBarrera(request, 'reintento-respuesta');
          const local = await comprobarPendientes(page, estado, creacion.idempotencyKey);
          expect(local.cola.find((op) => op.id === creacion.id).intentos).toBe(2);
          expect(consulta(`SELECT count(*) FROM inspeccion WHERE id='${estado.id}'`)).toBe('1');
          snapshots.push({ etapa: 'reintento con identidad conservada', local });
          await liberar(request, 'reintento-respuesta');
        }
        const confirmacion = await final;
        const red = await estadoRed(request);
        expect(red.barreras).toEqual([]);
        expect(red.eventos.filter((e) => e.tipo === 'error-proxy')).toEqual([]);
        const recibidas = red.eventos.filter(
          (e) => e.tipo === 'recibida' && e.clave === creacion.idempotencyKey,
        );
        const intentosEsperados = perfil === 'antes' ? 3 : perfil === 'respuesta' ? 2 : 1;
        expect(recibidas).toHaveLength(intentosEsperados);
        expect(new Set(recibidas.map((e) => e.huella)).size).toBe(1);
        expect(recibidas[0].huella).toMatch(/^[0-9a-f]{64}$/);
        expect(fallosNavegador).toHaveLength(intentosEsperados - 1);
        for (const fallo of fallosNavegador) {
          expect(fallo.clave).toBe(creacion.idempotencyKey);
          expect(fallo.error).toBeTruthy();
        }
        const confirmadas = red.eventos.filter(
          (e) => e.tipo === 'backend-confirmado' && e.clave === creacion.idempotencyKey,
        );
        if (perfil === 'respuesta') {
          expect(confirmadas.map((e) => e.replay)).toEqual([false, true]);
          expect(confirmadas.map((e) => e.status)).toEqual([201, 201]);
          expect(confirmadas[0].respuestaSha256).toBe(confirmadas[1].respuestaSha256);
        } else expect(confirmadas).toHaveLength(1);
        // Restituir transporte transparente antes del replay manual del oráculo de fase 1.
        await configurar(request, 'normal');
        const resultado = await verificarSincronizacion(
          page,
          context,
          request,
          testInfo,
          estado,
          confirmacion,
          token,
        );
        expect(resultado.enviadas).toHaveLength(
          estado.pendiente.cola.length + intentosEsperados - 1,
        );
        for (const op of estado.pendiente.cola) {
          expect(resultado.enviadas.filter((e) => e.clave === op.idempotencyKey)).toHaveLength(
            op.id === creacion.id ? intentosEsperados : 1,
          );
        }
        await testInfo.attach('red-y-recuperacion.json', {
          body: JSON.stringify(
            { id: estado.id, perfil, red, fallosNavegador, snapshots, resultado },
            null,
            2,
          ),
          contentType: 'application/json',
        });
      } finally {
        await testInfo.attach('red-al-final.json', {
          body: JSON.stringify(await estadoRed(request), null, 2),
          contentType: 'application/json',
        });
        await configurar(request, 'normal');
        expect((await estadoRed(request)).barreras).toEqual([]);
      }
    });
  }
});
