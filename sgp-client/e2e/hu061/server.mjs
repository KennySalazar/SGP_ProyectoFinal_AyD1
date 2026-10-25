import { createServer, request as proxyRequest } from 'node:http';
import { readFile } from 'node:fs/promises';
import { resolve, extname, sep } from 'node:path';
import { gzipSync } from 'node:zlib';
import { createHash } from 'node:crypto';
import { setTimeout as esperar } from 'node:timers/promises';
import { fixture, user, schema, photos } from './fixture.mjs';
const root = resolve('dist/sgp-client/browser');
// Modo de aceptación contra Spring Boot aislado: ninguna API se simula.
const apiReal = process.env.HU061_API_ORIGIN;
const escriturasReales = [];
const fase2 = process.env.HU062_FASE2 === 'true';
let red = { perfil: 'normal' };
const barreras = new Map();
const cuerpo = async (stream) => {
  const partes = [];
  for await (const parte of stream) partes.push(parte);
  return Buffer.concat(partes);
};
const hash = (bytes) => createHash('sha256').update(bytes).digest('hex');
function registrar(perfil, tipo, operacion, extra = {}) {
  perfil.eventos.push({ tipo, ms: performance.now() - perfil.inicio, ...operacion, ...extra });
}
// Un cierre antes de cabeceras puede ser reintentado transparentemente por HTTP.
// Un cuerpo JSON incompleto fuerza un error de transporte observable por Angular.
function interrumpirRespuesta(res, status, headers, bytes) {
  const cabeceras = { ...headers, 'content-length': String(bytes.length), connection: 'close' };
  delete cabeceras['transfer-encoding'];
  res.writeHead(status, cabeceras);
  res.write(bytes.subarray(0, 1), () => res.destroy());
}
async function barrera(perfil, nombre, operacion) {
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      barreras.delete(nombre);
      reject(new Error(`Barrera sin liberar: ${nombre}`));
    }, 15000);
    barreras.set(nombre, {
      liberar: () => {
        clearTimeout(timer);
        barreras.delete(nombre);
        resolve();
      },
      cancelar: () => {
        clearTimeout(timer);
        barreras.delete(nombre);
        reject(new Error('Perfil cancelado'));
      },
    });
    registrar(perfil, 'retenida', operacion, { barrera: nombre });
  });
}

