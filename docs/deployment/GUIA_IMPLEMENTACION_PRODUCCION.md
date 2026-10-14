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
| Dockerfiles reutilizables | Preparados y publicados para AMD64/ARM64 desde `develop`; variante ARM64 comprobada en EC2. |
| Compose de staging | Adaptado para RDS y S3 externos; ejecución real validada en AWS. |
| Workflow CI, publicación y staging | Validaciones, publicación y primer despliegue automático completados correctamente. |
| Imágenes en GHCR | Referencias `develop` accesibles; descarga e inspección `linux/arm64` verificadas desde EC2. |
| Convención de versiones | Definida y utilizada en la primera publicación. |
| Infraestructura AWS compartida | Red privada y DB Subnet Group confirmados; `sgp-rds-postgres` operativo para staging a través del backend y Flyway. |
| Infraestructura exclusiva de staging | Security Group, bucket, rol IAM y EC2 creados; Session Manager, GHCR, OIDC, SSM y despliegue verificados. |
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
10. Bases RDS, buckets S3, Security Groups, redes Compose, roles IAM y secretos
    deben ser independientes por ambiente. La VPC seleccionada sí puede ser
    compartida.
11. No se usan datos reales de producción en staging.
12. No deben modificarse migraciones Flyway ya aplicadas.
13. Las imágenes deben publicarse para `linux/amd64` y `linux/arm64`; las EC2
    objetivo usan ARM64 (`t4g`).
14. La RDS compartida será PostgreSQL 16 Single-AZ para reducir costos; esta
    decisión acepta explícitamente no disponer de conmutación automática de AZ.
15. No se comprará un dominio ni se creará una zona DNS pública. Producción debe
    exponer HTTPS válido directamente sobre una IPv4 pública estable.

La nueva arquitectura asigna una EC2 independiente a cada ambiente: `t4g.micro`
para staging y `t4g.small` para producción. Ambas usarán una RDS compartida, pero
con bases y credenciales separadas. La EC2, los Security Groups y el Compose de
producción no se crean desde esta parte de la historia de usuario.

### Infraestructura compartida ya seleccionada

Staging comenzó la preparación en `us-east-1` sobre la VPC predeterminada
`vpc-04c3c96ee06abd1bc`, CIDR `172.31.0.0/16`, con resolución DNS y nombres de
host DNS habilitados. Existen seis subredes, una en cada zona desde
`us-east-1a` hasta `us-east-1f`; esas subredes predeterminadas no se modificaron.
Staging utiliza la subred pública `subnet-0745affbda7ec4bb1`, CIDR
`172.31.64.0/20`, en `us-east-1f`.

Para RDS se creó una red dedicada dentro de la VPC: la tabla
`sgp-rds-private-rt` (`rtb-0bd589b3134333454`) solo contiene
`172.31.0.0/16 → local`, sin Internet Gateway ni NAT. Sus subredes son
`sgp-rds-private-1a` (`subnet-0e261924496a742c8`, `172.31.96.0/24`,
`us-east-1a`) y `sgp-rds-private-1b` (`subnet-0e6f2f94648deb3f9`,
`172.31.97.0/24`, `us-east-1b`). Ambas están asociadas con la tabla privada. El
DB Subnet Group `sgp-rds-subnet-group` contiene las dos subredes.

La presencia de dos zonas en el DB Subnet Group es un requisito de cobertura de
red y no implica Multi-AZ. La RDS compartida se desplegó como Single-AZ; el
responsable de producción debe considerar la falta de failover automático y no
cambiar esta decisión sin revisar costos y coordinar con staging.

