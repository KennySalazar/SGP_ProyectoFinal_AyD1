# Despliegue de staging en AWS

## Estado del documento

Esta guía describe la infraestructura de staging implementada en el repositorio y
se actualiza de forma incremental durante la historia de usuario.

**Actualización posterior:** el usuario confirmó la comprobación de `verify-full`
y extensiones, la realización de pruebas funcionales y de correo, y que el
rendimiento es satisfactorio. No se registraron fechas, casos o mediciones.
También informó que ambos entornos funcionaban el 27 de octubre de 2026; no se
registraron los datos del certificado servido. Los inventarios y resultados de
pruebas iniciales de las secciones siguientes conservan su carácter histórico.

| Área | Estado |
|---|---|
| Imágenes reutilizables de backend y frontend | Publicadas desde `develop` para AMD64/ARM64; descarga ARM64 verificada en EC2. |
| Compose aislado de staging | Adaptado para ejecutar solo Nginx/Angular y Spring Boot; RDS y S3 son externos a la EC2. |
| Preparación de Amazon Linux 2023 | Completada exitosamente en la EC2 ARM64 el 11 de octubre de 2026. |
| Workflow de validación, publicación y despliegue | Integrado en `develop`; validación, publicación y primer despliegue completados correctamente. |
| Publicación en GHCR | Referencias multi-arquitectura publicadas; autenticación, manifiestos y descarga ARM64 verificados desde EC2. |
| Despliegue automático desde `develop` | Activo con `STAGING_ENABLED=true`, OIDC y Systems Manager; primera ejecución exitosa. |
| Red AWS | VPC, tabla privada, dos subredes dedicadas y DB Subnet Group de RDS confirmados. |
| Security Groups | EC2 permite públicamente solo TCP 80/443; RDS permite 5432 únicamente desde el grupo de staging. |
| IAM y Systems Manager | Session Manager, confianza OIDC con identificadores inmutables y Run Command verificados. |
| S3 | Bucket privado operativo; permisos de objetos y cargas funcionales verificadas el 27 de octubre de 2026. |
| RDS | Backend y migraciones operativos; el usuario confirmó `verify-full` y extensiones, sin evidencia técnica detallada registrada. |
| EC2 | `sgp-staging-ec2` creada, en ejecución y con sus tres comprobaciones superadas; preparación inicial y autenticación en GHCR completadas. |
| Despliegue real en AWS | Automatización operativa desde `develop`; el despliegue posterior a la integración HTTPS dejó ambos contenedores saludables. |
| HTTPS público | Operativo y validado en `https://3.235.197.36`; certificado IP de Let's Encrypt y comprobación automática de renovación instalados. |

Se crearon los dos Security Groups, el bucket S3, RDS y la EC2 de staging. El rol
de instancia está asociado y el acceso mediante Session Manager funciona. Una
prueba desde EC2 confirmó alcance TCP al endpoint privado de RDS en el puerto
5432. La preparación inicial del servidor finalizó correctamente. GitHub Actions
asumió el rol OIDC, ordenó el despliegue mediante Systems Manager y dejó ambos
contenedores saludables. La primera revisión desplegada fue
`488dae1ea7278eefc5f39e557eaadde4c07d00f9`. Git identifica la integración de
los archivos HTTPS en `develop` con el merge commit `e006c19`; no se dispone de
un identificador de ejecución de GitHub Actions ni de una lectura posterior de
`/opt/sgp/staging/REVISION`, por lo que no se inventa una revisión desplegada más
reciente.

En la EC2 se creó `/etc/sgp/staging/staging.env` con propietario `root` y modo
`0600`. Se verificaron las 12 variables obligatorias: las cinco de PostgreSQL,
`SECRET_KEY_JWT`, las dos del administrador inicial y cuatro variables SMTP. La
comprobación devolvió `[OK]` para todas ellas. No se registran valores sensibles
en esta guía. En la preparación inicial SMTP tenía valores ficticios; el usuario
informó posteriormente que se realizaron pruebas funcionales y de correo, sin
detallar configuración, casos ni resultados.

## Inventario AWS confirmado

Inventario basado en las comprobaciones manuales de AWS y GitHub del 15 de octubre
de 2026, con la actualización S3 e IAM del 27 de octubre:

| Elemento | Valor o estado |
|---|---|
| Región | `us-east-1` (Norte de Virginia). |
| VPC | Predeterminada, `vpc-04c3c96ee06abd1bc`. |
| CIDR de la VPC | `172.31.0.0/16`. |
| DNS de la VPC | Resolución DNS y nombres de host DNS habilitados. |
| Subredes predeterminadas | Seis existentes, distribuidas entre `us-east-1a` y `us-east-1f`; no fueron modificadas. |
| Tabla de rutas privada | `sgp-rds-private-rt` (`rtb-0bd589b3134333454`), no principal y asociada a la VPC seleccionada. |
| Rutas de la tabla privada | Únicamente `172.31.0.0/16 → local`; sin Internet Gateway ni NAT Gateway. |
| Subred privada RDS 1 | `sgp-rds-private-1a` (`subnet-0e261924496a742c8`), `172.31.96.0/24`, en `us-east-1a`. |
| Subred privada RDS 2 | `sgp-rds-private-1b` (`subnet-0e6f2f94648deb3f9`), `172.31.97.0/24`, en `us-east-1b`. |
| Asociación de subredes | Ambas asociadas con `rtb-0bd589b3134333454`. |
| DB Subnet Group | `sgp-rds-subnet-group`, compuesto por ambas subredes y zonas. |
| Instancia RDS | `sgp-rds-postgres`, estado observado `Disponible` en `us-east-1b`. |
| Endpoint RDS | `sgp-rds-postgres.c8hwo202k9fb.us-east-1.rds.amazonaws.com:5432`. |
| Administrador RDS | `postgres`; `rolsuper=false` y `rolcreaterole=true`. El script comprueba además `rolcreatedb` antes de crear la base. |
| Extensiones de staging | El usuario confirmó su verificación; no se registró la consulta SQL ni sus resultados. |
| Instancia EC2 | `sgp-staging-ec2` (`i-0385c95df8cd7909d`), Amazon Linux 2023 ARM64, `t4g.micro`, en ejecución y con comprobaciones `3/3`. |
| Ubicación EC2 | Subred pública `subnet-0745affbda7ec4bb1`, CIDR `172.31.64.0/20`, en `us-east-1f`. |
| Almacenamiento EC2 | 20 GiB gp3 cifrados con `aws/ebs`; se observaron 5.4 GB utilizados, 15 GB disponibles y 27 % de uso. |
| Red de EC2 | IPv4 pública dinámica `3.235.197.36`, IPv4 privada `172.31.64.35` y DNS público `ec2-3-235-197-36.compute-1.amazonaws.com`; no existe Elastic IP. |
| Security Group de EC2 | `sgp-staging-ec2-sg` (`sg-0e617972b3eedeb73`), entrada TCP 80 y 443 desde `0.0.0.0/0`; salida hacia `0.0.0.0/0`. |
| Security Group de RDS | `sgp-rds-sg` (`sg-0ab8d04a2ef4d3db8`), con salida predeterminada. |
| Regla de entrada de RDS | PostgreSQL TCP 5432 desde `sg-0e617972b3eedeb73` (`sgp-staging-ec2-sg`); no usa un CIDR público. |
| Bucket S3 | `sgp-staging-archivos-ayd1-2026-gt01`, propósito general, creado vacío en `us-east-1`. |
| Seguridad del bucket | Bucket owner enforced, ACL deshabilitadas y todas las opciones de bloqueo de acceso público habilitadas. |
| Protección de objetos | SSE-S3 habilitado; versionado, S3 Bucket Key y Object Lock deshabilitados. |
| Etiquetas del bucket | `Project=SGP` y `Environment=staging`. |
| Configuración adicional de S3 | Sin política de bucket personalizada ni CORS en la revisión inicial; las cargas posteriores demuestran que ya no estaba vacío. |
| Rol IAM de EC2 | `sgp-staging-ec2-role`, asociado a la instancia, con confianza en `ec2.amazonaws.com` y políticas `AmazonSSMManagedInstanceCore` y `sgp-staging-s3-objects-policy`. |
| Administración EC2 | Acceso interactivo mediante AWS Systems Manager Session Manager verificado. |
| Par de claves | `sgp-staging-key`, ED25519 y formato privado `.pem`; archivo conservado fuera del repositorio. SSH no probado y sin regla de entrada pública. |
| Acceso S3 desde IAM | `PutObject`, `GetObject` y `DeleteObject` sobre objetos del bucket de staging, verificados el 27 de octubre. |
| Rol OIDC de GitHub Actions | `arn:aws:iam::416086757987:role/sgp-staging-github-actions-role`; asunción y ejecución de SSM verificadas desde `develop`. |
| Nginx del host | Versión `1.30.5`, activo como terminación TLS y proxy hacia `127.0.0.1:8088`; configuración validada con `nginx -t`. |
| Certificado HTTPS | Let's Encrypt para `3.235.197.36`, línea `sgp-staging-ip`, vigente desde `2026-10-15 23:30:54 UTC` hasta `2026-10-22 15:30:53 UTC`. |
| Renovación HTTPS | `sgp-certbot-renew.timer` habilitado, dos comprobaciones diarias; ejecución manual del servicio exitosa, sin renovación porque aún no correspondía. |
| Créditos disponibles | USD 120 al momento de la revisión. |
| Alertas de presupuesto | No fue posible configurarlas; quedan a cargo del propietario de la cuenta y el consumo se revisará manualmente. |

