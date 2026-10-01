# Desarrollo local

Esta guía explica cómo preparar y ejecutar SGP en un entorno de desarrollo. Está basada en la configuración vigente del repositorio; para arquitectura, interfaz y otros temas consulte la documentación específica en `docs/`.

## Requisitos

| Herramienta | Versión | Uso |
|---|---|---|
| Java (JDK) | 21 | Ejecutar el backend Spring Boot. |
| Maven | No se fija una versión local; no hay Maven Wrapper en `sgp-api/`. La imagen de compilación usa Maven 3.9.9. | Resolver dependencias y ejecutar el backend localmente. |
| Node.js | `^20.19.0`, `^22.12.0` o `>=24.0.0`, según las dependencias de Angular bloqueadas. La imagen del cliente usa Node 24. | Ejecutar y compilar el frontend. |
| npm | No se fija una versión en el proyecto. | Instalar las dependencias bloqueadas en `package-lock.json` y ejecutar scripts. |
| Docker y Docker Compose | No se fija una versión de host. | Ejecutar PostgreSQL/PostGIS, MinIO y, opcionalmente, toda la aplicación en contenedores. |

## Estructura relevante para desarrollo

```text
SGP_ProyectoFinal_AyD1/
├── sgp-api/                 # Backend Spring Boot
├── sgp-client/              # Frontend Angular
├── infra/                   # Inicialización de PostgreSQL
├── docs/
└── docker-compose.yml
```

## Configuración local

El backend carga `sgp-api/.env` mediante `spring.config.import`. Cree ese archivo a partir de la plantilla:

```bash
cp sgp-api/.env.example sgp-api/.env
```

Complete los valores de `sgp-api/.env`. Las variables sin valor predeterminado y necesarias para iniciar la API son:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
MIGRATION_DATABASE_USERNAME
MIGRATION_DATABASE_PASSWORD
SECRET_KEY_JWT
INITIAL_ADMIN_EMAIL
INITIAL_ADMIN_PASSWORD
MAIL_USERNAME
MAIL_PASSWORD
```

Para el entorno local con Docker, la plantilla ya contiene la conexión a PostgreSQL publicada en `localhost:55432` y los usuarios creados por Compose. Use una clave JWT de al menos 32 caracteres y credenciales SMTP válidas: el correo se usa para los flujos actuales de registro, 2FA y recuperación de contraseña. `MAIL_HOST`, `MAIL_PORT` y `MAIL_FROM` tienen valores predeterminados o pueden configurarse en el mismo archivo.

No versionar `sgp-api/.env`; está excluido por `.gitignore`.

## Iniciar infraestructura

Para el desarrollo actual, PostgreSQL/PostGIS es el servicio de infraestructura necesario para iniciar la API:

```bash
docker compose up -d postgres
docker compose ps
```

PostgreSQL debe quedar `healthy`. El contenedor inicializa la base `sgp_db`, el usuario migrador y el usuario de aplicación; sus datos persisten en el volumen `postgres_data`.

| Servicio | Dirección local |
|---|---|
| PostgreSQL/PostGIS | `localhost:55432` |
| MinIO API | `http://localhost:19000` |
| MinIO Console | `http://localhost:19001` |

MinIO forma parte de la infraestructura definida, pero actualmente el backend no contiene configuración ni código que lo consuma. No es necesario para iniciar la API en local y puede levantarse cuando sea necesario con:

```bash
docker compose up -d minio
```

Para revisar el arranque de la base:

```bash
docker compose logs -f postgres
```

## Iniciar backend

Con PostgreSQL en estado saludable y `sgp-api/.env` configurado:

```bash
cd sgp-api
mvn clean spring-boot:run
```

La API inicia en `http://localhost:8090`. Flyway se ejecuta al iniciar y valida el esquema antes de que la aplicación quede disponible.

Compruebe el servicio con:

```bash
curl http://localhost:8090/actuator/health
```

La respuesta esperada contiene `"status":"UP"`. También están disponibles la interfaz de OpenAPI en `http://localhost:8090/swagger-ui/index.html` y el documento en `http://localhost:8090/api/docs` cuando `OPENAPI_PUBLIC=true`.

## Iniciar frontend

En otra terminal, instale las dependencias bloqueadas y arranque Angular:

```bash
cd sgp-client
npm ci
npm start
```

Abra `http://localhost:4200`. El script `start` usa `proxy.conf.json`: todas las solicitudes a `/api` se reenvían a `http://localhost:8090`. Por ello no se requieren archivos `environment` ni una URL de API adicional para el desarrollo local.

## Comandos habituales

| Área | Comando | Propósito |
|---|---|---|
| Infraestructura | `docker compose ps` | Ver el estado de los contenedores. |
| Infraestructura | `docker compose logs -f postgres` | Ver los registros de PostgreSQL. |
| Backend | `mvn test` | Ejecutar las pruebas del backend. |
| Backend | `mvn clean verify` | Verificar el backend y generar el informe de cobertura configurado. |
| Frontend | `npm run build` | Compilar el cliente. |
| Frontend | `npm run lint` | Ejecutar ESLint. |
| Frontend | `npm run format:check` | Comprobar formato con Prettier. |
| Frontend | `npm run format` | Aplicar formato con Prettier. |

Las pruebas automatizadas del frontend están pendientes de completar su configuración. Actualmente `npm test` no puede ejecutarse correctamente porque faltan las dependencias necesarias de Vitest y un entorno DOM.

## Ejecutar todo con Docker Compose

Como alternativa al flujo de desarrollo con procesos locales, Compose también construye y ejecuta `backend` y `nginx`:

```bash
docker compose up -d --build
docker compose ps
```

La aplicación queda disponible en `http://localhost:8088`. En esta modalidad, Nginx reenvía `/api/` al backend interno; el backend no publica el puerto `8090` en la máquina anfitriona. Compose toma las variables adicionales del archivo `sgp-api/.env`.

## Detener el entorno

Detenga los procesos locales de Maven y Angular con `Ctrl+C`. Para detener los contenedores sin borrar sus datos:

```bash
docker compose down
```

No use `docker compose down -v` como cierre habitual: elimina los volúmenes `postgres_data` y `minio_data` junto con los datos locales.