# Sistema de Gestion de Puentes (SGP)

Proyecto Final de **Analisis y Diseno de Sistemas 1**.

Este repositorio contiene una aplicacion cliente-servidor para inventario, inspeccion tecnica, revision academica, colaboracion y mantenimiento de puentes, con capacidad PWA, operacion offline y sincronizacion posterior.

El proyecto fue preparado como una base limpia para trabajo colaborativo. Actualmente ya incluye infraestructura, autenticacion, seguridad base, 2FA, recuperacion de contrasena, PWA, IndexedDB/Dexie, MinIO, PostgreSQL/PostGIS, Flyway y la estructura de modulos de dominio requerida por el enunciado.

---

## 1. Estructura general

```text
sgp-proyecto-final-ayd1/
├── sgp-api/                  Backend Spring Boot
├── sgp-client/               Frontend Angular PWA
├── infra/                    Inicializacion de infraestructura
│   └── postgres/
├── docs/                     Documentacion y ADR
├── docker-compose.yml
├── .gitignore
└── README.md
```

---

## 2. Por que el proyecto se organiza por dominios

Este proyecto **no se organiza principalmente por tipo tecnico global** de esta forma:

```text
controllers/
services/
repositories/
entities/
dto/
```

En lugar de eso, el backend se divide por **modulos de dominio**:

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
```

La razon es que cada carpeta representa una capacidad real del sistema y contiene las capas necesarias para implementar esa funcionalidad.

Ejemplo:

```text
puente/
├── controller/
├── dto/
├── entity/
├── repository/
├── service/
└── validation/
```

Dentro de cada dominio se conserva estrictamente la separacion:

```text
controller -> service -> repository -> entity
```

Un controlador no debe acceder directamente al repositorio.

Los DTO son obligatorios en la frontera de la API y las entidades JPA no se deben serializar directamente hacia el frontend.

### Ventajas para el equipo

La organizacion por dominio ayuda a:

- Reducir conflictos de Git.
- Permitir que varios integrantes trabajen en funcionalidades diferentes.
- Mantener alta cohesion dentro de cada modulo.
- Reducir acoplamiento entre funcionalidades.
- Localizar rapidamente las clases de una funcionalidad.
- Facilitar mantenimiento.
- Hacer que frontend y backend reflejen la misma division funcional.
- Facilitar el traspaso del proyecto a futuros equipos.

Por ejemplo, un integrante asignado a `puente` deberia trabajar principalmente en:

```text
sgp-api/src/main/java/gt/usac/cunoc/sgp/puente/
sgp-client/src/app/features/puentes/
```

mientras otro integrante puede trabajar en `mantenimiento` sin modificar constantemente los mismos archivos.

---

## 3. Que pertenece a `common/`

`common/` contiene elementos transversales utilizados por varios dominios:

```text
common/
├── config/
├── exception/
├── mail/
├── security/
├── util/
└── audit/          # auditoria transversal cuando se complete el modulo
```

Ejemplos correctos:

- Configuracion de Spring Security.
- JWT.
- Manejo global de excepciones.
- Correo.
- Auditoria transversal.
- Utilidades realmente compartidas.

Ejemplos que **no** deben ir a `common/`:

- `PuenteService`.
- `InspeccionRepository`.
- DTO exclusivos de mantenimiento.
- Reglas propias del formulario.

---

## 4. Simetria frontend / backend

Se mantiene una correspondencia clara entre dominios:

```text
BACKEND                         FRONTEND

puente/                         features/puentes/
inspeccion/                     features/inspecciones/
formulario/                     features/formulario/
revision/                       features/revision/
foro/                           features/foro/
mantenimiento/                  features/mantenimiento/
usuario/                        features/usuario/ + core/auth
sync/                           offline/
```

Esto facilita entender donde implementar una funcionalidad completa.

---

# 5. Stack tecnologico actual

## 5.1 Backend

```text
Java 21.0.12 LTS
Spring Boot 3.3.13
Maven 3.8.7
Spring Web
Spring Data JPA
Spring Security
Spring Validation
Spring Boot Actuator
Flyway 10.10.0
PostgreSQL Driver 42.7.7
Hibernate Spatial 6.5.3.Final
Hypersistence Utils 3.9.4
JJWT 0.12.6
MapStruct 1.6.3
Springdoc OpenAPI 2.6.0
NetworkNT JSON Schema Validator 1.5.6
ShedLock 6.3.0
Testcontainers 1.21.4
JaCoCo 0.8.12
Spotless 2.44.5
```

## 5.2 Base de datos

```text
PostgreSQL 16.9
PostGIS 3.5.2
pg_trgm 1.6
uuid-ossp 1.1
```

## 5.3 Frontend

```text
Angular 21.2.24
Angular CLI 21.2.24
TypeScript 5.9.3
Node.js 24.19.0
npm 11.17.0
RxJS 7.8.2
PrimeNG 21.x
@jsverse/transloco 8.4.0
PrimeIcons
PrimeUIX Themes
Standalone Components
Angular Signals
Reactive Forms
Angular Service Worker / PWA
Dexie 4.4.6
IndexedDB
MapLibre GL 5.24.0
ESLint 9.x
Prettier 3.9.9
```

## 5.4 Infraestructura

```text
Docker 29.8.1
Docker Compose 5.5.1
PostgreSQL + PostGIS
MinIO
Nginx
```

---

# 6. Requisitos tecnicos principales del proyecto

La base esta preparada para cumplir las directrices obligatorias del enunciado:

- Arquitectura cliente-servidor desacoplada.
- Angular como SPA.
- API REST con Spring Boot.
- Backend organizado por modulos de dominio.
- Capas `controller -> service -> repository -> entity`.
- DTO obligatorios en la API.
- JWT y aplicacion stateless.
- PostgreSQL 16 o superior.
- PostGIS, `pg_trgm` y `uuid-ossp`.
- JSONB para datos variables del formulario.
- Flyway para migraciones.
- UUID v7 para identificadores que deban generarse offline.
- OpenAPI / Swagger.
- Angular standalone.
- TypeScript strict.
- Signals.
- Reactive Forms.
- PWA.
- IndexedDB con Dexie.
- MapLibre GL para mapas.
- MinIO para almacenamiento de objetos.
- Docker Compose.
- Nginx.
- Testcontainers.
- JaCoCo.
- Spotless.
- ESLint.
- Prettier.
- ADR para decisiones relevantes.

---

# 7. Configuracion y puertos de desarrollo

## 7.1 Puertos actuales

| Servicio | URL / puerto |
|---|---|
| Frontend Angular | `http://localhost:4200` |
| Backend Spring Boot | `http://localhost:8090` |
| Health | `http://localhost:8090/actuator/health` |
| Swagger UI | `http://localhost:8090/swagger-ui/index.html` |
| OpenAPI | `http://localhost:8090/api/docs` |
| PostgreSQL desde host | `localhost:55432` |
| PostgreSQL dentro de Docker | `postgres:5432` |
| MinIO API | `http://localhost:19000` |
| MinIO Console | `http://localhost:19001` |