Los USD 120 son una fotografía temporal, no un presupuesto garantizado. Antes
de crear cada recurso con cobro continuo debe volver a revisarse el saldo y
registrarse la fecha de la comprobación.

La EC2 usa la subred pública predeterminada `subnet-0745affbda7ec4bb1` en
`us-east-1f`, con asignación automática de IPv4 pública. Las dos nuevas subredes
de RDS usan CIDR independientes que no se superponen con las seis subredes
predeterminadas. Su aislamiento se basa en la tabla que solo contiene la ruta
local.

El backend ya dispone de configuración externa para el proveedor, región y
bucket. El estado y los requisitos de traspaso se detallan en la
[guía de integración S3](GUIA_INTEGRACION_S3.md).

### Red privada para RDS

Se creó `sgp-rds-private-rt` (`rtb-0bd589b3134333454`) dentro de
`vpc-04c3c96ee06abd1bc`. No es la tabla principal y solo contiene la ruta local
de la VPC:

```text
172.31.0.0/16 -> local
```

La tabla de rutas pública existente también fue revisada, sin modificarla. La
EC2 utiliza `subnet-0745affbda7ec4bb1` (`172.31.64.0/20`) en `us-east-1f`.

La tabla privada no incluye rutas hacia Internet Gateway ni NAT Gateway. Se
crearon dos subredes dedicadas a RDS:

| Nombre | ID | Zona | CIDR |
|---|---|---|---|
| `sgp-rds-private-1a` | `subnet-0e261924496a742c8` | `us-east-1a` | `172.31.96.0/24` |
| `sgp-rds-private-1b` | `subnet-0e6f2f94648deb3f9` | `us-east-1b` | `172.31.97.0/24` |

Ambas subredes están asociadas con `sgp-rds-private-rt`.

El DB Subnet Group `sgp-rds-subnet-group` contiene las dos subredes. El primer
intento falló porque solo se había seleccionado una zona de disponibilidad; la
selección se corrigió agregando `us-east-1a` y `us-east-1b`. Posteriormente se
usó este grupo en la solicitud de creación de RDS aceptada por AWS.

La instancia será PostgreSQL 16 Single-AZ. Las dos zonas del DB Subnet Group
cumplen la cobertura de red exigida por RDS, pero no convierten la instancia en
Multi-AZ ni proporcionan conmutación automática ante la falla de una zona.

### Instancia RDS compartida

AWS completó la creación de la instancia compartida y se observó el estado
`Disponible`. Además de la conectividad TCP inicial, el despliegue real confirmó
que el backend puede autenticarse, ejecutar Flyway, validar el esquema y atender
consultas. El usuario confirmó posteriormente la comprobación administrativa de
`verify-full`, sin registrar la fecha ni la evidencia técnica.

| Propiedad | Configuración solicitada |
|---|---|
| Identificador | `sgp-rds-postgres` |
| Endpoint | `sgp-rds-postgres.c8hwo202k9fb.us-east-1.rds.amazonaws.com` |
| Región y red | `us-east-1`, IPv4, `vpc-04c3c96ee06abd1bc` |
| Zona de disponibilidad | `us-east-1b` |
| Motor | PostgreSQL 16 |
| Disponibilidad | Single-AZ |
| Clase | `db.t4g.micro`, 2 vCPU y 1 GiB de RAM |
| Almacenamiento | 20 GiB gp3, cifrado con la clave administrada `aws/rds` |
| DB Subnet Group | `sgp-rds-subnet-group` |
| Security Group | `sg-0ab8d04a2ef4d3db8` (`sgp-rds-sg`) |
| Acceso y puerto | Sin acceso público; PostgreSQL TCP 5432 |
| Base inicial | Se creó `sgp_staging` durante la preparación; `sgp_prod` no corresponde a staging. |
| Credenciales | Autoadministradas, autenticación mediante contraseña |
| Entidad certificadora | `rds-ca-rsa2048-g1` |
| RDS Proxy | Deshabilitado |
| Supervisión | Database Insights estándar; métricas detalladas habilitadas con retención de siete días; monitorización mejorada deshabilitada |
| Mantenimiento | Actualizaciones secundarias automáticas; ventana sin preferencia |
| Protección | Protección contra eliminación habilitada |
| Etiquetas | `Project=SGP`, `Environment=shared` |

El valor `shared` identifica que la instancia será compartida por staging y
producción. Esta historia solo preparará `sgp_staging`; no debe crear
`sgp_prod`, sus usuarios ni ejecutar migraciones de producción.

Desde la EC2 se abrió correctamente una conexión TCP al endpoint en el puerto
5432 mediante `/dev/tcp`. Después, la ejecución saludable del backend y una
consulta HTTP real confirmaron autenticación de aplicación, Flyway y acceso al
esquema. La prueba TCP por sí sola solo confirmó resolución DNS, enrutamiento y
reglas de red; no sustituye la comprobación administrativa de TLS e inventario
de extensiones.

#### Excepción de retención de respaldos

El diseño de referencia mantiene el requisito de 30 días de respaldos
automáticos de RDS. AWS rechazó esa retención porque supera el máximo permitido
por el plan gratuito de la cuenta. No se autorizó actualizar el plan de
facturación, por lo que la solicitud aceptada usa temporalmente:

```text
The specified backup retention period exceeds the maximum available to free
tier customers. To remove all limitations, upgrade your account plan.
```

- respaldos automáticos habilitados;
- retención de 1 día;
- ventana de respaldos sin preferencia;
- copia de etiquetas a respaldos automáticos e instantáneas habilitada;
- replicación a otra región deshabilitada.

La retención de RDS es complementaria. La [guía de respaldos](RESPALDO_Y_RESTAURACION.md)
documenta volcados diarios y copias mensuales a S3, y una prueba de restauración
en producción el 17 de octubre de 2026. El enunciado no exige AWS Backup ni una
retención de 30 días en RDS.

### Procedimiento de verificación TLS

La conexión administrativa deberá realizarse desde un equipo con alcance de red
a RDS. La EC2 administrada mediante SSM ya demostró alcance TCP y se utilizará
para la siguiente prueba. AWS proporciona el bundle global de CA para verificar
el endpoint:

```bash
curl --fail --show-error --location \
  --output global-bundle.pem \
  https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem

export RDSHOST="sgp-rds-postgres.c8hwo202k9fb.us-east-1.rds.amazonaws.com"

psql \
  "host=${RDSHOST} port=5432 dbname=postgres user=<RDS_ADMIN> sslmode=verify-full sslrootcert=./global-bundle.pem"

unset RDSHOST
```

`<RDS_ADMIN>` debe sustituirse por el usuario maestro configurado realmente; no
se debe asumir que sea `postgres` ni escribir su contraseña en comandos,
documentación o logs. El usuario confirmó la verificación, pero no se dispone de
la fecha, el comando utilizado ni sus resultados detallados.

La conexión de staging a RDS quedó demostrada mediante el backend y Flyway.
La prueba administrativa de `psql` y la configuración TLS del backend son
comprobaciones distintas: la plantilla del backend usa `sslmode=require`, y no
se dispone de evidencia de `verify-full` en el driver JDBC.

También se prevé un túnel de Systems Manager hacia el endpoint para usar un
cliente local como DBeaver. Session Manager interactivo ya funciona, pero el
túnel de puertos y una conexión PostgreSQL autenticada todavía no se han probado.

### Transición de arquitectura

La aclaración recibida el 10 de octubre de 2026 exige almacenar los archivos
fuera del servidor de aplicación. La arquitectura objetivo usa dos EC2 ARM64
separadas, una RDS PostgreSQL/PostGIS compartida por ambientes y buckets S3
independientes. El Compose de staging ya refleja esta separación: PostgreSQL y
MinIO permanecen únicamente en el Compose de desarrollo local.

## Arquitectura de staging

El punto de entrada interno es el Nginx del contenedor. El puerto se publica
únicamente en loopback y se comprobó directamente desde EC2. Un segundo Nginx
`1.30.5` en el host termina TLS en 443, conserva el desafío ACME por HTTP,
redirige el resto de 80 a HTTPS y reenvía a `127.0.0.1:8088`. El backend no
publica puertos en el host. RDS permanece en subredes privadas y el bucket S3
bloquea todo acceso público.

