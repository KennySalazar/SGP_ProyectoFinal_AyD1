# Instalación y configuración del entorno

Esta guía configura el entorno local con la base de datos en Docker y la API y el cliente ejecutados localmente. Se requieren JDK 21, Maven, Node.js/npm y Docker Compose.

## 1. Configurar variables

Desde la raíz del proyecto, cree el archivo local de configuración del backend:

```bash
cp sgp-api/.env.example sgp-api/.env
```

Revise el archivo y establezca credenciales válidas para correo SMTP y administrador inicial. La plantilla configura PostgreSQL local y valores de desarrollo. Sustituya la clave JWT por una cadena aleatoria de al menos 32 caracteres. No comparta ni versiona el archivo `.env`.

Las variables esenciales para iniciar el backend incluyen URL, usuario y contraseña de la base; usuario y contraseña de migración; clave JWT; correo y contraseña SMTP; y credenciales para crear el administrador inicial. `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, CORS y otros parámetros tienen valores predeterminados o pueden ajustarse en el mismo archivo.

## 2. Iniciar PostgreSQL/PostGIS

En la raíz del proyecto:

```bash
docker compose up -d postgres
docker compose ps
```

Espere a que PostgreSQL aparezca saludable. Compose crea la base `sgp_db`, el usuario de aplicación y el usuario migrador. Los datos locales permanecen en el volumen `postgres_data`.

## 3. Iniciar la API

En una terminal:

```bash
cd sgp-api
mvn clean spring-boot:run
```

Flyway aplica las migraciones y Hibernate valida el esquema al iniciar. La API queda en `http://localhost:8090`; compruebe su estado en `/actuator/health`. La documentación OpenAPI está disponible cuando la configuración local permite exponerla.

## 4. Iniciar Angular

En otra terminal:

```bash
cd sgp-client
npm ci
npm start
```

Abra `http://localhost:4200`. El servidor de desarrollo reenvía las solicitudes `/api` hacia la API local, por lo que no requiere configurar una URL distinta del backend para este flujo.

## Alternativa con Compose

Para construir y ejecutar los servicios de la aplicación definidos en Compose:

```bash
docker compose up -d --build
docker compose ps
```

La aplicación web queda disponible en `http://localhost:8088`. Para detener contenedores sin borrar datos use `docker compose down`; evite `docker compose down -v` si necesita conservar la base local.

## Configuración de archivos

El backend usa almacenamiento local de forma predeterminada. La configuración también admite proveedor S3 con región y bucket, y endpoint explícito para MinIO con credenciales de desarrollo. Asegure que el bucket exista y sea privado cuando se use este proveedor. No se necesita MinIO para iniciar el flujo local predeterminado.