## 7.2 Base de datos actual

```text
Base de datos: sgp_db
Usuario de migraciones: sgp_migrator
Usuario de aplicacion: sgp_user
Puerto host: 55432
Puerto interno Docker: 5432
```

El backend ejecutado localmente usa:

```text
jdbc:postgresql://localhost:55432/sgp_db
```

El backend ejecutado dentro de Docker usa:

```text
jdbc:postgresql://postgres:5432/sgp_db
```

---

# 8. Variables de entorno

El backend utiliza `sgp-api/.env`.

**Nunca se debe versionar el archivo `.env` real.**

Debe existir `.env.example` como referencia.

Variables principales:

```env
DATABASE_URL=jdbc:postgresql://localhost:55432/sgp_db
DATABASE_USERNAME=sgp_user
DATABASE_PASSWORD=sgp_password

MIGRATION_DATABASE_USERNAME=sgp_migrator
MIGRATION_DATABASE_PASSWORD=sgp_migrator_password

SECRET_KEY_JWT=CAMBIAR_EN_CADA_AMBIENTE
ACCESS_EXPIRATION_TIME_JWT=900000
EXPIRATION_TIME_JWT=86400000

INITIAL_ADMIN_EMAIL=correo_admin
INITIAL_ADMIN_PASSWORD=contrasena_admin

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=correo
MAIL_PASSWORD=app_password
MAIL_FROM=correo

OTP_EXPIRATION_MINUTES=10
OTP_MAX_ATTEMPTS=5
```

Los secretos reales nunca deben colocarse en Git.

---

# 9. Migraciones Flyway actuales

Las migraciones estan ubicadas en:

```text
sgp-api/src/main/resources/db/migration/
```

Actualmente existen tres migraciones aplicadas correctamente.

## V1 - `V1__infraestructura_y_seguridad.sql`

Esta migracion crea la infraestructura inicial de seguridad y soporte de la aplicacion.

Entre los objetos ya existentes en la base se encuentran:

```text
auditoria
desafio_otp
flyway_schema_history
rol
shedlock
token_refresco
usuario
spatial_ref_sys
```

Tambien habilita las extensiones requeridas:

```text
postgis
pg_trgm
uuid-ossp
```

### Tablas principales creadas por la base inicial

#### `usuario`

Contiene las cuentas del sistema y los datos necesarios para autenticacion y seguridad.

La base actual soporta, entre otros conceptos:

- correo.
- hash de contrasena.
- rol.
- estado de cuenta.
- verificacion.
- 2FA.
- control de version de token.
- marcas de tiempo.

#### `rol`

Catalogo de roles globales.

#### `desafio_otp`

Almacena desafios OTP utilizados para:

- verificacion.
- autenticacion de dos factores.
- recuperacion de contrasena.
- cambios de seguridad.

#### `token_refresco`

Almacena refresh tokens persistentes para permitir revocacion de sesiones.

#### `auditoria`

Base de la bitacora de auditoria requerida por el proyecto.

La implementacion funcional completa mediante aspectos se continuara desarrollando.

#### `shedlock`

Tabla utilizada por ShedLock para bloquear ejecuciones duplicadas de tareas programadas cuando existan varias replicas del backend.

## V2 - `V2__roles_iniciales.sql`

Inserta los cuatro roles globales iniciales:

```text
ADMINISTRADOR
CATEDRATICO
PROFESIONAL_EXTERNO
ESTUDIANTE
```

Estos roles corresponden a los actores autenticados principales del sistema.

Actualmente el autorregistro crea cuentas de tipo `ESTUDIANTE`.

Los demas roles se implementaran mediante flujo de invitacion del Administrador.