```text
Equipo del proyecto
        |
        | HTTPS 443 (operativo)
        v
Nginx del host + certificado IP
        |
        | proxy a 127.0.0.1:8088
        |
        v
Nginx + Angular ---- red sgp_staging_proxy ---- Spring Boot
                                                   |
                               +-------------------+-------------------+
                               |                                       |
                               v                                       v
                  RDS PostgreSQL/PostGIS privado          S3 privado mediante
                  base y usuarios de staging              rol IAM de la EC2 (*)
```

`(*)` El backend implementa almacenamiento S3 y utiliza la cadena de credenciales
del SDK, incluido el rol de instancia, sin claves estáticas en AWS.

## Archivos implementados

| Archivo | Responsabilidad |
|---|---|
| `sgp-api/Dockerfile` | Construye la API con Java 21 una vez y genera runtimes AMD64/ARM64 sin privilegios. |
| `sgp-client/Dockerfile` | Construye Angular una vez y genera runtimes Nginx AMD64/ARM64 sin privilegios. |
| `sgp-client/nginx.conf` | Sirve la SPA, comprime con gzip y reenvía `/api/` al backend. |
| `sgp-api/src/main/resources/application-staging.properties` | Desactiva OpenAPI público y configura el perfil de staging. |
| `infra/staging/compose.yml` | Define el stack aislado que consume imágenes publicadas. |
| `infra/staging/.env.example` | Plantilla sin credenciales reales. |
| `infra/staging/bootstrap-amazon-linux-2023.sh` | Prepara Amazon Linux 2023 ARM64 o x86_64 con Docker, Compose, SSM y swap. |
| `infra/staging/deploy.sh` | Valida, actualiza y comprueba el stack con bloqueo y rollback. |
| `infra/staging/https/install-https.sh` | Instala Nginx del host, obtiene el certificado IP y activa la renovación. |
| `infra/staging/https/renew-certificate.sh` | Comprueba la IP mediante IMDSv2, renueva con Certbot y recarga Nginx. |
| `infra/staging/https/nginx-*.conf.template` | Configuración ACME, redirección HTTP y proxy HTTPS hacia loopback. |
| `infra/staging/https/sgp-certbot-renew.*` | Unidad y temporizador systemd para comprobar la renovación dos veces al día. |
| `infra/rds/prepare-database.sql` | Prepara una base RDS con extensiones y roles separados, sin incluir nombres ni secretos reales. |
| `infra/postgres/init/00-create-app-user.sh` | Inicializa PostgreSQL únicamente en desarrollo local. |

El `docker-compose.yml` de la raíz continúa siendo el entorno de desarrollo y no
ha sido sustituido por la configuración de staging.

## Requisitos de infraestructura

La infraestructura objetivo asigna una EC2 independiente a staging:

- Región confirmada: `us-east-1`.
- VPC confirmada: VPC predeterminada `vpc-04c3c96ee06abd1bc`, con CIDR
  `172.31.0.0/16` y DNS habilitado.
- Amazon Linux 2023, arquitectura ARM64 (`aarch64`).
- Tipo `t4g.micro` como punto de partida, con el heap de Java limitado y sujeto a
  medición. Si la memoria no es suficiente, cualquier aumento requiere revisar
  primero los créditos y el presupuesto.
- Disco raíz de 20 GiB gp3, cifrado con `aws/ebs`.
- 2 GiB de swap configurados mediante el script de preparación.
- Rol de instancia `sgp-staging-ec2-role`, asociado y probado para acceso
  interactivo con `AmazonSSMManagedInstanceCore`.
- La política `sgp-staging-s3-objects-policy` limita las operaciones de objetos
  al bucket `sgp-staging-archivos-ayd1-2026-gt01`.
- Salida HTTPS hacia AWS, GHCR/Docker Hub y el proveedor SMTP.
- Administración mediante Systems Manager, sin publicar SSH.
- RDS PostgreSQL 16 no pública, en la misma VPC, cuyo Security Group acepte el
  puerto 5432 únicamente desde el Security Group de la EC2 de staging.
- Modalidad Single-AZ, aceptando que no habrá conmutación automática entre zonas.
- Una base `sgp_staging`, un usuario migrador y un usuario de aplicación que no
  se reutilicen en producción.
- Bucket S3 `sgp-staging-archivos-ayd1-2026-gt01`, privado e independiente del
  futuro bucket de producción. En la revisión inicial no tenía CORS ni política
  de bucket personalizada; posteriormente recibió cargas funcionales.
- IMDSv2 obligatorio y salto de respuesta igual a `2`, necesario para que un SDK
  dentro de Docker pueda obtener credenciales temporales del rol de instancia.
- No se comprará un dominio ni se creará una zona DNS pública para el proyecto.
- El Security Group permite TCP 80/443 para el proxy público. Los puertos 8088,
  8090 y 5432 no están publicados.
- HTTPS opera con Nginx del host y un certificado de corta duración válido para
  la IPv4 dinámica. No se usará Elastic IP; un cambio de dirección requiere
  actualizar la configuración y emitir un certificado nuevo.
- No se usarán ALB, NAT Gateway, ECS ni EKS.

La EC2 y su disco están aprovisionados. RDS está `Disponible`; el despliegue
inició el backend, ejecutó Flyway, validó el esquema con Hibernate y atendió una
consulta real de la API. El usuario confirmó posteriormente la comprobación
independiente de TLS, sin evidencia técnica detallada. La imposibilidad de
configurar alertas automáticas obliga a mantener un control manual del saldo.

### EC2 de staging creada

La instancia confirmada tiene la siguiente configuración:

| Propiedad | Valor confirmado |
|---|---|
| Nombre | `sgp-staging-ec2` |
| ID | `i-0385c95df8cd7909d` |
| Sistema y arquitectura | Amazon Linux 2023 ARM64 |
| Tipo | `t4g.micro`, 2 vCPU y 1 GiB de RAM |
| Disco | 20 GiB gp3 cifrados con `aws/ebs` |
| VPC | `vpc-04c3c96ee06abd1bc` |
| Subred | `subnet-0745affbda7ec4bb1`, `172.31.64.0/20`, `us-east-1f` |
| IPv4 pública | Dinámica `3.235.197.36`, sin Elastic IP |
| IPv4 privada | `172.31.64.35` |
| DNS público | `ec2-3-235-197-36.compute-1.amazonaws.com` |
| Security Group | `sgp-staging-ec2-sg` (`sg-0e617972b3eedeb73`) |
| Rol IAM | `sgp-staging-ec2-role` |
| Administración | Session Manager verificado; SSH no probado ni expuesto |
| Estado | En ejecución; comprobaciones `3/3` superadas |

El Security Group permite TCP 80 y 443 desde `0.0.0.0/0`; no expone 8088, 8090
ni 5432. El contenedor Nginx continúa ligado exclusivamente a
`127.0.0.1:8088`; el Nginx del host es el único proceso que atiende los puertos
públicos y reenvía el tráfico al contenedor.

Se decidió conservar la IPv4 pública dinámica por la fecha de entrega del 28 de
octubre de 2026 y no asumir el costo ni la operación de una Elastic IP. La
dirección suele mantenerse mientras la instancia permanece encendida, pero
puede cambiar al detenerla e iniciarla. Un cambio exige actualizar las URLs
públicas y emitir otro certificado para la nueva IP; no se comprará un dominio.

EC2 está en `us-east-1f` y RDS en `us-east-1b`. La comunicación privada funciona,
pero cruza zonas de disponibilidad y puede implicar costo de transferencia y
latencia adicionales. No se trasladará ningún recurso sin una evaluación de
costo e impacto.

## Preparación del servidor

El script está diseñado para ejecutarse una sola vez como `root` en Amazon Linux
2023. Detecta `aarch64` para la EC2 `t4g` objetivo y conserva compatibilidad con
`x86_64`:

```bash
sudo bash /tmp/bootstrap-amazon-linux-2023.sh
```

Realiza las siguientes operaciones:

1. Instala Docker y el cliente PostgreSQL 16. Utiliza el comando `curl` ya
   provisto por `curl-minimal` en Amazon Linux 2023.
2. Verifica y habilita `amazon-ssm-agent` y `chronyd`.
3. Instala Docker Compose `v2.40.3` y verifica su SHA-256.
4. Crea `/opt/sgp/staging` y `/etc/sgp/staging`.
5. Configura `/swapfile` de 2 GiB si no existe.
6. Agrega `ssm-user` al grupo `docker`, si el usuario existe.

La preparación se ejecutó exitosamente en `sgp-staging-ec2` el 11 de octubre de
2026. El primer intento solicitaba el paquete `curl` y falló porque Amazon Linux
2023 ya incluía `curl-minimal`; ambos paquetes entran en conflicto. Se eliminó
`curl` de la instalación de DNF, conservando el comando proporcionado por
`curl-minimal`, y la ejecución posterior completó Docker, Docker Compose,
PostgreSQL 16, SSM, swap y los directorios de staging.

## Directorios del servidor