AWS aceptó la creación de `sgp-rds-postgres` con PostgreSQL 16,
`db.t4g.micro`, 20 GiB gp3, cifrado `aws/rds`, Single-AZ, IPv4, puerto 5432 y
acceso público deshabilitado. Usa `sgp-rds-subnet-group` y `sgp-rds-sg`, tiene
protección contra eliminación, actualizaciones secundarias automáticas, la CA
`rds-ca-rsa2048-g1` y las etiquetas `Project=SGP` y `Environment=shared`. Se
observó `Disponible` en `us-east-1b`, con el endpoint privado
`sgp-rds-postgres.c8hwo202k9fb.us-east-1.rds.amazonaws.com:5432`. Una prueba
desde la EC2 de staging confirmó conectividad TCP al puerto 5432. El primer
despliegue confirmó además autenticación de aplicación, Flyway y acceso al
esquema. Sigue pendiente una prueba administrativa independiente con
`sslmode=verify-full` y una consulta directa de extensiones.

No se indicó una base inicial. Staging preparó únicamente `sgp_staging`; no
debe crear `sgp_prod`. El responsable de producción deberá preparar después
`sgp_prod` y sus usuarios independientes mediante el procedimiento compartido.
Las credenciales administrativas son autoadministradas y deben transferirse por
un medio seguro, nunca mediante Git, esta guía o logs.

La conexión deberá verificar la identidad de RDS mediante `sslmode=verify-full`
y el bundle oficial de CA. El usuario maestro real debe confirmarse; no debe
asumirse que sea `postgres`. Session Manager interactivo y el alcance TCP desde
EC2 ya se probaron; la conexión autenticada con `verify-full` y el túnel de
puertos para clientes locales siguen pendientes.

### Excepción compartida de respaldos

La retención original de 30 días fue rechazada por la limitación del plan
gratuito de la cuenta. Para aceptar la solicitud, RDS quedó temporalmente con
respaldos automáticos de 1 día, copia de etiquetas habilitada y sin replicación
a otra región. El equipo decidió no migrar la cuenta al plan de pago.

Esta excepción no elimina el requisito de 30 días. Tampoco están implementados
los 12 respaldos mensuales mediante AWS Backup ni la prueba de restauración. El
responsable de producción debe tratar los tres puntos como incumplimientos
pendientes y coordinarlos con el propietario de la cuenta.

Se creó `sgp-staging-ec2-sg` (`sg-0e617972b3eedeb73`) con entrada pública solo
para TCP 80 y 443, y con salida general. Los puertos 8088, 8090 y 5432 no están
expuestos. También se creó `sgp-rds-sg`
(`sg-0ab8d04a2ef4d3db8`), que acepta TCP 5432 desde el Security Group de EC2 de
staging, no desde un CIDR público. Producción debe usar un Security Group de EC2
propio y agregarlo como un origen independiente; no debe reutilizar
`sgp-staging-ec2-sg`.

El bucket privado de staging es `sgp-staging-archivos-ayd1-2026-gt01`. Tiene
ACL deshabilitadas, bloqueo completo de acceso público y SSE-S3; permanece vacío,
sin política personalizada, CORS, versionado ni Object Lock. Producción deberá
crear otro bucket con nombre, etiquetas, política IAM y configuración propias;
no debe reutilizar este bucket ni asumir que deshabilitar el versionado será la
decisión definitiva para producción.

El rol `sgp-staging-ec2-role` confía en EC2 y actualmente solo tiene
`AmazonSSMManagedInstanceCore`; ya está asociado y permitió administrar staging
mediante Session Manager. El acceso S3 futuro requerirá una política
adicional limitada al bucket de staging. Producción debe crear su propio rol de
instancia, limitado a su bucket. El rol OIDC usado por GitHub Actions es una
identidad diferente y su uso real ya fue validado. La confianza comprobada usa
el sujeto inmutable
`repo:KennySalazar@99755600/SGP_ProyectoFinal_AyD1@1386435373:ref:refs/heads/develop`;
no debe degradarse al formato basado únicamente en nombres.

