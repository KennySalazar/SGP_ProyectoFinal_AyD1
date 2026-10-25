import { expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFile, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import {
  origen,
  pg,
  autorId,
  puenteId,
  versionId,
  datos,
  consulta,
  locales,
  login,
  esperarPrecarga,
} from './real-helpers.mjs';

export const categorias = ['ACCESO', 'SUPERESTRUCTURA', 'SUBESTRUCTURA', 'CAUCE'];
const foto = fileURLToPath(new URL('./foto-exif.jpg', import.meta.url));
export const directorio = process.env.HU062_ARCHIVOS;
const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');
export const contar = () =>
  Number(
    consulta(
      `SELECT count(*) FROM inspeccion WHERE autor_id='${autorId}' AND puente_id='${puenteId}'`,
    ),
  );
export const escrituras = async (request) => {
  const res = await request.get('/__e2e/real-writes');
  expect(res.ok()).toBe(true);
  return res.json();
};

export function validarEntorno() {
  // Sin skip: una ejecución sin backend aislado nunca acredita estos escenarios.
  expect(origen, 'Ejecutar bash e2e/hu061/run-hu062-fase1.sh').toBe('http://127.0.0.1:18090');
  expect(directorio).toBeTruthy();
  expect(
    execFileSync(
      'docker',
      ['inspect', '--format', '{{index .Config.Labels "hu062.aislado"}}', pg],
      { encoding: 'utf8' },
    ).trim(),
  ).toBe('true');
  expect(consulta('SELECT current_database()')).toBe('hu061_aceptacion');
}

export async function prepararOffline(page, context, request) {
  await login(page, 'catedratico-hu061@example.test');
  await page.goto(`/puentes/${puenteId}`);
  await page.locator('.btn-iniciar-inspeccion').click();
  const dialogo = page.getByRole('dialog');
  await expect(dialogo.locator('.gps-estado.obtenido')).toBeVisible();
  await expect
    .poll(() =>
      page.evaluate(async (versionId) => {
        const db = await new Promise((resolve, reject) => {
          const req = indexedDB.open('sgp_offline');
          req.onsuccess = () => resolve(req.result);
          req.onerror = () => reject(req.error);
        });
        try {
          return await new Promise((resolve, reject) => {
            const req = db
              .transaction('esquemas_formulario')
              .objectStore('esquemas_formulario')
              .getAll();
            req.onsuccess = () =>
              resolve(req.result.some((s) => (s.origenId ?? s.id) === versionId));
            req.onerror = () => reject(req.error);
          });
        } finally {
          db.close();
        }
      }, versionId),
    )
    .toBe(true);
  await esperarPrecarga(page, request);
  const antes = await escrituras(request);
  const remotasAntes = contar();
  await context.setOffline(true);
  await expect.poll(() => page.evaluate(() => navigator.onLine)).toBe(false);
  await page.evaluate(() => window.dispatchEvent(new Event('offline')));
  await dialogo.locator('button[type=submit]').click();
  await dialogo.getByRole('button', { name: 'Ir al formulario' }).click();
  await expect(page).toHaveURL(/\/inspecciones\/[0-9a-f-]{36}$/);
  const id = new URL(page.url()).pathname.split('/').at(-1);
  expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  for (const [campo, valor] of Object.entries(datos.vigas)) {
    await page.locator(`[data-ruta="vigas/${campo}"] [data-valor="${valor}"]`).click();
  }
  for (let i = 0; i < categorias.length; i++) {
    const reporte = page.locator('app-reporte-fotografico');
    await reporte.locator('input[type=file]').setInputFiles(foto);
    await reporte.locator('#categoria-foto').click();
    await page.getByRole('option', { name: categorias[i], exact: true }).click();
    await reporte.getByRole('button', { name: 'Adjuntar fotografía', exact: true }).click();
    await expect.poll(async () => (await locales(page, id)).fotos.length).toBe(i + 1);
  }
  await page.locator('.btn-accion-final').click();
  await page
    .getByRole('dialog')
    .getByRole('button', { name: /^Publicar inspección$/ })
    .click();
  await expect(page.getByText(/Envío o publicación pendiente de confirmación/)).toBeVisible();
  const pendiente = await locales(page, id);
  expect(pendiente.inspeccion.estado).toBe('BORRADOR');
  expect(pendiente.inspeccion.intencionFinal.estado).toBe('PENDIENTE');
  expect(pendiente.inspeccion.payload.borrador.datos.vigas).toEqual(datos.vigas);
  expect(pendiente.fotos).toHaveLength(4);
  expect(pendiente.fotos.map((f) => f.metadatos.categoria).sort()).toEqual([...categorias].sort());
  for (const f of pendiente.fotos) {
    expect(f.propietarioId).toBe(autorId);
    expect(f.tamano).toBeGreaterThan(0);
    expect(f.sha256).toMatch(/^[0-9a-f]{64}$/);
    expect(f.sincronizado).toBe(false);
  }
  expect(pendiente.cola.filter((op) => op.tipo === 'FOTO')).toHaveLength(4);
  expect(pendiente.cola.filter((op) => op.operacion === 'CREAR_INSPECCION')).toHaveLength(1);
  expect(pendiente.cola.filter((op) => op.operacion === 'PUBLICAR_INSPECCION')).toHaveLength(1);
  expect(pendiente.cola.filter((op) => op.operacion === 'GUARDAR_BORRADOR').length).toBeGreaterThan(
    0,
  );
  for (const op of pendiente.cola) {
    expect(op.propietarioId).toBe(autorId);
    expect(op.idempotencyKey).toBeTruthy();
    expect(op.intentos).toBe(0);
  }
  expect(await escrituras(request)).toEqual(antes);
  expect(contar()).toBe(remotasAntes);
  expect(consulta(`SELECT count(*) FROM inspeccion WHERE id='${id}'`)).toBe('0');
  return { id, pendiente, antes, remotasAntes, archivosAntes: await readdir(directorio) };
}

export async function verificarSincronizacion(
  page,
  context,
  request,
  testInfo,
  estado,
  confirmacion,
  token,
) {
  const { id, pendiente, antes, remotasAntes, archivosAntes } = estado;
  const idsFotos = pendiente.fotos.map((f) => f.id);
  const final = pendiente.cola.find((op) => op.operacion === 'PUBLICAR_INSPECCION');
  expect(confirmacion.status(), await confirmacion.text()).toBe(200);
  expect(confirmacion.request().headers()['idempotency-key']).toBe(final.idempotencyKey);
  await expect
    .poll(async () => (await locales(page, id, idsFotos)).inspeccion.intencionFinal.estado)
    .toBe('CONFIRMADA');
  const sincronizada = await locales(page, id, idsFotos);
  expect(sincronizada.inspeccion.estado).toBe('PUBLICADA');
  expect(sincronizada.inspeccion.payload.borrador.datos.vigas).toEqual(datos.vigas);
  expect(sincronizada.fotos).toEqual([]);
  expect(sincronizada.cola).toEqual([]);
  expect(sincronizada.inspeccion.fotosConfirmadas.map((f) => f.id).sort()).toEqual(
    [...idsFotos].sort(),
  );
  expect(contar()).toBe(remotasAntes + 1);
  const remoto = JSON.parse(
    consulta(
      `SELECT json_build_object('estado',estado,'datos',datos) FROM inspeccion WHERE id='${id}'`,
    ),
  );
  expect(remoto.estado).toBe('PUBLICADA');
  expect(remoto.datos.vigas).toEqual(datos.vigas);
  const headers = { Authorization: `Bearer ${token}` };
  const listado = await context.request.get(`/api/v1/inspecciones/${id}/fotografias`, {
    headers,
  });
  expect(listado.status()).toBe(200);
  const fotos = await listado.json();
  expect(fotos.map((f) => f.id).sort()).toEqual([...idsFotos].sort());
  expect(fotos.map((f) => f.categoria).sort()).toEqual([...categorias].sort());
  const almacenadas = JSON.parse(
    consulta(
      `SELECT json_agg(json_build_object('id',a.id,'estado',a.estado,'sha256',a.checksum_sha256,'original',a.clave_objeto_original,'miniatura',a.clave_objeto_miniatura)) FROM archivo a JOIN archivo_inspeccion ai ON ai.archivo_id=a.id WHERE ai.inspeccion_id='${id}'`,
    ),
  );
  expect(almacenadas).toHaveLength(4);
  for (const f of fotos) {
    const fila = almacenadas.find((a) => a.id === f.id);
    expect(fila.estado).toBe('DISPONIBLE');
    for (const tipo of ['contenido', 'miniatura']) {
      const res = await context.request.get(
        `/api/v1/inspecciones/${id}/fotografias/${f.id}/${tipo}`,
        { headers },
      );
      expect(res.status()).toBe(200);
      expect(res.headers()['content-type']).toContain('image/jpeg');
      const bytes = await res.body();
      expect(bytes.length).toBeGreaterThan(0);
      const dimensiones = await page.evaluate(
        async (bytes) => {
          const bitmap = await createImageBitmap(
            new Blob([new Uint8Array(bytes)], { type: 'image/jpeg' }),
          );
          const resultado = { ancho: bitmap.width, alto: bitmap.height };
          bitmap.close();
          return resultado;
        },
        [...bytes],
      );
      expect(dimensiones.ancho).toBeGreaterThan(0);
      expect(dimensiones.alto).toBeGreaterThan(0);
      const objeto = await readFile(
        join(directorio, tipo === 'contenido' ? fila.original : fila.miniatura),
      );
      expect(bytes.equals(objeto)).toBe(true);
      if (tipo === 'contenido') {
        expect(bytes.length).toBe(f.tamanoBytes);
        expect(sha256(bytes)).toBe(fila.sha256);
      }
    }
  }
  expect((await readdir(directorio)).sort()).toEqual(
    [...archivosAntes, ...almacenadas.flatMap((f) => [f.original, f.miniatura])].sort(),
  );
  const enviadas = (await escrituras(request)).slice(antes.length);
  const conteos = () =>
    JSON.parse(
      consulta(
        `SELECT json_build_object('inspecciones',(SELECT count(*) FROM inspeccion WHERE autor_id='${autorId}' AND puente_id='${puenteId}'),'fotos',(SELECT count(*) FROM archivo_inspeccion WHERE inspeccion_id='${id}'),'historial',(SELECT count(*) FROM historial_estado_inspeccion WHERE inspeccion_id='${id}'),'ic',(SELECT count(*) FROM resultado_ic WHERE inspeccion_id='${id}' AND tipo='PUBLICACION'))`,
      ),
    );
  const antesReplay = conteos();
  expect(antesReplay).toEqual({ inspecciones: remotasAntes + 1, fotos: 4, historial: 2, ic: 1 });
  const replay = await context.request.post(`/api/v1/inspecciones/${id}/publicar`, {
    headers: { ...headers, 'Idempotency-Key': final.idempotencyKey },
  });
  expect(replay.status()).toBe(200);
  expect(replay.headers()['idempotent-replay']).toBe('true');
  expect(await replay.text()).toBe(await confirmacion.text());
  expect(conteos()).toEqual(antesReplay);
  await testInfo.attach('sincronizacion-real.json', {
    body: JSON.stringify(
      {
        id,
        pendiente,
        sincronizada,
        remoto,
        fotos,
        almacenadas,
        enviadas,
        antesReplay,
        despuesReplay: conteos(),
      },
      null,
      2,
    ),
    contentType: 'application/json',
  });
  return { enviadas, sincronizada, conteos: antesReplay };
}

export async function estadoRed(request) {
  const res = await request.get('/__e2e/red');
  expect(res.status()).toBe(200);
  return res.json();
}
export async function configurar(request, perfil, id) {
  const res = await request.post('/__e2e/red', { data: { perfil, id } });
  expect(res.status()).toBe(200);
}
export async function esperarBarrera(request, nombre) {
  await expect
    .poll(async () => (await estadoRed(request)).barreras.includes(nombre), { timeout: 60000 })
    .toBe(true);
}
export async function liberar(request, nombre) {
  const res = await request.post('/__e2e/red/liberar', { data: { nombre } });
  expect(res.status()).toBe(200);
}
