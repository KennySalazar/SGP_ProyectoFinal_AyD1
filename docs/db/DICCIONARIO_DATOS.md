# Diccionario de datos

Descripción de cada tabla y columna de la base de datos de SGP (PostgreSQL 16 con PostGIS). Corresponde al esquema que resulta de aplicar las migraciones Flyway `V1` a `V25__planes_mantenimiento_rutinario`: **48 tablas y 532 columnas**.

Tipos, nulabilidad, valores por defecto, claves, restricciones e índices se extrajeron del catálogo de una base recién migrada, no se transcribieron a mano. Los valores permitidos de cada columna salen de sus restricciones `CHECK`. El diagrama entidad-relación está en [`diagrama_de_tablas_db.png`](diagrama_de_tablas_db.png).

## Convenciones

- **Identificadores:** `uuid` generados por la aplicación como UUID v7 (ordenables por tiempo).
- **Fechas y horas:** `timestamp with time zone`, guardadas en UTC; la interfaz las muestra en hora de Guatemala.
- **`version`:** contador para el bloqueo optimista de JPA; una escritura con versión vieja responde 409.
- **Geografía:** `geography(Point,4326)` para ubicaciones y `geometry(...,4326)` para límites territoriales (WGS84).
- **JSON:** `jsonb` para el formulario dinámico, desgloses del IC, manifiestos y bitácoras.
- **Borrado lógico:** las tablas de negocio marcan fechas (`eliminado_en`, `inactivado_en`, `retirado_en`) en lugar de borrar filas.
- **Columna *Clave*:** `PK` clave primaria, `FK →` llave foránea (tabla y columna destino), `UNIQUE` valor único.
- Las tablas técnicas `flyway_schema_history` (Flyway) y `spatial_ref_sys` (PostGIS) no se describen.

## Índice

