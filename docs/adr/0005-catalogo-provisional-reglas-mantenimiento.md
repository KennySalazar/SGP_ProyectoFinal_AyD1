# ADR-0005: Catálogo provisional de reglas de mantenimiento daño → intervención

## Estado

Aceptada

## Contexto

Las reglas de negocio RN-MTO-02 y RN-MTO-03, junto con los requerimientos técnicos DT-BD-02 y DT-BE-12, establecen que el Sistema de Gestión de Puentes (SGP) debe sugerir propuestas de órdenes de mantenimiento preventivo, correctivo, rutinario o de emergencia al publicarse una inspección técnica.

La tabla `regla_mantenimiento` ya fue provista en la base de datos (migración Flyway V10) con una estructura versionada por código, número de revisión y versión del formulario (`uq_regla_mantenimiento_revision`), y con una restricción parcial de unicidad para la regla activa (`uq_regla_mantenimiento_activa`).

Se requiere definir:
1. El carácter provisional del catálogo inicial.
2. La metodología de derivación de la semilla a partir de los indicadores de daño del esquema de inspección SIECA (`sieca-v1`).
3. La estructura de datos de las condiciones y prioridades (contrato entre frentes).
4. El mecanismo de versionamiento ante modificaciones.
5. Los criterios y procedimiento de validación formal por parte del Catedrático de Puentes.

## Decisiones

### 1. Carácter provisional del catálogo inicial

- Al iniciar el sistema sobre una versión activa del formulario de inspección (por ejemplo, `SIECA-v1`) que no posea reglas de mantenimiento, el backend sembrará automáticamente un conjunto de 18 reglas iniciales derivadas de los elementos e indicadores del esquema.
- Toda regla recién sembrada o creada por el Administrador que no haya sido validada por un docente especialista permanece en estado **«Provisional · sin validar»** (`validado_por_id = null`, `validado_en = null`).
- Las reglas provisionales están activas y operativas para generar propuestas de mantenimiento, pero se distinguen visual y técnicamente con el rótulo correspondiente para advertir a los inspectores y administradores que son parámetros sugeridos pendientes de validación por un docente especialista.

### 2. Construcción de la semilla basada en indicadores del esquema SIECA

La semilla inicial de 18 reglas se diseñó mapeando directamente las rutas canónicas del esquema JSON (`sieca-v1/esquema.schema.json`) y cubriendo exhaustivamente las 4 tipologías del manual (RN-MTO-03):

- **RUTINARIO (4 reglas):** Intervenciones periódicas simples de conservación menor:
  - Limpieza de juntas de entrada (`/elementos_no_estructurales/junta_entrada`).
  - Limpieza de cauce asolvado/sedimentado (`/caracteristicas_cauce/estado_cauce`).
  - Desazolve de drenajes aledaños (`/otros/drenajes_aledanos`).
  - Repintado de señalización horizontal (`/otros/senalizacion/horizontal`).
- **PREVENTIVO (5 reglas):** Tratamientos anticipados para mitigar degradación:
  - Sellado de grietas por flexión en losa de rodadura (`/superestructura/losa_rodadura`).
  - Tratamiento preventivo anticorrosivo en vigas de acero (`/superestructura/elemento_portante_acero`).
  - Mantenimiento y engrase de apoyos elastoméricos/neopreno (`/superestructura/apoyos`).
  - Mantenimiento y limpieza de juntas de salida (`/elementos_no_estructurales/junta_salida`).
  - Limpieza preventiva de tuberías y bajadas de drenaje (`/elementos_no_estructurales/drenaje_superficie`).
- **CORRECTIVO (6 reglas):** Reparaciones de elementos con daño funcional moderado a severo:
  - Inyección epóxica en grietas diagonales de vigas de concreto (`/superestructura/elemento_portante_concreto`).
  - Reparación de barandales y pasamanos golpeados (`/elementos_no_estructurales/pasamanos_barandal`).
  - Reparación y reposición de juntas dañadas (`/elementos_no_estructurales/junta_entrada`).
  - Reemplazo y nivelación de apoyos fijos o móviles dañados (`/superestructura/apoyos`).
  - Reparación de mampostería o concreto en estribos por socavación moderada (`/subestructura/estribo_entrada/socavacion`).
  - Encamisado y reparación de agrietamiento severo en columnas de pilas (`/subestructura/pilas/socavacion`).