// Solo tráfico mutante de un UUID preparado offline; nunca auth, recursos PWA ni lecturas.
async function reenviarDegradada(req, res, url, perfil) {
  const clave = req.headers['idempotency-key'];
  const intento = (perfil.intentos.get(clave) ?? 0) + 1;
  perfil.intentos.set(clave, intento);
  const json = req.headers['content-type']?.startsWith('application/json');
  const bytes = json ? await cuerpo(req) : undefined;
  const op = {
    metodo: req.method,
    ruta: url.pathname,
    clave,
    intento,
    huella: bytes && hash(bytes),
  };
  const creacion = url.pathname === '/api/v1/inspecciones';
  const foto = url.pathname.endsWith('/fotografias');
  if (foto && !perfil.primeraFoto) perfil.primeraFoto = clave;
  registrar(perfil, 'recibida', op);
  if (perfil.perfil === 'antes' && creacion) {
    await barrera(perfil, `antes-${intento}`, op);
    if (intento <= 2) {
      registrar(perfil, 'fallo-antes', op);
      interrumpirRespuesta(res, 200, { 'content-type': 'application/json' }, Buffer.from('{}'));
      return;
    }
  }
  if (perfil.perfil === 'respuesta' && creacion && intento === 2)
    await barrera(perfil, 'reintento-respuesta', op);
  if (perfil.perfil === 'latencia') {
    await esperar(1500);
    if (foto && clave === perfil.primeraFoto) await barrera(perfil, 'peticion-foto', op);
  }
  if (perfil !== red || res.destroyed) throw new Error('Operación de prueba cancelada');
  registrar(perfil, 'reenviada', op);
  const respuesta = await new Promise((resolve, reject) => {
    const proxy = proxyRequest(
      new URL(req.url, apiReal),
      { method: req.method, headers: req.headers },
      resolve,
    );
    proxy.on('error', reject);
    if (bytes) proxy.end(bytes);
    else req.pipe(proxy);
  });
  const contenido = await cuerpo(respuesta);
  const resultado = {
    status: respuesta.statusCode,
    replay: respuesta.headers['idempotent-replay'] === 'true',
    respuestaSha256: hash(contenido),
  };
  registrar(perfil, 'backend-confirmado', op, resultado);
  if (perfil.perfil === 'respuesta' && creacion && intento === 1) {
    if (respuesta.statusCode !== 201)
      throw new Error(`Creación real rechazada: ${respuesta.statusCode}`);
    await barrera(perfil, 'respuesta-perdida', op);
    registrar(perfil, 'respuesta-perdida', op, resultado);
    interrumpirRespuesta(res, respuesta.statusCode, respuesta.headers, contenido);
    return;
  }
  if (perfil.perfil === 'latencia') {
    await esperar(1500);
    if (foto && clave === perfil.primeraFoto) await barrera(perfil, 'respuesta-foto', op);
  }
  if (perfil !== red || res.destroyed) throw new Error('Operación de prueba cancelada');
  res.writeHead(respuesta.statusCode, respuesta.headers);
  res.end(contenido);
  registrar(perfil, 'entregada', op, resultado);
}
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
  if (apiReal && fase2 && url.pathname === '/__e2e/red') {
    if (req.method === 'POST') {
      const config = JSON.parse((await cuerpo(req)).toString());
      if (
        !['normal', 'latencia', 'antes', 'respuesta'].includes(config.perfil) ||
        (config.perfil !== 'normal' && !/^[0-9a-f-]{36}$/.test(config.id ?? ''))
      ) {
        json({ error: 'Perfil o UUID inválido' }, 400);
        return;
      }
      for (const espera of barreras.values()) espera.cancelar();
      red = {
        ...config,
        peticionMs: config.perfil === 'latencia' ? 1500 : 0,
        respuestaMs: config.perfil === 'latencia' ? 1500 : 0,
        inicio: performance.now(),
        eventos: [],
        intentos: new Map(),
      };
    }
    json({
      perfil: red.perfil,
      peticionMs: red.peticionMs,
      respuestaMs: red.respuestaMs,
      eventos: red.eventos ?? [],
      barreras: [...barreras.keys()],
    });
    return;
  }
  if (apiReal && fase2 && url.pathname === '/__e2e/red/liberar' && req.method === 'POST') {
    const { nombre } = JSON.parse((await cuerpo(req)).toString());
    const espera = barreras.get(nombre);
    if (!espera) {
      json({ error: 'No existe esa barrera' }, 409);
      return;
    }
    espera.liberar();
    json({ ok: true });
    return;
  }
  if (apiReal && url.pathname.startsWith('/api/')) {
    if (
      ['POST', 'PUT', 'PATCH', 'DELETE'].includes(req.method) &&
      url.pathname.startsWith('/api/v1/inspecciones')
    ) {
      escriturasReales.push({
        metodo: req.method,
        ruta: url.pathname,
        clave: req.headers['idempotency-key'],
      });
    }
    if (
      fase2 &&
      red.perfil !== 'normal' &&
      ['POST', 'PUT'].includes(req.method) &&
      (url.pathname.startsWith(`/api/v1/inspecciones/${red.id}/`) ||
        (url.pathname === '/api/v1/inspecciones' && req.headers['idempotency-key'] === red.id))
    ) {
      const perfil = red;
      try {
        await reenviarDegradada(req, res, url, perfil);
      } catch (error) {
        registrar(perfil, 'error-proxy', {}, { mensaje: error.message });
        res.destroy();
      }
      return;
    }
    const proxy = proxyRequest(
      new URL(req.url, apiReal),
      {
        method: req.method,
        headers: req.headers,
      },
      (respuesta) => {
        res.writeHead(respuesta.statusCode, respuesta.headers);
        respuesta.pipe(res);
      },
    );
    proxy.on('error', () => json({ detail: 'Backend de prueba no disponible' }, 502));
    req.pipe(proxy);
    return;
  }
  if (apiReal && url.pathname === '/__e2e/real-writes') {
    json(escriturasReales);
    return;
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
