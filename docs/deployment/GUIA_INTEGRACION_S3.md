# Guía de traspaso para la integración con Amazon S3

## 1. Propósito y alcance

Este documento transfiere el contexto técnico para una futura historia de
usuario de almacenamiento de archivos del Sistema de Gestión de Puentes. La
infraestructura básica de S3 de staging ya existe, pero la aplicación todavía no
almacena ni recupera objetos. Esta guía no autoriza cambios en AWS ni define
reglas de negocio nuevas.

La futura implementación debe conservar una separación clara:

- el binario vive en almacenamiento de objetos;
- PostgreSQL conserva metadatos, estado y asociaciones de negocio;
- el dominio depende de una abstracción, no del SDK de AWS;
- MinIO sirve para desarrollo local y Amazon S3 para AWS;
- cada ambiente usa bucket, rol IAM, datos y configuración independientes.

Los criterios propuestos aquí deben contrastarse con la HU vigente y el
enunciado antes de comenzar. Si existe una diferencia, debe resolverse con el
equipo y documentarse; no se debe asumir una regla nueva.

## 2. Infraestructura existente

Último estado documentado, no verificado nuevamente durante la creación de esta
guía:

| Propiedad | Estado conocido |
|---|---|
| Proveedor | Amazon S3. |
| Región | `us-east-1`. |
| Bucket | `sgp-staging-archivos-ayd1-2026-gt01`. |
| Ambiente | Staging. |
| Acceso público | Bloqueado en todas sus opciones. |
| Propiedad | Bucket owner enforced; ACL deshabilitadas. |
| Cifrado predeterminado | SSE-S3. |
| Etiquetas | `Project=SGP`, `Environment=staging`. |
| Versionado | Deshabilitado. |
| Object Lock | Deshabilitado. |
| Bucket policy | Sin política personalizada. |
| CORS | Sin configuración específica. |
| Contenido | Creado inicialmente vacío; no se ha aportado una verificación posterior. |
| Integración backend | No implementada ni validada. |

La EC2 de staging usa el rol `sgp-staging-ec2-role`. Su permiso confirmado es
`AmazonSSMManagedInstanceCore`; no se ha asignado una política S3. La instancia
exige IMDSv2 y usa hop limit `2`, condición necesaria para que un SDK dentro de
Docker pueda consultar las credenciales temporales del rol.

No deben crearse access keys para la aplicación ni variables
`AWS_ACCESS_KEY_ID` o `AWS_SECRET_ACCESS_KEY` en `.env`. En AWS, el SDK debe usar
la cadena de credenciales predeterminada y el rol de instancia.

## 3. Estado del repositorio

### Preparado

- `docker-compose.yml` contiene MinIO para desarrollo local, publicado solo en
  loopback (`19000` API y `19001` consola) y con volumen `minio_data`.
- `docs/DEVELOPMENT.md` aclara que MinIO es infraestructura preparada, no una
  dependencia funcional actual del backend.
- `sgp-api/src/main/resources/db/migration/V10__mantenimiento_y_archivos.sql`
  ya creó el modelo de metadatos. La migración aplicada no debe modificarse.
- `docs/ARCHITECTURE.md` reserva el dominio backend `archivo/` y establece que
  las capacidades frontend se integran en las features que gestionan adjuntos.
- El frontend conserva marcadores explícitos de HU-028 para el reporte
  fotográfico pendiente y la persistencia offline debe pasar por `offline/`.

### No implementado

- No existe el contrato `AlmacenamientoArchivos` ni una implementación S3 o
  MinIO en el backend.
- No existe dependencia ni configuración del AWS SDK en `sgp-api`.
- No hay endpoints funcionales de carga, descarga, confirmación o eliminación.
- No hay variables de bucket consumidas por Spring Boot.
- No hay política IAM S3 asociada al rol de EC2.
- No se han probado cargas, descargas, autorizaciones, purga ni recuperación de
  objetos en staging.

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

## 5. Requisitos identificados en el enunciado

La futura HU debe confirmar al menos los siguientes requisitos explícitos:

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

El enunciado define MinIO compatible con S3 para el servidor universitario. El
ADR-0002 adapta esa decisión a AWS: MinIO permanece local y S3 es el proveedor
de staging/producción. La portabilidad se conserva mediante
`AlmacenamientoArchivos`.

## 6. Diseño que debe completar la futura HU

### Contrato y adaptadores

Definir el contrato en el dominio `archivo/` con las operaciones mínimas que
exija la HU, por ejemplo almacenar, leer y retirar. Los DTO del dominio no deben
exponer clases del SDK ni nombres de bucket. Las implementaciones concretas de
S3 y MinIO pertenecen a infraestructura.

Antes de fijar una API, decidir con el equipo:

- si el backend transmite siempre los bytes o emite URLs firmadas cortas;
- cómo se confirma una carga y se recuperan objetos huérfanos ante un fallo de
  la transacción de PostgreSQL;
- qué operaciones son idempotentes y cómo se relacionan con la cola offline;
- estructura de claves y prefijos por ambiente y contexto;
- comportamiento exacto de retiro, purga y conservación de metadatos;
- dónde se genera la miniatura de 300 px;
- cómo se valida autorización para cada tipo de asociación.

