# Despliegue de staging en AWS

## Estado del documento

Esta guía describe la infraestructura de staging implementada en el repositorio y
se actualiza de forma incremental durante la historia de usuario.

| Área | Estado |
|---|---|
| Imágenes reutilizables de backend y frontend | Build multi-arquitectura implementado; pendiente de publicación desde `develop`. |
| Compose aislado de staging | Adaptado para ejecutar solo Nginx/Angular y Spring Boot; RDS y S3 son externos a la EC2. |
| Preparación de Amazon Linux 2023 | Completada exitosamente en la EC2 ARM64 el 11 de octubre de 2026. |
| Workflow de validación, publicación y despliegue | Versión anterior ejecutada correctamente; cambio actual pendiente de CI. |
| Publicación en GHCR | Paquetes privados publicados inicialmente para AMD64; manifiestos AMD64/ARM64 pendientes de este cambio. |
| Despliegue automático desde `develop` | Implementado con variables de repositorio y deshabilitado hasta completar los requisitos restantes. |
| Red AWS | VPC, tabla privada, dos subredes dedicadas y DB Subnet Group de RDS confirmados. |
| Security Groups | Grupos de EC2 y RDS creados; la regla 5432 de RDS referencia únicamente al grupo de staging. |
| IAM y Systems Manager | Rol asociado a la EC2 y acceso interactivo mediante Session Manager verificado. |
| S3 | Bucket privado de staging creado y vacío; integración de aplicación no implementada. |
| RDS | `sgp-rds-postgres` disponible; conectividad TCP desde EC2 verificada, con autenticación, bases, extensiones y migraciones pendientes. |
| EC2 | `sgp-staging-ec2` creada, en ejecución y con sus tres comprobaciones superadas; preparación inicial completada. |
| Despliegue real en AWS | No ejecutado. |

Se crearon los dos Security Groups, el bucket S3, RDS y la EC2 de staging. El rol
de instancia está asociado y el acceso mediante Session Manager funciona. Una
prueba desde EC2 confirmó alcance TCP al endpoint privado de RDS en el puerto
5432. La preparación inicial del servidor finalizó correctamente. El rol OIDC de
GitHub Actions y sus variables de repositorio ya tienen identificadores
asignados, pero la confianza para `develop` y la asunción del rol todavía no han
sido validadas. Tampoco se han preparado la base ni el despliegue real.

## Inventario AWS confirmado

Estado actualizado mediante las comprobaciones manuales realizadas en la consola
el 10 y 11 de octubre de 2026:

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
| Extensiones disponibles | PostGIS 3.4.6, `pg_trgm` 1.6 y `uuid-ossp` 1.1 confirmadas como disponibles; todavía no instaladas en `sgp_staging`. |
| Instancia EC2 | `sgp-staging-ec2` (`i-0385c95df8cd7909d`), Amazon Linux 2023 ARM64, `t4g.micro`, en ejecución y con comprobaciones `3/3`. |
| Ubicación EC2 | Subred pública `subnet-0745affbda7ec4bb1`, CIDR `172.31.64.0/20`, en `us-east-1f`. |
| Almacenamiento EC2 | 20 GiB gp3 cifrados con `aws/ebs`. |
| IPv4 pública observada | `3.235.197.36`, asignada automáticamente y dinámica; no existe Elastic IP. |
| Security Group de EC2 | `sgp-staging-ec2-sg` (`sg-0e617972b3eedeb73`), sin reglas de entrada y con salida hacia `0.0.0.0/0`. |
| Security Group de RDS | `sgp-rds-sg` (`sg-0ab8d04a2ef4d3db8`), con salida predeterminada. |
| Regla de entrada de RDS | PostgreSQL TCP 5432 desde `sg-0e617972b3eedeb73` (`sgp-staging-ec2-sg`); no usa un CIDR público. |
| Bucket S3 | `sgp-staging-archivos-ayd1-2026-gt01`, propósito general, creado vacío en `us-east-1`. |
| Seguridad del bucket | Bucket owner enforced, ACL deshabilitadas y todas las opciones de bloqueo de acceso público habilitadas. |
| Protección de objetos | SSE-S3 habilitado; versionado, S3 Bucket Key y Object Lock deshabilitados. |
| Etiquetas del bucket | `Project=SGP` y `Environment=staging`. |
| Configuración adicional de S3 | Sin política personalizada, CORS ni objetos. |
| Rol IAM de EC2 | `sgp-staging-ec2-role`, asociado a la instancia, con confianza en `ec2.amazonaws.com` y política `AmazonSSMManagedInstanceCore`. |
| Administración EC2 | Acceso interactivo mediante AWS Systems Manager Session Manager verificado. |
| Par de claves | `sgp-staging-key`, ED25519 y formato privado `.pem`; archivo conservado fuera del repositorio. SSH no probado y sin regla de entrada pública. |
| Acceso S3 desde IAM | No concedido todavía; el rol confirmado solo habilita la administración mediante SSM. |
| Rol OIDC de GitHub Actions | `arn:aws:iam::416086757987:role/sgp-staging-github-actions-role`; es independiente del rol de instancia EC2. Su confianza para la rama `develop` y su uso real siguen pendientes de configurar o validar. |
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