## V3 - `V3__corregir_tipo_token_hash.sql`

Corrige el tipo de la columna:

```text
token_refresco.token_hash
```

de `CHAR(64)` a:

```text
VARCHAR(64)
```

para mantener compatibilidad exacta entre el esquema PostgreSQL y la entidad JPA utilizada por Hibernate.

Migracion:

```sql
ALTER TABLE token_refresco
ALTER COLUMN token_hash TYPE VARCHAR(64)
USING token_hash::VARCHAR(64);
```

## Estado actual de Flyway

La base verificada actualmente contiene:

```text
version | description                  | success
--------+------------------------------+--------
1       | infraestructura y seguridad | true
2       | roles iniciales             | true
3       | corregir tipo token hash    | true
```

### Regla obligatoria

**Nunca modificar una migracion Flyway ya aplicada.**

Si se requiere modificar el esquema:

```text
V1
V2
V3
V4__nueva_modificacion.sql
V5__otra_modificacion.sql
...
```

---

# 10. Que existe actualmente en PostgreSQL

Para consultar las tablas:

```bash
docker compose exec postgres \
psql -U sgp_migrator -d sgp_db -c "\\dt"
```

La base inicial contiene actualmente al menos:

```text
public | auditoria
public | desafio_otp
public | flyway_schema_history
public | rol
public | shedlock
public | spatial_ref_sys
public | token_refresco
public | usuario
```

## Consultar extensiones

```bash
docker compose exec postgres \
psql -U sgp_migrator -d sgp_db \
-c "SELECT extname, extversion FROM pg_extension ORDER BY extname;"
```

Resultado esperado:

```text
pg_trgm
plpgsql
postgis
uuid-ossp
```

## Consultar migraciones

