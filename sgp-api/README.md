# SGP API - Backend

Backend del **Sistema de Gestion de Puentes (SGP)** para el Proyecto Final de Analisis y Diseno de Sistemas 1.

Este servicio implementa la API REST del sistema y concentra la logica de negocio, seguridad, persistencia, validaciones, auditoria, sincronizacion y procesos programados. La arquitectura sigue las directrices del enunciado: **organizacion por modulos de dominio** y, dentro de cada modulo, separacion estricta por capas.

## Stack verificado

| Tecnologia | Version / uso |
|---|---|
| Java | OpenJDK **21.0.12 LTS** |
| Spring Boot | **3.3.13** |
| Maven | **3.8.7** |
| Spring Web | API REST |
| Spring Data JPA | Persistencia |
| Spring Security | Autenticacion y autorizacion |
| Spring Validation | Validacion de DTO |
| Spring Actuator | Health y metricas |
| Spring Mail | OTP, 2FA y recuperacion de contrasena |
| PostgreSQL JDBC | **42.7.7** |
| PostgreSQL | **16.9** |
| PostGIS | **3.5.2** |
| pg_trgm | **1.6** |
| uuid-ossp | **1.1** |
| Hibernate Spatial | **6.5.3.Final** |
| Hypersistence Utils | **3.9.4** |
| Flyway | **10.10.0** |
| JJWT | **0.12.6** |
| MapStruct | **1.6.3** |
| Springdoc OpenAPI | **2.6.0** |
| NetworkNT JSON Schema Validator | **1.5.6** |
| ShedLock | **6.3.0** |
| Testcontainers | **1.20.4** |
| Spotless | **2.44.5** |
| JaCoCo | **0.8.12** |

## Arquitectura

El paquete raiz es:

```text
gt.usac.cunoc.sgp
```

La organizacion principal es por **dominio funcional**:

```text
gt.usac.cunoc.sgp/
├── archivo/
├── formulario/
├── foro/
├── inspeccion/
├── mantenimiento/
├── puente/
├── revision/
├── sync/
├── usuario/
└── common/
    ├── config/
    ├── exception/
    ├── mail/
    ├── security/
    └── util/
```

Cada modulo de dominio crece con sus propias capas. Ejemplo:

```text
puente/
├── controller/
├── dto/
├── entity/
├── repository/
├── service/
└── validation/
```

La dependencia entre capas debe mantenerse siempre asi:

```text
controller -> service -> repository -> entity
```

### Reglas obligatorias de estructura

- Un `controller` no accede directamente a un `repository`.
- La logica de negocio vive en `service`.
- Las entidades JPA no se exponen directamente en la API.
- La API recibe y devuelve DTO.
- El mapeo entidad <-> DTO debe realizarse con MapStruct cuando aplique. `usuario/mapper/UserMapper` es el ejemplo base para los nuevos modulos.
- Las relaciones JPA son `LAZY` por defecto.
- Las colecciones de API deben paginarse con `Pageable`.
- La funcionalidad transversal que usan varios dominios se coloca en `common/`.
- No mover clases de un dominio a `common/` solo por conveniencia.

## Funcionalidad base actualmente preparada

La base inicial conserva y adapta al SGP:

- Login por correo y contrasena.
- JWT de acceso.
- Refresh token persistido y revocable.
- Refresh token en cookie HttpOnly.
- Registro de estudiantes.
- Verificacion de registro mediante OTP.
- Activacion y desactivacion de 2FA mediante OTP.
- Login con segundo factor cuando 2FA esta activo.
- Recuperacion de contrasena por OTP.
- Roles iniciales:
  - `ADMINISTRADOR`
  - `CATEDRATICO`
  - `PROFESIONAL_EXTERNO`
  - `ESTUDIANTE`
- Bloqueo temporal por intentos fallidos.
- BCrypt para contrasenas.
- Manejo centralizado de errores con Problem Details.
- OpenAPI / Swagger.
- Flyway.
- ShedLock preparado para tareas programadas.

Los modulos de puentes, inspecciones, formularios, revision, foro, mantenimiento, archivos y sincronizacion deben implementarse progresivamente conforme al enunciado.

## Base de datos

La base local se ejecuta en PostgreSQL con PostGIS.

### Conexion desde la maquina host

```text
Host: localhost
Puerto: 55432
Base: sgp_db
```

### Conexion entre contenedores Docker

```text
jdbc:postgresql://postgres:5432/sgp_db
```

Se utilizan dos usuarios distintos:

```text
sgp_migrator -> ejecuta migraciones Flyway
sgp_user     -> usuario normal de la aplicacion, sin privilegios DDL
```

Las extensiones activas verificadas son:

```text
postgis   3.5.2
pg_trgm   1.6
uuid-ossp 1.1
```

## Migraciones Flyway

