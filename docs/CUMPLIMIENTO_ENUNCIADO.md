## 1. Arquitectura y backend

| Requisito | Estado en esta base | Evidencia / observacion |
|---|---|---|
| Angular SPA + API REST Spring Boot | Base preparada | `sgp-client` y `sgp-api` desacoplados. |
| Capas controller -> service -> repository -> entity | Base preparada | Modulo `usuario` sigue la separacion; los nuevos modulos deben conservarla. |
| Organizacion por dominio | Base preparada | Paquetes `puente`, `inspeccion`, `formulario`, `revision`, `foro`, `mantenimiento`, `archivo`, `usuario`, `sync`, `common`. |
| DTO en frontera de API | Base preparada | Autenticacion usa DTO; no expone entidades JPA. |
| Stateless + JWT | Base preparada | Spring Security sin sesion; access token JWT. |
| Java 21 + Spring Boot >= 3.3 + Maven | Base preparada | `pom.xml` usa Java 21, Spring Boot 3.3.13 y Maven. |
| Dependencias tecnicas obligatorias | Base preparada | Web, JPA, Security, Validation, Actuator, Flyway, PostgreSQL, Spatial, Hypersistence, JJWT, MapStruct, Springdoc, JSON Schema Validator y ShedLock declaradas. |
| MapStruct para entidad <-> DTO | Base preparada | `usuario/mapper/UserMapper` sirve como referencia; los modulos nuevos deben crear su propio `@Mapper`. |
| RFC 7807 | Base preparada | `@RestControllerAdvice`, `ProblemDetail` y handlers de seguridad. |
| Pageable max 100 | Pendiente de los endpoints de dominio | No hay colecciones de dominio aun. |
| Evitar N+1 / LAZY | Base preparada como norma | Relaciones existentes son LAZY; repositorios usan `@EntityGraph` donde se necesita rol. |
| Maquinas de estados | Pendiente | Se implementan al crear inspecciones y mantenimiento. |
| OpenAPI `/api/docs` | Base preparada | Springdoc configurado. |
| Scheduled + ShedLock | Base preparada | Configuracion de ShedLock lista; tareas se agregan con los modulos. |

## 2. Base de datos

| Requisito | Estado en esta base | Evidencia / observacion |
|---|---|---|
| PostgreSQL 16+ | Base preparada | Compose usa PostGIS sobre PostgreSQL 16. |
| `postgis`, `pg_trgm`, `uuid-ossp` | Base preparada | Flyway V1 habilita las tres extensiones. |
| Flyway versionado | Base preparada | `db/migration/V1...`, `V2...` y `V3...`; una prueba Testcontainers valida su aplicacion sobre PostgreSQL/PostGIS real. |
| Usuario app sin DDL + usuario migrador | Base preparada | Variables separadas y script de inicializacion. |
| UUID v7 | Base preparada | Generador UUID v7 para usuario, OTP y refresh token; debe reutilizarse en dominio. |
| Snake case, singular, TIMESTAMPTZ/UTC | Base preparada | Convencion aplicada en migraciones iniciales y Hibernate UTC. |
| Modelo hibrido tipado + JSONB | Pendiente de inspecciones | Se implementa con el modulo de formulario/inspeccion. |
| GEOGRAPHY(Point,4326) + GiST | Pendiente de `puente` | PostGIS ya disponible. |
| GIN para JSONB consultado | Preparado | V1 incluye ejemplo GIN de auditoria; se agregan los indices de inspeccion cuando exista el JSONB. |
| Baja logica | Pendiente de entidades de dominio | Usuario usa `activo`; puentes/ordenes se implementan despues. |
| Auditoria por aspectos | Parcial | Tabla `auditoria` creada; falta el aspecto de auditoria cuando se definan acciones de dominio. |

## 3. Seguridad y autenticacion

| Requisito | Estado en esta base | Evidencia / observacion |
|---|---|---|
| Roles SGP | Base preparada | `ADMINISTRADOR`, `CATEDRATICO`, `PROFESIONAL_EXTERNO`, `ESTUDIANTE`. |
| Autorregistro solo Estudiante pendiente | Base preparada | Registro crea `ESTUDIANTE`, verifica correo y queda sin activar. |
| Password >= 10, letra + numero, BCrypt >= 12 | Base preparada | Validador y `BCryptPasswordEncoder(12)`. |
| Bloqueo 5 intentos / 15 min | Base preparada | Ventana y bloqueo implementados en `UserAccount`. |
| Access JWT 15 min + refresh 7 dias persistido | Base preparada | `ACCESS_EXPIRATION_TIME_JWT=900000`, refresh 604800000 ms y tabla `token_refresco`. Se conserva `EXPIRATION_TIME_JWT=86400000` del .env de referencia como variable heredada, pero el SGP usa la variable especifica de acceso. |
| Refresh en cookie HttpOnly/SameSite Strict | Base preparada | Cookie configurada; `Secure=true` se activa en produccion. |
| Access token solo en memoria | Base preparada | `AuthStore` usa signal; no se usa `localStorage`. |
| `@PreAuthorize` backend | Base preparada | Endpoints privados de cuenta ya lo usan; cada modulo nuevo debe aplicarlo. |
| Row-level authorization | Pendiente | Depende de cursos, asignaciones e inspecciones. |
| CORS explicito | Base preparada | Origenes configurables; no usa `*`. |
| Rate limiting | Parcial | Login, registro, recuperacion y refresh; comentarios se agrega con foro. |
| MIME real / URLs firmadas | Pendiente | Corresponde al modulo de archivos/MinIO. |
| TLS/HSTS | Pendiente de despliegue real | Nginx local es HTTP; produccion debe terminar TLS y forzar HTTPS/HSTS. |
| 2FA | Conservado y adaptado | OTP por correo para activar/desactivar y login. |
| Recuperacion de contrasena | Conservada y adaptada | OTP por correo y revocacion de sesiones. |