La instancia `sgp-staging-ec2` (`i-0385c95df8cd7909d`) ejecuta Amazon Linux
2023 ARM64 sobre `t4g.micro`, con 20 GiB gp3 cifrados. Está en `us-east-1f`, usa
`sgp-staging-ec2-sg`, obtuvo automáticamente la IPv4 pública dinámica
`3.235.197.36` y no tiene Elastic IP. Sus comprobaciones `3/3` y Session Manager
fueron satisfactorios. La preparación inicial instaló Docker, Docker Compose y
el cliente PostgreSQL 16, configuró swap y creó los directorios operativos. El
primer despliegue automático dejó frontend y backend saludables en la revisión
`488dae1ea7278eefc5f39e557eaadde4c07d00f9`. RDS reside en `us-east-1b`; la diferencia de
zona no impide la conectividad, pero debe considerarse al evaluar costo de
transferencia y latencia para producción.

Staging conservará la IPv4 dinámica `3.235.197.36` sin Elastic IP por el plazo
académico hasta el 28 de octubre de 2026. Puede cambiar al detener e iniciar la
instancia, por lo que cualquier certificado emitido para ella debe reemplazarse
si cambia. Esta decisión acotada a staging no resuelve la necesidad de una
dirección pública operativamente estable para producción.

Existe el par ED25519 `sgp-staging-key`, cuya clave privada permanece fuera del
repositorio. No se ha probado SSH ni se abrió una regla de entrada para utilizarlo;
SSM continúa siendo el mecanismo administrativo elegido.

Al momento de esta actualización la cuenta reportó USD 120 en créditos. No fue
posible crear alertas de presupuesto: el propietario de la cuenta asumió esa
responsabilidad y el consumo debe revisarse manualmente. Este saldo puede cambiar
y no debe considerarse una garantía de capacidad futura.

## 4. Componentes entregados por staging

- Dockerfiles multietapa, multi-arquitectura, con usuario sin privilegios y
  healthcheck. Las etapas de compilación usan la plataforma nativa del runner
  para no repetir Maven y Angular por cada arquitectura.
- Nginx con SPA, proxy inverso, gzip, encabezados de seguridad y caché controlada.
- Perfil `application-staging.properties`.
- Perfil `application-prod.properties` ya existente, que desactiva OpenAPI y
  exige cookie segura.
- Compose de staging con una red explícita y sin volúmenes de datos locales.
- Plantilla de variables sin secretos.
- Preparación de Amazon Linux y despliegue con rollback.
- Preparación parametrizada de RDS con extensiones y usuarios separados.
- Plantillas y scripts para Nginx del host, certificado IP de corta duración y
  renovación con systemd. Su instalación real en staging continúa pendiente.

El bootstrap se ejecutó exitosamente en staging el 11 de octubre de 2026. En
Amazon Linux 2023 no debe instalarse el paquete `curl` encima de `curl-minimal`:
la AMI ya proporciona el comando requerido y ambos paquetes entran en conflicto.
Esta corrección debe conservarse al reutilizar el script para producción.

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

Cada etiqueta publicada desde `develop` contiene un índice OCI con variantes
`linux/amd64` y `linux/arm64`. Una EC2 `t4g` selecciona
automáticamente la variante ARM64 sin cambiar el nombre ni la etiqueta. El digest
aprobado debe corresponder al índice multi-arquitectura, no a una variante
aislada.

En staging, `docker manifest inspect`, la descarga explícita con `--platform
linux/arm64` y la inspección local confirmaron `linux/arm64` para backend y
frontend. El problema histórico `no matching manifest for linux/arm64` quedó
resuelto al integrar el build multi-arquitectura. Producción debe conservar esta
configuración y aprobar únicamente digests cuyo índice OCI sea compatible con la
arquitectura de su EC2.

Los paquetes ya existen en GHCR y permanecen privados. El responsable de staging
tiene acceso de lectura, pero no permisos administrativos sobre los paquetes de
la cuenta `KennySalazar`; por ello la EC2 de staging se autenticó con un
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
| `infra/rds/prepare-database.sql` | Reutilizable para preparar la base RDS de producción con nombres y secretos propios; se ejecuta una vez como administrador. |
| `infra/postgres/init/00-create-app-user.sh` | Exclusivo del PostgreSQL contenedorizado de desarrollo local. |
| `sgp-api/src/main/resources/application-prod.properties` | Perfil obligatorio de producción. |
| `infra/staging/compose.yml` | Referencia de diseño; no es la configuración de producción. |
| `infra/staging/deploy.sh` | Referencia para comprobaciones; producción será manual. |