| Ruta | Contenido | Permisos esperados |
|---|---|---|
| `/opt/sgp/staging` | Compose activo, imágenes seleccionadas, revisión y script de despliegue. | Administrado por `root`. |
| `/etc/sgp/staging/staging.env` | Secretos y configuración de staging. | `0600`, propietario `root`. |

La EC2 no necesita clonar el repositorio ni compilar Java o Angular.

## Imágenes y versiones

Las rutas acordadas son:

```text
ghcr.io/kennysalazar/sgp-api
ghcr.io/kennysalazar/sgp-client
```

El workflow `.github/workflows/ci.yml` publicará solamente desde `develop`:

- `sha-<SHA completo>` como etiqueta inmutable y trazable.
- `develop` como referencia móvil informativa.

El despliegue no utilizará `latest`: recibirá referencias con digest, por
ejemplo `ghcr.io/kennysalazar/sgp-api@sha256:...`. La primera publicación desde
`develop` se completó correctamente. Los paquetes pertenecen a la cuenta
`KennySalazar` y permanecen privados: el integrante que opera staging puede
leerlos, pero no posee permisos administrativos para cambiar su visibilidad.

## Autenticación de la EC2 en GHCR

La EC2 utiliza un Personal Access Token (classic) de una cuenta con acceso a los
paquetes. La autenticación se realizó como `root` desde Session Manager mediante
`docker login ghcr.io` y devolvió `Login Succeeded`. El token no se
incorporó al repositorio, la documentación, `staging.env` ni GitHub Actions.

El token debe conceder únicamente `read:packages`, tener fecha de expiración y
no incluir `write:packages` ni `delete:packages`. Es una credencial de operación
del servidor: tampoco debe guardarse en User Data o logs.

Después de preparar la instancia, abra una sesión interactiva de Systems Manager
y autentique al usuario `root`, que es el usuario con el que Run Command ejecuta
el despliegue:

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

El valor escrito con `read -s` no se muestra ni se incorpora al historial. Docker
guarda la credencial en `/root/.docker/config.json`; el archivo debe permanecer
propiedad de `root` y con modo `0600`. Esta configuración no se transfiere mediante
el workflow.

Compruebe el acceso usando un digest publicado, no la etiqueta móvil:

```bash
sudo docker pull \
  ghcr.io/kennysalazar/sgp-api@sha256:<digest-publicado>
```

Para rotar el token, cree otro con el mismo permiso, repita `docker login`, pruebe
un `docker pull` por digest y solo entonces revoque el anterior. La expiración no
detiene contenedores en ejecución, pero impide descargar imágenes en el siguiente
despliegue. Debe registrarse externamente una fecha de renovación sin registrar el
valor del token.

### Resolución del problema ARM64

La primera publicación de `develop` contenía solo AMD64 y los `docker pull
--platform linux/arm64` fallaban con `no matching manifest for linux/arm64`.
Después de integrar el build multi-arquitectura, GitHub Actions publicó de nuevo:

```text
ghcr.io/kennysalazar/sgp-api:develop
ghcr.io/kennysalazar/sgp-client:develop
```

Desde la EC2 se descargaron correctamente ambas referencias con:

```bash
sudo docker pull --platform linux/arm64 ghcr.io/kennysalazar/sgp-api:develop
sudo docker pull --platform linux/arm64 ghcr.io/kennysalazar/sgp-client:develop
```

La inspección local confirmó `linux/arm64` para backend y frontend. Esto valida
publicación, autenticación GHCR, acceso desde EC2 y compatibilidad Graviton. No es
necesario cambiar la arquitectura de la instancia ni volver al build exclusivo
AMD64. El despliegue continúa consumiendo digests inmutables producidos por el
workflow, no la etiqueta móvil `develop`.

## GitHub Actions y GHCR

El workflow reutiliza las validaciones existentes y aplica el siguiente flujo:

```text
Pull Request -> backend + frontend

push a develop -> backend + frontend
                         |
                         +-> publicar API en GHCR -----+
                         +-> publicar cliente en GHCR -+-> desplegar staging
```

Los Pull Requests, incluidos los provenientes de forks, reciben únicamente
`contents: read`; no publican imágenes ni solicitan credenciales AWS. Los jobs de
publicación se ejecutan después de que backend y frontend finalicen correctamente
y reciben solamente `packages: write`. El job de despliegue recibe
`id-token: write` para OIDC y no usa claves AWS permanentes.

Las acciones de terceros están fijadas a commits inmutables. Buildx usa cachés
separadas para cada imagen, genera SBOM y procedencia, y publica un único
manifiesto con `linux/amd64` y `linux/arm64`. QEMU se registra antes de Buildx y
su imagen `binfmt` está fijada por digest. Las etapas Maven y Angular usan
`BUILDPLATFORM`, por lo que se ejecutan una vez en la arquitectura nativa del
runner; las etapas finales producen los runtimes de ambas arquitecturas.

El job no depende de un GitHub Environment. Configure todas las entradas en
**Settings → Secrets and variables → Actions → Repository variables**:

| Variable | Nivel | Estado inicial | Propósito |
|---|---|---|---|
| `STAGING_ENABLED` | Repositorio | Configurada: `true` | Interruptor del job; el despliegue automático está activo. |
| `AWS_REGION` | Repositorio | Configurada: `us-east-1` | Región de la instancia y del rol. |
| `AWS_ROLE_ARN` | Repositorio | Configurada: `arn:aws:iam::416086757987:role/sgp-staging-github-actions-role` | Rol asumido mediante OIDC. |
| `STAGING_INSTANCE_ID` | Repositorio | Configurada: `i-0385c95df8cd7909d` | Instancia administrada por SSM. |

Las variables anteriores son identificadores de infraestructura, no
credenciales. No deben agregarse access keys, contraseñas ni tokens como
variables de repositorio. Para suspender nuevos despliegues sin retirar OIDC,
cambie `STAGING_ENABLED` a `false`; esto no detiene las imágenes ya ejecutadas.

La publicación en GHCR no requiere GitHub Secrets: utiliza el `GITHUB_TOKEN`
efímero. La descarga desde la EC2 es una operación diferente y utiliza el PAT de
solo lectura instalado manualmente en Docker. AWS usa OIDC y tampoco requiere
claves permanentes en GitHub. El primer intento falló en
`Configure temporary AWS credentials` con
`Not authorized to perform sts:AssumeRoleWithWebIdentity`. La confianza basada
únicamente en el nombre del repositorio no coincidió con el sujeto emitido. Se
corrigió y validó el sujeto inmutable:

```text
repo:KennySalazar@99755600/SGP_ProyectoFinal_AyD1@1386435373:ref:refs/heads/develop
```

No debe restaurarse el formato anterior
`repo:KennySalazar/SGP_ProyectoFinal_AyD1:ref:refs/heads/develop`. La política
mantiene permisos limitados a enviar `AWS-RunShellScript` a la instancia de
staging, consultar el resultado y cancelar el comando en caso de timeout.

La protección que antes habría aportado el Environment se reemplaza de forma
explícita por tres controles: el workflow solo recibe eventos `push` de
`develop`, el job vuelve a comprobar `github.ref == 'refs/heads/develop'` y la
confianza de AWS acepta únicamente el sujeto OIDC inmutable de esa misma
referencia. Esta adaptación no agrega claves permanentes ni cambia el despliegue
mediante SSM.

Cada job vuelve a consultar el SHA actual de `develop` antes de publicar o
desplegar. La publicación por imagen está serializada, el despliegue tiene una
concurrencia exclusiva y el servidor utiliza `flock`. Con estas tres protecciones
se evitan despliegues simultáneos y revisiones antiguas fuera de orden.

## Variables de entorno

La plantilla es `infra/staging/.env.example`. El archivo real debe existir solo
en `/etc/sgp/staging/staging.env` y nunca debe registrarse en Git.

El archivo ya existe en la EC2 con propietario `root` y modo `0600`. Una
comprobación devolvió `[OK]` para `DATABASE_URL`, `DATABASE_USERNAME`,
`DATABASE_PASSWORD`, `MIGRATION_DATABASE_USERNAME`,
`MIGRATION_DATABASE_PASSWORD`, `SECRET_KEY_JWT`, `INITIAL_ADMIN_EMAIL`,
`INITIAL_ADMIN_PASSWORD`, `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` y
`MAIL_FROM`. Los valores reales permanecen fuera de Git y de esta documentación.