- **Seguridad, usuarios y acceso:** [`rol`](#rol), [`usuario`](#usuario), [`usuario_profesional`](#usuario_profesional), [`invitacion_usuario`](#invitacion_usuario), [`desafio_otp`](#desafio_otp), [`token_refresco`](#token_refresco), [`auditoria`](#auditoria)
- **Territorio y academia:** [`departamento`](#departamento), [`municipio`](#municipio), [`municipio_limite`](#municipio_limite), [`limite_territorial`](#limite_territorial), [`asignatura`](#asignatura), [`curso`](#curso), [`curso_estudiante`](#curso_estudiante)
- **Inventario de puentes:** [`puente`](#puente), [`solicitud_alta_puente`](#solicitud_alta_puente), [`asignacion_puente`](#asignacion_puente)
- **Formulario dinámico e índice de condición:** [`version_formulario`](#version_formulario), [`configuracion_ic_elemento`](#configuracion_ic_elemento), [`mapeo_campo_formulario`](#mapeo_campo_formulario), [`aprobacion_calibracion_ic`](#aprobacion_calibracion_ic), [`resultado_ic`](#resultado_ic), [`confirmacion_estado_inspeccion`](#confirmacion_estado_inspeccion)
- **Inspecciones:** [`preparacion_salida`](#preparacion_salida), [`inspeccion`](#inspeccion), [`inspeccion_acompanante`](#inspeccion_acompanante), [`inspeccion_dato_tecnico`](#inspeccion_dato_tecnico), [`inspeccion_tramo`](#inspeccion_tramo), [`historial_estado_inspeccion`](#historial_estado_inspeccion)
- **Revisión académica:** [`revision`](#revision), [`observacion_revision`](#observacion_revision)
- **Archivos y evidencia:** [`archivo`](#archivo), [`archivo_inspeccion`](#archivo_inspeccion), [`archivo_evidencia_mantenimiento`](#archivo_evidencia_mantenimiento), [`archivo_acta_calibracion`](#archivo_acta_calibracion)
- **Mantenimiento:** [`regla_mantenimiento`](#regla_mantenimiento), [`orden_mantenimiento`](#orden_mantenimiento), [`orden_mantenimiento_ejecucion`](#orden_mantenimiento_ejecucion), [`historial_estado_orden`](#historial_estado_orden), [`plan_mantenimiento_rutinario`](#plan_mantenimiento_rutinario), [`plan_mantenimiento_rutinario_actividad`](#plan_mantenimiento_rutinario_actividad)
- **Foro y avisos:** [`foro_hilo`](#foro_hilo), [`foro_comentario`](#foro_comentario), [`comentario_mencion`](#comentario_mencion), [`notificacion`](#notificacion)
- **Sincronización offline e infraestructura:** [`operacion_idempotente`](#operacion_idempotente), [`conflicto_sincronizacion`](#conflicto_sincronizacion), [`shedlock`](#shedlock)

## Seguridad, usuarios y acceso

### rol

Catálogo de los cuatro roles del sistema: ADMINISTRADOR, CATEDRATICO, PROFESIONAL_EXTERNO y ESTUDIANTE. *Creada en `V1`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `nombre` | `character varying(40)` | No |  | UNIQUE | Nombre único del rol. |
| `descripcion` | `character varying(255)` | No |  |  | Descripción legible del rol. |

### usuario

Cuenta de acceso de una persona. Concentra credenciales, estado de la cuenta, protección contra fuerza bruta y el rol asignado. *Creada en `V1`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `email` | `character varying(320)` | No |  | UNIQUE | Correo de inicio de sesión, único y en minúsculas. |
| `password_hash` | `character varying(100)` | No |  |  | Hash BCrypt de la contraseña. |
| `verificado` | `boolean` | No | `false` |  | El correo fue verificado con un código OTP. |
| `activado` | `boolean` | No | `false` |  | La cuenta fue activada por quien corresponde (catedrático o invitación aceptada). |
| `activo` | `boolean` | No | `true` |  | La cuenta puede usarse; falso cuando un administrador la desactiva. |
| `two_factor_habilitado` | `boolean` | No | `false` |  | El usuario exige código OTP adicional al iniciar sesión. |
| `token_version` | `integer` | No | `0` |  | Versión de las sesiones; al incrementarse invalida los tokens emitidos antes. |
| `intentos_login_fallidos` | `integer` | No | `0` |  | Intentos fallidos de inicio de sesión en la ventana actual. |
| `ventana_intentos_iniciada_en` | `timestamp with time zone` | Sí |  |  | Inicio de la ventana en que se cuentan los intentos fallidos. |
| `bloqueado_hasta` | `timestamp with time zone` | Sí |  |  | Fin del bloqueo temporal por intentos fallidos. |
| `rol_id` | `uuid` | No |  | FK → `rol.id` | Rol asignado al usuario. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |
| `nombre_completo` | `character varying(200)` | Sí |  |  | Nombre completo de la persona (no se expone en el foro). |
| `nombre_usuario` | `character varying(60)` | Sí |  |  | Nombre de usuario público, único sin distinguir mayúsculas; se usa en el foro y en las menciones. |
| `desactivado_en` | `timestamp with time zone` | Sí |  |  | Momento en que se desactivó la cuenta. |
| `desactivado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Administrador que desactivó la cuenta. |
| `motivo_desactivacion` | `character varying(1000)` | Sí |  |  | Motivo registrado al desactivar la cuenta. |

**Restricciones e índices adicionales**

- **CHECK** `usuario_email_lowercase`: `CHECK (((email) = lower((email))))`
- **CHECK** `usuario_intentos_nonnegative`: `CHECK ((intentos_login_fallidos >= 0))`
- **CHECK** `usuario_token_version_nonnegative`: `CHECK ((token_version >= 0))`
- **Índice** `idx_usuario_email_trgm`: `INDEX idx_usuario_email_trgm ON usuario USING gin (email gin_trgm_ops)`
- **Índice** `idx_usuario_rol_activo`: `INDEX idx_usuario_rol_activo ON usuario (rol_id, activo)`
- **Índice** `uq_usuario_nombre_usuario_lower`: `UNIQUE INDEX uq_usuario_nombre_usuario_lower ON usuario (lower((nombre_usuario)::text)) WHERE (nombre_usuario IS NOT NULL)`

### usuario_profesional

Subtipo 1:1 de usuario solo para profesionales externos: guarda el número de colegiado y su verificación por un administrador. *Creada en `V4`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `usuario_id` | `uuid` | No |  | FK → `usuario.id`<br>PK | Usuario profesional externo al que pertenece el registro (clave primaria). |
| `numero_colegiado` | `character varying(50)` | No |  | UNIQUE | Número de colegiado profesional, único. |
| `colegiado_verificado_en` | `timestamp with time zone` | Sí |  |  | Momento en que un administrador verificó la colegiación. |
| `colegiado_verificado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Administrador que verificó la colegiación. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |

**Restricciones e índices adicionales**

- **CHECK** `usuario_profesional_verificacion_completa`: `CHECK ((((colegiado_verificado_en IS NULL) AND (colegiado_verificado_por_id IS NULL)) OR ((colegiado_verificado_en IS NOT NULL) AND (colegiado_verificado_por_id IS NOT NULL))))`

### invitacion_usuario

Invitación por correo para crear una cuenta con un rol controlado (catedráticos y profesionales externos). *Creada en `V4`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `email` | `character varying(320)` | No |  |  | Correo al que se envió la invitación. |
| `rol_id` | `uuid` | No |  | FK → `rol.id` | Rol con que se creará la cuenta. |
| `token_hash` | `character varying(64)` | No |  | UNIQUE | Hash del token del enlace de activación. |
| `invitado_por_id` | `uuid` | No |  | FK → `usuario.id` | Administrador que emitió la invitación. |
| `expira_en` | `timestamp with time zone` | No |  |  | Vencimiento de la invitación. |
| `aceptado_en` | `timestamp with time zone` | Sí |  |  | Momento en que se aceptó y se creó la cuenta. |
| `cancelado_en` | `timestamp with time zone` | Sí |  |  | Momento en que se revocó la invitación. |
| `usuario_creado_id` | `uuid` | Sí |  | FK → `usuario.id` | Cuenta creada al aceptar la invitación. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |

**Restricciones e índices adicionales**

- **CHECK** `invitacion_email_lowercase`: `CHECK (((email) = lower((email))))`
- **CHECK** `invitacion_estado_excluyente`: `CHECK ((NOT ((aceptado_en IS NOT NULL) AND (cancelado_en IS NOT NULL))))`
- **CHECK** `invitacion_expira_despues_creacion`: `CHECK ((expira_en > creado_en))`
- **Índice** `idx_invitacion_expira`: `INDEX idx_invitacion_expira ON invitacion_usuario (expira_en)`
- **Índice** `uq_invitacion_pendiente_email_rol`: `UNIQUE INDEX uq_invitacion_pendiente_email_rol ON invitacion_usuario (email, rol_id) WHERE ((aceptado_en IS NULL) AND (cancelado_en IS NULL))`

### desafio_otp

Código temporal de un solo uso enviado por correo para verificar registro, iniciar sesión con 2FA o recuperar contraseña. *Creada en `V1`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `usuario_id` | `uuid` | No |  | FK → `usuario.id` ON DELETE CASCADE | Usuario al que se envió el código. |
| `proposito` | `character varying(40)` | No |  |  | Uso del código. Valores: `REGISTRO`, `LOGIN_2FA`, `RECUPERACION_PASSWORD`, `ACTIVAR_2FA`, `DESACTIVAR_2FA`. |
| `codigo_hash` | `character varying(100)` | No |  |  | Hash del código enviado; el código no se guarda en claro. |
| `expira_en` | `timestamp with time zone` | No |  |  | Vencimiento del código. |
| `intentos` | `integer` | No | `0` |  | Intentos de verificación realizados. |
| `max_intentos` | `integer` | No |  |  | Intentos permitidos antes de invalidar el código. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `consumido_en` | `timestamp with time zone` | Sí |  |  | Momento en que el código se usó correctamente. |

**Restricciones e índices adicionales**

- **CHECK** `desafio_otp_intentos_nonnegative`: `CHECK ((intentos >= 0))`
- **CHECK** `desafio_otp_max_intentos_positive`: `CHECK ((max_intentos > 0))`
- **Índice** `idx_desafio_otp_expira`: `INDEX idx_desafio_otp_expira ON desafio_otp (expira_en)`
- **Índice** `idx_desafio_otp_usuario_proposito_creado`: `INDEX idx_desafio_otp_usuario_proposito_creado ON desafio_otp (usuario_id, proposito, creado_en DESC)`

### token_refresco

Token de refresco de sesión emitido al usuario; solo se guarda su hash. *Creada en `V1`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `usuario_id` | `uuid` | No |  | FK → `usuario.id` ON DELETE CASCADE | Usuario dueño de la sesión. |
| `token_hash` | `character varying(64)` | No |  | UNIQUE | Hash del token de refresco entregado en la cookie. |
| `expira_en` | `timestamp with time zone` | No |  |  | Vencimiento del token. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `revocado_en` | `timestamp with time zone` | Sí |  |  | Momento en que se revocó (cierre de sesión o rotación). |

**Restricciones e índices adicionales**

- **Índice** `idx_token_refresco_usuario`: `INDEX idx_token_refresco_usuario ON token_refresco (usuario_id, expira_en)`

### auditoria

Bitácora de acciones relevantes (crear, modificar, cambiar estado, moderar, procesos programados) con valores antes y después (RN-USR-09). *Creada en `V1`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `usuario_id` | `uuid` | Sí |  | FK → `usuario.id` | Usuario que realizó la acción; nulo si la ejecutó un proceso automático. |
| `accion` | `character varying(100)` | No |  |  | Acción registrada: CREAR, MODIFICAR, CAMBIAR_ESTADO, MODERAR o EJECUTAR_PROCESO. |
| `entidad` | `character varying(100)` | No |  |  | Tabla o concepto afectado (por ejemplo foro_comentario). |
| `entidad_id` | `uuid` | Sí |  |  | Identificador del registro afectado. |
| `valores_anteriores` | `jsonb` | Sí |  |  | Valores relevantes antes de la acción (JSON). |
| `valores_posteriores` | `jsonb` | Sí |  |  | Valores relevantes después de la acción, incluido el motivo cuando aplica (JSON). |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `proceso_automatico` | `character varying(100)` | Sí |  |  | Nombre del proceso automático que originó la acción, si no fue un usuario. |

**Restricciones e índices adicionales**

- **Índice** `idx_auditoria_creado`: `INDEX idx_auditoria_creado ON auditoria (creado_en DESC)`
- **Índice** `idx_auditoria_entidad_id_creado`: `INDEX idx_auditoria_entidad_id_creado ON auditoria (entidad, entidad_id, creado_en DESC)`
- **Índice** `idx_auditoria_posteriores_gin`: `INDEX idx_auditoria_posteriores_gin ON auditoria USING gin (valores_posteriores)`
- **Índice** `idx_auditoria_usuario_creado`: `INDEX idx_auditoria_usuario_creado ON auditoria (usuario_id, creado_en DESC)`

## Territorio y academia

### departamento

Catálogo de departamentos de Guatemala según el INE. *Creada en `V5`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `codigo_ine` | `character varying(10)` | No |  | UNIQUE | Código INE del departamento, único. |
| `nombre` | `character varying(100)` | No |  |  | Nombre del departamento. |
| `activo` | `boolean` | No | `true` |  | El departamento se ofrece en los catálogos. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |

**Restricciones e índices adicionales**

- **Índice** `uq_departamento_nombre_lower`: `UNIQUE INDEX uq_departamento_nombre_lower ON departamento (lower((nombre)::text))`

### municipio

Catálogo de municipios de Guatemala según el INE; lleva el correlativo usado para codificar los puentes. *Creada en `V5`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `departamento_id` | `uuid` | No |  | FK → `departamento.id` | Departamento al que pertenece. |
| `codigo_ine` | `character varying(10)` | No |  | UNIQUE | Código INE del municipio, único. |
| `nombre` | `character varying(120)` | No |  |  | Nombre del municipio. |
| `activo` | `boolean` | No | `true` |  | El municipio se ofrece en los catálogos. |
| `ultimo_correlativo_puente` | `integer` | No | `0` |  | Último correlativo asignado a un puente del municipio; alimenta el código del puente. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |

**Restricciones e índices adicionales**

- **CHECK** `municipio_correlativo_rango`: `CHECK (((ultimo_correlativo_puente >= 0) AND (ultimo_correlativo_puente <= 9999)))`
- **Índice** `idx_municipio_departamento`: `INDEX idx_municipio_departamento ON municipio (departamento_id)`
- **Índice** `uq_municipio_departamento_nombre_lower`: `UNIQUE INDEX uq_municipio_departamento_nombre_lower ON municipio (departamento_id, lower((nombre)::text))`

### municipio_limite

Polígono del límite de un municipio, usado para validar que la ubicación de un puente cae en su municipio. *Creada en `V14`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `municipio_id` | `uuid` | No |  | FK → `municipio.id`<br>PK | Municipio al que pertenece el polígono. |
| `fuente` | `text` | No |  |  | Fuente de la geometría. |
| `geometria` | `geometry(MultiPolygon,4326)` | No |  |  | Polígono del límite municipal (PostGIS, SRID 4326). |

**Restricciones e índices adicionales**

- **CHECK** `municipio_limite_geometria_no_vacia`: `CHECK ((NOT st_isempty(geometria)))`
- **CHECK** `municipio_limite_geometria_valida`: `CHECK (st_isvalid(geometria))`
- **Índice** `idx_municipio_limite_geometria_gist`: `INDEX idx_municipio_limite_geometria_gist ON municipio_limite USING gist (geometria)`

### limite_territorial

Polígono del territorio nacional, usado para validar que una ubicación está dentro de Guatemala. *Creada en `V13`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `codigo` | `character varying(3)` | No |  | PK | Código del territorio (país). |
| `nombre` | `character varying(100)` | No |  |  | Nombre del territorio. |
| `fuente` | `text` | No |  |  | Fuente de la geometría. |
| `geometria` | `geometry(MultiPolygon,4326)` | No |  |  | Polígono del territorio (PostGIS, SRID 4326). |

**Restricciones e índices adicionales**

- **CHECK** `limite_territorial_geometria_no_vacia`: `CHECK ((NOT st_isempty(geometria)))`
- **CHECK** `limite_territorial_geometria_valida`: `CHECK (st_isvalid(geometria))`
- **Índice** `idx_limite_territorial_geometria_gist`: `INDEX idx_limite_territorial_geometria_gist ON limite_territorial USING gist (geometria)`

### asignatura

Asignatura académica estable, identificada por su código, independiente del período en que se imparte. *Creada en `V5`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `codigo` | `character varying(50)` | No |  | UNIQUE | Código oficial de la asignatura, único. |
| `nombre` | `character varying(150)` | No |  |  | Nombre de la asignatura. |
| `activo` | `boolean` | No | `true` |  | La asignatura puede ofertarse. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |

### curso

Oferta de una asignatura en un período, a cargo de un catedrático. *Creada en `V5`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `asignatura_id` | `uuid` | No |  | FK → `asignatura.id` | Asignatura que se imparte. |
| `periodo` | `character varying(50)` | No |  |  | Período académico del curso. |
| `catedratico_id` | `uuid` | No |  | FK → `usuario.id` | Catedrático a cargo. |
| `fecha_inicio` | `date` | No |  |  | Inicio del curso. |
| `fecha_fin` | `date` | No |  |  | Fin del curso. |
| `activo` | `boolean` | No | `true` |  | El curso admite operaciones. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |

**Restricciones e índices adicionales**

- **CHECK** `curso_fechas_validas`: `CHECK ((fecha_fin >= fecha_inicio))`
- **UNIQUE** `uq_curso_oferta`: `UNIQUE (asignatura_id, periodo)`
- **Índice** `idx_curso_asignatura`: `INDEX idx_curso_asignatura ON curso (asignatura_id)`
- **Índice** `idx_curso_catedratico_activo`: `INDEX idx_curso_catedratico_activo ON curso (catedratico_id, activo)`

### curso_estudiante

Inscripción de un estudiante en un curso, con su estado de aprobación por el catedrático y su sección. *Creada en `V5`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `curso_id` | `uuid` | No |  | FK → `curso.id` | Curso en que se inscribe. |
| `estudiante_id` | `uuid` | No |  | FK → `usuario.id` | Estudiante inscrito. |
| `estado` | `character varying(20)` | No | `'ACTIVO'` |  | Estado de la inscripción; RETIRADO exige fecha de retiro. Valores: `ACTIVO`, `RETIRADO`. |
| `inscrito_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la inscripción. |
| `inscrito_por_id` | `uuid` | No |  | FK → `usuario.id` | Usuario que aprobó o registró la inscripción. |
| `retirado_en` | `timestamp with time zone` | Sí |  |  | Momento en que el estudiante quedó retirado. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |
| `seccion` | `character varying(30)` | No |  |  | Sección del curso a la que pertenece el estudiante. |

**Restricciones e índices adicionales**

- **CHECK** `curso_estudiante_retiro_coherente`: `CHECK (((((estado) = 'ACTIVO') AND (retirado_en IS NULL)) OR (((estado) = 'RETIRADO') AND (retirado_en IS NOT NULL))))`
- **UNIQUE** `uq_curso_estudiante`: `UNIQUE (curso_id, estudiante_id)`
- **Índice** `idx_curso_estudiante_estudiante_estado`: `INDEX idx_curso_estudiante_estudiante_estado ON curso_estudiante (estudiante_id, estado)`

## Inventario de puentes

### puente

Activo físico del inventario. Además de sus datos fijos, conserva el estado vigente que resulta de la última inspección publicada. *Creada en `V6`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | FK compuesta → `inspeccion`<br>PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `codigo` | `character varying(40)` | No |  | UNIQUE | Código único del puente (departamento, municipio y correlativo). |
| `correlativo_municipal` | `integer` | No |  |  | Correlativo del puente dentro de su municipio. |
| `municipio_id` | `uuid` | No |  | FK → `municipio.id` | Municipio donde se ubica. |
| `nombre` | `character varying(200)` | No |  |  | Nombre del puente. |
| `ruta` | `character varying(100)` | No |  |  | Ruta o carretera en que se encuentra. |
| `kilometraje` | `numeric(10,3)` | Sí |  |  | Kilómetro de la ruta en que se ubica. |
| `ubicacion` | `geography(Point,4326)` | No |  |  | Punto geográfico del puente (PostGIS geography, WGS84). |
| `activo` | `boolean` | No | `true` |  | El puente forma parte del inventario activo; falso tras darlo de baja. |
| `inactivado_en` | `timestamp with time zone` | Sí |  |  | Momento de la baja del puente. |
| `inactivado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Administrador que dio de baja el puente. |
| `motivo_inactivacion` | `character varying(1000)` | Sí |  |  | Motivo de la baja. |
| `creado_por_id` | `uuid` | No |  | FK → `usuario.id` | Usuario que registró el puente. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |
| `estado_actual` | `character varying(20)` | No | `'Sin evaluar'` |  | Estado vigente: Sin evaluar, Bueno, Regular o Malo, según la última inspección publicada. Valores: `Sin evaluar`, `Bueno`, `Regular`, `Malo`. |
| `indice_condicion_actual` | `numeric(8,5)` | Sí |  |  | Índice de condición vigente (0 a 100). |
| `fecha_ultima_inspeccion` | `date` | Sí |  |  | Fecha de la inspección vigente. |
| `inspeccion_vigente_id` | `uuid` | Sí |  | FK compuesta → `inspeccion`<br>FK compuesta → `resultado_ic` | Inspección publicada que determina el estado vigente. |
| `resultado_ic_vigente_id` | `uuid` | Sí |  | FK compuesta → `resultado_ic` | Resultado del IC que determina el estado vigente. |
| `inspeccion_vigente_publicada_en` | `timestamp with time zone` | Sí |  |  | Momento de publicación de la inspección vigente. |

**Restricciones e índices adicionales**

- **CHECK** `puente_correlativo_rango`: `CHECK (((correlativo_municipal >= 1) AND (correlativo_municipal <= 9999)))`
- **CHECK** `puente_estado_vigente_completo`: `CHECK (((((estado_actual) = 'Sin evaluar') AND (indice_condicion_actual IS NULL) AND (fecha_ultima_inspeccion IS NULL) AND (inspeccion_vigente_id IS NULL) AND (resultado_ic_vigente_id IS NULL) AND (inspeccion_vigente_publicada_en IS NULL)) OR (((estado_actual) = ANY (ARRAY['Bueno', 'Regular', 'Malo']))) AND (indice_condicion_actual IS NOT NULL) AND (fecha_ultima_inspeccion IS NOT NULL) AND (inspeccion_vigente_id IS NOT NULL) AND (resultado_ic_vigente_id IS NOT NULL) AND (inspeccion_vigente_publicada_en IS NOT NULL))))`
- **CHECK** `puente_ic_actual_rango`: `CHECK (((indice_condicion_actual IS NULL) OR ((indice_condicion_actual >= (0)) AND (indice_condicion_actual <= (100)))))`
- **CHECK** `puente_inactivacion_completa`: `CHECK (((activo AND (inactivado_en IS NULL) AND (inactivado_por_id IS NULL) AND (motivo_inactivacion IS NULL)) OR ((NOT activo) AND (inactivado_en IS NOT NULL) AND (inactivado_por_id IS NOT NULL) AND (motivo_inactivacion IS NOT NULL))))`
- **CHECK** `puente_kilometraje_no_negativo`: `CHECK (((kilometraje IS NULL) OR (kilometraje >= (0))))`
- **FK** `fk_puente_inspeccion_vigente`: `FOREIGN KEY (inspeccion_vigente_id, id) REFERENCES inspeccion(id, puente_id)`
- **FK** `fk_puente_resultado_ic_vigente`: `FOREIGN KEY (resultado_ic_vigente_id, inspeccion_vigente_id) REFERENCES resultado_ic(id, inspeccion_id)`
- **UNIQUE** `uq_puente_id_municipio`: `UNIQUE (id, municipio_id)`
- **UNIQUE** `uq_puente_municipio_correlativo`: `UNIQUE (municipio_id, correlativo_municipal)`
- **Índice** `idx_puente_municipio_activo`: `INDEX idx_puente_municipio_activo ON puente (municipio_id, activo)`
- **Índice** `idx_puente_nombre_trgm`: `INDEX idx_puente_nombre_trgm ON puente USING gin (nombre gin_trgm_ops)`
- **Índice** `idx_puente_ruta`: `INDEX idx_puente_ruta ON puente (ruta)`
- **Índice** `idx_puente_ubicacion_gist`: `INDEX idx_puente_ubicacion_gist ON puente USING gist (ubicacion)`

### solicitud_alta_puente

Solicitud de un estudiante para dar de alta un puente que no existe en el inventario; la resuelve un administrador. *Creada en `V6`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `solicitado_por_id` | `uuid` | No |  | FK → `usuario.id` | Estudiante que solicita el alta. |
| `nombre_propuesto` | `character varying(200)` | No |  |  | Nombre propuesto para el puente. |
| `municipio_id` | `uuid` | No |  | FK → `municipio.id` | Municipio propuesto. |
| `ruta` | `character varying(100)` | No |  |  | Ruta propuesta. |
| `kilometraje` | `numeric(10,3)` | Sí |  |  | Kilometraje propuesto. |
| `ubicacion` | `geography(Point,4326)` | No |  |  | Ubicación propuesta (PostGIS geography). |
| `justificacion` | `text` | Sí |  |  | Por qué debe incorporarse el puente. |
| `estado` | `character varying(20)` | No | `'PENDIENTE'` |  | Estado de la solicitud; APROBADA exige el puente creado y RECHAZADA exige motivo. Valores: `PENDIENTE`, `APROBADA`, `RECHAZADA`, `CANCELADA`. |
| `revisado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Administrador que resolvió la solicitud. |
| `revisado_en` | `timestamp with time zone` | Sí |  |  | Momento de la resolución. |
| `motivo_decision` | `text` | Sí |  |  | Motivo de la aprobación o el rechazo. |
| `puente_creado_id` | `uuid` | Sí |  | FK → `puente.id` | Puente creado al aprobarse la solicitud. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |

**Restricciones e índices adicionales**

- **CHECK** `solicitud_aprobada_completa`: `CHECK ((((estado) <> 'APROBADA') OR ((revisado_por_id IS NOT NULL) AND (revisado_en IS NOT NULL) AND (puente_creado_id IS NOT NULL))))`
- **CHECK** `solicitud_kilometraje_no_negativo`: `CHECK (((kilometraje IS NULL) OR (kilometraje >= (0))))`
- **CHECK** `solicitud_rechazada_completa`: `CHECK ((((estado) <> 'RECHAZADA') OR ((revisado_por_id IS NOT NULL) AND (revisado_en IS NOT NULL) AND (motivo_decision IS NOT NULL))))`
- **Índice** `idx_solicitud_estado_creado`: `INDEX idx_solicitud_estado_creado ON solicitud_alta_puente (estado, creado_en)`
- **Índice** `idx_solicitud_solicitante`: `INDEX idx_solicitud_solicitante ON solicitud_alta_puente (solicitado_por_id)`
- **Índice** `idx_solicitud_ubicacion_gist`: `INDEX idx_solicitud_ubicacion_gist ON solicitud_alta_puente USING gist (ubicacion)`
- **Índice** `uq_solicitud_puente_creado`: `UNIQUE INDEX uq_solicitud_puente_creado ON solicitud_alta_puente (puente_creado_id) WHERE (puente_creado_id IS NOT NULL)`

### asignacion_puente

Asignación histórica de un puente a un estudiante inscrito, hecha por su catedrático. *Creada en `V6`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `curso_estudiante_id` | `uuid` | No |  | FK → `curso_estudiante.id` | Inscripción del estudiante al que se asigna el puente. |
| `puente_id` | `uuid` | No |  | FK → `puente.id` | Puente asignado. |
| `asignado_por_id` | `uuid` | No |  | FK → `usuario.id` | Catedrático que hizo la asignación. |
| `asignado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la asignación. |
| `revocado_en` | `timestamp with time zone` | Sí |  |  | Momento en que se revocó. |
| `motivo_revocacion` | `character varying(1000)` | Sí |  |  | Motivo de la revocación. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **CHECK** `asignacion_revocacion_completa`: `CHECK ((((revocado_en IS NULL) AND (motivo_revocacion IS NULL)) OR ((revocado_en IS NOT NULL) AND (motivo_revocacion IS NOT NULL))))`
- **Índice** `idx_asignacion_puente_puente`: `INDEX idx_asignacion_puente_puente ON asignacion_puente (puente_id)`
- **Índice** `uq_asignacion_puente_activa`: `UNIQUE INDEX uq_asignacion_puente_activa ON asignacion_puente (curso_estudiante_id, puente_id) WHERE (revocado_en IS NULL)`

## Formulario dinámico e índice de condición

### version_formulario

Versión del formulario dinámico de inspección (esquema JSON) con su configuración de cálculo del índice de condición. *Creada en `V7`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `codigo` | `character varying(50)` | No |  | UNIQUE | Código único de la versión (por ejemplo SIECA-v1). |
| `estado` | `character varying(20)` | No | `'BORRADOR'` |  | Estado de la versión; una publicada no cambia y exige hash, versión de algoritmo y quién la publicó. Valores: `BORRADOR`, `PUBLICADA`. |
| `activa` | `boolean` | No | `false` |  | Es la versión que se usa para nuevas inspecciones; solo una puede estarlo y debe estar publicada. |
| `esquema` | `jsonb` | No |  |  | Esquema JSON del formulario dinámico. |
| `vinculos_almacenamiento` | `jsonb` | No | `'{}'` |  | Correspondencia entre campos del formulario y columnas consultables (JSON). |
| `reglas_evaluacion_ic` | `jsonb` | No | `'{}'` |  | Reglas de cálculo del índice de condición: rangos de estado y reglas críticas (JSON). |
| `hash_contenido` | `character varying(64)` | Sí |  |  | Hash del contenido publicado, para detectar alteraciones. |
| `version_algoritmo_ic` | `character varying(50)` | Sí |  |  | Versión del algoritmo de cálculo del IC con que se publicó. |
| `version_anterior_id` | `uuid` | Sí |  | FK → `version_formulario.id` | Versión de la que deriva. |
| `creado_por_id` | `uuid` | No |  | FK → `usuario.id` | Usuario que creó la versión. |
| `publicado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Usuario que la publicó. |
| `publicado_en` | `timestamp with time zone` | Sí |  |  | Momento de la publicación. |
| `calibrado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Catedrático que calibró el IC de la versión. |
| `calibrado_en` | `timestamp with time zone` | Sí |  |  | Momento de la calibración. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |

**Restricciones e índices adicionales**

- **CHECK** `version_formulario_activa_publicada`: `CHECK (((NOT activa) OR ((estado) = 'PUBLICADA')))`
- **CHECK** `version_formulario_anterior_distinta`: `CHECK (((version_anterior_id IS NULL) OR (version_anterior_id <> id)))`
- **CHECK** `version_formulario_publicacion_completa`: `CHECK ((((estado) <> 'PUBLICADA') OR ((hash_contenido IS NOT NULL) AND (version_algoritmo_ic IS NOT NULL) AND (publicado_por_id IS NOT NULL) AND (publicado_en IS NOT NULL))))`
- **Índice** `uq_version_formulario_activa`: `UNIQUE INDEX uq_version_formulario_activa ON version_formulario ((true)) WHERE activa`

### configuracion_ic_elemento

Peso de cada elemento del formulario en el índice de condición, congelado al publicar la versión. *Creada en `V7`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `version_formulario_id` | `uuid` | No |  | FK → `version_formulario.id` | Versión de formulario a la que pertenece el peso. |
| `elemento_ref` | `character varying(300)` | No |  |  | Ruta del elemento evaluable dentro del esquema. |
| `peso` | `numeric(12,6)` | No |  |  | Peso del elemento en el índice de condición. |
| `descripcion` | `character varying(500)` | No |  |  | Descripción del elemento. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **CHECK** `configuracion_ic_peso_positivo`: `CHECK ((peso > (0)))`
- **UNIQUE** `uq_configuracion_ic_version_elemento`: `UNIQUE (version_formulario_id, elemento_ref)`

### mapeo_campo_formulario

Correspondencia declarada entre campos de dos versiones del formulario, para comparar inspecciones de versiones distintas. *Creada en `V7`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `version_origen_id` | `uuid` | No |  | FK → `version_formulario.id` | Versión de origen. |
| `version_destino_id` | `uuid` | No |  | FK → `version_formulario.id` | Versión de destino. |
| `elemento_ref_origen` | `character varying(300)` | Sí |  |  | Campo en la versión de origen. |
| `elemento_ref_destino` | `character varying(300)` | Sí |  |  | Campo equivalente en la versión de destino. |
| `tipo` | `character varying(20)` | No |  |  | Tipo de correspondencia; NUEVO no tiene origen y ELIMINADO no tiene destino. Valores: `EQUIVALENTE`, `RENOMBRADO`, `NUEVO`, `ELIMINADO`. |
| `observacion` | `text` | Sí |  |  | Nota sobre la correspondencia. |
| `creado_por_id` | `uuid` | No |  | FK → `usuario.id` | Usuario que declaró el mapeo. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **CHECK** `mapeo_referencias_coherentes`: `CHECK (((((tipo) = 'NUEVO') AND (elemento_ref_origen IS NULL) AND (elemento_ref_destino IS NOT NULL)) OR (((tipo) = 'ELIMINADO') AND (elemento_ref_origen IS NOT NULL) AND (elemento_ref_destino IS NULL)) OR (((tipo) = ANY (ARRAY['EQUIVALENTE', 'RENOMBRADO']))) AND (elemento_ref_origen IS NOT NULL) AND (elemento_ref_destino IS NOT NULL))))`
- **CHECK** `mapeo_versiones_distintas`: `CHECK ((version_origen_id <> version_destino_id))`
- **Índice** `uq_mapeo_destino`: `UNIQUE INDEX uq_mapeo_destino ON mapeo_campo_formulario (version_origen_id, version_destino_id, elemento_ref_destino) WHERE (elemento_ref_destino IS NOT NULL)`
- **Índice** `uq_mapeo_origen`: `UNIQUE INDEX uq_mapeo_origen ON mapeo_campo_formulario (version_origen_id, version_destino_id, elemento_ref_origen) WHERE (elemento_ref_origen IS NOT NULL)`

### aprobacion_calibracion_ic

Aprobación académica de la calibración del índice de condición de una versión de formulario (HU-054). *Creada en `V22`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `version_formulario_id` | `uuid` | No |  | FK → `version_formulario.id`<br>UNIQUE | Versión cuya calibración se aprueba. |
| `aprobado_por_id` | `uuid` | No |  | FK → `usuario.id` | Catedrático que aprueba. |
| `aprobado_por_nombre` | `character varying(320)` | No |  |  | Nombre del catedrático capturado al aprobar. |
| `aprobado_en` | `timestamp with time zone` | No |  |  | Momento de la aprobación. |
| `observaciones` | `character varying(2000)` | Sí |  |  | Observaciones de la aprobación. |

**Restricciones e índices adicionales**

- **CHECK** `aprobacion_calibracion_nombre_no_vacio`: `CHECK ((length(btrim((aprobado_por_nombre))) > 0))`

### resultado_ic

Resultado inmutable de un cálculo del índice de condición (al publicar o al recalcular), con su desglose y el estado confirmado. *Creada en `V9`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `inspeccion_id` | `uuid` | No |  | FK → `inspeccion.id` | Inspección evaluada. |
| `version_configuracion_id` | `uuid` | No |  | FK → `version_formulario.id` | Versión de formulario cuya configuración se usó. |
| `tipo` | `character varying(20)` | No |  |  | Resultado definitivo al publicar o recálculo posterior. Valores: `PUBLICACION`, `RECALCULO`. |
| `indice_calculado` | `numeric(8,5)` | No |  |  | Índice de condición calculado (0 a 100). |
| `estado_por_rango` | `character varying(20)` | No |  |  | Estado que corresponde al índice según los rangos. |
| `estado_calculado` | `character varying(20)` | No |  |  | Estado tras aplicar las condiciones críticas de anulación. |
| `estado_confirmado` | `character varying(20)` | No |  |  | Estado confirmado por quien publica; si difiere del calculado exige justificación. |
| `condiciones_anulacion` | `jsonb` | No | `'[]'` |  | Condiciones críticas que forzaron el estado (JSON). |
| `desglose` | `jsonb` | No |  |  | Desglose del cálculo por elemento (JSON). |
| `hash_entrada` | `character varying(64)` | No |  |  | Hash de los datos de entrada del cálculo. |
| `version_algoritmo` | `character varying(50)` | No |  |  | Versión del algoritmo usado. |
| `justificacion_ajuste` | `text` | Sí |  |  | Justificación cuando el estado confirmado difiere del calculado. |
| `confirmado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Usuario que confirmó un estado distinto del calculado. |
| `confirmado_en` | `timestamp with time zone` | Sí |  |  | Momento de esa confirmación. |
| `solicitado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Usuario que solicitó un recálculo. |
| `calculado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento del cálculo. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **CHECK** `resultado_ic_ajuste_completo`: `CHECK (((((estado_confirmado) = (estado_calculado)) AND (justificacion_ajuste IS NULL) AND (confirmado_por_id IS NULL) AND (confirmado_en IS NULL)) OR (((estado_confirmado) <> (estado_calculado)) AND (justificacion_ajuste IS NOT NULL) AND (confirmado_por_id IS NOT NULL) AND (confirmado_en IS NOT NULL))))`
- **CHECK** `resultado_ic_estados_validos`: `CHECK ((((estado_por_rango) = ANY (ARRAY['BUENO', 'REGULAR', 'MALO']))) AND ((estado_calculado) = ANY (ARRAY['BUENO', 'REGULAR', 'MALO']))) AND ((estado_confirmado) = ANY (ARRAY['BUENO', 'REGULAR', 'MALO'])))))`
- **CHECK** `resultado_ic_rango`: `CHECK (((indice_calculado >= (0)) AND (indice_calculado <= (100))))`
- **UNIQUE** `uq_resultado_ic_id_inspeccion`: `UNIQUE (id, inspeccion_id)`
- **Índice** `idx_resultado_ic_inspeccion_fecha`: `INDEX idx_resultado_ic_inspeccion_fecha ON resultado_ic (inspeccion_id, calculado_en DESC)`
- **Índice** `uq_resultado_ic_publicacion`: `UNIQUE INDEX uq_resultado_ic_publicacion ON resultado_ic (inspeccion_id) WHERE ((tipo)::text = 'PUBLICACION'::text)`
- **Índice** `uq_resultado_ic_recalculo`: `UNIQUE INDEX uq_resultado_ic_recalculo ON resultado_ic (inspeccion_id, version_configuracion_id, hash_entrada, version_algoritmo) WHERE ((tipo)::text = 'RECALCULO'::text)`

### confirmacion_estado_inspeccion

Confirmación del estado del puente por quien publica una inspección, incluido el ajuste justificado del estado calculado. *Creada en `V18`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `inspeccion_id` | `uuid` | No |  | FK → `inspeccion.id`<br>PK | Inspección cuyo estado se confirma. |
| `version_formulario_id` | `uuid` | No |  | FK → `version_formulario.id` | Versión de formulario de la inspección. |
| `hash_entrada` | `character varying(64)` | No |  |  | Hash de los datos usados en el cálculo mostrado. |
| `version_algoritmo` | `character varying(50)` | No |  |  | Versión del algoritmo usado. |
| `indice_calculado` | `numeric(8,5)` | No |  |  | Índice calculado al confirmar. |
| `estado_calculado` | `character varying(20)` | No |  |  | Estado calculado al confirmar. |
| `estado_confirmado` | `character varying(20)` | No |  |  | Estado confirmado por el usuario. |
| `justificacion_ajuste` | `text` | Sí |  |  | Justificación del ajuste, si lo hubo. |
| `realizado_por_id` | `uuid` | No |  | FK → `usuario.id` | Usuario que confirmó. |
| `realizado_en` | `timestamp with time zone` | No |  |  | Momento de la confirmación. |
| `calculo` | `jsonb` | No |  |  | Detalle del cálculo mostrado al confirmar (JSON). |

**Restricciones e índices adicionales**

- **CHECK** `confirmacion_estado_algoritmo_no_vacio`: `CHECK ((NULLIF(btrim((version_algoritmo)), '') IS NOT NULL))`
- **CHECK** `confirmacion_estado_calculo_objeto`: `CHECK ((jsonb_typeof(calculo) = 'object'))`
- **CHECK** `confirmacion_estado_hash_valido`: `CHECK (((hash_entrada) ~ '^[0-9a-f]{64}$'))`
- **CHECK** `confirmacion_estado_ic_rango`: `CHECK (((indice_calculado >= (0)) AND (indice_calculado <= (100))))`
- **CHECK** `confirmacion_estado_justificacion_coherente`: `CHECK (((((estado_confirmado) = (estado_calculado)) AND (justificacion_ajuste IS NULL)) OR (((estado_confirmado) <> (estado_calculado)) AND (NULLIF(btrim(justificacion_ajuste), '') IS NOT NULL))))`
- **CHECK** `confirmacion_estado_valores_validos`: `CHECK ((((estado_calculado) = ANY (ARRAY['BUENO', 'REGULAR', 'MALO']))) AND ((estado_confirmado) = ANY (ARRAY['BUENO', 'REGULAR', 'MALO'])))))`

## Inspecciones

### preparacion_salida

Paquete verificable descargado al dispositivo antes de una salida de campo, para capturar inspecciones sin conexión. *Creada en `V8`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `usuario_id` | `uuid` | No |  | FK → `usuario.id` | Usuario que preparó la salida. |
| `dispositivo_id` | `character varying(100)` | No |  |  | Identificador del dispositivo que descargó el paquete. |
| `version_formulario_id` | `uuid` | No |  | FK → `version_formulario.id` | Versión de formulario incluida en el paquete. |
| `contexto_autorizado` | `jsonb` | No |  |  | Puentes, datos y permisos descargados para trabajar sin conexión (JSON). |
| `preparado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la preparación. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **Índice** `idx_preparacion_usuario_fecha`: `INDEX idx_preparacion_usuario_fecha ON preparacion_salida (usuario_id, preparado_en DESC)`

### inspeccion

Agregado principal de una inspección: borrador, envío, revisión, publicación y correcciones, con los datos capturados en el formulario dinámico. *Creada en `V8`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `puente_id` | `uuid` | No |  | FK compuesta → `inspeccion`<br>FK compuesta → `inspeccion`<br>FK → `puente.id` | Puente inspeccionado. |
| `version_formulario_id` | `uuid` | No |  | FK → `version_formulario.id` | Versión de formulario con que se levantó; no cambia después. |
| `autor_id` | `uuid` | No |  | FK → `usuario.id` | Usuario que levanta la inspección. |
| `rol_autor_creacion` | `character varying(40)` | No |  |  | Rol del autor al crear la inspección (decide si se publica con o sin revisión). |
| `nombre_autor_capturado` | `character varying(200)` | No |  |  | Nombre del autor capturado al crear. |
| `asignacion_puente_id` | `uuid` | Sí |  | FK → `asignacion_puente.id` | Asignación que autoriza al estudiante a inspeccionar el puente. |
| `preparacion_salida_id` | `uuid` | Sí |  | FK → `preparacion_salida.id` | Preparación de salida con que se capturó sin conexión. |
| `inspeccion_base_id` | `uuid` | Sí |  | FK compuesta → `inspeccion` | Inspección de la que se copiaron datos permanentes. |
| `tipo` | `character varying(20)` | No | `'ORIGINAL'` |  | Original o corrección de una inspección publicada. Valores: `ORIGINAL`, `CORRECCION`. |
| `supersede_a_id` | `uuid` | Sí |  | FK compuesta → `inspeccion` | Inspección publicada que esta corrección reemplaza. |
| `motivo_correccion` | `text` | Sí |  |  | Motivo de la corrección. |
| `estado` | `character varying(30)` | No | `'BORRADOR'` |  | Estado en la máquina de estados; PUBLICADA exige fecha de publicación y no puede eliminarse. Valores: `BORRADOR`, `ENVIADA`, `EN_REVISION`, `CAMBIOS_SOLICITADOS`, `PUBLICADA`, `RECHAZADA`. |
| `fecha_inspeccion` | `date` | No |  |  | Fecha de la visita. |
| `dispositivo_id` | `character varying(100)` | No |  |  | Dispositivo en que se creó. |
| `ubicacion_inicio` | `geography(Point,4326)` | Sí |  |  | Punto GPS al iniciar la inspección (PostGIS geography). |
| `precision_gps_m` | `numeric(10,2)` | Sí |  |  | Precisión del GPS al iniciar, en metros. |
| `distancia_al_puente_m` | `numeric(12,2)` | Sí |  |  | Distancia entre el punto de inicio y el puente, en metros. |
| `datos_permanentes_confirmados_en` | `timestamp with time zone` | Sí |  |  | Momento en que el autor confirmó los datos permanentes del puente. |
| `identificacion_capturada` | `jsonb` | Sí |  |  | Datos de identificación del puente capturados en la visita (JSON). |
| `datos` | `jsonb` | No | `'{}'` |  | Respuestas del formulario dinámico (JSON validado contra el esquema). |
| `creado_dispositivo_en` | `timestamp with time zone` | Sí |  |  | Momento de creación según el dispositivo. |
| `enviado_dispositivo_en` | `timestamp with time zone` | Sí |  |  | Momento de envío según el dispositivo. |
| `sincronizado_en` | `timestamp with time zone` | Sí |  |  | Momento en que llegó al servidor. |
| `publicado_en` | `timestamp with time zone` | Sí |  |  | Momento de publicación. |
| `ultima_actividad_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Última modificación; se usa para avisar y purgar borradores inactivos. |
| `eliminado_en` | `timestamp with time zone` | Sí |  |  | Momento en que se eliminó el borrador (borrado lógico; una inspección publicada no se elimina). |
| `eliminado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Usuario que eliminó el borrador. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |
| `huella_solicitud_inicio` | `character varying(64)` | Sí |  |  | Huella de la solicitud de inicio, para reconocer reintentos offline. |

**Restricciones e índices adicionales**

- **CHECK** `inspeccion_base_distinta`: `CHECK (((inspeccion_base_id IS NULL) OR (inspeccion_base_id <> id)))`
- **CHECK** `inspeccion_correccion_coherente`: `CHECK (((((tipo) = 'ORIGINAL') AND (supersede_a_id IS NULL) AND (motivo_correccion IS NULL)) OR (((tipo) = 'CORRECCION') AND (supersede_a_id IS NOT NULL) AND (motivo_correccion IS NOT NULL))))`
- **CHECK** `inspeccion_publicacion_coherente`: `CHECK ((((estado) <> 'PUBLICADA') OR (publicado_en IS NOT NULL)))`
- **CHECK** `inspeccion_publicada_no_eliminada`: `CHECK ((((estado) <> 'PUBLICADA') OR (eliminado_en IS NULL)))`
- **FK** `fk_inspeccion_base_mismo_puente`: `FOREIGN KEY (inspeccion_base_id, puente_id) REFERENCES inspeccion(id, puente_id)`
- **FK** `fk_inspeccion_correccion_mismo_puente`: `FOREIGN KEY (supersede_a_id, puente_id) REFERENCES inspeccion(id, puente_id)`
- **UNIQUE** `uq_inspeccion_id_puente`: `UNIQUE (id, puente_id)`
- **Índice** `idx_inspeccion_asignacion_estado`: `INDEX idx_inspeccion_asignacion_estado ON inspeccion (asignacion_puente_id, estado)`
- **Índice** `idx_inspeccion_autor_estado`: `INDEX idx_inspeccion_autor_estado ON inspeccion (autor_id, estado)`
- **Índice** `idx_inspeccion_datos_gin`: `INDEX idx_inspeccion_datos_gin ON inspeccion USING gin (datos jsonb_path_ops)`
- **Índice** `idx_inspeccion_puente_fecha`: `INDEX idx_inspeccion_puente_fecha ON inspeccion (puente_id, fecha_inspeccion DESC)`
- **Índice** `idx_inspeccion_ubicacion_gist`: `INDEX idx_inspeccion_ubicacion_gist ON inspeccion USING gist (ubicacion_inicio)`
- **Índice** `idx_inspeccion_version_formulario`: `INDEX idx_inspeccion_version_formulario ON inspeccion (version_formulario_id)`
- **Índice** `uq_inspeccion_sucesora_publicada`: `UNIQUE INDEX uq_inspeccion_sucesora_publicada ON inspeccion (supersede_a_id) WHERE ((estado)::text = 'PUBLICADA'::text)`

### inspeccion_acompanante

Persona que acompañó la visita de inspección, sea usuario del sistema o externo. *Creada en `V8`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `inspeccion_id` | `uuid` | No |  | FK → `inspeccion.id` | Inspección acompañada. |
| `usuario_id` | `uuid` | Sí |  | FK → `usuario.id` | Usuario acompañante, si tiene cuenta. |
| `nombre` | `character varying(200)` | No |  |  | Nombre del acompañante. |
| `numero_colegiado` | `character varying(50)` | Sí |  |  | Número de colegiado del acompañante, si aplica. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **Índice** `uq_inspeccion_acompanante_usuario`: `UNIQUE INDEX uq_inspeccion_acompanante_usuario ON inspeccion_acompanante (inspeccion_id, usuario_id) WHERE (usuario_id IS NOT NULL)`

### inspeccion_dato_tecnico

Extensión 1:1 opcional con los datos técnicos consultables del puente capturados en la inspección. *Creada en `V8`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `inspeccion_id` | `uuid` | No |  | FK → `inspeccion.id`<br>PK | Inspección a la que pertenecen los datos (clave primaria). |
| `longitud_m` | `numeric(10,2)` | Sí |  |  | Longitud total del puente, en metros. |
| `numero_tramos` | `integer` | Sí |  |  | Cantidad de tramos. |
| `ancho_rodadura_m` | `numeric(10,2)` | Sí |  |  | Ancho de rodadura, en metros. |
| `ancho_acera_derecha_m` | `numeric(10,2)` | Sí |  |  | Ancho de la acera derecha, en metros. |
| `ancho_acera_izquierda_m` | `numeric(10,2)` | Sí |  |  | Ancho de la acera izquierda, en metros. |
| `altura_libre_superior_m` | `numeric(10,2)` | Sí |  |  | Altura libre sobre la calzada, en metros. |
| `altura_libre_sobre_cauce_m` | `numeric(10,2)` | Sí |  |  | Altura libre sobre el cauce, en metros. |
| `tipologia_puente` | `character varying(100)` | Sí |  |  | Tipología estructural del puente. |
| `tipo_cruce` | `character varying(100)` | Sí |  |  | Qué cruza el puente (río, quebrada, vía, etc.). |
| `numero_vias_por_sentido` | `integer` | Sí |  |  | Vías por sentido. |
| `material_superestructura` | `character varying(100)` | Sí |  |  | Material principal de la superestructura. |
| `material_subestructura` | `character varying(100)` | Sí |  |  | Material principal de la subestructura. |
| `carga_diseno` | `character varying(100)` | Sí |  |  | Carga de diseño. |
| `anio_construccion` | `smallint` | Sí |  |  | Año de construcción. |
| `trafico_vehiculos_dia` | `integer` | Sí |  |  | Tránsito promedio diario. |
| `porcentaje_camiones_buses` | `numeric(5,2)` | Sí |  |  | Porcentaje de camiones y buses. |
| `ruta_pavimentada` | `character varying(10)` | Sí |  |  | La ruta está pavimentada. Valores: `SI`, `NO`, `PARCIAL`. |
| `alineamiento_horizontal` | `character varying(15)` | Sí |  |  | Alineamiento horizontal del puente. Valores: `TANGENTE`, `CURVA`. |
| `esviaje` | `boolean` | Sí |  |  | Esviaje del puente respecto del cauce o vía. |
| `poblacion_antes` | `character varying(200)` | Sí |  |  | Población antes del puente. |
| `poblacion_despues` | `character varying(200)` | Sí |  |  | Población después del puente. |
| `tipo_cuerpo_agua` | `character varying(50)` | Sí |  |  | Tipo de cuerpo de agua que cruza. |
| `nombre_rio` | `character varying(200)` | Sí |  |  | Nombre del río. |
| `encauzamiento` | `character varying(20)` | Sí |  |  | Existe encauzamiento. Valores: `RECTO`, `CURVA`, `INDEFINIDO`. |
| `antecedente_desbordamiento` | `boolean` | Sí |  |  | Hay antecedentes de desbordamiento. |
| `frecuencia_desbordamiento_anios` | `numeric(8,2)` | Sí |  |  | Frecuencia de desbordamiento, en años. |
| `fecha_ultimo_desbordamiento` | `date` | Sí |  |  | Fecha del último desbordamiento conocido. |
| `alumbrado_existe` | `boolean` | Sí |  |  | El puente tiene alumbrado. |
| `drenajes_aledanos_existen` | `boolean` | Sí |  |  | Existen drenajes aledaños. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |

**Restricciones e índices adicionales**

- **CHECK** `inspeccion_dato_tecnico_anio_valido`: `CHECK (((anio_construccion IS NULL) OR ((anio_construccion >= 1) AND (anio_construccion <= 9999))))`
- **CHECK** `inspeccion_dato_tecnico_medidas_validas`: `CHECK ((((longitud_m IS NULL) OR (longitud_m >= (0))) AND ((ancho_rodadura_m IS NULL) OR (ancho_rodadura_m >= (0))) AND ((ancho_acera_derecha_m IS NULL) OR (ancho_acera_derecha_m >= (0))) AND ((ancho_acera_izquierda_m IS NULL) OR (ancho_acera_izquierda_m >= (0))) AND ((altura_libre_superior_m IS NULL) OR (altura_libre_superior_m >= (0))) AND ((altura_libre_sobre_cauce_m IS NULL) OR (altura_libre_sobre_cauce_m >= (0)))))`
- **CHECK** `inspeccion_dato_tecnico_porcentaje_valido`: `CHECK (((porcentaje_camiones_buses IS NULL) OR ((porcentaje_camiones_buses >= (0)) AND (porcentaje_camiones_buses <= (100)))))`
- **CHECK** `inspeccion_dato_tecnico_tramos_validos`: `CHECK (((numero_tramos IS NULL) OR (numero_tramos >= 0)))`

### inspeccion_tramo

Tramo repetible del puente registrado en una inspección. *Creada en `V8`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `inspeccion_id` | `uuid` | No |  | FK → `inspeccion.id` | Inspección a la que pertenece el tramo. |
| `instancia_elemento_id` | `uuid` | No |  |  | Identificador de la instancia del tramo en los datos del formulario. |
| `orden` | `integer` | No |  |  | Posición del tramo. |
| `longitud_m` | `numeric(10,2)` | Sí |  |  | Longitud del tramo, en metros. |
| `tipo_seccion` | `character varying(100)` | Sí |  |  | Tipo de sección del tramo. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **CHECK** `inspeccion_tramo_longitud_positiva`: `CHECK (((longitud_m IS NULL) OR (longitud_m > (0))))`
- **CHECK** `inspeccion_tramo_orden_positivo`: `CHECK ((orden > 0))`
- **UNIQUE** `uq_inspeccion_tramo_instancia`: `UNIQUE (inspeccion_id, instancia_elemento_id)`
- **UNIQUE** `uq_inspeccion_tramo_orden`: `UNIQUE (inspeccion_id, orden)`

### historial_estado_inspeccion

Bitácora inmutable y secuencial de cada transición de estado de una inspección. *Creada en `V9`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `inspeccion_id` | `uuid` | No |  | FK → `inspeccion.id` | Inspección que cambió de estado. |
| `secuencia` | `integer` | No |  |  | Número consecutivo de la transición dentro de la inspección. |
| `estado_anterior` | `character varying(30)` | Sí |  |  | Estado previo (nulo en la creación). |
| `estado_nuevo` | `character varying(30)` | No |  |  | Estado resultante. Valores: `BORRADOR`, `ENVIADA`, `EN_REVISION`, `CAMBIOS_SOLICITADOS`, `PUBLICADA`, `RECHAZADA`. |
| `realizado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Usuario que provocó la transición. |
| `revision_id` | `uuid` | Sí |  | FK → `revision.id` | Ciclo de revisión relacionado, si aplica. |
| `evento` | `character varying(60)` | No |  |  | Evento de la máquina de estados (ENVIAR, APROBAR, PUBLICAR_SIN_REVISION, etc.). |
| `motivo` | `text` | Sí |  |  | Motivo escrito, obligatorio al solicitar cambios o rechazar. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **CHECK** `historial_estado_inspeccion_secuencia_positiva`: `CHECK ((secuencia > 0))`
- **UNIQUE** `uq_historial_estado_inspeccion_secuencia`: `UNIQUE (inspeccion_id, secuencia)`
- **Índice** `idx_historial_estado_inspeccion_fecha`: `INDEX idx_historial_estado_inspeccion_fecha ON historial_estado_inspeccion (inspeccion_id, creado_en)`

## Revisión académica

### revision

Ciclo de revisión académica de una inspección: uno por cada envío, con veredicto y calificación del catedrático. *Creada en `V9`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `inspeccion_id` | `uuid` | No |  | FK → `inspeccion.id` | Inspección revisada. |
| `numero_ciclo` | `integer` | No |  |  | Número del ciclo de revisión de la inspección. |
| `revisor_id` | `uuid` | No |  | FK → `usuario.id` | Catedrático que revisa. |
| `estado` | `character varying(20)` | No | `'PENDIENTE'` |  | Estado del ciclo de revisión; RESUELTA exige veredicto y fecha. Valores: `PENDIENTE`, `EN_CURSO`, `RESUELTA`. |
| `veredicto` | `character varying(30)` | Sí |  |  | Resultado del ciclo; CAMBIOS_SOLICITADOS o RECHAZADA exigen observación general. Valores: `APROBADA`, `CAMBIOS_SOLICITADOS`, `RECHAZADA`. |
| `observacion_general` | `text` | Sí |  |  | Observación general del revisor. |
| `calificacion` | `numeric(6,2)` | Sí |  |  | Calificación académica asignada. |
| `enviada_en` | `timestamp with time zone` | No |  |  | Momento del envío a revisión. |
| `iniciada_en` | `timestamp with time zone` | Sí |  |  | Momento en que el revisor tomó la revisión. |
| `resuelta_en` | `timestamp with time zone` | Sí |  |  | Momento del veredicto. |
| `version_inspeccion_enviada` | `bigint` | No |  |  | Versión de la inspección que se envió. |
| `contenido_enviado` | `jsonb` | No |  |  | Copia del contenido enviado a revisión (JSON). |
| `hash_contenido` | `character varying(64)` | No |  |  | Hash del contenido enviado. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **CHECK** `revision_ciclo_positivo`: `CHECK ((numero_ciclo > 0))`
- **CHECK** `revision_motivo_veredicto`: `CHECK ((((veredicto) <> ALL ((ARRAY['CAMBIOS_SOLICITADOS', 'RECHAZADA']))) OR (NULLIF(btrim(observacion_general), '') IS NOT NULL)))`
- **CHECK** `revision_resolucion_completa`: `CHECK ((((estado) <> 'RESUELTA') OR ((veredicto IS NOT NULL) AND (resuelta_en IS NOT NULL))))`
- **UNIQUE** `uq_revision_inspeccion_ciclo`: `UNIQUE (inspeccion_id, numero_ciclo)`
- **Índice** `idx_revision_revisor_estado_fecha`: `INDEX idx_revision_revisor_estado_fecha ON revision (revisor_id, estado, enviada_en)`
- **Índice** `uq_revision_abierta_por_inspeccion`: `UNIQUE INDEX uq_revision_abierta_por_inspeccion ON revision (inspeccion_id) WHERE ((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'EN_CURSO'::character varying])::text[]))`

### observacion_revision

Observación puntual del revisor sobre un campo o elemento de la inspección, y la respuesta del autor. *Creada en `V9`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `revision_id` | `uuid` | No |  | FK → `revision.id` | Ciclo de revisión al que pertenece. |
| `elemento_ref` | `character varying(300)` | Sí |  |  | Campo o elemento observado. |
| `instancia_elemento_id` | `uuid` | Sí |  |  | Instancia observada de un elemento repetible. |
| `contenido` | `text` | No |  |  | Texto de la observación. |
| `estado` | `character varying(20)` | No | `'PENDIENTE'` |  | Estado de la observación; RESUELTA exige quién y cuándo. Valores: `PENDIENTE`, `RESUELTA`. |
| `creado_por_id` | `uuid` | No |  | FK → `usuario.id` | Revisor que la escribió. |
| `resuelto_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Usuario que la marcó resuelta. |
| `resuelto_en` | `timestamp with time zone` | Sí |  |  | Momento de la resolución. |
| `respuesta_autor` | `text` | Sí |  |  | Respuesta del autor de la inspección. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `categoria_fotografica` | `character varying(30)` | Sí |  |  | Categoría fotográfica observada, cuando la observación es sobre la evidencia. Valores: `ACCESO`, `SUPERESTRUCTURA`, `SUBESTRUCTURA`, `CAUCE`, `OTRA`. |

**Restricciones e índices adicionales**

- **CHECK** `observacion_categoria_sin_ancla`: `CHECK (((categoria_fotografica IS NULL) OR ((elemento_ref IS NULL) AND (instancia_elemento_id IS NULL))))`
- **CHECK** `observacion_instancia_requiere_ref`: `CHECK (((instancia_elemento_id IS NULL) OR (elemento_ref IS NOT NULL)))`
- **CHECK** `observacion_resolucion_completa`: `CHECK (((((estado) = 'PENDIENTE') AND (resuelto_por_id IS NULL) AND (resuelto_en IS NULL)) OR (((estado) = 'RESUELTA') AND (resuelto_por_id IS NOT NULL) AND (resuelto_en IS NOT NULL))))`
- **Índice** `idx_observacion_revision_estado`: `INDEX idx_observacion_revision_estado ON observacion_revision (revision_id, estado)`

## Archivos y evidencia

### archivo

Metadatos de un objeto almacenado (foto o documento): claves en el almacenamiento, tipo real, tamaño, checksum y ciclo de vida. El binario vive en S3/MinIO. *Creada en `V10`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `tipo_uso` | `character varying(30)` | No |  |  | Uso del archivo; determina con qué tabla de vínculo se relaciona. Valores: `FOTO_INSPECCION`, `DOCUMENTO_INSPECCION`, `EVIDENCIA_MANTENIMIENTO`, `ACTA_CALIBRACION`. |
| `categoria_foto` | `character varying(30)` | Sí |  |  | Categoría de la fotografía; obligatoria en fotos de inspección y nula en los demás archivos. Valores: `ACCESO`, `SUPERESTRUCTURA`, `SUBESTRUCTURA`, `CAUCE`, `OTRA`. |
| `nombre_original` | `character varying(255)` | Sí |  |  | Nombre del archivo enviado por el usuario. |
| `nombre_almacenado` | `character varying(255)` | No |  |  | Nombre generado por el servidor. |
| `clave_objeto_original` | `character varying(500)` | No |  | UNIQUE | Clave del objeto original en el almacenamiento. |
| `clave_objeto_miniatura` | `character varying(500)` | Sí |  |  | Clave de la miniatura de 300 px. |
| `mime_real` | `character varying(100)` | No |  |  | Tipo MIME detectado por la firma binaria. |
| `tamano_bytes` | `bigint` | No |  |  | Tamaño en bytes. |
| `checksum_sha256` | `character varying(64)` | No |  |  | SHA-256 del contenido. |
| `ancho_px` | `integer` | Sí |  |  | Ancho de la imagen, en píxeles. |
| `alto_px` | `integer` | Sí |  |  | Alto de la imagen, en píxeles. |
| `ubicacion_captura` | `geography(Point,4326)` | Sí |  |  | Punto GPS de la captura (PostGIS geography). |
| `capturado_en` | `timestamp with time zone` | Sí |  |  | Momento de la captura. |
| `subido_por_id` | `uuid` | No |  | FK → `usuario.id` | Usuario que subió el archivo. |
| `confirmado_en` | `timestamp with time zone` | No |  |  | Momento en que la carga quedó confirmada. |
| `estado` | `character varying(20)` | No | `'DISPONIBLE'` |  | Ciclo de vida del archivo; una foto DISPONIBLE exige ubicación, fecha de captura, miniatura y dimensiones. Valores: `DISPONIBLE`, `RETIRADO`, `PURGADO`. |
| `retirado_en` | `timestamp with time zone` | Sí |  |  | Momento en que se retiró. |
| `purgado_en` | `timestamp with time zone` | Sí |  |  | Momento en que se purgaron los objetos. |
| `motivo_retiro` | `text` | Sí |  |  | Motivo del retiro. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **CHECK** `archivo_categoria_solo_foto`: `CHECK ((((tipo_uso) = 'FOTO_INSPECCION') OR (categoria_foto IS NULL)))`
- **CHECK** `archivo_documento_inspeccion_formato_valido`: `CHECK ((((tipo_uso) <> 'DOCUMENTO_INSPECCION') OR (((mime_real) = 'application/pdf') AND (tamano_bytes <= 20971520))))`
- **CHECK** `archivo_evidencia_mantenimiento_imagen_valida`: `CHECK ((((tipo_uso) <> 'EVIDENCIA_MANTENIMIENTO') OR (((mime_real) = ANY (ARRAY['image/jpeg', 'image/webp']))) AND (ancho_px IS NOT NULL) AND (ancho_px > 0) AND (alto_px IS NOT NULL) AND (alto_px > 0))))`
- **CHECK** `archivo_foto_disponible_metadatos_completos`: `CHECK ((((tipo_uso) <> 'FOTO_INSPECCION') OR ((estado) <> 'DISPONIBLE') OR ((ubicacion_captura IS NOT NULL) AND (capturado_en IS NOT NULL) AND (clave_objeto_miniatura IS NOT NULL) AND (ancho_px IS NOT NULL) AND (ancho_px > 0) AND (alto_px IS NOT NULL) AND (alto_px > 0) AND ((mime_real) = ANY (ARRAY['image/jpeg', 'image/webp']))))))`
- **CHECK** `archivo_foto_inspeccion_categoria_obligatoria`: `CHECK ((((tipo_uso) <> 'FOTO_INSPECCION') OR (categoria_foto IS NOT NULL)))`
- **CHECK** `archivo_tamano_bytes_check`: `CHECK ((tamano_bytes > 0))`
- **UNIQUE** `uq_archivo_id_tipo_uso`: `UNIQUE (id, tipo_uso)`
- **Índice** `idx_archivo_checksum_tamano`: `INDEX idx_archivo_checksum_tamano ON archivo (checksum_sha256, tamano_bytes)`
- **Índice** `idx_archivo_tipo_estado`: `INDEX idx_archivo_tipo_estado ON archivo (tipo_uso, estado)`

### archivo_inspeccion

Vínculo de una foto o documento con la inspección a la que pertenece, opcionalmente anclado a un elemento. *Creada en `V10`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `archivo_id` | `uuid` | No |  | FK compuesta → `archivo`<br>PK | Archivo vinculado (clave primaria). |
| `tipo_uso` | `character varying(30)` | No |  | FK compuesta → `archivo` | Repite el tipo de uso para garantizar el vínculo correcto. Valores: `FOTO_INSPECCION`, `DOCUMENTO_INSPECCION`. |
| `inspeccion_id` | `uuid` | No |  | FK → `inspeccion.id` | Inspección a la que pertenece. |
| `elemento_ref` | `character varying(300)` | Sí |  |  | Elemento del formulario al que se ancla, si aplica. |
| `instancia_elemento_id` | `uuid` | Sí |  |  | Instancia del elemento repetible, si aplica. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **CHECK** `archivo_inspeccion_instancia_requiere_ref`: `CHECK (((instancia_elemento_id IS NULL) OR (elemento_ref IS NOT NULL)))`
- **FK** `fk_archivo_inspeccion_tipo`: `FOREIGN KEY (archivo_id, tipo_uso) REFERENCES archivo(id, tipo_uso)`
- **Índice** `idx_archivo_inspeccion_inspeccion_tipo`: `INDEX idx_archivo_inspeccion_inspeccion_tipo ON archivo_inspeccion (inspeccion_id, tipo_uso)`

### archivo_evidencia_mantenimiento

Vínculo de una foto o documento de evidencia con una orden de mantenimiento. *Creada en `V10`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `archivo_id` | `uuid` | No |  | FK compuesta → `archivo`<br>PK | Archivo vinculado (clave primaria). |
| `tipo_uso` | `character varying(30)` | No | `'EVIDENCIA_MANTENIMIENTO'` | FK compuesta → `archivo` | Repite el tipo de uso para garantizar el vínculo correcto. Valores: `EVIDENCIA_MANTENIMIENTO`. |
| `orden_mantenimiento_id` | `uuid` | No |  | FK → `orden_mantenimiento.id` | Orden de mantenimiento evidenciada. |
| `elemento_ref` | `character varying(300)` | Sí |  |  | Elemento al que se refiere la evidencia, si aplica. |
| `instancia_elemento_id` | `uuid` | Sí |  |  | Instancia del elemento, si aplica. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **CHECK** `archivo_evidencia_instancia_requiere_ref`: `CHECK (((instancia_elemento_id IS NULL) OR (elemento_ref IS NOT NULL)))`
- **FK** `fk_archivo_evidencia_tipo`: `FOREIGN KEY (archivo_id, tipo_uso) REFERENCES archivo(id, tipo_uso)`
- **Índice** `idx_archivo_evidencia_orden`: `INDEX idx_archivo_evidencia_orden ON archivo_evidencia_mantenimiento (orden_mantenimiento_id)`

### archivo_acta_calibracion

Vínculo del acta de calibración (PDF) con la versión de formulario que respalda. *Creada en `V10`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `archivo_id` | `uuid` | No |  | FK compuesta → `archivo`<br>PK | Archivo vinculado (clave primaria). |
| `tipo_uso` | `character varying(30)` | No | `'ACTA_CALIBRACION'` | FK compuesta → `archivo` | Repite el tipo de uso para garantizar el vínculo correcto. Valores: `ACTA_CALIBRACION`. |
| `version_formulario_id` | `uuid` | No |  | FK → `version_formulario.id` | Versión de formulario que respalda el acta. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **FK** `fk_archivo_acta_tipo`: `FOREIGN KEY (archivo_id, tipo_uso) REFERENCES archivo(id, tipo_uso)`
- **Índice** `idx_archivo_acta_version`: `INDEX idx_archivo_acta_version ON archivo_acta_calibracion (version_formulario_id)`

## Mantenimiento

### regla_mantenimiento

Regla versionada que, al cumplirse en una inspección publicada, propone una orden de mantenimiento. *Creada en `V10`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `codigo` | `character varying(80)` | No |  |  | Código estable de la regla. |
| `numero_revision` | `integer` | No |  |  | Revisión de la regla; cada cambio crea una nueva. |
| `version_formulario_id` | `uuid` | No |  | FK → `version_formulario.id` | Versión de formulario a la que aplica. |
| `elemento_ref` | `character varying(300)` | No |  |  | Elemento del formulario evaluado. |
| `condicion` | `jsonb` | No |  |  | Condición que dispara la propuesta (JSON: indicador, operador, umbral). |
| `tipo_mantenimiento` | `character varying(20)` | No |  |  | Tipo de mantenimiento que propone. Valores: `RUTINARIO`, `PREVENTIVO`, `CORRECTIVO`, `EMERGENCIA`. |
| `intervencion_sugerida` | `text` | No |  |  | Intervención sugerida. |
| `regla_prioridad` | `jsonb` | No |  |  | Cómo se sugiere la prioridad (JSON). |
| `activa` | `boolean` | No | `false` |  | La regla se evalúa al publicar inspecciones. |
| `creado_por_id` | `uuid` | No |  | FK → `usuario.id` | Usuario que creó la regla. |
| `validado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Catedrático que validó la regla. |
| `validado_en` | `timestamp with time zone` | Sí |  |  | Momento de la validación. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **CHECK** `regla_mantenimiento_numero_revision_check`: `CHECK ((numero_revision > 0))`
- **UNIQUE** `uq_regla_mantenimiento_revision`: `UNIQUE (codigo, numero_revision, version_formulario_id)`
- **Índice** `uq_regla_mantenimiento_activa`: `UNIQUE INDEX uq_regla_mantenimiento_activa ON regla_mantenimiento (codigo, version_formulario_id) WHERE activa`

### orden_mantenimiento

Orden de mantenimiento, propuesta por el sistema, creada a mano o generada por un plan rutinario, con su ciclo de vida hasta la ejecución, el descarte o la cancelación. *Creada en `V10`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `puente_id` | `uuid` | No |  | FK compuesta → `inspeccion`<br>FK compuesta → `plan_mantenimiento_rutinario`<br>FK → `puente.id` | Puente intervenido. |
| `inspeccion_origen_id` | `uuid` | Sí |  | FK compuesta → `inspeccion`<br>FK compuesta → `resultado_ic` | Inspección que originó la propuesta, si aplica. |
| `regla_origen_id` | `uuid` | Sí |  | FK → `regla_mantenimiento.id` | Regla que generó la propuesta, si aplica. |
| `resultado_ic_origen_id` | `uuid` | Sí |  | FK compuesta → `resultado_ic` | Resultado de IC que justificó la propuesta, si aplica. |
| `plan_mantenimiento_rutinario_id` | `uuid` | Sí |  | FK compuesta → `plan_mantenimiento_rutinario` | Plan rutinario que generó la orden, si aplica. |
| `ciclo_plan` | `integer` | Sí |  |  | Ciclo del plan rutinario al que corresponde. |
| `elemento_ref` | `character varying(300)` | Sí |  |  | Elemento del puente que se interviene. |
| `instancia_elemento_id` | `uuid` | Sí |  |  | Instancia del elemento, si aplica. |
| `clave_generacion` | `character varying(200)` | Sí |  |  | Clave que evita generar dos veces la misma propuesta. |
| `tipo` | `character varying(20)` | No |  |  | Tipo de mantenimiento; las órdenes de un plan rutinario son RUTINARIO. Valores: `RUTINARIO`, `PREVENTIVO`, `CORRECTIVO`, `EMERGENCIA`. |
| `estado` | `character varying(20)` | No | `'PROPUESTA'` |  | Estado de la orden; PROGRAMADA exige aceptación y fecha, DESCARTADA o CANCELADA exigen motivo. Valores: `PROPUESTA`, `PROGRAMADA`, `EN_EJECUCION`, `EJECUTADA`, `DESCARTADA`, `CANCELADA`. |
| `descripcion` | `text` | No |  |  | Descripción del trabajo. |
| `prioridad_sugerida` | `character varying(20)` | No |  |  | Prioridad sugerida por el sistema. Valores: `BAJA`, `MEDIA`, `ALTA`, `CRITICA`. |
| `prioridad_confirmada` | `character varying(20)` | No |  |  | Prioridad confirmada por quien acepta. Valores: `BAJA`, `MEDIA`, `ALTA`, `CRITICA`. |
| `justificacion_prioridad` | `text` | Sí |  |  | Justificación cuando la prioridad confirmada difiere de la sugerida. |
| `fundamento_generacion` | `jsonb` | Sí |  |  | Datos que motivaron la propuesta (JSON). |
| `creado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Usuario que creó la orden manualmente. |
| `aceptado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Usuario que aceptó la propuesta. |
| `aceptado_en` | `timestamp with time zone` | Sí |  |  | Momento de la aceptación. |
| `fecha_programada` | `date` | Sí |  |  | Fecha programada para ejecutar. |
| `motivo_cierre_sin_ejecucion` | `text` | Sí |  |  | Motivo para descartar o cancelar la orden. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |

**Restricciones e índices adicionales**

- **CHECK** `orden_cierre_motivo`: `CHECK ((((estado) <> ALL ((ARRAY['DESCARTADA', 'CANCELADA']))) OR (motivo_cierre_sin_ejecucion IS NOT NULL)))`
- **CHECK** `orden_plan_ciclo_coherente`: `CHECK ((((plan_mantenimiento_rutinario_id IS NULL) AND (ciclo_plan IS NULL)) OR ((plan_mantenimiento_rutinario_id IS NOT NULL) AND (ciclo_plan IS NOT NULL) AND (ciclo_plan > 0) AND ((tipo) = 'RUTINARIO'))))`
- **CHECK** `orden_programada_completa`: `CHECK ((((estado) <> 'PROGRAMADA') OR ((aceptado_por_id IS NOT NULL) AND (aceptado_en IS NOT NULL) AND (fecha_programada IS NOT NULL))))`
- **CHECK** `orden_resultado_origen_requiere_inspeccion`: `CHECK (((resultado_ic_origen_id IS NULL) OR (inspeccion_origen_id IS NOT NULL)))`
- **FK** `fk_orden_inspeccion_puente`: `FOREIGN KEY (inspeccion_origen_id, puente_id) REFERENCES inspeccion(id, puente_id)`
- **FK** `fk_orden_plan_puente`: `FOREIGN KEY (plan_mantenimiento_rutinario_id, puente_id) REFERENCES plan_mantenimiento_rutinario(id, puente_id)`
- **FK** `fk_orden_resultado_misma_inspeccion`: `FOREIGN KEY (resultado_ic_origen_id, inspeccion_origen_id) REFERENCES resultado_ic(id, inspeccion_id)`
- **Índice** `idx_orden_estado_programada`: `INDEX idx_orden_estado_programada ON orden_mantenimiento (estado, fecha_programada)`
- **Índice** `idx_orden_puente_estado`: `INDEX idx_orden_puente_estado ON orden_mantenimiento (puente_id, estado)`
- **Índice** `uq_orden_generacion`: `UNIQUE INDEX uq_orden_generacion ON orden_mantenimiento (inspeccion_origen_id, clave_generacion) WHERE (clave_generacion IS NOT NULL)`
- **Índice** `uq_orden_plan_ciclo`: `UNIQUE INDEX uq_orden_plan_ciclo ON orden_mantenimiento (plan_mantenimiento_rutinario_id, ciclo_plan) WHERE (plan_mantenimiento_rutinario_id IS NOT NULL)`

### orden_mantenimiento_ejecucion

Datos de ejecución de una orden; existe solo cuando la orden entra en ejecución. *Creada en `V10`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `orden_mantenimiento_id` | `uuid` | No |  | FK → `orden_mantenimiento.id`<br>PK | Orden en ejecución (clave primaria). |
| `iniciado_en` | `timestamp with time zone` | No |  |  | Momento en que inició la ejecución. |
| `fecha_ejecucion` | `date` | Sí |  |  | Fecha en que se ejecutó. |
| `responsable_usuario_id` | `uuid` | Sí |  | FK → `usuario.id` | Usuario responsable de la ejecución, si tiene cuenta. |
| `responsable_nombre` | `character varying(200)` | Sí |  |  | Nombre del responsable. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |

**Restricciones e índices adicionales**

- **CHECK** `orden_ejecucion_fecha_coherente`: `CHECK (((fecha_ejecucion IS NULL) OR (fecha_ejecucion >= (iniciado_en)::date)))`
- **CHECK** `orden_ejecucion_responsable_requerido`: `CHECK (((responsable_usuario_id IS NOT NULL) OR (responsable_nombre IS NOT NULL)))`

### historial_estado_orden

Bitácora inmutable y secuencial de los cambios de estado de una orden de mantenimiento. *Creada en `V10`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `orden_mantenimiento_id` | `uuid` | No |  | FK → `orden_mantenimiento.id` | Orden que cambió de estado. |
| `secuencia` | `integer` | No |  |  | Número consecutivo del cambio dentro de la orden. |
| `estado_anterior` | `character varying(20)` | Sí |  |  | Estado previo. |
| `estado_nuevo` | `character varying(20)` | No |  |  | Estado resultante. |
| `realizado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Usuario que hizo el cambio. |
| `motivo` | `text` | Sí |  |  | Motivo del cambio, cuando aplica. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **CHECK** `historial_estado_orden_secuencia_check`: `CHECK ((secuencia > 0))`
- **UNIQUE** `uq_historial_orden_secuencia`: `UNIQUE (orden_mantenimiento_id, secuencia)`
- **Índice** `idx_historial_orden_fecha`: `INDEX idx_historial_orden_fecha ON historial_estado_orden (orden_mantenimiento_id, creado_en)`

### plan_mantenimiento_rutinario

Programación periódica de mantenimiento rutinario de un puente, que genera órdenes por ciclo. *Creada en `V10`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `puente_id` | `uuid` | No |  | FK → `puente.id`<br>UNIQUE | Puente del plan. |
| `periodicidad_meses` | `smallint` | No | `12` |  | Cada cuántos meses se genera un ciclo. |
| `fecha_inicio` | `date` | No |  |  | Fecha del primer ciclo. |
| `responsable_usuario_id` | `uuid` | Sí |  | FK → `usuario.id` | Usuario responsable del plan. |
| `activo` | `boolean` | No | `true` |  | El plan genera órdenes. |
| `aprobado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Usuario que aprobó el plan. |
| `aprobado_en` | `timestamp with time zone` | Sí |  |  | Momento de la aprobación. |
| `inactivado_en` | `timestamp with time zone` | Sí |  |  | Momento en que se inactivó. |
| `motivo_inactivacion` | `text` | Sí |  |  | Motivo de la inactivación. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |

**Restricciones e índices adicionales**

- **CHECK** `plan_inactivacion_completa`: `CHECK (((activo AND (inactivado_en IS NULL) AND (motivo_inactivacion IS NULL)) OR ((NOT activo) AND (inactivado_en IS NOT NULL) AND (motivo_inactivacion IS NOT NULL))))`
- **CHECK** `plan_mantenimiento_rutinario_periodicidad_meses_check`: `CHECK (((periodicidad_meses >= 1) AND (periodicidad_meses <= 12)))`
- **UNIQUE** `uq_plan_id_puente`: `UNIQUE (id, puente_id)`

### plan_mantenimiento_rutinario_actividad

Actividad ordenada que forma parte de un plan de mantenimiento rutinario. *Creada en `V25`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `plan_mantenimiento_rutinario_id` | `uuid` | No |  | FK → `plan_mantenimiento_rutinario.id` | Plan al que pertenece la actividad. |
| `posicion` | `integer` | No |  |  | Orden de la actividad dentro del plan. |
| `descripcion` | `text` | No |  |  | Descripción de la actividad. |

**Restricciones e índices adicionales**

- **CHECK** `plan_mantenimiento_rutinario_actividad_descripcion_check`: `CHECK ((length(TRIM(BOTH FROM descripcion)) > 0))`
- **CHECK** `plan_mantenimiento_rutinario_actividad_posicion_check`: `CHECK ((posicion > 0))`
- **UNIQUE** `uq_plan_actividad_posicion`: `UNIQUE (plan_mantenimiento_rutinario_id, posicion) DEFERRABLE INITIALLY DEFERRED`

## Foro y avisos

### foro_hilo

Hilo de discusión de un puente o del hilo único de una inspección publicada. *Creada en `V11`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `puente_id` | `uuid` | No |  | FK compuesta → `inspeccion`<br>FK → `puente.id` | Puente al que pertenece el hilo. |
| `inspeccion_id` | `uuid` | Sí |  | FK compuesta → `inspeccion` | Inspección publicada, si es el hilo de una inspección (uno por inspección). |
| `autor_id` | `uuid` | No |  | FK → `usuario.id` | Usuario que abrió el hilo. |
| `categoria` | `character varying(30)` | No |  |  | Categoría del hilo (RN-FOR-06). Valores: `DISCUSION_TECNICA`, `PROPUESTA_INTERVENCION`, `CONSULTA`, `REFERENCIA_DOCUMENTAL`, `ALERTA`. |
| `titulo` | `character varying(250)` | No |  |  | Título del hilo. |
| `cerrado_en` | `timestamp with time zone` | Sí |  |  | Momento en que un moderador lo cerró. |
| `cerrado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Moderador que lo cerró. |
| `motivo_cierre` | `text` | Sí |  |  | Motivo del cierre, visible para todos. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `actualizado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la última modificación. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |

**Restricciones e índices adicionales**

- **CHECK** `foro_hilo_cierre_completo`: `CHECK ((((cerrado_en IS NULL) AND (cerrado_por_id IS NULL) AND (motivo_cierre IS NULL)) OR ((cerrado_en IS NOT NULL) AND (cerrado_por_id IS NOT NULL) AND (motivo_cierre IS NOT NULL))))`
- **FK** `fk_foro_hilo_inspeccion_puente`: `FOREIGN KEY (inspeccion_id, puente_id) REFERENCES inspeccion(id, puente_id)`
- **Índice** `idx_foro_hilo_inspeccion`: `INDEX idx_foro_hilo_inspeccion ON foro_hilo (inspeccion_id)`
- **Índice** `idx_foro_hilo_puente_fecha`: `INDEX idx_foro_hilo_puente_fecha ON foro_hilo (puente_id, creado_en DESC)`
- **Índice** `uq_foro_hilo_inspeccion`: `UNIQUE INDEX uq_foro_hilo_inspeccion ON foro_hilo (inspeccion_id) WHERE (inspeccion_id IS NOT NULL)`

### foro_comentario

Comentario o respuesta (un solo nivel) de un hilo; admite edición, eliminación con marca y ocultamiento por moderación. *Creada en `V11`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `hilo_id` | `uuid` | No |  | FK compuesta → `foro_comentario`<br>FK → `foro_hilo.id` | Hilo al que pertenece. |
| `autor_id` | `uuid` | No |  | FK → `usuario.id` | Autor del comentario. |
| `padre_id` | `uuid` | Sí |  | FK compuesta → `foro_comentario` | Comentario principal al que responde (un solo nivel). |
| `elemento_ref` | `character varying(300)` | Sí |  |  | Campo o elemento de la inspección al que se ancla, si aplica. |
| `instancia_elemento_id` | `uuid` | Sí |  |  | Instancia del elemento repetible, si aplica. |
| `contenido` | `text` | No |  |  | Texto del comentario; se conserva aunque se elimine u oculte. |
| `editado_en` | `timestamp with time zone` | Sí |  |  | Última edición del autor (solo dentro de 15 minutos). |
| `eliminado_en` | `timestamp with time zone` | Sí |  |  | Momento en que el autor lo eliminó; queda la marca. |
| `ocultado_en` | `timestamp with time zone` | Sí |  |  | Momento en que un moderador lo ocultó. |
| `ocultado_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Moderador que lo ocultó. |
| `motivo_ocultamiento` | `text` | Sí |  |  | Motivo del ocultamiento, visible solo para moderadores. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |
| `version` | `bigint` | No | `0` |  | Versión para el bloqueo optimista (JPA `@Version`). |

**Restricciones e índices adicionales**

- **CHECK** `foro_comentario_instancia_ref`: `CHECK (((instancia_elemento_id IS NULL) OR (elemento_ref IS NOT NULL)))`
- **CHECK** `foro_comentario_no_autopadre`: `CHECK (((padre_id IS NULL) OR (padre_id <> id)))`
- **CHECK** `foro_comentario_ocultamiento_completo`: `CHECK ((((ocultado_en IS NULL) AND (ocultado_por_id IS NULL) AND (motivo_ocultamiento IS NULL)) OR ((ocultado_en IS NOT NULL) AND (ocultado_por_id IS NOT NULL) AND (motivo_ocultamiento IS NOT NULL))))`
- **FK** `fk_foro_comentario_padre_mismo_hilo`: `FOREIGN KEY (padre_id, hilo_id) REFERENCES foro_comentario(id, hilo_id)`
- **UNIQUE** `uq_foro_comentario_id_hilo`: `UNIQUE (id, hilo_id)`
- **Índice** `idx_foro_comentario_hilo_fecha`: `INDEX idx_foro_comentario_hilo_fecha ON foro_comentario (hilo_id, creado_en)`
- **Índice** `idx_foro_comentario_padre`: `INDEX idx_foro_comentario_padre ON foro_comentario (padre_id)`

### comentario_mencion

Registro de que un comentario menciona a un usuario con @usuario; evita avisarle dos veces por el mismo comentario. *Creada en `V11`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `comentario_id` | `uuid` | No |  | FK → `foro_comentario.id` | Comentario que contiene la mención. |
| `usuario_mencionado_id` | `uuid` | No |  | FK → `usuario.id` | Usuario mencionado y avisado. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **UNIQUE** `uq_comentario_mencion`: `UNIQUE (comentario_id, usuario_mencionado_id)`
- **Índice** `idx_comentario_mencion_usuario_fecha`: `INDEX idx_comentario_mencion_usuario_fecha ON comentario_mencion (usuario_mencionado_id, creado_en DESC)`

### notificacion

Aviso dentro de la aplicación para un usuario, único por usuario y clave de evento. *Creada en `V11`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `usuario_id` | `uuid` | No |  | FK → `usuario.id` | Destinatario del aviso. |
| `tipo` | `character varying(50)` | No |  |  | Tipo de aviso (por ejemplo FORO_MENCION o FORO_ALERTA_PUENTE_MALO). |
| `titulo` | `character varying(200)` | No |  |  | Título del aviso. |
| `mensaje` | `text` | No |  |  | Texto del aviso, sin datos sensibles. |
| `entidad` | `character varying(100)` | Sí |  |  | Entidad a la que lleva el aviso. |
| `entidad_id` | `uuid` | Sí |  |  | Identificador de esa entidad. |
| `clave_evento` | `character varying(250)` | No |  |  | Clave de negocio que evita avisar dos veces el mismo evento. |
| `leido_en` | `timestamp with time zone` | Sí |  |  | Momento en que el usuario lo leyó. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **CHECK** `notificacion_entidad_coherente`: `CHECK ((((entidad IS NULL) AND (entidad_id IS NULL)) OR ((entidad IS NOT NULL) AND (entidad_id IS NOT NULL))))`
- **UNIQUE** `uq_notificacion_usuario_evento`: `UNIQUE (usuario_id, clave_evento)`
- **Índice** `idx_notificacion_no_leida`: `INDEX idx_notificacion_no_leida ON notificacion (usuario_id, creado_en DESC) WHERE (leido_en IS NULL)`
- **Índice** `idx_notificacion_usuario_fecha`: `INDEX idx_notificacion_usuario_fecha ON notificacion (usuario_id, creado_en DESC)`

## Sincronización offline e infraestructura

### operacion_idempotente

Reserva y resultado de una operación HTTP con Idempotency-Key, para que los reintentos offline no dupliquen efectos. *Creada en `V11`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `usuario_id` | `uuid` | No |  | FK → `usuario.id` | Usuario que hizo la operación. |
| `clave` | `character varying(100)` | No |  |  | Idempotency-Key enviada por el cliente; única por usuario. |
| `metodo` | `character varying(10)` | No |  |  | Método HTTP. |
| `ruta` | `character varying(300)` | No |  |  | Ruta HTTP. |
| `hash_solicitud` | `character varying(64)` | No |  |  | Hash de la solicitud; otra solicitud con la misma clave se rechaza. |
| `estado` | `character varying(20)` | No | `'EN_PROCESO'` |  | Estado de la operación; COMPLETADA exige código de respuesta y fecha. Valores: `EN_PROCESO`, `COMPLETADA`. |
| `codigo_respuesta` | `integer` | Sí |  |  | Código HTTP de la respuesta guardada. |
| `respuesta` | `jsonb` | Sí |  |  | Respuesta guardada para repetirla, o manifiesto de la carga en curso (JSON). |
| `entidad` | `character varying(100)` | Sí |  |  | Entidad creada o modificada. |
| `entidad_id` | `uuid` | Sí |  |  | Identificador de esa entidad. |
| `completado_en` | `timestamp with time zone` | Sí |  |  | Momento en que se completó. |
| `expira_en` | `timestamp with time zone` | Sí |  |  | Vencimiento de la reserva o de la respuesta guardada. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **CHECK** `operacion_completada_coherente`: `CHECK (((((estado) = 'EN_PROCESO') AND (codigo_respuesta IS NULL) AND (completado_en IS NULL)) OR (((estado) = 'COMPLETADA') AND (codigo_respuesta IS NOT NULL) AND (completado_en IS NOT NULL))))`
- **UNIQUE** `uq_operacion_usuario_clave`: `UNIQUE (usuario_id, clave)`
- **Índice** `idx_operacion_expira`: `INDEX idx_operacion_expira ON operacion_idempotente (expira_en) WHERE (expira_en IS NOT NULL)`

### conflicto_sincronizacion

Registro inmutable de un conflicto detectado al sincronizar una inspección y su resolución. *Creada en `V11`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `id` | `uuid` | No |  | PK | Identificador del registro (UUID v7 generado por la aplicación). |
| `inspeccion_id` | `uuid` | No |  | FK → `inspeccion.id` | Inspección en conflicto. |
| `operacion_idempotente_id` | `uuid` | No |  | FK → `operacion_idempotente.id` | Operación de sincronización que lo detectó. |
| `version_servidor` | `bigint` | No |  |  | Versión que tenía el servidor. |
| `version_cliente` | `bigint` | Sí |  |  | Versión que tenía el cliente. |
| `copia_servidor` | `jsonb` | No |  |  | Copia de los datos del servidor antes de resolver (JSON). |
| `hash_copia_cliente` | `character varying(64)` | No |  |  | Hash de los datos enviados por el cliente. |
| `resolucion` | `character varying(30)` | No | `'CLIENTE_PREVALECE'` |  | Cómo se resolvió (prevalece el cliente). Valores: `CLIENTE_PREVALECE`. |
| `detectado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la detección. |
| `resuelto_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de la resolución. |
| `resuelto_por_id` | `uuid` | Sí |  | FK → `usuario.id` | Usuario cuya sincronización resolvió el conflicto. |
| `creado_en` | `timestamp with time zone` | No | `CURRENT_TIMESTAMP` |  | Momento de creación del registro. |

**Restricciones e índices adicionales**

- **UNIQUE** `uq_conflicto_operacion_inspeccion`: `UNIQUE (operacion_idempotente_id, inspeccion_id)`
- **Índice** `idx_conflicto_inspeccion_fecha`: `INDEX idx_conflicto_inspeccion_fecha ON conflicto_sincronizacion (inspeccion_id, detectado_en DESC)`

### shedlock

Bloqueos distribuidos de ShedLock para que cada proceso programado se ejecute en una sola instancia. *Creada en `V1`.*

| Columna | Tipo | Nulo | Por defecto | Clave | Significado |
|---|---|---|---|---|---|
| `name` | `character varying(64)` | No |  | PK | Nombre del proceso programado (clave primaria). |
| `lock_until` | `timestamp with time zone` | No |  |  | Hasta cuándo dura el bloqueo. |
| `locked_at` | `timestamp with time zone` | No |  |  | Momento en que se tomó el bloqueo. |
| `locked_by` | `character varying(255)` | No |  |  | Instancia que tomó el bloqueo. |