## 4. Frontend y offline

| Requisito | Estado en esta base | Evidencia / observacion |
|---|---|---|
| Angular LTS >= 19 + TS strict | Base preparada | Angular 21 y TypeScript strict. |
| Standalone | Base preparada | No se crean NgModules. |
| Signals | Base preparada | Estado de sesion/conectividad con signals. |
| `@if/@for/@switch` moderno | Base preparada | Plantillas iniciales usan `@if`. |
| Estructura por dominio | Base preparada | `features/*`, `core`, `shared`, `offline`. |
| Lazy loading | Base preparada | Rutas usan `loadComponent`. |
| Inicial <= 500 KB | Presupuesto configurado; validar en cada build | Budget de 500 kB en `angular.json`. |
| Formularios dinamicos JSON Schema | Pendiente | Dependencias/backend preparados; motor se construye en `formulario`. |
| ReactiveForms | Base preparada | Autenticacion usa formularios reactivos. |
| Interceptor token/refresh/RFC7807 | Base preparada | Interceptores de auth y problem details. |
| Encolar peticiones offline | Esqueleto, pendiente de dominio | Interceptor offline reservado; no se encolan escrituras genericas todavia para evitar semantica incorrecta. |
| Guardias por rol | Base preparada | `authGuard` y `roleGuard`. |
| Textos centralizados/i18n | Base preparada | Transloco 8.4.0, loader HTTP y `public/i18n/es.json`; las pantallas base consumen claves del catalogo. |
| WCAG AA / controles tactiles | Base inicial | Controles de formulario >=44 px; auditoria WCAG completa queda para pantallas finales. |
| MapLibre + OSM | Dependencia preparada | `maplibre-gl` declarado; pantalla de mapa pendiente. |
| PWA + Angular service worker | Base preparada | Manifest, iconos, `ngsw-config.json` y service worker configurados. |
| Dexie/IndexedDB | Base preparada | DB local con almacenes `puentes`, `inspecciones`, `fotos`, `esquemas_formulario`, `cola_sync`. |
| UUID v7 cliente | Base preparada | Utilidad `uuidV7()`. |
| Cola persistente + backoff + Idempotency-Key | Parcial | Esquema de cola e idempotency key listos; motor de reintento se implementa con inspecciones. |
| `navigator.storage.persist()` | Base preparada | Servicio y vista de diagnostico. |
| Compresion de fotos antes de persistir | Pendiente | Se implementa al crear captura fotografica. |
| Pruebas de red degradada | Pendiente | Se agregan con Playwright/Cypress al implementar sincronizacion. |

## 5. Infraestructura, calidad y entrega

| Requisito | Estado en esta base | Evidencia / observacion |
|---|---|---|
| Docker Compose | Base preparada | Postgres/PostGIS, MinIO, backend y Nginx. |
| Nginx unico punto externo | Base preparada | Solo `nginx` publica puerto en el compose. |
| `.env` fuera de Git + `.env.example` | Base preparada | `.gitignore` raiz y ejemplos incluidos. |
| Contenedores con reinicio | Base preparada | `restart: unless-stopped`. |
| Nginx gzip, cache, 25MB | Base preparada | Configuracion incluida junto con CSP, `nosniff`, `DENY` y `Referrer-Policy`. |
| Brotli | Pendiente en imagen de despliegue | Imagen local no carga modulo Brotli. |
| Backups / restauracion | Pendiente de infraestructura final | Debe definirse con el servidor/proveedor elegido. |
| 70% servicio + 100% IC/estados | Preparado para control final | JaCoCo genera reporte y el perfil `coverage-final` valida 70% de servicios. Las reglas de 100% se agregan cuando existan IC y maquinas de estado. |
| Testcontainers PostgreSQL/PostGIS | Base preparada | `DatabaseMigrationIntegrationTest` levanta PostGIS real y valida V1-V3, extensiones y tablas. Cada modulo debe agregar sus propias pruebas. |
| Spotless / Prettier / ESLint | Base preparada | CI ejecuta `spotless:check` en backend y `lint` + `format:check` + build en frontend. |
| CI por PR | Base preparada | Workflow inicial en `.github/workflows/ci.yml`. |
| ADR | Base preparada | `docs/adr/0001-base-arquitectonica.md`. |
| Manuales y diccionario | Pendiente | Se completan al finalizar funcionalidad. |


