# Guía de traspaso para la implementación de producción

## 1. Contexto y responsabilidades

La historia de despliegue se dividió en dos responsabilidades:

- **Staging y CI/CD:** imágenes, GHCR, Compose de staging, despliegue automático
  desde `develop`, validaciones y documentación.
- **Producción:** infraestructura AWS de producción, Compose independiente,
  selección y despliegue manual de versiones aprobadas, HTTPS sobre IP pública,
  seguridad y validación final.

Este documento no implementa producción. Registra contratos y decisiones que el
responsable de producción debe conservar o coordinar antes de modificarlos.

## 2. Estado del traspaso

| Componente | Estado |
|---|---|
| Dockerfiles reutilizables | Implementados y construidos localmente. |
| Compose de staging | Implementado y probado localmente. |
| Workflow CI, publicación y staging | Validaciones y publicación ejecutadas correctamente; despliegue AWS deshabilitado. |
| Imágenes en GHCR | Backend y frontend publicados como paquetes privados. |
| Convención de versiones | Definida y utilizada en la primera publicación. |
| Infraestructura AWS de staging | Pendiente. |
| Infraestructura y Compose de producción | Responsabilidad del segundo integrante. |

## 3. Decisiones que deben conservarse

1. AWS EC2 con Linux y Docker Compose es la plataforma acordada.
2. EC2 solo descarga y ejecuta imágenes; no compila Angular ni Java.
3. Backend y frontend son imágenes independientes y reutilizables entre ambientes.
4. La configuración y secretos se inyectan externamente.
5. `develop` publica y despliega staging; no despliega producción.
6. Producción selecciona manualmente una versión aprobada.
7. No se depende exclusivamente de la etiqueta `latest`.
8. Nginx es el punto de entrada y proxy de `/api/`.
9. Backend, PostgreSQL y almacenamiento S3 no deben publicarse directamente.
10. Bases, redes, volúmenes y secretos deben ser independientes por ambiente.
11. No se usan datos reales de producción en staging.
12. No deben modificarse migraciones Flyway ya aplicadas.

La decisión de usar una o dos EC2 sigue pendiente. El Compose de producción debe
poder coexistir con staging sin compartir recursos, incluso si inicialmente se
elige una sola instancia.

## 4. Componentes entregados por staging

- Dockerfiles multietapa con usuario sin privilegios y healthcheck.
- Nginx con SPA, proxy inverso, gzip, encabezados de seguridad y caché controlada.
- Perfil `application-staging.properties`.
- Perfil `application-prod.properties` ya existente, que desactiva OpenAPI y
  exige cookie segura.
- Compose de staging con redes y volúmenes explícitos.
- Plantilla de variables sin secretos.
- Preparación de Amazon Linux y despliegue con rollback.
- Inicialización segura de PostgreSQL con usuarios separados.

Estos elementos sirven de referencia; `infra/staging/compose.yml` no debe
utilizarse directamente como Compose de producción.

## 5. Imágenes y convención de versiones

Repositorios reservados:

```text
ghcr.io/kennysalazar/sgp-api
ghcr.io/kennysalazar/sgp-client
```

Convención definida:

- `sha-<SHA completo>`: etiqueta inmutable asociada al commit de `develop`.
- `develop`: etiqueta móvil para identificar la última integración exitosa.
- `<imagen>@sha256:<digest>`: referencia que debe usarse en un despliegue.

Los paquetes ya existen en GHCR y permanecen privados. El responsable de staging
tiene acceso de lectura, pero no permisos administrativos sobre los paquetes de
la cuenta `KennySalazar`; por ello la EC2 de staging se autenticará con un
Personal Access Token (classic) limitado a `read:packages`.

Producción no debe reutilizar el token de staging. Su responsable deberá obtener
una credencial independiente de una cuenta con lectura sobre ambos paquetes,
definir su vigencia y mantenerla fuera del repositorio, Compose, archivos de
entorno, User Data y logs. Si posteriormente el propietario vuelve públicos los
paquetes o adopta una identidad compartida, el cambio debe coordinarse y
documentarse; no es una decisión tomada por staging.

Producción no debe reconstruir una versión. Debe usar exactamente los digests
aprobados en staging, de modo que el binario validado y el desplegado sean el
mismo.

## 6. Archivos compartidos y específicos

| Archivo | Uso en producción |
|---|---|
| `sgp-api/Dockerfile` | Compartido sin cambios específicos por ambiente. |
| `sgp-client/Dockerfile` | Compartido sin cambios específicos por ambiente. |
| `sgp-client/nginx.conf` | Compartido; el TLS puede terminar en una capa Nginx separada o en una adaptación coordinada. |
| `infra/postgres/init/00-create-app-user.sh` | Reutilizable en una base nueva. Solo se ejecuta al inicializar el volumen. |
| `sgp-api/src/main/resources/application-prod.properties` | Perfil obligatorio de producción. |
| `infra/staging/compose.yml` | Referencia de diseño; no es la configuración de producción. |
| `infra/staging/deploy.sh` | Referencia para comprobaciones; producción será manual. |

