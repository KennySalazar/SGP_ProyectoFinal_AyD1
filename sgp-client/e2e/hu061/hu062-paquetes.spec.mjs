import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { readFile, readdir } from 'node:fs/promises';
import { join } from 'node:path';
import { consulta, locales, datos } from './real-helpers.mjs';
import {
  validarEntorno,
  prepararOffline,
  verificarSincronizacion,
  configurar,
  esperarBarrera,
  liberar,
  estadoRed,
} from './hu062-helpers.mjs';

const backend = process.env.HU062_BACKEND_ID;
const helper = process.env.HU062_HELPER_ID;
const evidencias = process.env.HU062_EVIDENCIAS;
const loader = '/qa-tools/ld-linux-x86-64.so.2';
const tcArgs = ['--library-path', '/qa-tools', '/qa-tools/tc'];
const docker = (...args) => execFileSync('docker', args, { encoding: 'utf8' }).trim();
const inspeccionar = (id) => JSON.parse(docker('inspect', id))[0];
const tc = (...args) => docker('exec', helper, loader, ...tcArgs, ...args);
const qdiscs = () => JSON.parse(tc('-s', '-j', 'qdisc', 'show', 'dev', 'eth0'));

test.use({
  serviceWorkers: 'allow',
  screenshot: 'only-on-failure',
  actionTimeout: 15000,
  geolocation: { latitude: 14.481, longitude: -90.615 },
  permissions: ['geolocation'],
  timezoneId: 'America/Guatemala',
});