El nombre del bucket queda registrado como identificador de infraestructura, no
como variable del backend. No debe agregarse a Compose ni al archivo de entorno
hasta que el código implemente y defina el contrato de almacenamiento S3.

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
`Disponible`. La conectividad TCP desde EC2 está confirmada, pero no la
autenticación PostgreSQL ni la preparación de las bases.

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
| Base inicial | Sin nombre de base inicial; `sgp_staging` aún no existe |
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
5432 mediante `/dev/tcp`. Esta prueba confirma resolución DNS, enrutamiento y
reglas de red; no valida TLS, autenticación PostgreSQL, el usuario administrador,
PostGIS, Flyway ni el backend.

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

Esta excepción no satisface el requisito original de 30 días. Los 12 respaldos
mensuales mediante AWS Backup y la prueba de restauración tampoco están
implementados y permanecen como trabajo pendiente fuera de esta historia.

### Verificación TLS pendiente

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
documentación o logs. Este procedimiento todavía no se ha ejecutado y no se
declara exitoso.

Esta prueba cubre al cliente `psql`, no al backend. La plantilla actual del
backend usa `sslmode=require`; pasarla a `verify-full` también requiere hacer
disponible el bundle CA dentro del contenedor y validar el driver JDBC. Esa
integración sigue pendiente y no debe declararse implementada solo porque la
prueba administrativa funcione.

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

El punto de entrada es Nginx. El puerto se publica únicamente en la interfaz de
loopback de la instancia para acceder mediante un túnel de AWS Systems Manager.
El backend no publica puertos en el host. RDS debe permanecer en subredes
privadas y el bucket S3 debe bloquear todo acceso público.