Las migraciones se encuentran en:

```text
src/main/resources/db/migration/
```

Migraciones actuales:

```text
V1__infraestructura_y_seguridad.sql
V2__roles_iniciales.sql
V3__corregir_tipo_token_hash.sql
```

### Regla importante

No modificar una migracion ya aplicada en ambientes compartidos. Si el esquema necesita cambiar, crear una nueva migracion:

```text
V4__descripcion_del_cambio.sql
V5__otro_cambio.sql
```

No modificar el esquema manualmente como mecanismo normal de desarrollo.

## Variables de entorno

El backend lee configuracion sensible desde `sgp-api/.env`.

El archivo `.env` **no debe versionarse**. Mantener `.env.example` con los nombres de variables requeridas, pero sin secretos reales.

Variables principales:

```env
DATABASE_URL=
DATABASE_USERNAME=
DATABASE_PASSWORD=
MIGRATION_DATABASE_USERNAME=
MIGRATION_DATABASE_PASSWORD=

SECRET_KEY_JWT=
ACCESS_EXPIRATION_TIME_JWT=
EXPIRATION_TIME_JWT=

INITIAL_ADMIN_EMAIL=
INITIAL_ADMIN_PASSWORD=

MAIL_HOST=
MAIL_PORT=
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=

OTP_EXPIRATION_MINUTES=
OTP_MAX_ATTEMPTS=

CORS_ALLOWED_ORIGINS=
REFRESH_COOKIE_SECURE=
OPENAPI_PUBLIC=
```

Nunca subir contrasenas, tokens, claves de Gmail ni secretos JWT al repositorio.

## Puertos de desarrollo

| Servicio | Puerto |
|---|---:|
| Backend Spring Boot | `8090` |
| PostgreSQL host | `55432` |
| PostgreSQL contenedor | `5432` |
| MinIO API host | `19000` |
| MinIO Console host | `19001` |
| Frontend Angular | `4200` |

## Ejecutar el backend

Primero levantar PostgreSQL y MinIO desde la raiz del proyecto:

```bash
docker compose up -d postgres minio
```

Comprobar:

```bash
docker compose ps
```

Luego:

```bash
cd sgp-api
mvn clean spring-boot:run
```

Backend:

```text
http://localhost:8090
```

Health:

```text
http://localhost:8090/actuator/health
```

Swagger UI:

```text
http://localhost:8090/swagger-ui/index.html
```

OpenAPI:

```text
http://localhost:8090/api/docs
```

## Comandos utiles

Compilar:

```bash
mvn clean compile
```

Ejecutar pruebas:

```bash
mvn test
```

La base incluye `DatabaseMigrationIntegrationTest`, que utiliza Testcontainers para levantar `postgis/postgis:16-3.5`, ejecutar Flyway sobre PostgreSQL/PostGIS real y validar extensiones y tablas iniciales. Docker debe estar disponible para ejecutar esta prueba.

Verificacion completa:

```bash
mvn clean verify
```

Formato:

```bash
mvn spotless:check
mvn spotless:apply
```

Reporte JaCoCo:

```bash
mvn clean verify
```

Validacion de la meta final del 70% en paquetes de servicio:

```bash
mvn -Pcoverage-final verify
```

El perfil `coverage-final` se deja separado del CI base mientras los modulos funcionales aun estan en construccion. Cuando existan el Indice de Condicion y las maquinas de estado se deben agregar reglas especificas de cobertura del 100% para esos componentes.

El reporte queda normalmente en:

```text
target/site/jacoco/index.html
```

Dependencias:

```bash
mvn dependency:tree
```

## Seguridad

Principios que deben mantenerse durante todo el proyecto:

- JWT stateless.
- Access token en memoria del frontend, no en `localStorage`.
- Refresh token mediante cookie HttpOnly.
- Autorizacion real en backend con `@PreAuthorize`.
- Las guardias del frontend no sustituyen la seguridad del backend.
- Autorizacion por fila en la capa de servicio.
- BCrypt con factor de trabajo >= 12.
- CORS restringido.
- Rate limiting en endpoints sensibles.
- No registrar contrasenas, OTP, tokens o secretos en logs.
- No exponer archivos directamente sin autorizacion.

## Convenciones para nuevos modulos

Ejemplo de archivos para una nueva funcionalidad de puentes:

```text
puente/
├── controller/
│   └── PuenteController.java
├── dto/
│   ├── CrearPuenteRequest.java
│   ├── ActualizarPuenteRequest.java
│   └── PuenteResponse.java
├── entity/
│   └── Puente.java
├── repository/
│   └── PuenteRepository.java
├── service/
│   └── PuenteService.java
└── validation/
```

No crear paquetes globales como `controllers/`, `services/`, `repositories/` o `entities/` en la raiz. El proyecto debe permanecer organizado por dominio.
