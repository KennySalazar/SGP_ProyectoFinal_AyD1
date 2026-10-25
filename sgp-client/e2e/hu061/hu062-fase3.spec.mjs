import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { readdir } from 'node:fs/promises';
import { consulta, locales, datos, autorId } from './real-helpers.mjs';
import {
  validarEntorno,
  prepararOffline,
  verificarSincronizacion,
  directorio,
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

const remotoFoto = (id, fotoId, clave) =>
  JSON.parse(
    consulta(`SELECT json_build_object(
  'vinculos',(SELECT count(*) FROM archivo_inspeccion WHERE inspeccion_id='${id}' AND archivo_id='${fotoId}'),
  'disponibles',(SELECT count(*) FROM archivo WHERE id='${fotoId}' AND estado='DISPONIBLE'),
  'operaciones',(SELECT count(*) FROM operacion_idempotente WHERE usuario_id='${autorId}' AND clave='${clave}' AND entidad_id='${fotoId}' AND estado='COMPLETADA' AND codigo_respuesta=201))`),
  );

async function conservar(page, estado, evento) {
  const local = await locales(
    page,
    estado.id,
    estado.pendiente.fotos.map((f) => f.id),
  );
  const original = estado.pendiente.fotos.find((f) => f.id === evento.fotoId);
  expect(original).toBeDefined();
  expect(local.fotos).toEqual(estado.pendiente.fotos);
  expect(local.inspeccion.payload.borrador.datos.vigas).toEqual(datos.vigas);
  expect(local.inspeccion.intencionFinal.estado).toBe('PENDIENTE');
  const operacion = local.cola.find((op) => op.entityId === evento.fotoId);
  expect(operacion.idempotencyKey).toBe(original.idempotencyKey);
  expect(evento.clave).toBe(original.idempotencyKey);
  expect(evento.fotoSha256).toBe(original.sha256);
  expect(evento.fotoBytes).toBe(original.tamano);
  expect(evento.metadatos).toEqual(original.metadatos);
  return local;
}

test.describe('HU-062 fase 3 / Spring Boot real', () => {
  test.setTimeout(120000);
  test.beforeAll(validarEntorno);
  for (const [perfil, titulo] of [
    ['corte-foto', 'corte al 50 % binario y recuperación tras reconectar'],
    ['confirmacion-foto', 'confirmación fotográfica perdida después del 201 real'],
  ]) {
    test(titulo, async ({ page, context, request }, testInfo) => {
      let token;
      let estado;
      let objetivo;
      let resultado;
      const snapshots = [];
      const fallos = [];
      const pruebasTcp = [];
      context.on('response', async (res) => {
        if (/\/api\/v1\/auth\/(login|refresh)$/.test(res.url()) && res.status() === 200)
          token = (await res.json().catch(() => null))?.accessToken ?? token;
      });
      page.on('requestfailed', (req) => {
        if (req.url().endsWith('/fotografias') && req.method() === 'POST')
          fallos.push({ clave: req.headers()['idempotency-key'], error: req.failure()?.errorText });
      });
      estado = await prepararOffline(page, context, request);
      await configurar(request, perfil, estado.id);
      try {
        const final = page.waitForResponse(
          (r) =>
            r.url().endsWith(`/inspecciones/${estado.id}/publicar`) &&
            r.request().method() === 'POST',
          { timeout: 90000 },
        );
        final.catch(() => {});
        await context.setOffline(false);
        await page.evaluate(() => window.dispatchEvent(new Event('online')));
        const barreraInicial = perfil === 'corte-foto' ? 'foto-parcial' : 'confirmacion-foto';
        await esperarBarrera(request, barreraInicial);
        objetivo = (await estadoRed(request)).eventos.find((e) => e.barrera === barreraInicial);
        snapshots.push({
          etapa: barreraInicial,
          local: await conservar(page, estado, objetivo),
          remoto: remotoFoto(estado.id, objetivo.fotoId, objetivo.clave),
        });
        if (perfil === 'corte-foto') {
          expect(objetivo.binariosEnviados).toBe(Math.floor(objetivo.fotoBytes / 2));
          expect(objetivo.binariosEnviados).toBeGreaterThan(0);
          expect(objetivo.multipartEnviado).toBeLessThan(objetivo.multipartTotal);
          expect(objetivo.puertoBackend).toBe(18090);
          // Lectura TCP_INFO del peer, sin capacidades ni cambios de red.
          await expect
            .poll(() => {
              const ss = execFileSync(
                'ss',
                ['-tinH', `sport = :18090 and dport = :${objetivo.puertoProxy}`],
                { encoding: 'utf8' },
              );
              const recibidos = Number(/bytes_received:(\d+)/.exec(ss)?.[1] ?? 0);
              pruebasTcp.push({ ss, recibidos, wireEnviado: objetivo.wireEnviado });
              return recibidos;
            })
            .toBeGreaterThanOrEqual(objetivo.wireEnviado);
          expect(remotoFoto(estado.id, objetivo.fotoId, objetivo.clave)).toEqual({
            vinculos: 0,
            disponibles: 0,
            operaciones: 0,
          });
          const contenido = await context.request.get(
            `/api/v1/inspecciones/${estado.id}/fotografias/${objetivo.fotoId}/contenido`,
            { headers: { Authorization: `Bearer ${token}` } },
          );
          expect(contenido.status()).toBe(404);
          expect((await readdir(directorio)).sort()).toEqual([...estado.archivosAntes].sort());
          await liberar(request, 'foto-parcial');
          await esperarBarrera(request, 'red-cortada');
          await context.setOffline(true);
          await page.evaluate(() => window.dispatchEvent(new Event('offline')));
          await liberar(request, 'red-cortada');
          await expect
            .poll(
              async () =>
                (await locales(page, estado.id)).cola.find((op) => op.entityId === objetivo.fotoId)
                  ?.estado,
            )
            .toBe('PENDIENTE');
          snapshots.push({
            etapa: 'corte real y navegador offline',
            local: await conservar(page, estado, objetivo),
            remoto: remotoFoto(estado.id, objetivo.fotoId, objetivo.clave),
          });
          await context.setOffline(false);
          await page.evaluate(() => window.dispatchEvent(new Event('online')));
        } else {
          expect(remotoFoto(estado.id, objetivo.fotoId, objetivo.clave)).toEqual({
            vinculos: 1,
            disponibles: 1,
            operaciones: 1,
          });
          await liberar(request, 'confirmacion-foto');
        }
        await esperarBarrera(request, 'reintento-foto');
        const reintento = (await estadoRed(request)).eventos.find(
          (e) => e.barrera === 'reintento-foto',
        );
        const local = await conservar(page, estado, reintento);
        expect(local.cola.find((op) => op.entityId === objetivo.fotoId).intentos).toBe(2);
        expect(reintento.fotoId).toBe(objetivo.fotoId);
        expect(reintento.metadatosSha256).toBe(objetivo.metadatosSha256);
        expect(reintento.fotoSha256).toBe(objetivo.fotoSha256);
        const esperado = perfil === 'corte-foto' ? 0 : 1;
        expect(remotoFoto(estado.id, objetivo.fotoId, objetivo.clave)).toEqual({
          vinculos: esperado,
          disponibles: esperado,
          operaciones: esperado,
        });
        snapshots.push({
          etapa: 'reintento antes de reenviar',
          local,
          remoto: remotoFoto(estado.id, objetivo.fotoId, objetivo.clave),
        });
        await liberar(request, 'reintento-foto');
        const confirmacion = await final;
        const red = await estadoRed(request);
        expect(red.barreras).toEqual([]);
        expect(red.eventos.filter((e) => e.tipo === 'error-proxy')).toEqual([]);
        const respuestas = red.eventos.filter(
          (e) => e.tipo === 'backend-confirmado' && e.clave === objetivo.clave,
        );
        expect(respuestas.map((e) => e.status)).toEqual(
          perfil === 'corte-foto' ? [201] : [201, 201],
        );
        if (perfil === 'confirmacion-foto')
          expect(respuestas[0].respuestaSha256).toBe(respuestas[1].respuestaSha256);
        expect(
          red.eventos.filter((e) => e.tipo === 'recibida' && e.clave === objetivo.clave),
        ).toHaveLength(2);
        await configurar(request, 'normal');
        resultado = await verificarSincronizacion(
          page,
          context,
          request,
          testInfo,
          estado,
          confirmacion,
          token,
        );
        expect(resultado.enviadas).toHaveLength(estado.pendiente.cola.length + 1);
        expect(remotoFoto(estado.id, objetivo.fotoId, objetivo.clave)).toEqual({
          vinculos: 1,
          disponibles: 1,
          operaciones: 1,
        });
        expect(fallos.length).toBeGreaterThan(0);
        const temporales = await readdir(process.env.HU062_TEMP, { recursive: true });
        expect(
          temporales.filter((p) => /(?:upload.*\.tmp|sgp-fotografia-.*\.tmp)$/.test(p)),
        ).toEqual([]);
        await testInfo.attach('fotografia-y-recuperacion.json', {
          body: JSON.stringify(
            {
              id: estado.id,
              perfil,
              objetivo,
              red,
              snapshots,
              pruebasTcp,
              fallos,
              resultado,
              remotoFinal: remotoFoto(estado.id, objetivo.fotoId, objetivo.clave),
              temporalesFotograficos: [],
            },
            null,
            2,
          ),
          contentType: 'application/json',
        });
        if (process.env.HU062_FALLO_CONTROLADO === 'true' && perfil === 'confirmacion-foto')
          expect(false, 'HU062_FALLO_CONTROLADO: comprobar evidencias después de sincronizar').toBe(
            true,
          );
      } finally {
        // Se adjunta incluso si falla una aserción, antes de desmontar el contexto.
        await testInfo.attach('diagnostico-fotografico.json', {
          body: JSON.stringify(
            {
              id: estado.id,
              perfil,
              objetivo,
              snapshots,
              pruebasTcp,
              fallos,
              red: await estadoRed(request),
              local: await locales(
                page,
                estado.id,
                estado.pendiente.fotos.map((f) => f.id),
              ),
              remoto: objetivo && remotoFoto(estado.id, objetivo.fotoId, objetivo.clave),
              resultado,
            },
            null,
            2,
          ),
          contentType: 'application/json',
        });
        await configurar(request, 'normal');
        expect((await estadoRed(request)).barreras).toEqual([]);
      }
    });
  }
});
