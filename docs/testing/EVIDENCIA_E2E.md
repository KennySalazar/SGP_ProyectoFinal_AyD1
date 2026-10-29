# Evidencia E2E con red simulada (DT-CAL-04)

> DT-CAL-04: «El módulo de sincronización offline requiere pruebas de extremo a extremo con red simulada (Playwright o Cypress).»

El proyecto usa **Playwright** como único ejecutor E2E; la decisión está en [ADR-0008](../adr/0008-hu062-pruebas-e2e-red-degradada.md). La evidencia está en dos niveles:

| Nivel | Qué cubre | Dónde corre | Dónde se ve |
|---|---|---|---|
| **1. Suite PWA con red simulada** | Precarga, operación sin red, reapertura offline, sincronización al recuperar sesión, instalación PWA sin red y carga en 3G simulado | **CI (GitHub Actions)**, en cada PR y push a `develop`/`main` | Artefacto `playwright-report` del job «E2E offline con red simulada» |
| **2. Red degradada contra backend real** | Latencia, fallos de transporte, respuesta perdida, corte de subida fotográfica y pérdida real de paquetes (`tc/netem`) | **Manual**, con `run-hu062-fase1.sh` (necesita Docker y `NET_ADMIN`) | Carpeta de evidencias que imprime el script y el resumen de esta página |

## 1. Suite PWA en CI

### Cómo ver la evidencia

1. En GitHub, abre **Actions** y elige la ejecución del workflow **CI** de un PR o de `develop`.
2. Abre el job **E2E offline con red simulada** para ver el resultado de cada prueba en el log.
3. Al final de la ejecución, en **Artifacts**, descarga **`playwright-report`**. Se guarda 30 días.
4. Descomprímelo y abre `playwright-report/index.html` en el navegador. Muestra cada prueba con:
   - sus pasos y duración;
   - una captura de pantalla.

   Si una prueba falla, `test-results/` incluye también su traza.

El job sube el reporte aunque alguna prueba falle, para poder revisar el error.

### Pruebas incluidas

Archivos `sgp-client/e2e/hu061/hu061.spec.mjs` y `hu057-060.spec.mjs`. Sirven el build de producción con su Service Worker y una API simulada (`e2e/hu061/server.mjs`), que puede cortar la sesión o la red:

| Prueba | Qué demuestra |
|---|---|
| Precarga, reabre offline y sincroniza tras recuperar sesión (Catedrático) | Borrador conservado en IndexedDB, reapertura sin red, publicación diferida con `Idempotency-Key` y sincronización al volver la sesión |
| Lo mismo con rol Estudiante | Envío a revisión diferido con el mismo mecanismo |
| Revalida referencias con el worker y permite posponer una nueva versión | Catálogos con *stale-while-revalidate* y aviso de actualización |
| Instala la PWA y la lanza sin red desde un perfil persistente | Instalación y arranque completamente offline |
| Carga inicial fría en 3G simulado (< 5 s) | Rendimiento con red lenta emulada por CDP |
| HU-057/HU-060: captura con worker, reapertura offline, diagnóstico y eliminación segura | Fotos persistidas localmente, diagnóstico de sincronización y borrado seguro |

`hu061-real.spec.mjs` se omite automáticamente si no hay backend real (`HU061_API_ORIGIN`).

### Ejecución local

Necesita Node 24, igual que en CI. Desde `sgp-client`:

```bash
npx playwright install chromium
npm run build
npm run test:pwa
npx playwright show-report
```

Con Node 20, `hu061-real.spec.mjs` no carga, porque importa un archivo `.ts` y Node 20 no lo interpreta. En ese caso pueden correrse solo los dos archivos simulados:

```bash
npx playwright test hu061.spec.mjs hu057-060.spec.mjs
```

### Resultado registrado

| Fecha | Entorno | Resultado |
|---|---|---|
| 28/10/2026 | Local: Node 20.19, Playwright 1.64.1, Chromium 1248 | **6/6 aprobadas** en 25 s; reporte HTML y 10 capturas |

Antes de esta ejecución se actualizó el simulador. El borrador ahora consulta `GET /api/v1/puentes/{id}` para completar ruta, departamento y municipio, y el simulador no la respondía. Por eso fallaban 3 de las 6 pruebas.

## 2. Red degradada contra backend real (HU-062)

El procedimiento completo, con requisitos, perfiles y oráculos, está en [HU-062.md](HU-062.md). Cada fase levanta un PostGIS desechable en tmpfs y Spring Boot real, y al terminar limpia lo que creó. Desde `sgp-client`, con Node 24, Java 21, Maven y Docker:

```bash
bash e2e/hu061/run-hu062-fase1.sh fase1      # offline y red normal
bash e2e/hu061/run-hu062-fase1.sh fase2      # latencia y fallos de transporte
bash e2e/hu061/run-hu062-fase1.sh fase3      # cortes de subida fotográfica
bash e2e/hu061/run-hu062-fase1.sh paquetes   # pérdida real de paquetes con tc/netem
```

El modo `paquetes` necesita además la imagen local `sgp_proyectofinal_ayd1-backend:latest`. La crea `docker compose build backend`.

### Resultados registrados (24/10/2026, America/Guatemala)

| Fase | Perfil | Resultado |
|---|---|---|
| Fase 1 | Creación offline, recarga con Service Worker, sincronización normal y *replay* | **2/2** nuevas, más **2/2** regresiones reales HU-061 y regresiones PWA aprobadas |
| Fase 2 | Latencia de 1500 ms + 1500 ms, dos fallos de transporte antes del backend y confirmación perdida tras el commit | **6/6** (3 casos × 2 corridas) y **7/7** regresiones |
| Fase 3 | Corte de subida al 50 % y confirmación fotográfica perdida, con un fallo controlado para validar los artefactos | **12/12** funcionales (2/2 fase 3, 3/3 fase 2, 2/2 fase 1, 2/2 HU-061 real y 3/3 PWA) |
| Paquetes | `netem loss 100%` en la red del backend desechable; 36 y 33 paquetes descartados por corrida | **2/2**, más **1/1** regresión del proxy |

En todos los casos se conservan las respuestas, las 4 fotografías (Blob y SHA-256) y las claves de idempotencia durante la falla. Al recuperar la red, la inspección queda PUBLICADA una sola vez, sin duplicados, con 4 fotos disponibles y la cola vacía.

Los informes detallados de cada fase se versionaron y luego se retiraron en el commit `377ea67`. Se pueden consultar con:

```bash
git show 377ea67^:temporalHu062/FASE_1_HU062_RESULTADOS.md
git show 377ea67^:temporalHu062/FASE_2_HU062_RESULTADOS.md
git show 377ea67^:temporalHu062/FASE_3_HU062_RESULTADOS.md
git show 377ea67^:temporalHu062/QA_PENDIENTES_HU062_RESULTADOS.md
```

## Límites

- **Solo Chromium y almacenamiento local.** No se certifica S3/staging ni la autonomía offline de 8 horas, que sigue siendo una medición manual.
- **El nivel 2 no corre en CI.** Necesita Docker con `NET_ADMIN` y un backend completo (ver ADR-0008).
- **Las trazas de Playwright pueden contener sesiones.** Son privadas: no se publican ni se versionan, y `playwright-report/` y `test-results/` están en `.gitignore`. La suite de CI usa solo credenciales y tokens ficticios del simulador.