| Variable | Servicio | Obligatoria | Propósito / ejemplo seguro |
|---|---|---:|---|
| `BACKEND_IMAGE` | Compose | Sí, inyectada | Digest de `ghcr.io/kennysalazar/sgp-api`; el despliegue la escribe en `images.env`. |
| `FRONTEND_IMAGE` | Compose | Sí, inyectada | Digest de `ghcr.io/kennysalazar/sgp-client`. |
| `STAGING_HTTP_PORT` | Nginx | No | Puerto loopback; `8088`. |
| `DATABASE_URL` | Backend/Flyway | Sí | JDBC hacia el endpoint privado y base de staging, terminada en `?sslmode=require`. |
| `DATABASE_USERNAME` | Backend | Sí | Usuario de mínimo privilegio; `sgp_staging_app`. |
| `DATABASE_PASSWORD` | Backend | Sí | Secreto hexadecimal aleatorio del usuario de aplicación. |
| `MIGRATION_DATABASE_USERNAME` | Flyway | Sí | Propietario de migraciones; `sgp_staging_migrator`. |
| `MIGRATION_DATABASE_PASSWORD` | Flyway | Sí | Secreto distinto al del usuario de aplicación. |
| `SECRET_KEY_JWT` | Backend | Sí | Clave JWT de al menos 32 caracteres. |
| `ACCESS_EXPIRATION_TIME_JWT` | Backend | No | Vigencia de acceso en ms; `900000`. |
| `REFRESH_EXPIRATION_TIME_JWT` | Backend | No | Vigencia de renovación en ms; `604800000`. |
| `INITIAL_ADMIN_EMAIL` | Backend | Sí | Administrador inicial exclusivo de staging. |
| `INITIAL_ADMIN_PASSWORD` | Backend | Sí | Entre 10 y 72 caracteres, con letras y números. |
| `MAIL_HOST` | Backend | Sí | Host SMTP; `smtp.example.com`. |
| `MAIL_PORT` | Backend | No | Puerto SMTP; `587`. |
| `MAIL_USERNAME` | Backend | Sí | Cuenta SMTP de staging. |
| `MAIL_PASSWORD` | Backend | Sí | Credencial o contraseña de aplicación SMTP. |
| `MAIL_FROM` | Backend | Sí | Remitente de correos de staging. |
| `MAIL_FROM_NAME` | Backend | No | Nombre visible; `SGP CUNOC - Staging`. |
| `OTP_LENGTH` | Backend | No | Longitud OTP; `6`. |
| `OTP_EXPIRATION_MINUTES` | Backend | No | Vigencia OTP; `10`. |
| `OTP_MAX_ATTEMPTS` | Backend | No | Intentos permitidos; `5`. |
| `OTP_RESEND_COOLDOWN_SECONDS` | Backend | No | Espera para reenvío; `60`. |
| `INVITACION_VIGENCIA_HORAS` | Backend | No | Vigencia de invitación; `72`. |
| `INVITACION_URL_ACTIVACION` | Backend | No | URL pública exacta; `https://3.235.197.36/activar-cuenta`. |
| `REFRESH_COOKIE_SECURE` | Backend | No | `true`; impide enviar la cookie de refresh mediante HTTP. |
| `CORS_ALLOWED_ORIGINS` | Backend | No | Origen público exacto; `https://3.235.197.36`. |
| `JAVA_TOOL_OPTIONS` | Backend | No | Límites de JVM definidos en la plantilla. |

### SMTP temporal sin servicio de correo

Para las primeras pruebas de infraestructura se configuró temporalmente un
destino SMTP ficticio:

```dotenv
MAIL_HOST=127.0.0.1
MAIL_PORT=2525
MAIL_USERNAME=no-reply@example.invalid
MAIL_PASSWORD=SMTP_NO_CONFIGURADO
MAIL_FROM=no-reply@example.invalid
```

En la configuración inicial no había un servidor SMTP escuchando en esa dirección
y no se esperaba que los correos se enviaran. Esa configuración permitía completar el contrato de variables,
pero no valida correo, OTP, invitaciones, activación de cuentas, recuperación de
contraseñas ni ningún flujo de autenticación dependiente del correo. El usuario
informó posteriormente que se realizaron pruebas de correo; no se registraron
los casos ni la configuración empleada. El marcador `SMTP_NO_CONFIGURADO` no
debe reutilizarse en producción.

En staging, `SPRING_PROFILES_ACTIVE=staging`, `OPENAPI_PUBLIC=false` y
`REFRESH_COOKIE_SECURE=true` están definidos por Compose. El perfil mantiene
este último valor configurable desde el exterior, con `true` como valor seguro
predeterminado. El túnel SSM sigue disponible para diagnóstico, pero los flujos
de autenticación del navegador deben validarse mediante HTTPS público.

Para generar secretos que no requieran escapar caracteres especiales en el
archivo de entorno puede utilizarse:

```bash
openssl rand -hex 32
```

No se definen `AWS_ACCESS_KEY_ID` ni `AWS_SECRET_ACCESS_KEY` para la aplicación
en AWS. El SDK utiliza credenciales temporales del rol IAM de la EC2; las
propiedades disponibles se describen en la [guía S3](GUIA_INTEGRACION_S3.md).

## Preparación de la base RDS

Antes del primer despliegue se prepararon la base y los roles de staging. El
siguiente procedimiento queda como referencia reproducible y de recuperación;
no debe repetirse sobre la base existente sin revisar primero su estado y contar
con autorización. Copie temporalmente `infra/rds/prepare-database.sql` a un
equipo con acceso de red, por ejemplo la EC2 administrada mediante SSM, y
ejecútelo como `postgres` con el bundle oficial:

```bash
export SGP_DATABASE_NAME=sgp_staging
export SGP_MIGRATOR_USER=sgp_staging_migrator
read -rsp "Clave del migrador: " SGP_MIGRATOR_PASSWORD; printf '\n'
export SGP_APP_USER=sgp_staging_app
read -rsp "Clave de aplicacion: " SGP_APP_PASSWORD; printf '\n'
read -rsp "Clave del administrador RDS: " PGPASSWORD; printf '\n'
export SGP_DATABASE_NAME SGP_MIGRATOR_USER SGP_MIGRATOR_PASSWORD
export SGP_APP_USER SGP_APP_PASSWORD
export PGPASSWORD

psql \
  "host=sgp-rds-postgres.c8hwo202k9fb.us-east-1.rds.amazonaws.com port=5432 dbname=postgres user=postgres sslmode=verify-full sslrootcert=<RUTA_BUNDLE_CA>" \
  --file prepare-database.sql

unset SGP_DATABASE_NAME SGP_MIGRATOR_USER SGP_MIGRATOR_PASSWORD
unset SGP_APP_USER SGP_APP_PASSWORD PGPASSWORD
```

Sustituya `<RUTA_BUNDLE_CA>` por la ruta absoluta del bundle descargado desde el
trust store oficial de RDS. Las claves se leen sin mostrarse y no deben incluirse
en el historial, el repositorio o la salida de Session Manager.

El archivo valida identificadores, roles separados y los atributos `CREATEROLE`
y `CREATEDB` del administrador. PostgreSQL 16 exige que quien ejecuta
`CREATE DATABASE ... OWNER` pueda hacer `SET ROLE` al propietario. Por ello el
script concede temporalmente `sgp_staging_migrator` a `postgres`, crea la base y
revoca inmediatamente las capacidades temporales para asumir o heredar el rol.

Después se conecta a la base, abre una transacción, vuelve a conceder la
membresía temporal y crea `postgis`, `pg_trgm` y `uuid-ossp` con el administrador.
Las concesiones de esquema y `ALTER DEFAULT PRIVILEGES` se ejecutan mediante
`SET ROLE sgp_staging_migrator`; así los privilegios predeterminados se aplican a
los objetos que Flyway creará realmente. Al finalizar se ejecutan `RESET ROLE` y
`REVOKE`, por lo que `postgres` deja de poder ejecutar `SET ROLE` o heredar sus
privilegios. PostgreSQL 16 puede conservar para el creador del rol una membresía
administrativa con `ADMIN OPTION`, `SET=false` e `INHERIT=false`; esta capacidad
permite administrar el rol, pero no asumirlo ni acceder como migrador.

El migrador conserva DDL sobre `public` sin recibir `rds_superuser`; la cuenta de
aplicación recibe `CONNECT`, `USAGE` de esquema y DML sobre tablas y secuencias
creadas por el migrador. En una reejecución solo se actualizan objetos existentes
que pertenezcan al migrador, evitando conceder DML sobre tablas internas de
PostGIS. El responsable de producción deberá ejecutar el archivo posteriormente
y por separado para `sgp_prod`, usando otros nombres y secretos. Staging no debe
preparar esa base.

Si la ejecución se interrumpe específicamente entre el primer `GRANT` temporal y
su `REVOKE`, revise la membresía y elimínela antes de reintentar:

```sql
SELECT pg_has_role('postgres', 'sgp_staging_migrator', 'SET');
REVOKE sgp_staging_migrator FROM postgres;
```

La primera migración también contiene `CREATE EXTENSION IF NOT EXISTS`; al estar
las extensiones ya instaladas, Flyway puede ejecutarla sin elevar privilegios.
El arranque exitoso de Spring Boot confirmó que los roles y el esquema son
utilizables por la aplicación y que Flyway completó sus migraciones. No se
conservó evidencia detallada de una ejecución administrativa independiente con
`sslmode=verify-full` ni de una consulta de versiones de extensiones; el usuario
confirmó posteriormente ambas comprobaciones.

## Docker Compose