El responsable deberá crear la configuración de producción en una ubicación
independiente. Su ruta y nombre de red aún no están definidos; debe elegirse
explícitamente sin reutilizar `sgp_staging_proxy`.

## 7. Variables requeridas en producción

Producción requiere las mismas categorías de configuración, pero con valores y
controles independientes:

| Variable o grupo | Requisito de producción |
|---|---|
| Imágenes backend/frontend | Digests aprobados, no `latest`. |
| `DATABASE_URL` | JDBC hacia el endpoint RDS privado y la base exclusiva de producción; debe exigir TLS. |
| `MIGRATION_DATABASE_USERNAME/PASSWORD` | Credencial exclusiva para Flyway. |
| `DATABASE_USERNAME/PASSWORD` | Usuario de aplicación distinto y con menor privilegio. |
| S3 | El backend aún no define variables de bucket. Cuando exista la integración, usar el bucket de producción y el rol IAM de su EC2, nunca claves estáticas. |
| `SECRET_KEY_JWT` | Secreto aleatorio de al menos 32 caracteres, distinto de staging. |
| Expiraciones JWT/OTP | Revisar y aprobar valores antes del despliegue. |
| `INITIAL_ADMIN_EMAIL/PASSWORD` | Identidad controlada; contraseña de 10–72 caracteres con letras y números. |
| `MAIL_*` | Cuenta SMTP de producción y remitente autorizado. |
| `INVITACION_URL_ACTIVACION` | URL HTTPS con la IP pública definitiva; por ejemplo, `https://<IP_PUBLICA>/activar-cuenta`. |
| `CORS_ALLOWED_ORIGINS` | Únicamente el origen `https://<IP_PUBLICA>` de producción. |
| `SPRING_PROFILES_ACTIVE` | `prod`. |
| `REFRESH_COOKIE_SECURE` | `true`; el perfil `prod` ya lo establece. |
| `OPENAPI_PUBLIC` | `false`; el perfil `prod` ya lo establece. |

Los secretos no deben incluirse en Compose, AMI, User Data, logs, GitHub Actions
ni archivos versionados. El mecanismo definitivo de almacenamiento de secretos
en producción sigue pendiente de decisión.

Staging usa temporalmente un destino SMTP ficticio en `127.0.0.1:2525`; no hay
un servidor de correo operativo y los flujos de OTP, invitación, activación y
recuperación de contraseña no están validados. Producción no debe copiar esos
valores: necesita un proveedor SMTP real, credenciales propias, un remitente
autorizado y pruebas completas de los flujos dependientes del correo.

Antes del primer despliegue, el responsable de producción deberá ejecutar
`infra/rds/prepare-database.sql` como administrador de RDS, usando nombres y
contraseñas exclusivos de producción mediante las variables
`SGP_DATABASE_NAME`, `SGP_MIGRATOR_USER`, `SGP_MIGRATOR_PASSWORD`,
`SGP_APP_USER` y `SGP_APP_PASSWORD`. El procedimiento detallado está en la guía
de staging; no debe reutilizar sus valores. El archivo crea las extensiones con
el administrador y deja a Flyway con permisos DDL sin concederle
`rds_superuser`.

En PostgreSQL 16 el administrador debe poder hacer `SET ROLE` al propietario
indicado en `CREATE DATABASE ... OWNER` y al rol indicado por los privilegios
predeterminados. El script resuelve este requisito con una membresía temporal al
migrador, ejecuta como migrador las concesiones de esquema y
`ALTER DEFAULT PRIVILEGES`, y revoca las capacidades `SET` e `INHERIT` al
finalizar. PostgreSQL 16 puede conservar el `ADMIN OPTION` entregado
automáticamente a quien creó el rol; esto no permite asumirlo. Producción debe
conservar este mecanismo y comprobar que su administrador tenga `CREATEROLE` y
`CREATEDB`; no debe conceder esos atributos al migrador ni a la aplicación.

