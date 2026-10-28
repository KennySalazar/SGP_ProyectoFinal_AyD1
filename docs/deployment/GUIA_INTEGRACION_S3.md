# Guía de traspaso para la integración con Amazon S3

## 1. Propósito y alcance

Esta guía transfiere el estado de la infraestructura y de la integración de
archivos para quienes continúen su desarrollo. No autoriza cambios en AWS ni
establece reglas de negocio nuevas. Contrastar los requisitos vigentes y las
historias correspondientes con el código antes de ampliar cualquier flujo.

Los binarios se guardan en almacenamiento de objetos y PostgreSQL conserva
metadatos y asociaciones. MinIO se usa en desarrollo local y S3 en AWS; cada
ambiente debe tener bucket, rol, datos y configuración independientes.

## 2. Infraestructura y pruebas de staging

El bucket privado `sgp-staging-archivos-ayd1-2026-gt01` existe en `us-east-1`.
En la revisión inicial tenía bloqueo de acceso público, Bucket owner enforced,
ACL deshabilitadas, SSE-S3, etiquetas `Project=SGP` y `Environment=staging`,
sin versionado, Object Lock, política de bucket personalizada ni CORS. Fue
creado vacío y después recibió cargas; no se ha inventariado su contenido actual.

La EC2 usa `sgp-staging-ec2-role`, con `AmazonSSMManagedInstanceCore` y, desde
el 27 de octubre de 2026, `sgp-staging-s3-objects-policy`, que permite
`PutObject`, `GetObject` y `DeleteObject` sobre los objetos de ese bucket. La
instancia exige IMDSv2 y tiene hop limit `2`. El SDK usa su cadena de
credenciales predeterminada, incluido el rol de instancia; no crear access keys
para AWS ni registrar tokens. El rol OIDC de despliegue es una identidad distinta.

Ese día Session Manager confirmó la identidad del rol y se verificaron subida,
lectura comparada con el original y eliminación de un objeto de diagnóstico;
la carga indicó SSE-S3 `AES256`. En el navegador se confirmaron fotos de
`ACCESO` y `SUPERESTRUCTURA`, y el usuario confirmó carga de PDF. La evidencia,
el alcance y la incidencia están en [staging](STAGING_AWS.md). La prueba de
eliminación solo afectó al objeto de diagnóstico y no acredita una eliminación
funcional desde la interfaz.

La política IAM corrigió el permiso `DeleteObject` que causaba un 403 durante
el rollback de un PDF. El usuario confirmó que la corrección también resolvió
los errores 409 observados anteriormente.

## 3. Implementación existente en el repositorio

- [`AlmacenamientoArchivos`](../../sgp-api/src/main/java/gt/usac/cunoc/sgp/archivo/service/AlmacenamientoArchivos.java)
  define guardar, abrir, consultar existencia y eliminar; las claves son opacas.
- [`AlmacenamientoS3`](../../sgp-api/src/main/java/gt/usac/cunoc/sgp/archivo/service/AlmacenamientoS3.java)
  usa el SDK de AWS para S3 y también para MinIO compatible; la carga evita
  sobrescribir mediante `If-None-Match`. Existe además
  [`AlmacenamientoLocal`](../../sgp-api/src/main/java/gt/usac/cunoc/sgp/archivo/service/AlmacenamientoLocal.java).
- [`AlmacenamientoConfiguration`](../../sgp-api/src/main/java/gt/usac/cunoc/sgp/archivo/config/AlmacenamientoConfiguration.java)
  selecciona el proveedor; con endpoint explícito admite credenciales para MinIO
  y path-style. Sin endpoint usa la cadena del SDK; rechaza credenciales
  explícitas sin endpoint.
- [`FotografiaController`](../../sgp-api/src/main/java/gt/usac/cunoc/sgp/inspeccion/controller/FotografiaController.java)
  implementa carga multipart, listado, retiro y lectura de contenido y miniatura
  bajo `/api/v1/inspecciones/{id}/fotografias`.
  [`DocumentoInspeccionController`](../../sgp-api/src/main/java/gt/usac/cunoc/sgp/inspeccion/controller/DocumentoInspeccionController.java)
  implementa carga, listado, lectura de contenido y retiro bajo la ruta equivalente
  `/documentos`. Su existencia no acredita que todos los flujos se hayan probado
  en staging.
- [`RecuperacionCargasService`](../../sgp-api/src/main/java/gt/usac/cunoc/sgp/archivo/service/RecuperacionCargasService.java)
  gestiona recuperación de cargas.
- `docker-compose.yml` prepara MinIO local. La migración
  `sgp-api/src/main/resources/db/migration/V10__mantenimiento_y_archivos.sql`
  creó metadatos y no debe modificarse retroactivamente.

## 4. Modelo persistente disponible

La migración V10 guarda referencias, nunca el binario. La tabla `archivo`
incluye, entre otros datos:

- `tipo_uso`: `FOTO_INSPECCION`, `DOCUMENTO_INSPECCION`,
  `EVIDENCIA_MANTENIMIENTO` o `ACTA_CALIBRACION`;
- claves separadas para objeto original y miniatura;
- nombre original y nombre generado de almacenamiento;
- MIME real, tamaño y checksum SHA-256;
- dimensiones, geografía y fecha de captura;
- usuario que subió, fecha de confirmación y estado;
- fechas y motivo para retiro o purga.

Las tablas `archivo_inspeccion`, `archivo_evidencia_mantenimiento` y
`archivo_acta_calibracion` asocian el metadato con su contexto. Las fotos de
inspección y evidencias pueden conservar `elemento_ref` e
`instancia_elemento_id`.

El comentario histórico de V10 menciona MinIO, pero la estructura por claves es
compatible con S3. No se debe editar la migración para cambiar ese comentario.
Si la implementación necesita columnas o restricciones nuevas, deberá crear la
siguiente migración disponible después de revisar todas las existentes.

## 5. Requisitos de almacenamiento

La implementación debe contrastarse con estos requisitos:

- Toda fotografía pertenece a una inspección y puede quedar anclada mediante
  `elemento_ref` (RN-ARC-01).
- El cliente comprime antes de persistir localmente y subir: lado mayor de 1600
  px, WebP o JPEG y objetivo de 200–400 KB (RN-ARC-02 y DT-OFF-08).
- Se conservan geolocalización y fecha de captura (RN-ARC-03).
- Máximo 60 fotos y 10 documentos por inspección; PDF de hasta 20 MB
  (RN-ARC-04). Nginx admite como máximo 25 MB, según DT-DEP-03.
- Una inspección solo puede enviarse a revisión con las cuatro categorías
  fotográficas mínimas: acceso, superestructura, subestructura y cauce
  (RN-INS-06).
- Las fotos de una inspección publicada no se eliminan; en borrador sí
  (RN-ARC-05).
- Se notifica un borrador inactivo a los 180 días y se purgan sus objetos a los
  210 días conservando el registro (RN-ARC-06). El proceso programado requiere
  `@Scheduled` y ShedLock (DT-BE-14).
- Cerrar una orden como ejecutada exige al menos una fotografía de evidencia
  (RN-MTO-05).
- Fotografías e informes requieren una cuenta activa (RN-USR-10).
- Los identificadores de inspección y foto nacen en el cliente como UUID v7 y
  las operaciones encoladas usan `Idempotency-Key` (DT-OFF-05 y DT-OFF-07).
- El servidor valida MIME por firma binaria, tamaño y cantidad, y genera los
  nombres de objeto (DT-SEC-08).
- El acceso se realiza mediante un endpoint autorizado o URL firmada de vigencia
  corta; el bucket no se publica (DT-SEC-09).
- Se guardan el original comprimido y una miniatura de 300 px (DT-ALM-04).
- Los objetos deben incorporarse a la política de respaldos y restauración con
  la misma prioridad que PostgreSQL (DT-ALM-05 y DT-DEP-07/08).

La arquitectura de referencia contempla MinIO compatible con S3. El ADR-0002
adapta el despliegue a AWS: MinIO permanece local y S3 es el proveedor de
staging/producción. La portabilidad se conserva mediante `AlmacenamientoArchivos`.

## 6. Configuración y evolución

Las propiedades actuales en `sgp-api/src/main/resources/application.properties`
son `app.archivos.proveedor` (`ARCHIVOS_PROVEEDOR`, por defecto `local`),
`app.archivos.local.directorio` (`ARCHIVOS_DIRECTORIO`),
`app.archivos.s3.region` (`AWS_REGION`, por defecto `us-east-1`),
`app.archivos.s3.bucket` (`ARCHIVOS_BUCKET`), `app.archivos.s3.endpoint`
(`ARCHIVOS_ENDPOINT`) y las credenciales locales `MINIO_ACCESS_KEY` y
`MINIO_SECRET_KEY`. Para AWS se selecciona `s3`, se configura región y bucket
y se usa el rol EC2 sin credenciales estáticas. Para MinIO se configura el
endpoint local y sus credenciales locales. Producción necesita recursos propios.

Al extender los flujos, revisar autorización, claves generadas por servidor,
miniaturas, compensación entre PostgreSQL y objetos, idempotencia y cola offline.
La existencia del código no prueba por sí sola todos los requisitos ni sus
comportamientos en AWS.

## 7. IAM de mínimo privilegio

La política de staging concede `s3:PutObject`, `s3:GetObject` y
`s3:DeleteObject` únicamente sobre
`arn:aws:s3:::sgp-staging-archivos-ayd1-2026-gt01/*`; no concede `s3:*`,
`AmazonS3FullAccess`, administración de buckets ni permisos sobre otros ambientes.
El adaptador también usa `HeadObject` para consultar existencia. Si se agregan
operaciones o prefijos, revisar sus permisos efectivos; `s3:ListBucket` solo se
concede si se necesita, en el recurso bucket y restringido según corresponda.
Producción debe tener su propio rol y política adaptada a su propio bucket.