| Servicio | Exposición | Persistencia | Salud |
|---|---|---|---|
| `nginx` | `127.0.0.1:8088` | Imagen inmutable | `GET /healthz`. |
| `backend` | Solo red `sgp_staging_proxy` | Sin datos locales | Actuator interno; incluye conectividad de base. |

La única red de Compose es `sgp_staging_proxy`. Staging no crea volúmenes: la
persistencia reside en RDS y S3, fuera del ciclo de vida de los contenedores.
Eliminar o recrear los contenedores no elimina datos persistentes.

Los servicios utilizan `restart: unless-stopped`, límites de memoria/procesos y
el driver `json-file` con cinco archivos de 10 MiB. Esta rotación protege el
disco, pero no satisface por sí sola un requisito de retención de 90 días ni
convierte todos los logs de aplicación a JSON estructurado.

## Despliegue

El workflow implementado, cuando `STAGING_ENABLED=true`, invoca en el servidor:

```bash
sudo /opt/sgp/staging/deploy.sh \
  /opt/sgp/staging/compose.candidate.yml \
  ghcr.io/kennysalazar/sgp-api@sha256:<digest> \
  ghcr.io/kennysalazar/sgp-client@sha256:<digest> \
  <sha-git-de-40-caracteres>
```

El script:

1. Rechaza imágenes fuera de los repositorios permitidos o sin digest.
2. Usa `flock` para impedir despliegues simultáneos.
3. Valida Compose antes de reemplazar la configuración activa.
4. Conserva la configuración anterior para rollback.
5. Descarga las imágenes usando la autenticación GHCR de `root` y ejecuta
   `docker compose up --wait`.
6. Comprueba Nginx, la API y Actuator. Spring Boot solo queda saludable después
   de conectarse a RDS, ejecutar Flyway y validar el esquema con Hibernate.
7. Registra el SHA desplegado en `/opt/sgp/staging/REVISION`.

La publicación de imágenes y el despliegue son jobs independientes para que
un fallo de AWS no obligue a reconstruirlas.

La primera ejecución completa finalizó correctamente para la revisión
`488dae1ea7278eefc5f39e557eaadde4c07d00f9`: las validaciones, ambas
publicaciones y `Deploy staging` terminaron en `SUCCESS`. Systems Manager aplicó
los digests publicados y `REVISION` conserva el SHA desplegado.

## Acceso privado propuesto

Con Session Manager Plugin instalado en el equipo del integrante:

```bash
aws ssm start-session \
  --target i-0385c95df8cd7909d \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["8088"],"localPortNumber":["8088"]}'
```

Mientras la sesión esté abierta, la aplicación se accederá en
`http://localhost:8088`. El identificador y la región ya están confirmados; la
ejecución desde un equipo local, sus permisos y el Session Manager Plugin siguen
pendientes. El acceso interactivo desde la consola sí fue verificado, pero el
comando de port forwarding anterior aún no se ha ejecutado.

## HTTPS público mediante la IP

### Diseño y vigencia del certificado

La solución implementada usa el Nginx del sistema operativo como terminación TLS.
Este proceso escucha 80/443 y reenvía a `127.0.0.1:8088`; el Nginx de Angular y
el backend conservan su aislamiento actual. El directorio ACME queda disponible
por HTTP y todo el resto se redirige a HTTPS. No se versionan claves privadas ni
certificados.

Let's Encrypt ofrece certificados IP de perfil `shortlived` con vigencia de 160
horas. El flujo `webroot` para IP requiere Certbot 5.4 o posterior y el plugin de
Nginx no instala automáticamente certificados IP. Amazon Linux 2023 ofrece una
versión anterior, por lo que los scripts usan la imagen oficial 5.8.0 fijada
también por el digest del índice multi-arquitectura
`sha256:f70ad0adbb7e117f0fe42a63c553f28ea451edabc0148757b6efcd9735acaa20`,
que incluye ARM64. Nginx sí se instala desde DNF.

La renovación se comprueba dos veces al día mediante systemd. Certbot intenta
renovar los certificados de menos de diez días cuando han consumido la mitad de
su vigencia. Tras una ejecución correcta, el script valida y recarga Nginx sin
detener los contenedores. No se habilita HSTS mientras staging dependa de una IP
dinámica y certificados de corta duración.

La instalación real se completó con Nginx `1.30.5` y la imagen
`certbot/certbot:v5.8.0` fijada por digest. Let's Encrypt emitió el certificado
de la línea `sgp-staging-ip` para `3.235.197.36` y lo almacenó en:

```text
/etc/letsencrypt/live/sgp-staging-ip/fullchain.pem
/etc/letsencrypt/live/sgp-staging-ip/privkey.pem
```

La vigencia comprobada es `notBefore=Oct 15 23:30:54 2026 GMT` y
`notAfter=Oct 22 15:30:53 2026 GMT`. Los certificados y la clave privada solo
existen en EC2; no forman parte del repositorio.

### Configuración aplicada al backend

Desde Session Manager, edite el archivo protegido sin imprimirlo en la consola:

```bash
sudoedit /etc/sgp/staging/staging.env
```

Se verificó que contiene, además de las variables obligatorias:

```dotenv
REFRESH_COOKIE_SECURE=true
CORS_ALLOWED_ORIGINS=https://3.235.197.36
INVITACION_URL_ACTIVACION=https://3.235.197.36/activar-cuenta
```

Compruebe solo presencia y permisos, nunca valores sensibles:

```bash
sudo stat --format='Permisos: %a Propietario: %U' \
  /etc/sgp/staging/staging.env
sudo awk -F= '
  /^(REFRESH_COOKIE_SECURE|CORS_ALLOWED_ORIGINS|INVITACION_URL_ACTIVACION)=/ {
    print "[OK] " $1
  }
' /etc/sgp/staging/staging.env
```

El archivo conserva propietario `root` y modo `0600`. La configuración de
staging acepta encabezados reenviados y mantiene la cookie
de refresh con `Secure`, `HttpOnly` y `SameSite=Strict`. El Nginx del contenedor
preserva `X-Forwarded-Proto` y `X-Forwarded-Port` recibidos del proxy del host.
Un despliegue posterior sigue validando el servicio directamente por loopback;
el HTTPS del host no modifica el procedimiento OIDC/SSM.

### Procedimiento de instalación reproducible

Los pasos siguientes son el procedimiento que se ejecutó desde Session Manager y
quedan como referencia de recuperación. Los archivos se descargaron desde un
commit aprobado, se copiaron a `/opt/sgp/staging/https/` y se instalaron con
propietario `root` y permisos controlados. No deben repetirse sobre la instancia
operativa sin revisar su estado y contar con autorización:

```bash
HTTPS_REF=<SHA_APROBADO_EN_DEVELOP>
HTTPS_TMP_DIR=$(mktemp -d)

curl --fail --show-error --location \
  "https://github.com/KennySalazar/SGP_ProyectoFinal_AyD1/archive/${HTTPS_REF}.tar.gz" \
  --output "${HTTPS_TMP_DIR}/sgp.tar.gz"
tar --extract --gzip --file "${HTTPS_TMP_DIR}/sgp.tar.gz" \
  --directory "${HTTPS_TMP_DIR}"

sudo install -d -m 0755 /opt/sgp/staging/https
sudo cp -a \
  "${HTTPS_TMP_DIR}"/SGP_ProyectoFinal_AyD1-*/infra/staging/https/. \
  /opt/sgp/staging/https/
sudo chown -R root:root /opt/sgp/staging/https
```

La instalación comprobó que los puertos 80/443 estaban libres, obtuvo la IP con
IMDSv2 y ejecutó el instalador con un correo operativo para avisos ACME:

```bash
IMDS_TOKEN=$(curl --fail --silent --show-error --request PUT \
  --header 'X-aws-ec2-metadata-token-ttl-seconds: 60' \
  http://169.254.169.254/latest/api/token)
PUBLIC_IP=$(curl --fail --silent --show-error \
  --header "X-aws-ec2-metadata-token: ${IMDS_TOKEN}" \
  http://169.254.169.254/latest/meta-data/public-ipv4)

sudo /opt/sgp/staging/https/install-https.sh \
  "${PUBLIC_IP}" '<CORREO_OPERATIVO>'
```

El script comprueba primero `/healthz` por loopback, vuelve a consultar IMDSv2,
rechaza una IP distinta, instala Nginx, publica temporalmente solo el desafío
HTTP, descarga la imagen fijada de Certbot 5.8.0, solicita el certificado con
`--preferred-profile shortlived --ip-address`, activa el proxy y habilita el
temporizador. Una repetición con la misma IP reutiliza un certificado vigente y
con SAN correcto para evitar emisiones innecesarias; un cambio de IP fuerza su
reemplazo. Si falla, restaura la configuración Nginx anterior. La renovación
vuelve a descargar la misma imagen por digest únicamente si fue eliminada del
host. El correo y los archivos privados de `/etc/letsencrypt` no deben
registrarse.