La EC2 de producción debe recibir un rol de instancia que solo permita las
operaciones necesarias sobre su bucket. Debe exigir IMDSv2 y usar hop limit `2`
para cargas dentro de Docker. No se deben crear `AWS_ACCESS_KEY_ID` ni
`AWS_SECRET_ACCESS_KEY` para la aplicación.

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
- Definir una red con nombre explícito que no empiece por `sgp_staging_`.
- Publicar únicamente el punto de entrada HTTPS.
- Mantener el backend sin puertos publicados, RDS en subredes privadas y S3 sin
  acceso público.
- Usar las imágenes por digest.
- Inyectar todas las variables desde un archivo protegido o un gestor aprobado.
- Incluir healthchecks para frontend y backend. RDS y S3 no son servicios del
  Compose y no deben agregarse a `depends_on`.
- Mantener `restart: unless-stopped`, límites de recursos y rotación de logs.
- No montar el código fuente ni usar `build:`.
- Ejecutar Flyway con su usuario separado antes de aceptar tráfico.
- No compartir redes Compose, Security Groups de EC2, bases, buckets, roles IAM
  ni archivos de entorno con staging. La VPC y la instancia RDS sí pueden ser
  compartidas según la decisión vigente.

Los nombres y rutas definitivos pertenecen a la implementación de producción y
no se fijan en esta guía antes de que exista su diseño.

## 10. Nginx, HTTPS y seguridad

Producción debe exponer HTTPS sobre una IPv4 pública estable. La decisión de no
comprar dominio es definitiva para este proyecto: no debe presupuestarse un
registro de dominio ni una zona hospedada de DNS público. La resolución DNS
interna habilitada en la VPC no contradice esta decisión.

Staging preparó como referencia un Nginx en el host y certificados IP de Let's
Encrypt con perfil `shortlived`. Usa Certbot 5.8.0 mediante su imagen oficial,
ACME `webroot` y un temporizador systemd dos veces al día. El Nginx del
contenedor permanece en loopback. Esta solución todavía debe probarse en AWS;
producción no debe declararla validada ni reutilizar certificados, rutas o
archivos de staging.

El responsable de producción debe confirmar primero su IPv4 pública estable y
adaptar una copia independiente de la configuración. Los certificados IP tienen
vigencia de 160 horas, por lo que la renovación automática y su monitoreo son
obligatorios. Un certificado autofirmado no cumple el criterio de HTTPS válido
para usuarios finales.

Requisitos mínimos:

- Redirigir HTTP a HTTPS si se mantiene el puerto 80 para validación o redirección.
- Permitir públicamente solo los puertos estrictamente necesarios.
- Mantener `/api/` detrás de Nginx.
- Configurar `INVITACION_URL_ACTIVACION` y CORS con la URL HTTPS exacta.
- Conservar cookies `Secure`, `HttpOnly` y `SameSite=Strict`.
- No exponer Actuator, OpenAPI, PostgreSQL ni el almacenamiento.
- Exigir IMDSv2 con hop limit `2` para que el SDK ejecutado dentro de Docker
  pueda obtener las credenciales temporales del rol de instancia.
- Probar renovación del certificado y documentar su operación.

La referencia técnica de certificado ya existe en staging, pero la asignación
de una IPv4 pública estable y su adaptación independiente a producción no han
sido cerradas. La compra de dominio sí está fuera de consideración.

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
    ni modificar datos en RDS o S3.

Este procedimiento es una propuesta de interfaz; debe adaptarse y probarse al
crear el Compose real de producción.

## 12. Validaciones requeridas al responsable de producción

- Compose válido y sin `build:`.
- Imágenes coincidentes con los digests aprobados.
- HTTPS válido accediendo por IP pública.
- Renovación del certificado comprobada.
- Solo Nginx accesible desde Internet.
- RDS PostgreSQL/PostGIS accesible solo desde las EC2 autorizadas y con base y
  credenciales exclusivas de producción.