```text
Equipo del proyecto
        |
        | túnel SSM a la aplicación (pendiente de probar)
        v
127.0.0.1:8088 en EC2
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

`(*)` El backend todavía no implementa la interfaz de almacenamiento de objetos.
Por ello el Compose no recibe variables S3 ni credenciales estáticas. Cuando se
implemente el módulo, el SDK deberá obtener credenciales temporales desde el rol
de instancia y limitarse al bucket de staging.

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
- Cuando exista la integración de archivos, una política adicional de mínimo
  privilegio deberá limitar el rol al bucket
  `sgp-staging-archivos-ayd1-2026-gt01`; el rol actual no concede acceso S3.
- Salida HTTPS hacia AWS, GHCR/Docker Hub y el proveedor SMTP.
- Administración mediante Systems Manager, sin publicar SSH.
- RDS PostgreSQL 16 no pública, en la misma VPC, cuyo Security Group acepte el
  puerto 5432 únicamente desde el Security Group de la EC2 de staging.
- Modalidad Single-AZ, aceptando que no habrá conmutación automática entre zonas.
- Una base `sgp_staging`, un usuario migrador y un usuario de aplicación que no
  se reutilicen en producción.
- Bucket S3 `sgp-staging-archivos-ayd1-2026-gt01`, privado e independiente del
  futuro bucket de producción. Actualmente está vacío y sin CORS ni políticas
  personalizadas.
- IMDSv2 obligatorio y salto de respuesta igual a `2`, necesario para que un SDK
  dentro de Docker pueda obtener credenciales temporales del rol de instancia.
- No se comprará un dominio ni se creará una zona DNS pública para el proyecto.
  El acceso inicial de staging continuará mediante túnel SSM, sin publicar
  puertos.
- Si posteriormente se exige acceso público a staging, HTTPS deberá resolverse
  sobre una IPv4 pública estable con un certificado válido para esa IP. No se
  abrirán 80/443 hasta aprobar y validar ese mecanismo.
- No se usarán ALB, NAT Gateway, ECS ni EKS.

La EC2 y su disco están aprovisionados. RDS está `Disponible` y su puerto 5432 es
alcanzable desde la instancia, pero quedan pendientes TLS, autenticación y la
preparación de PostgreSQL. La imposibilidad de configurar alertas automáticas
obliga a mantener un control manual del saldo.

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
| IPv4 pública | Asignación automática; se observó `3.235.197.36`, sin Elastic IP |
| Security Group | `sgp-staging-ec2-sg` (`sg-0e617972b3eedeb73`) |
| Rol IAM | `sgp-staging-ec2-role` |
| Administración | Session Manager verificado; SSH no probado y sin entrada pública |
| Estado | En ejecución; comprobaciones `3/3` superadas |

La IPv4 pública dinámica proporciona la salida necesaria sin usar NAT Gateway,
pero no autoriza tráfico entrante: el Security Group sigue sin ingress y Nginx
permanecerá ligado a loopback. La dirección puede cambiar al detener e iniciar
la instancia y no se considera una interfaz pública estable.

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

La EC2 utilizará un Personal Access Token (classic) de una cuenta con acceso a
los paquetes. Debe conceder únicamente `read:packages`, tener fecha de expiración
y no incluir `write:packages` ni `delete:packages`. El token es una credencial de
operación del servidor: no debe guardarse en el repositorio, `staging.env`, User
Data, GitHub Actions, logs ni documentación.

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
| `STAGING_ENABLED` | Repositorio | Ausente o `false` | Interruptor del job. Solo debe cambiarse a `true` después de completar los requisitos y validar la confianza OIDC. |
| `AWS_REGION` | Repositorio | Configurada: `us-east-1` | Región de la instancia y del rol. |
| `AWS_ROLE_ARN` | Repositorio | Configurada: `arn:aws:iam::416086757987:role/sgp-staging-github-actions-role` | Rol asumido mediante OIDC. |
| `STAGING_INSTANCE_ID` | Repositorio | Configurada: `i-0385c95df8cd7909d` | Instancia administrada por SSM. |

Las variables anteriores son identificadores de infraestructura, no
credenciales. No deben agregarse access keys, contraseñas ni tokens como
variables de repositorio. La ausencia de `STAGING_ENABLED`, o cualquier valor
distinto de la cadena exacta `true`, mantiene omitido el job de despliegue.

La publicación en GHCR no requiere GitHub Secrets: utiliza el `GITHUB_TOKEN`
efímero. La descarga desde la EC2 es una operación diferente y utiliza el PAT de
solo lectura instalado manualmente en Docker. AWS usa OIDC y tampoco requiere
claves permanentes en GitHub. La relación de confianza del rol debe limitar el
sujeto a
`repo:KennySalazar/SGP_ProyectoFinal_AyD1:ref:refs/heads/develop`. Su
política debe permitir únicamente enviar `AWS-RunShellScript` a la instancia de
staging, consultar el resultado y cancelar el comando en caso de timeout.

La protección que antes habría aportado el Environment se reemplaza de forma
explícita por tres controles: el workflow solo recibe eventos `push` de
`develop`, el job vuelve a comprobar `github.ref == 'refs/heads/develop'` y la
confianza de AWS acepta únicamente el sujeto OIDC de esa misma referencia. Esta
adaptación no agrega claves permanentes ni cambia el despliegue mediante SSM.

Cada job vuelve a consultar el SHA actual de `develop` antes de publicar o
desplegar. La publicación por imagen está serializada, el despliegue tiene una
concurrencia exclusiva y el servidor utiliza `flock`. Con estas tres protecciones
se evitan despliegues simultáneos y revisiones antiguas fuera de orden.

## Variables de entorno

La plantilla es `infra/staging/.env.example`. El archivo real debe existir solo
en `/etc/sgp/staging/staging.env` y nunca debe registrarse en Git.

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
| `INVITACION_URL_ACTIVACION` | Backend | No | `http://localhost:8088/activar-cuenta` mientras se use el túnel. |
| `CORS_ALLOWED_ORIGINS` | Backend | No | `http://localhost:8088` para el acceso privado. |
| `JAVA_TOOL_OPTIONS` | Backend | No | Límites de JVM definidos en la plantilla. |