```bash
docker compose exec postgres \
psql -U sgp_migrator -d sgp_db \
-c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

## Consultar roles

```bash
docker compose exec postgres \
psql -U sgp_migrator -d sgp_db \
-c "SELECT id, nombre, descripcion FROM rol ORDER BY nombre;"
```

## Comprobar 2FA de un usuario

```bash
docker compose exec postgres \
psql -U sgp_migrator -d sgp_db \
-c "SELECT email, two_factor_habilitado FROM usuario ORDER BY email;"
```

---

# 11. Como levantar el proyecto desde cero

Se recomienda utilizar **tres terminales**:

```text
Terminal 1 -> Docker
Terminal 2 -> Backend
Terminal 3 -> Frontend
```

## 11.1 Requisitos instalados

Antes de iniciar:

```bash
java -version
javac -version
mvn -version
node -v
npm -v
npx ng version
git --version
docker --version
docker compose version
```

## 11.2 Levantar infraestructura Docker

Desde la raiz:

```bash
cd sgp-proyecto-final-ayd1
```

Levantar PostgreSQL/PostGIS y MinIO:

```bash
docker compose up -d postgres minio
```

Comprobar:

```bash
docker compose ps
```

PostgreSQL debe aparecer:

```text
Up (healthy)
```

MinIO debe aparecer:

```text
Up
```

### Ver logs de PostgreSQL

```bash
docker compose logs -f postgres
```

### Ver logs de MinIO

```bash
docker compose logs -f minio
```

### Detener infraestructura

```bash
docker compose down
```

Esto no elimina los datos.

### No utilizar normalmente

```bash
docker compose down -v
```

porque `-v` elimina los volumenes y puede borrar la base local.

---

# 12. Levantar el backend

En otra terminal:

```bash
cd sgp-api
mvn clean spring-boot:run
```

El backend debe iniciar en:

```text
http://localhost:8090
```

La salida debe terminar aproximadamente con:

```text
Started SgpApplication
```

## Health

```bash
curl http://localhost:8090/actuator/health
```

Debe responder:

```json
{"status":"UP"}
```

## Swagger

Abrir:

```text
http://localhost:8090/swagger-ui/index.html
```

## OpenAPI

```text
http://localhost:8090/api/docs
```

## Detener backend

```text
Ctrl + C
```

---

# 13. Levantar el frontend

En otra terminal:

```bash
cd sgp-client
```

La primera vez o despues de modificar dependencias:

```bash
npm install
```

Levantar Angular:

```bash
npm start
```

Abrir:

```text
http://localhost:4200
```

## Compilar frontend

```bash
npm run build
```

## Linter

```bash
npm run lint
```

## Detener frontend

```text
Ctrl + C
```

---

# 14. Levantar todo con Docker Compose

El proyecto tambien esta preparado para desplegar:

```text
nginx
backend
postgres
minio
```

Para construir y levantar todos los servicios:

```bash
docker compose up -d --build
```

Comprobar:

```bash
docker compose ps
```

Para detener:

```bash
docker compose down
```

En desarrollo diario se recomienda:

```text
PostgreSQL + MinIO -> Docker
Backend            -> Maven
Frontend           -> Angular CLI
```

porque permite recarga y depuracion mas rapidas.

---

# 15. Estado actual de autenticacion y seguridad

Actualmente se encuentra probado:

- Login.
- JWT access token.
- Refresh token persistente.
- Refresh token mediante cookie HttpOnly.
- Logout.
- Registro de estudiantes.
- Verificacion de correo.
- OTP.
- Activacion de 2FA.
- Desactivacion de 2FA.
- Login con 2FA.
- Recuperacion de contrasena.
- Cambio de contrasena.
- Administrador inicial.
- Roles base.
- Guards iniciales del frontend.
- Proteccion inicial de endpoints.

El access token se mantiene en memoria del frontend.

No se utiliza `localStorage` para guardar el access token.

---

# 16. Roles actuales

Los roles globales definidos son:

```text
ADMINISTRADOR
CATEDRATICO
PROFESIONAL_EXTERNO
ESTUDIANTE
```

Estado funcional actual:

### Administrador

Existe administrador inicial configurable por `.env`.

### Estudiante

Puede autorregistrarse y verificar su cuenta.

El flujo academico completo de activacion por Catedratico y vinculacion a curso todavia debe implementarse.

### Catedratico

El rol existe en la base, pero falta completar:

- invitacion por Administrador.
- activacion.
- administracion de cursos.
- estudiantes pendientes.
- vinculacion estudiante-curso.
- asignaciones de puentes.
- revision academica.

### Profesional externo

El rol existe en la base.

Falta implementar:

- invitacion.
- numero de colegiado.
- verificacion por Administrador.
- habilitacion.

---

# 17. Frontend actual

La aplicacion Angular usa:

```text
Standalone Components
Signals
Reactive Forms
PrimeNG
PrimeIcons
PWA
Service Worker
Dexie
IndexedDB
MapLibre GL
```

No se crean `NgModule` nuevos.

El frontend esta organizado por funcionalidades:

```text
src/app/
├── core/
├── shared/
├── features/
│   ├── admin/
│   ├── dashboard/
│   ├── usuario/
│   ├── puentes/          # al implementarse
│   ├── inspecciones/     # al implementarse
│   ├── formulario/       # al implementarse
│   ├── revision/         # al implementarse
│   ├── foro/             # al implementarse
│   └── mantenimiento/    # al implementarse
├── layouts/
├── offline/
└── theme/
```

---

# 18. Capacidad offline preparada

Actualmente se encuentra preparada la base para:

- PWA.
- Service Worker.
- IndexedDB.
- Dexie.
- almacenamiento persistente local.
- servicio de conectividad.
- cola de sincronizacion.

Los almacenes previstos son:

```text
puentes
inspecciones
fotos
esquemas_formulario
cola_sync
```

Todavia falta desarrollar completamente:

- Preparar salida.
- persistencia completa de inspecciones.
- fotos offline.
- orden de sincronizacion.
- reintentos.
- backoff exponencial.
- idempotencia completa.
- manejo de conflictos.
- diagnostico completo.

---

# 19. Modulos funcionales que faltan desarrollar

El sistema final debe completar:

## Inventario

- CRUD de puentes.
- codigos unicos.
- departamentos y municipios.
- WGS84.
- UTM.
- PostGIS.
- validacion de duplicados por cercania.
- mapa publico.
- baja logica.
- estado actual.

## Formularios

- JSON Schema.
- editor de formularios.
- versiones.
- version activa.
- mapeo entre versiones.
- ocho secciones SIECA.
- validacion cliente y servidor.

## Inspecciones

- BORRADOR.
- ENVIADA.
- EN_REVISION.
- PUBLICADA.
- CAMBIOS_SOLICITADOS.
- RECHAZADA.
- datos permanentes precargables.
- danos siempre vacios.
- GPS.
- fotos.
- correcciones.

## Revision academica

- bandeja de revision.
- observaciones ancladas.
- aprobacion.
- cambios solicitados.
- rechazo.
- historial.
- calificacion.
- alerta de 15 dias.

## Indice de Condicion

- formula IC.
- pesos configurables.
- versionado de pesos.
- reglas de anulacion.
- estado Bueno / Regular / Malo / Sin evaluar.
- anulacion manual justificada.

## Archivos

- fotos.
- documentos.
- compresion.
- metadata.
- MinIO.
- miniaturas.
- limites.
- purga de borradores.

## Foro

- hilos por puente.
- comentarios por inspeccion.
- `elemento_ref`.
- menciones.
- moderacion.
- categorias.
- alertas.

## Mantenimiento

- propuestas.
- programacion.
- ejecucion.
- cierre.
- evidencia.
- prioridad.
- alertas anuales.

## Usuarios / cursos / asignaciones

- invitaciones.
- activacion de Catedraticos.
- validacion de Profesional externo.
- cursos.
- estudiantes pendientes.
- vinculacion.
- asignacion de puentes.
- autorizacion por fila.

## Auditoria

- quien.
- cuando.
- accion.
- valores anteriores.
- valores posteriores.
- implementacion transversal mediante aspectos.

---

# 20. Calidad y pruebas

## Backend

Antes de integrar cambios al repositorio:

```bash
cd sgp-api
mvn clean verify
mvn spotless:check
```

Objetivos generales de calidad:

- cobertura minima del 70% en la capa de servicio.
- 100% de cobertura en el calculo del Indice de Condicion.
- 100% de cobertura en las maquinas de estado de inspecciones y ordenes de mantenimiento.
- pruebas de integracion con PostgreSQL real + PostGIS mediante Testcontainers.
- pruebas de autorizacion por rol.
- evitar consultas N+1.
- mantener relaciones JPA en `LAZY` salvo que exista una justificacion tecnica.
- validar las migraciones Flyway dentro de las pruebas de integracion.

### 20.1 Que es Testcontainers y por que se usa en este proyecto

Testcontainers es una libreria de pruebas que permite crear contenedores Docker temporales durante la ejecucion de los tests.

En este proyecto se utiliza principalmente para levantar una instancia **real de PostgreSQL con PostGIS** durante las pruebas de integracion.

El objetivo es que el backend no se pruebe contra una base simulada como H2, porque el Sistema de Gestion de Puentes depende de funcionalidades especificas de PostgreSQL y PostGIS que no se comportan igual en una base de datos en memoria.

Entre esas funcionalidades se encuentran:

```text
PostGIS
JSONB
pg_trgm
uuid-ossp
consultas espaciales
indices espaciales
restricciones reales de PostgreSQL
Hibernate Spatial
migraciones Flyway
```

Por esta razon, una prueba que funciona correctamente sobre H2 no garantiza necesariamente que vaya a funcionar sobre PostgreSQL/PostGIS.

### 20.2 Flujo de una prueba con Testcontainers

Cuando se ejecuta:

```bash
mvn test
```

o:

```bash
mvn verify
```

una prueba de integracion puede realizar automaticamente el siguiente flujo:

```text
Maven
  |
  v