El responsable deberá crear la configuración de producción en una ubicación
independiente. Su ruta y nombres de red/volumen aún no están definidos; deben
elegirse explícitamente sin reutilizar ningún nombre `sgp_staging_*`.

## 7. Variables requeridas en producción

Producción requiere las mismas categorías de configuración, pero con valores y
controles independientes:

| Variable o grupo | Requisito de producción |
|---|---|
| Imágenes backend/frontend | Digests aprobados, no `latest`. |
| `POSTGRES_DB` | Base exclusiva de producción. |
| `POSTGRES_MIGRATOR_USER/PASSWORD` | Credencial exclusiva para Flyway. |
| `POSTGRES_APP_USER/PASSWORD` | Usuario de aplicación distinto y con menor privilegio. |
| `MINIO_ROOT_USER/PASSWORD` | Credenciales exclusivas; confirmar primero la integración real. |
| `SECRET_KEY_JWT` | Secreto aleatorio de al menos 32 caracteres, distinto de staging. |
| Expiraciones JWT/OTP | Revisar y aprobar valores antes del despliegue. |
| `INITIAL_ADMIN_EMAIL/PASSWORD` | Identidad controlada; contraseña de 10–72 caracteres con letras y números. |
| `MAIL_*` | Cuenta SMTP de producción y remitente autorizado. |
| `INVITACION_URL_ACTIVACION` | URL pública HTTPS definitiva. |
| `CORS_ALLOWED_ORIGINS` | Únicamente el origen HTTPS público de producción. |
| `SPRING_PROFILES_ACTIVE` | `prod`. |
| `REFRESH_COOKIE_SECURE` | `true`; el perfil `prod` ya lo establece. |
| `OPENAPI_PUBLIC` | `false`; el perfil `prod` ya lo establece. |

Los secretos no deben incluirse en Compose, AMI, User Data, logs, GitHub Actions
ni archivos versionados. El mecanismo definitivo de almacenamiento de secretos
en producción sigue pendiente de decisión.

## 8. Descarga manual desde GHCR

Mientras los paquetes sean privados, cree un Personal Access Token (classic) con
solo `read:packages` y una fecha de expiración. En una sesión interactiva de la
instancia, autentique al mismo usuario que ejecutará el despliegue:

```bash
sudo -i
install -d -m 0700 /root/.docker
read -rsp "GHCR token: " GHCR_TOKEN; printf '\n'
printf '%s' "${GHCR_TOKEN}" | docker login ghcr.io \
  --username <USUARIO_GITHUB_DEL_TOKEN> \
  --password-stdin
unset GHCR_TOKEN
chmod 0600 /root/.docker/config.json
exit
```

No escriba el token directamente en el comando porque quedaría en el historial.
Docker lo conserva en `/root/.docker/config.json`; proteja ese archivo como una
credencial. Descargue luego los artefactos aprobados:

```bash
docker pull ghcr.io/kennysalazar/sgp-api@sha256:<digest-aprobado>
docker pull ghcr.io/kennysalazar/sgp-client@sha256:<digest-aprobado>
```

Verifique lo descargado:

```bash
docker image inspect ghcr.io/kennysalazar/sgp-api@sha256:<digest-aprobado>
docker image inspect ghcr.io/kennysalazar/sgp-client@sha256:<digest-aprobado>
```

No usar `docker build` en la EC2 ni sustituir el digest por `develop` durante el
despliegue manual. Antes de que expire el token, reemplácelo mediante otro
`docker login`, verifique una descarga y revoque el anterior. La expiración no
detiene los contenedores activos, pero bloqueará actualizaciones y rollback que
requieran descargar una imagen ausente del host.

## 9. Recomendaciones para Compose de producción

- Definir un nombre de proyecto diferente a `sgp-staging`.
- Definir redes y volúmenes con nombres explícitos que no empiecen por
  `sgp_staging_`.
- Publicar únicamente el punto de entrada HTTPS.
- Mantener backend, PostgreSQL y almacenamiento en redes privadas.
- Usar las imágenes por digest.
- Inyectar todas las variables desde un archivo protegido o un gestor aprobado.
- Incluir healthchecks y `depends_on` por salud.
- Mantener `restart: unless-stopped`, límites de recursos y rotación de logs.
- No montar el código fuente ni usar `build:`.
- Ejecutar Flyway con su usuario separado antes de aceptar tráfico.
- No compartir volúmenes, redes ni archivos de entorno con staging.

Los nombres y rutas definitivos pertenecen a la implementación de producción y
no se fijan en esta guía antes de que exista su diseño.

## 10. Nginx, HTTPS y seguridad

Producción debe exponer HTTPS sobre una IP pública sin comprar dominio. El
responsable debe confirmar una autoridad certificadora que emita certificados
válidos con la IP como Subject Alternative Name, su método de validación y la
renovación automática.

Requisitos mínimos:

- Redirigir HTTP a HTTPS si se mantiene el puerto 80 para validación o redirección.
- Permitir públicamente solo los puertos estrictamente necesarios.
- Mantener `/api/` detrás de Nginx.
- Configurar `INVITACION_URL_ACTIVACION` y CORS con la URL HTTPS exacta.
- Conservar cookies `Secure`, `HttpOnly` y `SameSite=Strict`.
- No exponer Actuator, OpenAPI, PostgreSQL ni el almacenamiento.
- Probar renovación del certificado y documentar su operación.

La solución de certificados no ha sido elegida y no debe considerarse cerrada.

## 11. Procedimiento manual propuesto

1. Obtener aprobación del commit y digests desplegados satisfactoriamente en
   staging.
2. Registrar los digests en la evidencia de liberación.
3. Preparar o actualizar el archivo externo de configuración de producción.
4. Descargar ambas imágenes por digest.
5. Ejecutar `docker compose config --quiet`.
6. Confirmar capacidad de disco, memoria y respaldo manual previo cuando ya
   exista un procedimiento aprobado.
7. Ejecutar `docker compose pull` y `docker compose up -d --wait`.
8. Comprobar PostgreSQL, Flyway, backend, frontend y proxy.
9. Ejecutar pruebas funcionales mínimas mediante HTTPS.
10. Registrar commit, digests, fecha, operador y resultado.
11. Ante un fallo, restaurar el Compose y los digests anteriores sin eliminar
    volúmenes.

Este procedimiento es una propuesta de interfaz; debe adaptarse y probarse al
crear el Compose real de producción.

## 12. Validaciones requeridas al responsable de producción

- Compose válido y sin `build:`.
- Imágenes coincidentes con los digests aprobados.
- HTTPS válido accediendo por IP pública.
- Renovación del certificado comprobada.
- Solo Nginx accesible desde Internet.
- PostgreSQL/PostGIS saludable y persistente.
- Todas las migraciones Flyway aplicadas una sola vez y sin errores.
- Backend saludable y OpenAPI no público.
- Frontend y `/api/v1` accesibles mediante Nginx.
- CORS restringido al origen de producción.
- Cookies seguras y flujo de renovación funcional.
- SMTP e invitaciones utilizando la URL de producción.
- Redes, volúmenes, credenciales y datos separados de staging.
- Reinicio de instancia y recuperación automática de servicios.
- Logs rotados sin secretos.
- Procedimiento de rollback probado sin eliminar datos.

## 13. Errores y limitaciones conocidos

- Las imágenes oficiales comunitarias de MinIO fueron retiradas. Staging usa
  `docker.io/pgsty/silo:RELEASE.2026-09-16T00-00-00Z` fijada por digest. Silo
  conserva compatibilidad S3 y formato de datos, pero es un
  [fork comunitario independiente](https://github.com/pgsty/silo).
- El backend todavía no consume MinIO/Silo.
- Brotli no está configurado; Nginx usa gzip.
- No existe retención centralizada de logs por 90 días.
- No se han implementado respaldos ni restauraciones.
- No se ha medido aún el consumo real en EC2.
- Los paquetes GHCR son privados. Cada ambiente necesita una credencial de
  lectura cuya vigencia y dependencia de una cuenta personal deben operarse.
- El PAT classic con `read:packages` puede leer los paquetes a los que tenga
  acceso su cuenta; no es una credencial limitada exclusivamente a este proyecto.

## 14. Decisiones pendientes

- Una EC2 compartida o dos instancias separadas.
- Región, VPC, tipo y capacidad definitiva de la instancia de producción.
- Solución de certificado HTTPS válido para IP pública.
- Mecanismo de secretos de producción.
- Cuenta responsable, vigencia y rotación de la credencial GHCR de producción.
- Nombres y rutas del Compose de producción.
- Retención y centralización de logs.
- Estrategia futura de respaldos y restauración.
- Permanencia de Silo cuando se implemente el almacenamiento de archivos.

Estas decisiones no deben cerrarse unilateralmente si cambian contratos
compartidos con staging.

## 15. Criterios de aceptación de producción

La parte de producción se considera completa cuando:

1. Existe infraestructura AWS autorizada y documentada.
2. Existe un Compose independiente que consume imágenes aprobadas de GHCR.
3. El despliegue manual por digest es reproducible y tiene rollback.
4. La aplicación es accesible mediante HTTPS válido sobre IP pública.
5. Solo el proxy está expuesto y los servicios internos permanecen aislados.
6. La persistencia, Flyway, salud, reinicio y separación de ambientes están
   demostrados.
7. Variables y secretos están documentados sin publicar valores reales.
8. Las limitaciones y tareas pendientes, incluidos respaldos, están registradas.
9. La documentación contiene evidencia real de las pruebas ejecutadas.

## Referencias compartidas

- [Retirada de la imagen de MinIO en Quay](https://access.redhat.com/solutions/7148629).
- [Repositorio y contrato de compatibilidad de Silo](https://github.com/pgsty/silo).
- [Release de Silo fijada por staging](https://github.com/pgsty/silo/releases/tag/RELEASE.2026-09-16T00-00-00Z).

