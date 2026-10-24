import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { resolve, extname, sep } from 'node:path';
import { gzipSync } from 'node:zlib';
import { fixture, user, schema, photos } from './fixture.mjs';
const root = resolve('dist/sgp-client/browser');
let publicaciones = [];
let estado = 'BORRADOR';
let referencia = 'Guatemala';
let version = 1;
let sesionActiva = true;
let usuario = user;
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
    sesionActiva = true;
    usuario = { ...user, role: url.searchParams.get('rol') ?? user.role };
    json({ ok: true });
    return;
  }
  if (url.pathname === '/__e2e/sesion') {
    sesionActiva = url.searchParams.get('activa') === 'true';
    json({ sesionActiva });
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
    json(
      sesionActiva ? { accessToken: 'token-de-prueba-e2e' } : { code: 'sesion_expirada' },
      sesionActiva ? 200 : 401,
    );
    return;
  }
  if (url.pathname === '/api/v1/auth/login') {
    sesionActiva = true;
    json({ accessToken: 'token-de-prueba-e2e', requiresTwoFactor: false });
    return;
  }
  if (url.pathname === '/api/v1/auth/logout') {
    sesionActiva = false;
    json({});
    return;
  }
  if (url.pathname === '/api/v1/auth/me') {
    json(sesionActiva ? usuario : { code: 'sesion_expirada' }, sesionActiva ? 200 : 401);
    return;
  }
  if (url.pathname === '/api/v1/catalogos/departamentos') {
    await new Promise((resolve) => setTimeout(resolve, 200));
    json([{ id: 'd', nombre: referencia }]);
    return;
  }
  if (url.pathname === '/api/v1/catalogos/departamentos/d/municipios') {
    json([{ id: 'm', nombre: 'Amatitlan' }]);
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
    json({ ...fixture, rolAutor: usuario.role, estado, version: req.method === 'PUT' ? 1 : 0 });
    return;
  }
  if (url.pathname.startsWith('/api/') && /\/(publicar|enviar)$/.test(url.pathname)) {
    if (!sesionActiva) {
      json({ code: 'sesion_expirada' }, 401);
      return;
    }
    const clave = req.headers['idempotency-key'];
    if (!clave) {
      json({ detail: 'Falta clave' }, 400);
      return;
    }
    if (!publicaciones.includes(clave)) publicaciones.push(clave);
    estado = url.pathname.endsWith('/enviar') ? 'ENVIADA' : 'PUBLICADA';
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
    // Misma compresión gzip nivel 6 que nginx.conf para medir transferencia 3G.
    const comprimir =
      /\bgzip\b/.test(req.headers['accept-encoding'] ?? '') &&
      ['text/html', 'application/javascript', 'text/css', 'application/json'].includes(tipo);
    if (comprimir) bytes = gzipSync(bytes, { level: 6 });
    res.writeHead(200, {
      'Content-Type': tipo ?? 'application/octet-stream',
      ...(comprimir ? { 'Content-Encoding': 'gzip', Vary: 'Accept-Encoding' } : {}),
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