## 8. CORS, versionado, ciclo de vida y recuperación

No habilitar CORS en S3 mientras el navegador transfiera archivos a través del
backend. Si se aprueban URLs firmadas para carga o descarga directa, definir los
orígenes, métodos y encabezados mínimos para cada ambiente; nunca usar `*` con
datos autenticados.

Versionado, ciclo de vida y retención requieren una decisión explícita. El
bucket actual tiene versionado y Object Lock deshabilitados. Antes de cambiarlo,
evaluar:

- recuperación ante eliminación accidental;
- costo de versiones y cargas incompletas;
- transición o expiración de objetos retirados/purgados;
- retención de borradores y protección de inspecciones publicadas;
- respaldos mensuales y prueba de restauración requeridos.

Una regla de ciclo de vida genérica no debe eliminar objetos sin considerar el
estado y la asociación almacenados en PostgreSQL. La aplicación y la política de
recuperación deben acordar cuál es la fuente de verdad.

## 9. Estrategia de pruebas

### Automatizadas

- Pruebas unitarias del servicio de archivos contra un doble del contrato, sin
  llamar AWS.
- Pruebas de adaptador contra MinIO local para carga, lectura, checksum,
  miniatura, retiro y errores.
- Pruebas de autorización por asociación y estado de inspección.
- Pruebas de MIME real, tamaños, cantidades y nombres maliciosos.
- Pruebas de idempotencia y reintentos offline.
- Pruebas del proceso de 180/210 días con reloj controlado y ShedLock.
- Pruebas de compensación cuando S3 funciona y PostgreSQL falla, y viceversa.

### En staging

1. Confirmar rol, región y política efectiva sin exponer credenciales.
2. Subir un archivo permitido mediante la API autenticada.
3. Verificar metadatos, checksum, original y miniatura.
4. Descargarlo con autorización y rechazar acceso anónimo o de otro usuario.
5. Rechazar MIME, tamaño, cantidad y contexto inválidos.
6. Probar retiro y purga conforme a las reglas aprobadas.
7. Confirmar que el objeto no es público y que una URL firmada expira, si se
   adopta ese mecanismo.
8. Ejecutar y documentar una recuperación desde respaldo antes de declarar
   satisfecho DT-ALM-05.

Las pruebas locales no prueban IAM y una llamada aislada a S3 no demuestra las
reglas funcionales. Ambas evidencias son necesarias.

Los registros confirmaron un 403 de `s3:DeleteObject` durante rollback de PDF;
el usuario confirmó que los 409 anteriores se resolvieron con la corrección IAM.
No se han declarado verificadas la recuperación de inspecciones antiguas ni las
operaciones funcionales de eliminación o restauración.

## 10. Criterios de seguimiento

El contrato, el adaptador y los endpoints existen; se deben comprobar sus
resultados frente a los requisitos vigentes, incluyendo autorización, límites,
consistencia, miniaturas, retención y recuperación. Las pruebas automatizadas
existentes no sustituyen pruebas reales de AWS ni prueban por sí solas el estado
del entorno desplegado.

## 11. Riesgos, dependencias y pendientes

- Confirmar las prioridades del trabajo funcional de archivos y el alcance de
  HU-028, mencionada en el frontend.
- La consistencia entre S3 y PostgreSQL no es transaccional; revisar el
  comportamiento implementado de idempotencia, compensación y recuperación.
- La EC2 `t4g.micro` tiene recursos limitados; procesamiento de imágenes debe
  medirse para evitar presión de memoria y CPU.
- El bucket no tiene versionado ni política de recuperación aplicada.
- La retención y restauración de objetos siguen fuera del alcance implementado
  para staging y son requisitos globales pendientes.
- El SMTP ficticio afecta notificaciones de borradores si esa función usa correo.
- La integración de actas depende también del trabajo pendiente documentado en
  `docs/pendientes/HU-019-acta-calibracion.md`.

## 12. Evidencia y referencias del repositorio

- `docs/ARCHITECTURE.md`: límites de los dominios `archivo/` y `offline/`.
- `docs/DEVELOPMENT.md`: operación actual de MinIO local.
- `docker-compose.yml`: servicio y volumen MinIO de desarrollo.
- `sgp-api/src/main/resources/db/migration/V10__mantenimiento_y_archivos.sql`:
  modelo de metadatos y asociaciones ya aplicado.
- `sgp-api/src/main/resources/formulario/sieca-v1/esquema.schema.json`:
  separación del reporte fotográfico respecto al esquema dinámico.
- `sgp-client/src/app/features/formulario/pages/vista-previa-formulario/`:
  marcador de integración futura del reporte fotográfico.
- `docs/adr/0002-despliegue-staging-aws-compose-ghcr.md`: decisión S3 en AWS y MinIO local.
- `docs/deployment/STAGING_AWS.md`: inventario y operación de staging.