Los nombres originales nunca deben formar por sí solos la clave. La clave debe
ser generada por el servidor, no revelar datos personales y evitar colisiones.

### Configuración por ambiente

- Desarrollo: MinIO local y credenciales exclusivamente locales; definir
  endpoint y path-style solo en el adaptador de desarrollo.
- Staging: S3 en `us-east-1`, bucket confirmado y credenciales temporales del
  rol EC2.
- Producción: bucket y rol propios, aún no creados ni nombrados en esta guía.

Los nombres exactos de propiedades de Spring Boot todavía no existen. Deben
definirse junto con la implementación, documentarse en `.env.example` sin
secretos y validarse al arranque. La región y el nombre de bucket no son
secretos; las credenciales sí y no deben configurarse manualmente en AWS.

## 7. IAM de mínimo privilegio pendiente

Primero deben definirse los flujos y prefijos. Después se creará una política
limitada al bucket de staging. Según las operaciones realmente implementadas,
podría requerir:

- `s3:ListBucket`, restringido al prefijo aprobado;
- `s3:GetObject` para descarga o generación de URL firmada;
- `s3:PutObject` para carga;
- `s3:DeleteObject` solo si retiro/purga ejecuta borrado físico.

No conceder `s3:*`, acceso a `arn:aws:s3:::*`, administración del bucket ni
permisos sobre otros ambientes. Si la aplicación no lista objetos, omitir
`ListBucket`; si solo marca objetos como retirados, no conceder `DeleteObject`
hasta implementar la purga autorizada.

La política deberá separar el recurso bucket de los recursos objeto y, cuando
se acuerde una estructura, restringir `s3:prefix`. Debe asociarse a
`sgp-staging-ec2-role`, no al rol OIDC de GitHub Actions. El workflow despliega;
la aplicación en EC2 es quien accede a S3.

Después de asociarla, verificar desde el contenedor que el SDK obtiene
credenciales temporales. No imprimir tokens de sesión ni respuestas de IMDS en
logs. La rotación corresponde a AWS mediante el rol.

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
- respaldos mensuales y prueba de restauración exigidos por el enunciado.

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

## 10. Orden sugerido de implementación

1. Revisar la HU, el enunciado, V10 y las features consumidoras.
2. Aprobar contrato, flujos de autorización, claves, consistencia y purga.
3. Crear pruebas del contrato y el adaptador MinIO local.
4. Implementar servicio de aplicación, DTO y endpoints bajo `/api/v1`.
5. Integrar frontend/offline respetando compresión, UUID v7 e idempotencia.
6. Definir propiedades externas y adaptar S3 sin credenciales estáticas.
7. Diseñar y revisar la política IAM de mínimo privilegio.
8. Aplicar la política en AWS mediante una actividad autorizada.
9. Desplegar por CI/CD y ejecutar las pruebas reales de staging.
10. Documentar operación, observabilidad, respaldo y restauración.

## 11. Criterios de aceptación propuestos

Sujetos a la HU y aprobación del equipo:

1. Existe `AlmacenamientoArchivos` con adaptadores intercambiables para MinIO y
   S3 sin dependencias AWS en el dominio.
2. Los binarios están fuera de PostgreSQL y los metadatos usan el modelo
   existente o una migración nueva compatible.
3. Carga, descarga, retiro y purga respetan autenticación, autorización, estado
   y límites del enunciado.
4. Fotos se comprimen en cliente, conservan geolocalización/fecha y almacenan
   original más miniatura.
5. Desarrollo funciona con MinIO y staging con el rol EC2, sin access keys.
6. La política IAM se limita al bucket y operaciones/prefijos necesarios.
7. El bucket continúa privado y los accesos son autorizados o firmados y breves.
8. Pruebas automatizadas y reales de staging cubren éxito, rechazo y fallos
   parciales.
9. Existe una política de respaldo/restauración probada o la excepción queda
   explícitamente aceptada; no se declara DT-ALM-05 completo sin evidencia.
10. La documentación y plantillas enumeran configuración sin secretos.

## 12. Riesgos, dependencias y pendientes

- La HU funcional de archivos y sus prioridades deben confirmarse; HU-028 se
  menciona en el frontend, pero esta guía no inventa su definición formal.
- La consistencia entre S3 y PostgreSQL no es transaccional y necesita un diseño
  de idempotencia, compensación y limpieza de huérfanos.
- La EC2 `t4g.micro` tiene recursos limitados; procesamiento de imágenes debe
  medirse para evitar presión de memoria y CPU.
- El bucket no tiene versionado ni política de recuperación aplicada.
- La retención y restauración de objetos siguen fuera de la HU de staging ya
  implementada, pero son requisitos globales pendientes.
- El SMTP ficticio afecta notificaciones de borradores si esa función usa correo.
- La integración de actas depende también del trabajo pendiente documentado en
  `docs/pendientes/HU-019-acta-calibracion.md`.
- Producción requiere bucket, rol, política, datos y pruebas independientes.

## 13. Evidencia y referencias del repositorio

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