- **EMERGENCIA (3 reglas):** Daños graves que comprometen la estabilidad o seguridad estructural inmediata:
  - Socavación crítica en estribo de entrada con cimentación expuesta (`CON_EXPOSICION`).
  - Asentamiento estructural detectado en estribo de entrada (`ASENTAMIENTO_ESTRUCTURA`).
  - Socavación severa en pilas con cimentación expuesta (`CON_EXPOSICION`).
  - **Prioridad base:** Asignada como `CRITICA` (la más alta del sistema), exigiendo atención urgente e inmediata.

### 3. Contrato de la condición y prioridad como datos (JSON)

Para evitar la ejecución de código dinámico y garantizar portabilidad, validación declarativa e interoperabilidad entre Angular y Spring Boot:

- **Condición (`condicion`):**
  ```json
  {
    "indicador": "pintura",
    "operador": "MENOR_O_IGUAL",
    "umbral": "REGULAR"
  }
  ```
  Los operadores admitidos son: `IGUAL`, `DISTINTO`, `MAYOR_O_IGUAL`, `MENOR_O_IGUAL`, `CONTIENE`.
  La regla exige que `elemento_ref` y `indicador` existan en el esquema JSON de la versión del formulario correspondiente. Si no existen, el sistema rechaza la operación con `422 Unprocessable Entity` (RFC 7807), explicando el nodo no encontrado.

- **Regla de Prioridad (`regla_prioridad`):**
  ```json
  {
    "prioridad_base": "CRITICA",
    "factores": []
  }
  ```
  Valores de prioridad base: `BAJA`, `MEDIA`, `ALTA`, `CRITICA`.

### 4. Ciclo de vida y versionamiento (Revisión N + 1)

- Al crear una regla, su revisión inicial es `1`.
- Al modificar cualquier atributo de una regla existente (`elemento_ref`, condición, intervención, tipo o prioridad), el sistema **no sobreescribe** el registro original. En su lugar, crea un nuevo registro con el mismo código, la misma versión de formulario y `numero_revision = MAX(revisiones) + 1`.
- Si la nueva revisión se marca como activa, las revisiones previas del mismo código para esa versión se desactivan automáticamente, respetando el índice único parcial de la base de datos `uq_regla_mantenimiento_activa`.
- Desactivar una regla no altera las órdenes de mantenimiento previamente generadas con dicha regla.

### 5. Criterios para la validación por parte del Catedrático

- **Validación colegiada o externa:** El Catedrático de Puentes revisa los umbrales de daño y las técnicas de intervención sugeridas según las directrices del Manual Centroamericano de Gestión del Riesgo en Puentes.
- **Registro en el sistema:** El Administrador, tras recibir el visto bueno formal del Catedrático, ejecuta la acción de validación en la plataforma.
- **Efecto de la validación:** El sistema almacena el identificador del usuario que validó (`validado_por_id`) y la marca temporal (`validado_en`), cambiando el estado de la regla a **«Validada»** y retirando la etiqueta de provisional. Toda acción de creación, edición, activación o validación queda registrada con instantáneas en la bitácora de auditoría (RN-USR-09).
- **Versión sin reglas activas:** Si una versión de formulario no posee reglas activas, la publicación de inspecciones procede sin errores (produciendo 0 propuestas), y el Administrador recibe la advertencia explícita: *«Esta versión no tiene reglas de mantenimiento activas»*.

## Consecuencias

### Positivas
- Trazabilidad y auditoría completas de la evolución de las reglas de mantenimiento.
- Flexibilidad para incorporar nuevos elementos e indicadores según evolucione el esquema SIECA.
- Transparencia hacia los inspectores y docentes sobre qué reglas cuentan con validación técnica y cuáles son propuestas preliminares.
- Cumplimiento de RBAC: Catedrático cuenta con acceso de lectura y consulta al catálogo, mientras que la mutación y administración está restringida al Administrador.
