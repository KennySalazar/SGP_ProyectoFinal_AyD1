import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { resolve, extname, sep } from 'node:path';
import { fixture, user, schema, photos } from './fixture.mjs';
const root = resolve('dist/sgp-client/browser');
let publicaciones = [];
let estado = 'BORRADOR';
let referencia = 'Guatemala';
let version = 1;
const tipos = {
  '.html': 'text/html',
  '.js': 'application/javascript',
  '.css': 'text/css',
  '.json': 'application/json',
  '.webmanifest': 'application/manifest+json',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2',
};
createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  function json(body, status = 200) {
    res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    res.end(JSON.stringify(body));
  }
  if (url.pathname === '/__e2e/setup') {
    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end('<html><title>Preparación aislada HU-061</title></html>');
    return;
  }
  if (url.pathname === '/__e2e/reset') {
    publicaciones = [];
    estado = 'BORRADOR';
    json({ ok: true });
    return;
  }
  if (url.pathname === '/__e2e/referencia') {
    referencia = url.searchParams.get('nombre');
    json({ ok: true });
    return;
  }
  if (url.pathname === '/__e2e/version') {
    version = Number(url.searchParams.get('numero'));
    json({ ok: true });
    return;
  }
  if (url.pathname === '/__e2e/resultados') {
    json({ publicaciones, estado });
    return;
  }
  if (url.pathname === '/api/v1/auth/refresh') {
    json({ accessToken: 'token-de-prueba-e2e' });
    return;
  }
  if (url.pathname === '/api/v1/auth/me') {
    json(user);
    return;
  }
  if (url.pathname === '/api/v1/catalogos/departamentos') {
    await new Promise((resolve) => setTimeout(resolve, 200));
    json([{ id: 'd', nombre: referencia }]);
    return;
  }
  if (url.pathname === '/api/v1/formularios/activo' || url.pathname.endsWith('/esquema')) {
    json({ id: fixture.versionFormularioId, codigo: 'HU061-PRUEBA', version: 1, esquema: schema });
    return;
  }
  if (url.pathname.startsWith('/api/') && url.pathname.endsWith('/borrador')) {
    if (req.method === 'PUT') {
      for await (const _ of req) {
        /* consumir JSON */
      }
    }
    json({ ...fixture, estado, version: req.method === 'PUT' ? 1 : 0 });
    return;
  }
  if (url.pathname.startsWith('/api/') && url.pathname.endsWith('/publicar')) {
    const clave = req.headers['idempotency-key'];
    if (!clave) {
      json({ detail: 'Falta clave' }, 400);
      return;
    }
    if (!publicaciones.includes(clave)) publicaciones.push(clave);
    estado = 'PUBLICADA';
    json({ ...fixture, estado });
    return;
  }
  if (url.pathname.startsWith('/api/') && url.pathname.endsWith('/fotografias')) {
    json(photos);
    return;
  }
  if (url.pathname.startsWith('/api/') && url.pathname.endsWith('/publicada')) {
    json({ ...fixture, estado });
    return;
  }
  if (url.pathname.startsWith('/api/')) {
    json({ detail: 'Fuera del alcance del simulador HU-061' }, 404);
    return;
  }
  try {
    const path = resolve(root, '.' + decodeURIComponent(url.pathname));
    if (path !== root && !path.startsWith(root + sep)) {
      res.writeHead(403);
      res.end();
      return;
    }
    let bytes, tipo;
    try {
      bytes = await readFile(path);
      tipo = tipos[extname(path)];
    } catch {
      bytes = await readFile(resolve(root, 'index.html'));
      tipo = 'text/html';
    }
    if (url.pathname === '/ngsw.json') {
      const manifest = JSON.parse(bytes.toString());
      manifest.appData = { versionPrueba: version };
      bytes = Buffer.from(JSON.stringify(manifest));
    }
    res.writeHead(200, {
      'Content-Type': tipo ?? 'application/octet-stream',
      'Cache-Control':
        tipo === 'text/html' || url.pathname.includes('ngsw')
          ? 'no-store'
          : 'public,max-age=31536000,immutable',
    });
    res.end(bytes);
  } catch {
    res.writeHead(500);
    res.end();
  }
}).listen(4173, '127.0.0.1');