- Todas las migraciones Flyway aplicadas una sola vez y sin errores.
- Backend saludable y OpenAPI no público.
- Frontend y `/api/v1` accesibles mediante Nginx.
- CORS restringido al origen de producción.
- Cookies seguras y flujo de renovación funcional.
- SMTP e invitaciones utilizando la URL de producción.
- Redes, base RDS, bucket S3, rol IAM, credenciales y datos separados de staging.
- Reinicio de instancia y recuperación automática de servicios.
- Logs rotados sin secretos.
- Procedimiento de rollback probado sin eliminar datos.

## 13. Errores y limitaciones conocidos

- El backend todavía no implementa la interfaz de almacenamiento de objetos ni
  consume S3. No deben inventarse variables ni declararse validado hasta que esa
  funcionalidad exista.
- La verificación completa del certificado RDS con `sslmode=verify-full` y el
  bundle CA debe incorporarse y probarse al preparar la infraestructura.
- Brotli no está configurado; Nginx usa gzip.
- No existe retención centralizada de logs por 90 días.
- RDS tiene únicamente 1 día de retención automática por la limitación del plan
  gratuito. No se cumplen todavía los 30 días, los 12 respaldos mensuales ni la
  prueba de restauración.
- No se ha medido aún el consumo real en EC2.
- La EC2 de staging y RDS están en zonas distintas; debe medirse el impacto de
  transferencia inter-AZ antes de repetir esa distribución en producción.
- La publicación multi-arquitectura y la descarga ARM64 ya fueron verificadas;
  producción debe conservar el índice OCI y desplegar un digest aprobado.
- Los paquetes GHCR son privados. Cada ambiente necesita una credencial de
  lectura cuya vigencia y dependencia de una cuenta personal deben operarse.
- El PAT classic con `read:packages` puede leer los paquetes a los que tenga
  acceso su cuenta; no es una credencial limitada exclusivamente a este proyecto.
- El SMTP de staging es ficticio; no existe evidencia de envío de correos ni de
  los flujos de autenticación que dependen de ellos.
- HTTPS para staging está preparado en el repositorio, pero todavía no se ha
  instalado, emitido ni probado en EC2.

## 14. Decisiones pendientes

- Validación administrativa de TLS `verify-full` y consulta directa de
  extensiones sobre `sgp-rds-postgres`; Flyway ya funcionó desde el backend.
- Capacidad definitiva de disco para las dos EC2 separadas.
- Asignación de IPv4 pública estable y solución de certificado HTTPS válido para
  esa IP, sin comprar dominio.
- Mecanismo de secretos de producción.
- Cuenta responsable, vigencia y rotación de la credencial GHCR de producción.
- Nombres y rutas del Compose de producción.
- Retención y centralización de logs.
- Estrategia futura de respaldos y restauración.
- Nombres de las variables S3 que expondrá el backend cuando se implemente
  `AlmacenamientoArchivos`.

Estas decisiones no deben cerrarse unilateralmente si cambian contratos
compartidos con staging.

## 15. Criterios de aceptación de producción

La parte de producción se considera completa cuando:

1. Existe infraestructura AWS autorizada y documentada.
2. Existe un Compose independiente que consume imágenes aprobadas de GHCR.
3. El despliegue manual por digest es reproducible y tiene rollback.
4. La aplicación es accesible mediante HTTPS válido sobre IP pública.
5. Solo el proxy está expuesto y los servicios internos permanecen aislados.
6. La persistencia en RDS/S3, Flyway, salud, reinicio y separación de ambientes están
   demostrados.
7. Variables y secretos están documentados sin publicar valores reales.
8. Las limitaciones y tareas pendientes, incluidos respaldos, están registradas.
9. La documentación contiene evidencia real de las pruebas ejecutadas.

## Referencias compartidas

- [Extensiones de PostgreSQL en Amazon RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.Extensions.html).
- [Configuración de PostGIS en RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.PostGIS.html).
- [Uso de TLS con RDS PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html).
- [Roles IAM para aplicaciones en EC2](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role-ec2.html).