test.describe('HU-062 paquetes / Spring Boot real', () => {
  test.setTimeout(150000);
  test.beforeAll(validarEntorno);
  for (const corrida of [1, 2]) {
    test(`pérdida literal y recuperación íntegra ${corrida}`, async ({
      page,
      context,
      request,
    }, testInfo) => {
      const app = inspeccionar(backend);
      const operador = inspeccionar(helper);
      expect(app.Config.Labels['hu062.aislado']).toBe('true');
      expect(operador.Config.Labels['hu062.aislado']).toBe('true');
      expect(app.HostConfig.CapDrop).toContain('ALL');
      expect(app.HostConfig.CapAdd ?? []).toEqual([]);
      expect(operador.HostConfig.CapAdd).toEqual(['NET_ADMIN']);
      expect(operador.HostConfig.NetworkMode).toBe(`container:${backend}`);
      const redes = Object.values(app.NetworkSettings.Networks);
      expect(redes).toHaveLength(1);
      const red = JSON.parse(docker('network', 'inspect', redes[0].NetworkID))[0];
      expect(red.Labels['hu062.aislado']).toBe('true');
      const namespace = docker('exec', helper, 'readlink', '/proc/self/ns/net');
      expect(namespace).toBe(docker('exec', backend, 'readlink', '/proc/self/ns/net'));
      expect(namespace).not.toBe(
        execFileSync('readlink', ['/proc/self/ns/net'], { encoding: 'utf8' }).trim(),
      );
      expect(qdiscs().every((q) => q.kind === 'noqueue')).toBe(true);
      let token;
      context.on('response', async (res) => {
        if (/\/api\/v1\/auth\/(login|refresh)$/.test(res.url()) && res.status() === 200)
          token = (await res.json().catch(() => null))?.accessToken ?? token;
      });
      const estado = await prepararOffline(page, context, request);
      const creacion = estado.pendiente.cola.find((op) => op.operacion === 'CREAR_INSPECCION');
      await configurar(request, 'paquetes', estado.id);
      const final = page.waitForResponse(
        (r) =>
          r.url().endsWith(`/inspecciones/${estado.id}/publicar`) &&
          r.request().method() === 'POST',
        { timeout: 120000 },
      );
      final.catch(() => {});
      let durante, pendientes, filtros, inicial;
      try {
        await context.setOffline(false);
        await page.evaluate(() => window.dispatchEvent(new Event('online')));
        await esperarBarrera(request, 'antes-paquetes');
        // Revalidar identidad tras la preparación y antes de conceder la pérdida.
        expect(inspeccionar(backend).State.Pid).toBe(app.State.Pid);
        expect(docker('exec', helper, 'readlink', '/proc/self/ns/net')).toBe(namespace);
        const prefijo = `paquetes-${corrida}`;
        // Vigilancia fuera de Playwright: restaura aunque una aserción falle o el runner muera.
        // El handle se crea a continuación; la ventana se limita a nueve segundos.
        docker(
          'exec',
          '-d',
          helper,
          '/bin/sh',
          '-c',
          `sleep 9; ${loader} ${tcArgs.join(' ')} -s -j qdisc show dev eth0 > /qa-evidence/${prefijo}-final.json; ` +
            `${loader} ${tcArgs.join(' ')} qdisc del dev eth0 root handle 620:; ` +
            `${loader} ${tcArgs.join(' ')} -s -j qdisc show dev eth0 > /qa-evidence/${prefijo}-restaurada.json`,
        );
        tc(
          'qdisc',
          'add',
          'dev',
          'eth0',
          'root',
          'handle',
          '620:',
          'prio',
          'bands',
          '3',
          'priomap',
          ...Array(16).fill('2'),
        );
        tc(
          'qdisc',
          'add',
          'dev',
          'eth0',
          'parent',
          '620:1',
          'handle',
          '621:',
          'netem',
          'loss',
          '100%',
        );
        tc(
          'filter',
          'add',
          'dev',
          'eth0',
          'parent',
          '620:',
          'protocol',
          'ip',
          'prio',
          '1',
          'u32',
          'match',
          'ip',
          'protocol',
          '6',
          '0xff',
          'match',
          'ip',
          'sport',
          '8090',
          '0xffff',
          'match',
          'ip',
          'dst',
          `${redes[0].Gateway}/32`,
          'flowid',
          '620:1',
        );
        inicial = qdiscs();
        expect(inicial.find((q) => q.handle === '621:').drops).toBe(0);
        filtros = tc('-j', 'filter', 'show', 'dev', 'eth0', 'parent', '620:');
        await liberar(request, 'antes-paquetes');
        await expect
          .poll(
            () => {
              durante = qdiscs();
              return durante.find((q) => q.handle === '621:')?.drops ?? 0;
            },
            { timeout: 6500, intervals: [100, 200, 300] },
          )
          .toBeGreaterThan(0);
        pendientes = await locales(page, estado.id);
        expect(pendientes.inspeccion.intencionFinal.estado).toBe('PENDIENTE');
        expect(pendientes.inspeccion.payload.borrador.datos.vigas).toEqual(datos.vigas);
        expect(pendientes.fotos).toEqual(estado.pendiente.fotos);
        expect(pendientes.cola.map((op) => [op.id, op.entityId, op.idempotencyKey])).toEqual(
          estado.pendiente.cola.map((op) => [op.id, op.entityId, op.idempotencyKey]),
        );
        expect(pendientes.cola.find((op) => op.id === creacion.id)).toBeDefined();
        expect(qdiscs().find((q) => q.handle === '621:').drops).toBeGreaterThan(0);
        await expect
          .poll(
            async () => {
              try {
                return JSON.parse(
                  await readFile(join(evidencias, `${prefijo}-restaurada.json`), 'utf8'),
                );
              } catch {
                return [];
              }
            },
            { timeout: 15000 },
          )
          .toEqual(expect.arrayContaining([expect.objectContaining({ kind: 'noqueue' })]));
        expect(qdiscs().some((q) => ['620:', '621:'].includes(q.handle))).toBe(false);
        const confirmacion = await final;
        const redProxy = await estadoRed(request);
        expect(redProxy.barreras).toEqual([]);
        expect(redProxy.eventos.filter((e) => e.tipo === 'error-proxy')).toEqual([]);
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
        expect(consulta(`SELECT count(*) FROM inspeccion WHERE id='${estado.id}'`)).toBe('1');
        const temporales = await readdir(process.env.HU062_TEMP, { recursive: true });
        expect(
          temporales.filter((p) => /(?:upload.*\.tmp|sgp-fotografia-.*\.tmp)$/.test(p)),
        ).toEqual([]);
        for (const op of estado.pendiente.cola)
          expect(resultado.enviadas.some((e) => e.clave === op.idempotencyKey)).toBe(true);
        await testInfo.attach('perdida-paquetes.json', {
          body: JSON.stringify(
            {
              corrida,
              id: estado.id,
              namespace,
              gateway: redes[0].Gateway,
              perfil: { loss: '100%', limiteSegundos: 9, puertoOrigen: 8090 },
              filtros: JSON.parse(filtros),
              inicial,
              durante,
              pendientes,
              redProxy,
              resultado,
              final: JSON.parse(await readFile(join(evidencias, `${prefijo}-final.json`), 'utf8')),
              restaurada: qdiscs(),
            },
            null,
            2,
          ),
          contentType: 'application/json',
        });
      } finally {
        // También retirar inmediatamente al fallar; el watchdog mantiene su respaldo.
        if (qdiscs().some((q) => q.handle === '620:'))
          tc('qdisc', 'del', 'dev', 'eth0', 'root', 'handle', '620:');
        await configurar(request, 'normal');
      }
    });
  }
});
