# Arquitectura

El Sistema de Gestión de Puentes (SGP) utiliza una arquitectura **cliente-servidor desacoplada**.

El backend se implementa en Spring Boot y se organiza mediante **módulos de dominio**, mientras que el frontend es una aplicación Angular PWA independiente que consume una API REST.

## 1. Estructura general

```text
sgp-proyecto-final-ayd1/
├── sgp-api/                  Backend Spring Boot
├── sgp-client/               Frontend Angular PWA
├── infra/                    Inicialización de infraestructura
│   └── postgres/
├── docs/                     Documentación y ADR
├── docker-compose.yml
├── .gitignore
└── README.md
```

## 2. Backend

El backend se encuentra en `sgp-api`.

Cada módulo representa una capacidad funcional del sistema y agrupa las capas necesarias para implementar esa funcionalidad.

Los dominios principales se encuentran en:

```text
sgp-api/src/main/java/gt/usac/cunoc/sgp/
├── archivo/
├── common/
├── formulario/
├── foro/
├── inspeccion/
├── mantenimiento/
├── puente/
├── revision/
├── sync/
├── usuario/
└── SgpApplication.java
```

Cada dominio se organiza según sus necesidades.

Por ejemplo:

```text
puente/
├── controller/
├── dto/
├── entity/
├── exception/
├── mapper/
├── model/
├── repository/
├── service/
└── validation/
```

Las carpetas internas de cada dominio se crean únicamente cuando sean necesarias.

Dentro de cada dominio se mantiene la separación:

```text
controller -> service -> repository -> entity
```

Responsabilidades principales:

- `controller/`: expone endpoints REST, valida la entrada y delega la operación al servicio.
- `dto/`: contiene requests y responses utilizados en la frontera de la API.
- `entity/`: contiene las entidades persistentes mapeadas mediante JPA.
- `exception/`: contiene excepciones específicas del dominio.
- `mapper/`: contiene el mapeo entre entidades y DTO mediante MapStruct.
- `model/`: contiene enums, value objects y otros conceptos internos del dominio.
- `repository/`: contiene el acceso a datos y las consultas.
- `service/`: contiene la lógica de negocio y coordina las operaciones del dominio.
- `validation/`: contiene validaciones específicas cuando sean necesarias.

Un controlador no debe acceder directamente a un repositorio.

La lógica de negocio debe permanecer en la capa de servicio.

Los DTO son obligatorios en la frontera de la API y las entidades JPA no deben serializarse directamente hacia el frontend.

La API REST funcional se mantiene versionada bajo `/api/v1`. Los endpoints técnicos, como OpenAPI y Actuator, mantienen sus rutas específicas.

## 3. `common/`

`common/` contiene elementos transversales utilizados por varios dominios.

```text
common/
├── config/
├── exception/
├── mail/
├── security/
└── util/
```

Responsabilidades:

- `config/`: configuración transversal de Spring y de la aplicación.
- `exception/`: manejo global de errores y excepciones compartidas.
- `mail/`: abstracciones y servicios relacionados con correo electrónico.
- `security/`: autenticación, JWT y configuración transversal de seguridad.
- `util/`: utilidades realmente compartidas entre varios dominios.

La infraestructura transversal de auditoría deberá permanecer también dentro de `common/` cuando sea implementada.

Las clases y reglas específicas de una funcionalidad deben permanecer dentro de su dominio correspondiente.

## 4. Frontend

El frontend se encuentra en `sgp-client` y se organiza por funcionalidades.

```text
src/app/
├── core/
├── shared/
├── features/
│   ├── admin/
│   ├── dashboard/
│   ├── usuario/
│   ├── puentes/
│   ├── inspecciones/
│   ├── formulario/
│   ├── revision/
│   ├── foro/
│   └── mantenimiento/
├── layouts/
├── offline/
└── theme/
```

`core/` contiene elementos transversales de la aplicación, como autenticación, guards, interceptores, manejo de sesión y servicios globales.

`shared/` contiene componentes, directivas, pipes y otros elementos reutilizables entre varias features.

Cada feature se organiza según sus necesidades:

```text
features/<feature>/
├── pages/                      # Pantallas ruteables de la feature
│   └── <pantalla>/             # Vista ruteable
│       ├── <pantalla>.ts
│       ├── <pantalla>.html
│       ├── <pantalla>.scss
│       ├── <pantalla>.spec.ts
│       └── <sub-componente>/   # Si solo lo utiliza esta pantalla
├── components/                 # Componentes compartidos por 2+ pantallas de la feature
├── models/                     # Modelos, interfaces y tipos de la feature
├── services/                   # Servicios de la feature y acceso a la API
└── <feature>.routes.ts         # Rutas propias de la feature
```

Las carpetas internas se crean únicamente cuando sean necesarias.

Las páginas y componentes no deben realizar llamadas HTTP directamente ni duplicar URLs o lógica de comunicación con el backend. El flujo esperado es:

```text
Page / Component
        ↓
Feature Service
        ↓
HttpClient
        ↓
Backend
```

Los servicios transversales utilizados por varias funcionalidades permanecen en `core/`.

Cada feature ruteable debe mantener sus rutas en un archivo propio `<feature>.routes.ts`, utilizando carga diferida cuando corresponda.

El archivo `app.routes.ts` debe concentrarse en las rutas de nivel superior y delegar las rutas específicas a cada feature, evitando acumular en él la navegación interna de todos los módulos.

`offline/` concentra la infraestructura de persistencia local y sincronización. Las features deben utilizar los servicios expuestos por esta capa en lugar de acceder directamente a IndexedDB o Dexie.

## 5. Correspondencia frontend / backend

Siempre que corresponda se mantiene una relación clara entre los dominios del backend y las features del frontend:

```text
BACKEND                         FRONTEND

puente/                         features/puentes/
inspeccion/                     features/inspecciones/
formulario/                     features/formulario/
revision/                       features/revision/
foro/                           features/foro/
mantenimiento/                  features/mantenimiento/
archivo/                        integrado en las features que gestionan adjuntos
usuario/                        features/usuario/ + core/
sync/                           offline/
```

Esta correspondencia permite localizar fácilmente las partes involucradas en una misma funcionalidad.

El dominio `archivo/` no requiere necesariamente una feature independiente en el frontend. Sus capacidades se integran en las funcionalidades que gestionan fotografías o documentos, principalmente inspecciones.

## 6. Reglas de organización

- Las clases deben permanecer dentro del dominio al que pertenecen.
- Las carpetas internas se crean únicamente cuando sean necesarias.
- La lógica específica de un dominio no debe trasladarse a `common/` salvo que su responsabilidad sea realmente transversal.
- Las páginas y componentes del frontend no deben acceder directamente a la API cuando la operación pertenezca a una feature; dicha comunicación debe centralizarse en sus servicios.
- Las rutas específicas de una funcionalidad deben mantenerse dentro de su archivo `<feature>.routes.ts`, dejando `app.routes.ts` para la composición de rutas de nivel superior.
- No deben agregarse nuevas capas ni modificarse las reglas principales de organización sin una necesidad arquitectónica justificada.
- Las decisiones que modifiquen de forma relevante la arquitectura deben documentarse mediante un ADR en `docs/adr/`.