En staging, `SPRING_PROFILES_ACTIVE=staging`, `OPENAPI_PUBLIC=false` y
`REFRESH_COOKIE_SECURE=false` están fijados por Compose. La cookie no se marca
`Secure` porque el HTTP viaja dentro del túnel cifrado de SSM. Producción deberá
usar HTTPS y el perfil `prod`, que exige cookie segura.

Para generar secretos que no requieran escapar caracteres especiales en el
archivo de entorno puede utilizarse:

```bash
openssl rand -hex 32
```

No se definen `AWS_ACCESS_KEY_ID` ni `AWS_SECRET_ACCESS_KEY`. El acceso futuro a
S3 utilizará credenciales temporales del rol IAM de la EC2. Tampoco se inventan
variables de bucket mientras el backend no tenga una configuración que las
consuma.

## Preparación de la base RDS

Con RDS ya observada `Disponible`, y antes del primer despliegue, copie
temporalmente `infra/rds/prepare-database.sql` a un equipo con acceso de red a
la instancia, por ejemplo la EC2 de staging administrada mediante SSM.
La base `sgp_staging` todavía no existe. Ejecute la versión corregida como
`postgres` solo después de descargar el bundle oficial y validar TLS:

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
La ejecución contra RDS y la validación completa con `verify-full` siguen
pendientes; no deben declararse exitosas hasta realizar la prueba real.

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

La publicación de imágenes y el despliegue serán jobs independientes para que
un fallo de AWS no obligue a reconstruirlas.

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
en ejecución, Session Manager funciona y el endpoint de RDS respondió a una
prueba TCP desde esa instancia. Todavía no se han probado TLS, autenticación,
PostGIS, Flyway ni la conexión del backend contra RDS. El bucket S3 tampoco tiene
una integración funcional que probar.

El script de preparación de Amazon Linux 2023 se ejecutó correctamente en la EC2
el 11 de octubre de 2026 después de eliminar de DNF la solicitud del paquete
`curl`, incompatible con `curl-minimal`. Esto valida la preparación del host, no
el acceso a GHCR ni la ejecución de los contenedores de la aplicación.

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
rechazado. Esta prueba es local; el SQL corregido todavía no se ha ejecutado en
la RDS real.