JUnit
  |
  v
Testcontainers
  |
  v
Docker crea un contenedor temporal
  |
  v
PostgreSQL + PostGIS real
  |
  v
Spring Boot obtiene la conexion
  |
  v
Flyway ejecuta las migraciones
  |
  v
Hibernate valida el esquema
  |
  v
Se ejecutan las pruebas
  |
  v
Finalizan los tests
  |
  v
Testcontainers elimina el contenedor
```

El desarrollador no necesita crear manualmente una base `sgp_test` ni mantener una instancia de pruebas ejecutandose permanentemente.

Cada ejecucion puede comenzar con una base limpia y aislada.

### 20.3 Diferencia entre una prueba unitaria y una prueba de integracion

Una **prueba unitaria** valida una clase de manera aislada.

Ejemplo:

```text
PuenteService
    |
    v
PuenteRepository simulado con Mockito
```

En ese caso:

- no se inicia PostgreSQL.
- no se ejecuta PostGIS.
- no se prueba Hibernate realmente.
- no se prueba Flyway.
- normalmente se simulan las dependencias.

Este tipo de prueba es util para reglas de negocio puras.

Por ejemplo:

```text
Si el Indice de Condicion es 85
entonces el estado debe clasificarse segun las reglas correspondientes.
```

Una **prueba de integracion con Testcontainers** valida que varias piezas reales trabajen correctamente juntas.

Ejemplo:

```text
Service
   |
   v
Repository real
   |
   v
Hibernate / JPA
   |
   v
PostgreSQL + PostGIS real
```

En esta prueba se pueden validar:

- entidades JPA.
- repositorios.
- consultas JPQL o SQL.
- restricciones.
- indices.
- JSONB.
- PostGIS.
- Flyway.
- transacciones.
- persistencia real.

### 20.4 Por que es especialmente importante en SGP

El Sistema de Gestion de Puentes utilizara PostGIS para trabajar con ubicaciones y distancias.

Por ejemplo, cuando se implemente la validacion de puentes cercanos, sera necesario comprobar realmente una consulta espacial similar a:

```text
Puente A
latitud / longitud
       |
       v
PostGIS
       |
       v
ST_DWithin(...)
       |
       v
Detectar otro puente dentro del radio permitido
```

Ese comportamiento debe probarse contra PostGIS real.

Tambien sera importante para los datos variables almacenados mediante JSONB.

Una prueba de integracion debe garantizar que el backend pueda:

```text
guardar datos
        |
        v
serializarlos a JSONB
        |
        v
leerlos nuevamente
        |
        v
mantener su estructura correctamente
```

### 20.5 Que funcionalidades deben probarse con Testcontainers

A medida que se desarrollen los modulos, se deben crear pruebas de integracion para casos como:

#### Puentes

- crear un puente.
- consultar un puente.
- modificar un puente.
- baja logica.
- coordenadas PostGIS.
- busqueda por cercania.
- deteccion de posibles duplicados.
- consultas espaciales.

#### Formularios

- persistencia de JSON Schema.
- JSONB.
- versiones.
- formulario activo.
- mapeos entre versiones.

#### Inspecciones

- creacion de borradores.
- envio.
- revision.
- cambios solicitados.
- publicacion.
- rechazo.
- transiciones validas de estado.
- transiciones invalidas de estado.

#### Indice de Condicion

- persistencia de resultados.
- configuracion de pesos.
- versiones de pesos.
- reglas de anulacion.

El calculo matematico puro puede probarse unitariamente, pero su persistencia debe probarse tambien de forma integrada.

#### Mantenimiento

- creacion de ordenes.
- cambios de estado.
- seguimiento.
- cierre.
- reglas de transicion.

#### Usuarios y seguridad

- roles.
- permisos.
- usuarios activos e inactivos.
- autorizacion por rol.
- asignaciones.
- relaciones entre Catedratico, Estudiante y Curso.

#### Sincronizacion

- idempotencia.
- operaciones repetidas.
- conflictos.
- persistencia del estado de sincronizacion.

#### Migraciones

Las pruebas de integracion tambien deben verificar que una base limpia pueda ejecutar correctamente todas las migraciones:

```text
V1
V2
V3
V4
...
```

Si una migracion nueva rompe una instalacion desde cero, el pipeline debe detectarlo antes de integrar el codigo.

### 20.6 Ejemplo conceptual de una prueba

Una prueba de integracion podria tener una estructura similar a:

```java
@Testcontainers
@SpringBootTest
class PuenteIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgis/postgis:16-3.5")
            .withDatabaseName("sgp_test")
            .withUsername("test")
            .withPassword("test");
}
```

Este ejemplo es solamente conceptual.

Cuando se implementen las pruebas reales se debe reutilizar una configuracion comun de Testcontainers para evitar duplicar configuracion entre todos los modulos.

### 20.7 Ventajas para el equipo

Testcontainers permite que todos los integrantes ejecuten las mismas pruebas en condiciones controladas.

Por ejemplo:

```text
PC integrante 1
sgp_db con muchos datos
         |
         X
         |