### Comprobaciones posteriores en EC2

```bash
sudo nginx -t
sudo systemctl status nginx --no-pager
sudo systemctl status sgp-certbot-renew.timer --no-pager
sudo systemctl list-timers sgp-certbot-renew.timer --no-pager

curl --fail --head http://3.235.197.36/
curl --fail https://3.235.197.36/healthz
curl --fail https://3.235.197.36/
curl --fail 'https://3.235.197.36/api/v1/puentes?size=1'

echo | openssl s_client \
  -connect 3.235.197.36:443 \
  -servername 3.235.197.36 2>/dev/null \
  | openssl x509 -noout -issuer -subject -dates -ext subjectAltName
```

Las pruebas reales devolvieron `301 Moved Permanently` con
`Location: https://3.235.197.36/` para HTTP y `200` para `/`, `/healthz` y
`/api/v1/puentes?size=1` mediante HTTPS. También se confirmó el acceso desde un
navegador externo. El frontend, la API y la cadena de proxy quedaron validados;
los flujos completos de autenticación, OTP y recuperación de contraseña no se
han probado y no deben inferirse de estas respuestas.

Para probar el mecanismo de renovación sin forzar una emisión:

```bash
sudo systemctl start sgp-certbot-renew.service
sudo systemctl status sgp-certbot-renew.service --no-pager
sudo journalctl -u sgp-certbot-renew.service --since '24 hours ago' --no-pager
```

La unidad se ejecutó manualmente y terminó correctamente. Certbot procesó
`/etc/letsencrypt/renewal/sgp-staging-ip.conf`, informó que el certificado aún no
debía renovarse, no intentó una emisión y `nginx -t` siguió siendo satisfactorio.
Después, `/healthz` continuó devolviendo HTTP 200 por HTTPS. Esto valida el
mecanismo de comprobación y recarga, no una renovación efectiva; observar una
renovación antes de la expiración es seguimiento operativo pendiente. Para
revisar la vigencia almacenada:

```bash
sudo openssl x509 \
  -in /etc/letsencrypt/live/sgp-staging-ip/fullchain.pem \
  -noout -issuer -subject -dates -ext subjectAltName
```

### Cambio de IP pública

El temporizador compara la IP guardada con IMDSv2. Si EC2 cambia de dirección,
la renovación falla de forma visible en `journalctl` en vez de solicitar un
certificado incoherente. El procedimiento de recuperación es:

1. Obtener la nueva IP mediante IMDSv2 y confirmar que 80/443 continúan
   permitidos en el Security Group.
2. Actualizar `CORS_ALLOWED_ORIGINS` e `INVITACION_URL_ACTIVACION` en
   `/etc/sgp/staging/staging.env`; conservar `REFRESH_COOKIE_SECURE=true`.
3. Actualizar también los ejemplos y valores predeterminados del repositorio en
   un cambio revisado, porque la IP anterior deja de ser válida.
4. Volver a ejecutar `install-https.sh <NUEVA_IP> <CORREO_OPERATIVO>`. El script
   reemplaza el certificado de la línea `sgp-staging-ip` y actualiza el proxy.
5. Permitir que el siguiente `push` aprobado a `develop`, o la repetición
   controlada del job correspondiente, recree el backend mediante el CI/CD. No
   ejecutar un despliegue manual con Compose.
6. Repetir la redirección, los tres GET HTTPS, la inspección del SAN y la prueba
   de cookie.

### Diagnóstico de HTTPS

| Síntoma | Revisión |
|---|---|
| ACME no valida | Confirmar que la IP detectada coincide, TCP 80 es público y `/.well-known/acme-challenge/` no se redirige. Revisar `journalctl -u nginx`. |
| Certbot no reconoce `--ip-address` | Confirmar que se usa la imagen 5.8.0 y el digest fijado en los scripts, no el paquete antiguo de DNF. |
| 502 desde Nginx del host | Confirmar `curl http://127.0.0.1:8088/healthz` y los contenedores saludables. |
| Navegador rechaza el certificado | Revisar SAN, fechas e IP actual; no ocultar el problema con `--insecure` ni certificados autofirmados. |
| Renovación falla por IP distinta | Seguir el procedimiento de cambio de IP; no editar solo el archivo de estado. |
| CORS o activación usa HTTP/IP anterior | Corregir el archivo de entorno y aplicar el cambio únicamente mediante el flujo CI/CD. |

La configuración real de Nginx reside en `/etc/nginx/conf.d/sgp-staging.conf` y
los certificados en `/etc/letsencrypt`; el workflow no modifica esas rutas. Los
scripts versionados permanecen en `/opt/sgp/staging/https`. Así, los despliegues
posteriores solo reemplazan el Compose y las imágenes y no eliminan HTTPS.

## Verificación y operación

En la instancia:

```bash
sudo docker compose \
  --env-file /etc/sgp/staging/staging.env \
  --env-file /opt/sgp/staging/images.env \
  -f /opt/sgp/staging/compose.yml ps

curl --fail http://127.0.0.1:8088/healthz
curl --fail 'http://127.0.0.1:8088/api/v1/puentes?size=1'

sudo cat /opt/sgp/staging/REVISION
```

Consultar logs sin revelar el archivo de secretos:

```bash
sudo docker compose \
  --env-file /etc/sgp/staging/staging.env \
  --env-file /opt/sgp/staging/images.env \
  -f /opt/sgp/staging/compose.yml logs --tail=200 backend
```

No utilizar opciones destructivas contra RDS o S3 durante una actualización. El
Compose de staging no administra esos recursos.

## Problemas frecuentes

| Síntoma | Revisión |
|---|---|
| Backend no saludable | Revisar el Security Group de RDS, endpoint, TLS, variables obligatorias, credenciales y logs de Flyway. |
| `unauthorized` al descargar GHCR | Confirmar `/root/.docker/config.json`, acceso de la cuenta a ambos paquetes, permiso `read:packages` y vigencia del PAT. Repetir `docker login` sin mostrar el token. |
| Puerto 8088 inaccesible | Confirmar que el túnel SSM siga abierto; el puerto no se publica a Internet. |
| Despliegue con código 75 | Ya existe otro despliegue usando el bloqueo `flock`. |
| Migración o validación de esquema falla | No continuar: revisar logs del backend y consultar `flyway_schema_history` con el usuario migrador. |
| Acceso S3 falla en el futuro | Confirmar rol de instancia, política del bucket, región e IMDSv2 con hop limit `2`; no agregar claves estáticas. |
| Memoria insuficiente | Revisar consumo y swap; evaluar un tamaño ARM64 superior con autorización. |

## Validación realizada

El 9 de octubre de 2026 se validó localmente:

- Construcción de ambas imágenes.
- Sintaxis de Compose y scripts.
- Inicio saludable de los cuatro servicios.
- Respuesta del frontend, API, Nginx y Actuator.
- Ejecución exitosa de las 16 migraciones Flyway.
- Persistencia de PostgreSQL al destruir y recrear contenedores sin borrar
  volúmenes.
- Aislamiento de puertos, redes y volúmenes.

Esas pruebas corresponden al Compose anterior con PostgreSQL y MinIO locales. En
este incremento se validaron estáticamente el nuevo Compose y los scripts. El
SQL de preparación se ejecutó dos veces sobre un contenedor local
PostgreSQL/PostGIS 16: se comprobaron las extensiones, los privilegios DML y de
secuencias del usuario de aplicación, y la prohibición de crear tablas con ese
usuario. Luego la imagen local del backend inició saludablemente con esos roles
y Flyway registró 16 migraciones exitosas y ninguna fallida. En AWS, la EC2 está
en ejecución, Session Manager funciona y el endpoint de RDS respondió primero a
una prueba TCP. El despliegue posterior confirmó además que el backend se
autentica, completa Flyway, valida el esquema y atiende la API. La validación posterior de S3 del 27 de octubre se describe más abajo.

El script de preparación de Amazon Linux 2023 se ejecutó correctamente en la EC2
el 11 de octubre de 2026 después de eliminar de DNF la solicitud del paquete
`curl`, incompatible con `curl-minimal`. Esto valida la preparación del host, no
la ejecución de los contenedores de la aplicación.

La corrección de `infra/rds/prepare-database.sql` se validó localmente en un
contenedor PostgreSQL 16 con PostGIS 3.5.2. Se utilizó un administrador ficticio
con `rolsuper=false`, `rolcreaterole=true` y `rolcreatedb=true`; PostGIS se marcó
como extensión confiable únicamente dentro del contenedor desechable para
simular la capacidad delegada al administrador de RDS. El archivo exacto se
ejecutó dos veces, conservó al migrador como propietario, retiró al administrador
las capacidades `SET` e `INHERIT` y dejó al usuario de aplicación sin permiso
`CREATE` sobre `public`.