El workflow también se ejecutó en GitHub después de integrar los cambios en
`develop`: las validaciones de backend y frontend finalizaron correctamente y se
publicaron ambas imágenes en GHCR. El job `Deploy staging` permaneció omitido,
como se esperaba, porque `STAGING_ENABLED` no está activo. Aún no se han validado
los nuevos manifiestos multi-arquitectura, el acceso desde EC2 a los paquetes
privados, OIDC ni el despliegue real. Session Manager sí quedó validado en la
instancia `i-0385c95df8cd7909d`. La URL o el identificador de cada ejecución debe
agregarse cuando se recopile la evidencia final.

## Limitaciones y trabajo pendiente

- Registrar la URL o el identificador de la ejecución exitosa de GitHub Actions.
- Publicar y verificar los manifiestos AMD64/ARM64 después de integrar este
  cambio en `develop`.
- Probar autenticación PostgreSQL desde EC2 con `sslmode=verify-full` y el bundle
  CA de RDS; la prueba TCP actual no cubre esa validación.
- Configurar y verificar la confianza OIDC del rol
  `sgp-staging-github-actions-role`, limitada a `refs/heads/develop`, y su
  política SSM limitada a la instancia de staging.
- Ejecutar y validar `infra/rds/prepare-database.sql` contra RDS PostgreSQL 16.
- Confirmar en la versión elegida de RDS la disponibilidad de PostGIS antes de
  crear datos.
- Crear, instalar y probar en EC2 el PAT classic de solo lectura para GHCR.
- Definir responsable y recordatorio de rotación del PAT; su disponibilidad
  depende de que la cuenta conserve acceso a los paquetes privados.
- Confirmar el tamaño definitivo de EC2 y disco mediante medición.
- El backend todavía no integra almacenamiento de objetos; S3 no puede validarse
  funcionalmente hasta que exista la interfaz requerida por DT-ALM-03.
- Brotli no está habilitado; Nginx utiliza gzip para evitar una imagen con módulos
  adicionales no justificados.
- La retención centralizada de logs por 90 días no está implementada.
- RDS conserva temporalmente solo 1 día de respaldos automáticos por la
  restricción del plan gratuito. La retención requerida de 30 días, los 12
  respaldos mensuales y la prueba de restauración permanecen pendientes.
- Los respaldos y restauraciones de S3 están fuera del alcance de esta historia.
- Definir y validar, solo si staging debe hacerse público, HTTPS válido sobre IP
  pública sin comprar dominio. Un certificado autofirmado no satisface este
  requisito.

## Secuencia de implementación pendiente

1. Descargar el bundle CA desde su origen oficial y probar PostgreSQL con
   `sslmode=verify-full`. Registrar el resultado sin exponer la contraseña.
2. Preparar `sgp_staging` y sus usuarios
   mediante `infra/rds/prepare-database.sql`; comprobar extensiones y Flyway sin
   modificar migraciones existentes. No crear `sgp_prod`.
3. Instalar en EC2 la autenticación de solo lectura para GHCR y configurar
   `/etc/sgp/staging/staging.env` sin versionar secretos.
4. Terminar la política de confianza del rol OIDC, revisar las variables de
   repositorio y probar el flujo manteniendo `STAGING_ENABLED` ausente o en
   `false` hasta que las comprobaciones manuales anteriores sean satisfactorias.
5. Habilitar el despliegue automático y recopilar evidencia de SSM, descarga por
   digest, salud, Flyway, frontend y `/api/v1`.

## Referencias de AWS

- [Extensiones de PostgreSQL en Amazon RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.Extensions.html).
- [Configuración de PostGIS en RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.PostGIS.html).
- [Uso de TLS con RDS PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html).
- [Creación de bases de datos en PostgreSQL 16](https://www.postgresql.org/docs/16/sql-createdatabase.html).
- [Privilegios predeterminados en PostgreSQL 16](https://www.postgresql.org/docs/16/sql-alterdefaultprivileges.html).
- [Membresía de roles en PostgreSQL 16](https://www.postgresql.org/docs/16/role-membership.html).
- [Roles IAM para aplicaciones en EC2](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role-ec2.html).
- [IMDSv2 en entornos con contenedores](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-IMDS-new-instances.html).