Testcontainers crea su propia BD limpia
```

```text
PC integrante 2
sgp_db vacia
         |
         X
         |
Testcontainers crea su propia BD limpia
```

La base de desarrollo local de cada integrante no afecta las pruebas.

Esto ayuda a evitar errores como:

```text
"En mi maquina funciona"
```

porque las pruebas automatizadas utilizan un entorno reproducible.

### 20.8 Testcontainers dentro de CI/CD

Cuando exista un Pull Request:

```text
feature/*
    |
    v
Pull Request hacia develop
    |
    v
GitHub Actions / CI
    |
    v
mvn verify
    |
    v
Testcontainers
    |
    v
PostgreSQL + PostGIS temporal
    |
    v
Pruebas
```

Si las pruebas fallan, el cambio no deberia integrarse hasta corregirse.

Por esto Testcontainers es importante tanto para desarrollo local como para integracion continua.

### 20.9 Dependencias ya incluidas

El backend ya incluye las dependencias necesarias:

```text
org.testcontainers:junit-jupiter:1.21.4
org.testcontainers:postgresql:1.21.4
```

La base incluye `DatabaseMigrationIntegrationTest`, que levanta `postgis/postgis:16-3.5`, ejecuta Flyway V1-V3 y comprueba extensiones/tablas iniciales.

Para validar la meta final de cobertura de servicios se dispone del perfil:

```bash
mvn -Pcoverage-final verify
```

Tener estas dependencias instaladas **no significa que las pruebas ya esten completas**.

Significa que el proyecto ya tiene preparada la herramienta y que cada modulo debe agregar sus pruebas de integracion conforme se implemente.

### 20.10 Regla para los integrantes

Cuando una funcionalidad dependa de PostgreSQL, PostGIS, JSONB, Flyway, Hibernate o restricciones reales de base de datos, no debe considerarse suficientemente probada solamente con Mockito.

Se debe agregar una prueba de integracion con Testcontainers cuando corresponda.

Resumen:

```text
Logica pura
    -> prueba unitaria

Persistencia / PostgreSQL / PostGIS / Flyway
    -> prueba de integracion con Testcontainers

Flujo completo desde navegador
    -> prueba E2E
```

## Frontend

Antes de integrar:

```bash
cd sgp-client
npm run lint
npm run build
```

Ademas deben desarrollarse pruebas E2E para el flujo offline y red degradada con Playwright o Cypress.

Las pruebas E2E deben cubrir progresivamente:

- instalacion y operacion PWA.
- preparacion de datos offline.
- creacion de inspecciones sin conexion.
- persistencia en IndexedDB.
- recuperacion despues de recargar la aplicacion.
- reconexion.
- sincronizacion.
- errores y reintentos.
- comportamiento con red degradada.

---

# 21. Reglas importantes para todos los integrantes

1. No modificar migraciones Flyway ya aplicadas.
2. Crear una migracion nueva para cada cambio de esquema.
3. No subir `.env`, contrasenas, tokens ni secretos.
4. No guardar access tokens en `localStorage`.
5. No devolver entidades JPA directamente.
6. No acceder desde controller a repository.
7. No crear `NgModule` nuevos.
8. No codificar el formulario SIECA como HTML fijo.
9. El formulario debe generarse desde JSON Schema.
10. No guardar inspecciones offline en `localStorage`.
11. Usar IndexedDB/Dexie.
12. No hacer `DELETE` fisico donde el dominio requiere historial.
13. Mantener las funcionalidades separadas por dominio.
14. No introducir cambios arquitectonicos relevantes sin ADR.
15. Mantener endpoints bajo `/api/v1`.
16. Mantener consultas de colecciones paginadas.
17. No utilizar nombres de archivo enviados por el cliente como nombres fisicos.
18. Mantener secretos fuera del repositorio.
19. Ejecutar pruebas y formato antes de integrar.
20. Seguir Conventional Commits.

---

# 22. Flujo Git del equipo

Ramas principales:

```text
main
develop
feature/*
```

Flujo recomendado:

```bash
git switch develop
git pull origin develop
git switch -c feature/nombre-funcionalidad
```

Ejemplos:

```text
feature/inventario-puentes
feature/formulario-dinamico
feature/inspecciones
feature/revision-academica
feature/indice-condicion
feature/mantenimiento
feature/foro
feature/usuarios-cursos
feature/offline-sync
```

Ejemplos de commits:

```text
feat: add bridge registration endpoint
fix: persist two factor authentication state
refactor: move bridge mapping to mapstruct
test: add bridge service integration tests
chore: configure spotless
docs: update project setup guide
```

Las ramas `feature/*` deben integrarse mediante Pull Request hacia:

```text
develop
```

`main` debe mantenerse estable y representar la version desplegable.

---

# 23. Integracion Continua (CI) y estado de CD

## 23.1 Que es CI

CI significa:

```text
Continuous Integration
Integracion Continua
```

El repositorio utiliza **GitHub Actions** para ejecutar verificaciones automaticas cuando se abre o actualiza un Pull Request.

El objetivo es detectar errores antes de integrar codigo a una rama compartida.

El flujo actual es:

```text
feature/*
    |
    | Pull Request
    v
develop
    |
    v
GitHub Actions
   /        \
  v          v
CI/backend  CI/frontend
```

En GitHub actualmente aparecen dos checks principales:

```text
CI / backend
CI / frontend
```

Estos checks no significan que el sistema se este desplegando. Significan que GitHub esta validando automaticamente que el cambio pueda integrarse sin romper la base del proyecto.

## 23.2 CI del backend

El check:

```text
CI / backend
```

se encarga de validar el proyecto Spring Boot.

En la configuracion actual, el check ejecuta `mvn -B spotless:check verify`, por lo que valida:

```text
compilacion Maven
pruebas automaticas
mvn verify
Spotless
JaCoCo
Testcontainers
Flyway sobre PostgreSQL/PostGIS real
```

El objetivo es detectar errores como:

```text
codigo Java que no compila
tests que fallan
errores de integracion
problemas de persistencia
problemas con PostgreSQL/PostGIS
problemas con Flyway
errores de formato cuando el workflow los valida
```

Las verificaciones exactas que se ejecutan estan definidas en:

```text
.github/workflows/
```

Los integrantes no deben modificar los workflows de CI sin coordinarlo con el equipo, porque un cambio en ellos afecta las verificaciones de todos los Pull Requests.

## 23.3 CI del frontend

El check:

```text
CI / frontend
```

valida el proyecto Angular.

En la configuracion actual ejecuta:

```text
npm ci
npm run lint
npm run format:check
npm run build
```

Con esto valida instalacion reproducible de dependencias, ESLint y compilacion Angular.

Su objetivo es detectar problemas como:

```text
imports incorrectos
errores TypeScript
errores en templates
dependencias faltantes
errores de compilacion
errores de lint
```

## 23.4 Que significan los estados de los checks

Cuando GitHub muestra:

```text
CI / backend   OK
CI / frontend  OK
```

y aparece:

```text
All checks have passed
```

significa que las verificaciones automaticas finalizaron correctamente.

Esto no significa que el codigo sea perfecto ni reemplaza la revision humana, pero indica que paso las validaciones automatizadas configuradas.

Cuando un check aparece en rojo:

```text
CI / backend   ERROR
```

o:

```text
CI / frontend  ERROR
```

el integrante debe abrir el detalle del check, revisar el log y corregir el problema antes de realizar el Merge.

## 23.5 Regla para hacer Merge

Un Pull Request no debe integrarse solamente porque GitHub indique que no existen conflictos.

La regla del equipo debe ser:

```text
Sin conflictos
      +
CI backend correcto
      +
CI frontend correcto
      +
Revision del cambio
      |
      v
    Merge
```

Antes de hacer Merge hacia `develop`, comprobar:

```text
[OK] No existen conflictos con la rama base
[OK] CI / backend pasa
[OK] CI / frontend pasa
[OK] El cambio corresponde a la funcionalidad de la rama
[OK] No se incluyeron secretos ni archivos .env
[OK] El codigo fue revisado
[OK] Las pruebas relevantes fueron ejecutadas
```

Si uno de los checks falla, primero debe corregirse el problema.

## 23.6 Relacion entre CI y Testcontainers

Testcontainers puede formar parte del CI del backend.

El flujo esperado para pruebas de integracion es:

```text
Pull Request
    |
    v
GitHub Actions
    |
    v
CI / backend
    |
    v
mvn verify
    |
    v
Testcontainers
    |
    v
PostgreSQL + PostGIS temporal
    |
    v
Flyway
    |
    v
Pruebas de integracion
```

De esta forma, el pipeline puede comprobar que las funcionalidades que dependen de PostgreSQL/PostGIS funcionen sobre una base real y reproducible.

A medida que el proyecto incorpore nuevas pruebas con Testcontainers, estas deberan ejecutarse dentro del pipeline de CI correspondiente.

## 23.7 Por que CI es importante para este equipo

El proyecto sera desarrollado por varios integrantes.

Sin CI podria ocurrir:

```text
Integrante A
"En mi maquina funciona"

Integrante B
"En mi maquina no compila"
```

CI reduce ese problema porque todos los Pull Requests pasan por las mismas verificaciones automatizadas.

Ejemplo:

```text
feature/inventario-puentes
          |
          v
          PR
          |
          v
CI backend + frontend
          |
         OK
          |
          v
       develop
```

Mientras tanto otro integrante puede trabajar en:

```text
feature/inspecciones
```

sin depender de la configuracion local del primer integrante.

## 23.8 CI no es lo mismo que CD

Es importante diferenciar:

```text
CI = Continuous Integration
```

de:

```text
CD = Continuous Delivery / Continuous Deployment
```

### CI

CI comprueba automaticamente que el codigo:

```text
compile
pase pruebas
cumpla validaciones
pueda integrarse
```

### CD

CD se encarga de preparar o realizar el despliegue de una version hacia un ambiente como:

```text
staging
produccion
servidor institucional
infraestructura en la nube
```

Un flujo completo podria ser:

```text
feature/*
    |
    v
Pull Request hacia develop
    |
    v
CI
    |
    v
develop
    |
    | cuando exista una version estable
    v
Pull Request hacia main
    |
    v
CI
    |
    v
CD
    |
    v
Despliegue
```

## 23.9 Estado actual de CD

**Actualmente este proyecto NO tiene CD automatico configurado.**

Lo que existe actualmente es:

```text
[OK] CI para backend
[OK] CI para frontend
[PENDIENTE] CD / despliegue automatico
```

Por lo tanto, cuando GitHub muestra:

```text
CI / backend
CI / frontend
```

no esta desplegando el sistema.

Solamente esta verificando el codigo.

El CD se configurara posteriormente, cuando el equipo defina:

```text
servidor o plataforma de despliegue
dominio
HTTPS / TLS
certificados
secrets de produccion
PostgreSQL de produccion
MinIO de produccion
Nginx final
estrategia de respaldos
variables de entorno
logs
procedimiento de rollback
```

Hasta ese momento, `main` debe mantenerse como la rama estable y desplegable, pero el despliegue automatico queda pendiente.

## 23.10 Flujo recomendado del proyecto

Durante el desarrollo normal:

```text
feature/*
    |
    | Pull Request
    v
develop
    |
    v
CI
    |
    v
Integracion del equipo
```

Cuando exista una version estable:

```text
develop
    |
    | Pull Request
    v
main
    |
    v
CI
    |
    v
Version preparada para despliegue
```

En una fase posterior:

```text
main
    |
    v
CI
    |
    v
CD
    |
    v
Produccion
```

---

# 24. Despliegue esperado

El despliegue final debe considerar:

```text
Internet
   |
   v
Nginx
   |
   +------> Angular
   |
   +------> Spring Boot
                |
                +------> PostgreSQL + PostGIS
                |
                +------> MinIO
```

Nginx actua como:

- proxy inverso.
- punto de entrada.
- servidor de archivos estaticos Angular.
- configuracion de cache.
- compresion.
- TLS/HTTPS.

---

# 25. Respaldos y operacion

La entrega final debe contemplar:

- `pg_dump` diario.
- retencion de respaldos.
- respaldo de MinIO.
- prueba de restauracion.
- logs estructurados.
- rotacion.
- health/metrics internos.
- NTP.
- TLS.
- HSTS.

Estas tareas forman parte del despliegue y operacion final y todavia deben completarse/documentarse.

---

# 26. Estado actual de la base del proyecto

Actualmente esta preparado y probado:

```text
[OK] Java 21 LTS
[OK] Spring Boot
[OK] Maven
[OK] PostgreSQL 16
[OK] PostGIS
[OK] pg_trgm
[OK] uuid-ossp
[OK] Flyway
[OK] Migraciones V1, V2 y V3
[OK] MinIO
[OK] Docker Compose
[OK] Angular 21
[OK] TypeScript strict
[OK] Standalone Components
[OK] Signals
[OK] Reactive Forms
[OK] PrimeNG
[OK] Transloco / textos centralizados
[OK] PWA
[OK] Angular Service Worker
[OK] Dexie
[OK] IndexedDB
[OK] MapLibre GL
[OK] Nginx con headers de seguridad base
[OK] Login
[OK] JWT
[OK] Refresh token
[OK] OTP
[OK] 2FA
[OK] Recuperacion de contrasena
[OK] Roles base
[OK] Estructura por dominios
[OK] Testcontainers smoke test PostgreSQL/PostGIS + Flyway
[OK] MapStruct con UserMapper de referencia
[OK] CI backend con GitHub Actions
[OK] CI frontend con GitHub Actions
```

Pendiente de implementar:

```text
[PENDIENTE] Inventario completo
[PENDIENTE] Formulario dinamico SIECA
[PENDIENTE] Inspecciones completas
[PENDIENTE] Revision academica
[PENDIENTE] Indice de Condicion
[PENDIENTE] Archivos completos con MinIO
[PENDIENTE] Foro
[PENDIENTE] Mantenimiento
[PENDIENTE] Cursos y asignaciones
[PENDIENTE] Profesional externo
[PENDIENTE] Auditoria completa
[PENDIENTE] Sincronizacion offline completa
[PENDIENTE] Pruebas completas y cobertura requerida
[PENDIENTE] Despliegue final
[PENDIENTE] Respaldos/restauracion
[PENDIENTE] CD / despliegue automatico
```

---

# 27. Documentacion adicional

Cada integrante debe leer:

```text
README.md
sgp-api/README.md
sgp-client/README.md
docs/CUMPLIMIENTO_ENUNCIADO.md
docs/adr/
```

La idea es que cualquier integrante nuevo pueda entender:

- que tecnologia se utiliza.
- por que se utiliza.
- como esta organizado el proyecto.
- que existe actualmente.
- que hay en la base de datos.
- que migraciones ya se ejecutaron.
- como levantar el sistema.
- donde debe trabajar.
- que reglas no debe romper.
- que falta implementar.

Antes de modificar codigo, cada integrante debe revisar este README y el README especifico del backend o frontend correspondiente.