Las 16 migraciones existentes se ejecutaron en orden con el rol migrador. V14 se
ejecutó en una sola transacción, como lo hace Flyway, debido a su tabla temporal
`ON COMMIT DROP`. Una tabla de prueba creada posteriormente por el migrador
heredó los privilegios predeterminados: la aplicación pudo realizar
`INSERT`, `SELECT`, `UPDATE` y `DELETE`, pero su intento de `CREATE TABLE` fue
rechazado. Esta prueba local cubrió en detalle los permisos. En RDS, el
funcionamiento de los roles y migraciones quedó demostrado indirectamente por
el arranque saludable de la aplicación. El usuario confirmó posteriormente la
verificación de extensiones y TLS `verify-full`, sin resultados detallados.

El workflow se ejecutó en GitHub después de integrar los cambios en `develop`.
`Validate backend`, `Validate frontend`, `Publish backend image`, `Publish
frontend image` y `Deploy staging` finalizaron en `SUCCESS`. La primera asunción
OIDC falló hasta corregir el sujeto de confianza al identificador inmutable
documentado; después, Systems Manager desplegó la revisión
`488dae1ea7278eefc5f39e557eaadde4c07d00f9` mediante digests.

Desde EC2 se validaron Docker Compose `v2.40.3`, `docker login` a GHCR y las
descargas explícitas de backend y frontend para `linux/arm64`. Ambos contenedores
quedaron `healthy`. Las siguientes pruebas reales devolvieron HTTP 200:

```text
GET http://127.0.0.1:8088/healthz
GET http://127.0.0.1:8088/
GET http://127.0.0.1:8088/api/v1/puentes?size=1
```

Esto valida el despliegue técnico básico, el frontend, el proxy Nginx, Spring
Boot y la conexión con RDS. Tras integrar el cambio HTTPS mediante el merge
commit `e006c19`, GitHub Actions volvió a completar `Validate backend`,
`Validate frontend`, `Publish backend image`, `Publish frontend image` y
`Deploy staging`. No se proporcionó el identificador de esa ejecución.

En EC2 se validó la sintaxis Bash de los instaladores, se instaló Nginx `1.30.5`
y `nginx -t` finalizó correctamente. Certbot 5.8.0 emitió un certificado válido
para `3.235.197.36`. Un navegador externo abrió la aplicación y las pruebas
reales confirmaron:

```text
GET http://3.235.197.36/                         -> 301 a HTTPS
GET https://3.235.197.36/                        -> 200
GET https://3.235.197.36/healthz                 -> 200
GET https://3.235.197.36/api/v1/puentes?size=1   -> 200
```

`sgp-certbot-renew.timer` quedó habilitado y la ejecución manual de
`sgp-certbot-renew.service` finalizó correctamente sin renovar, porque el
certificado aún no había alcanzado su ventana. Se verificó de nuevo `/healthz`
por HTTPS después de la comprobación. Esa prueba inicial no validó SMTP ni los
flujos dependientes del correo; el usuario informó posteriormente que se realizaron
pruebas funcionales y de correo. La validación parcial de S3 también fue posterior.

## Incidencia y validación S3 del 27 de octubre de 2026

En `sgp-staging-ec2` (`i-0385c95df8cd7909d`), región `us-east-1`, las
cargas de fotografías de inspección devolvieron HTTP 409 `operacion_en_proceso`
con reintentos de solicitudes multipart con metadatos JSON, imagen WebP e
`Idempotency-Key`. `RecuperacionCargasService` advirtió que conservaba
operaciones para reintento. Los registros de `AlmacenamientoS3` y
`DocumentoInspeccionService` confirmaron S3 403 `AccessDenied` para
`s3:DeleteObject` al eliminar documentos PDF durante un rollback. El usuario
confirmó posteriormente que la corrección IAM también resolvió los 409 observados.

El rol `sgp-staging-ec2-role` solo tenía `AmazonSSMManagedInstanceCore` antes
de la corrección. Desde AWS IAM se creó y asoció la política
`sgp-staging-s3-objects-policy`, sin cambiar la política SSM:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "AccesoArchivosSGPStaging",
    "Effect": "Allow",
    "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
    "Resource": "arn:aws:s3:::sgp-staging-archivos-ayd1-2026-gt01/*"
  }]
}
```

No se concedió `AmazonS3FullAccess` ni acceso a otros buckets, no se añadieron
claves estáticas ni se modificaron secretos, y no hizo falta reiniciar EC2 ni
contenedores. Desde Session Manager, `aws sts get-caller-identity` confirmó la
cuenta `416086757987` y el rol asumido
`arn:aws:sts::416086757987:assumed-role/sgp-staging-ec2-role/i-0385c95df8cd7909d`.
Con `aws s3api put-object`, `get-object` y `delete-object` se subió, descargó y
eliminó exclusivamente `diagnostico/iam-test-1793091793-6830.txt`; la lectura se
comparó con el original mediante `cmp` y la respuesta de carga indicó cifrado
`AES256`. No se eliminaron fotografías ni documentos reales.

En una nueva prueba desde el navegador, la aplicación permitió subir fotos y
mostró en **Confirmadas por el servidor** fotografías `ACCESO` y
`SUPERESTRUCTURA`; el usuario confirmó también la carga de PDF. El bloqueo
original no se presentó en estas nuevas pruebas. Esto no valida todos los flujos.

La corrección y las pruebas realizadas no constituyen evidencia de recuperación
de inspecciones antiguas ni de restauración de archivos.

## Limitaciones y trabajo pendiente

- No están disponibles la fecha ni la evidencia técnica detallada de las
  verificaciones de RDS y de las pruebas funcionales y de correo informadas.
- El usuario informó que el rendimiento es satisfactorio; no hay mediciones
  registradas para confirmar el tamaño definitivo de EC2 y disco.
- Brotli no está habilitado; Nginx utiliza gzip para evitar una imagen con módulos
  adicionales no justificados.
- La retención centralizada de logs por 90 días no está implementada.
- RDS conserva 1 día de respaldos automáticos. La guía de respaldos documenta
  volcados diarios y mensuales a S3 y una restauración probada en producción.
- Los respaldos y restauraciones de S3 están fuera del alcance de esta historia.
- Observar y registrar al menos una renovación efectiva del certificado. El
  temporizador y el servicio ya funcionan, pero Certbot determinó que aún no
  correspondía renovar durante la prueba manual.
- HSTS no está habilitado por la combinación de IP dinámica y certificados de
  corta duración. Esto deja pendiente el requisito global DT-SEC-10 y necesita
  aceptación explícita o una solución operativa estable antes del cierre final
  de todos los requisitos del proyecto.

## Seguimiento operativo y funcional

1. Vigilar `sgp-certbot-renew.timer` y conservar evidencia de la primera
   renovación efectiva antes del vencimiento del certificado.
2. Conservar el enlace o identificador de la ejecución de GitHub Actions como
   evidencia del sprint, si está disponible.

## Evaluación de cierre de la HU de staging

El alcance acordado de infraestructura, publicación y despliegue automatizado de
staging está implementado: existen los recursos AWS, RDS atiende a la
aplicación, GHCR publica imágenes multi-arquitectura, OIDC y SSM despliegan desde
`develop`, los contenedores están saludables y el acceso público HTTPS respondió
correctamente. La integración S3 recibió posteriormente la validación parcial
registrada aquí. El usuario informó pruebas funcionales y de correo posteriores;
producción y respaldos se documentan en sus guías respectivas.

Por ello se recomienda cerrar la HU de staging en su alcance de sprint, dejando
seguimiento operativo para la renovación efectiva del certificado y las
excepciones ya registradas. Este cierre no equivale a declarar cumplido todo el
enunciado final: DT-SEC-10 (HSTS) y la retención de logs requieren seguimiento.
Las verificaciones de RDS y las pruebas funcionales
fueron confirmadas por el usuario sin evidencia detallada. Si HSTS o
alguno de esos puntos forma parte explícita de los criterios de aceptación de la
HU en el tablero, el cierre debe ser condicionado a una excepción aceptada por
el equipo o la HU debe permanecer abierta.

## Referencias de AWS

- [Extensiones de PostgreSQL en Amazon RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.Extensions.html).
- [Configuración de PostGIS en RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.PostGIS.html).
- [Uso de TLS con RDS PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html).
- [Creación de bases de datos en PostgreSQL 16](https://www.postgresql.org/docs/16/sql-createdatabase.html).
- [Privilegios predeterminados en PostgreSQL 16](https://www.postgresql.org/docs/16/sql-alterdefaultprivileges.html).
- [Certificados IP y Certbot 5.4+](https://letsencrypt.org/2026/03/11/shorter-certs-certbot).
- [Perfil `shortlived` de Let's Encrypt](https://letsencrypt.org/ca/docs/profiles/).
- [Renovación con Certbot](https://eff-certbot.readthedocs.io/en/stable/using.html#renewing-certificates).
- [Membresía de roles en PostgreSQL 16](https://www.postgresql.org/docs/16/role-membership.html).
- [Roles IAM para aplicaciones en EC2](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role-ec2.html).
- [IMDSv2 en entornos con contenedores](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-IMDS-new-instances.html).

