# SGP Client - Frontend

Frontend del **Sistema de Gestion de Puentes (SGP)** desarrollado como SPA y PWA con Angular.

La aplicacion esta pensada para trabajo tecnico de campo y escritorio, incluyendo formularios dinamicos, inspecciones, georreferenciacion, trabajo offline y sincronizacion posterior.

## Stack verificado

| Tecnologia             | Version instalada / uso |
| ---------------------- | ----------------------- |
| Angular                | **21.2.24**             |
| Angular CLI            | **21.2.24**             |
| TypeScript             | **5.9.3**               |
| Node.js                | **24.19.0**             |
| npm                    | **11.17.0**             |
| RxJS                   | **7.8.2**               |
| Angular Service Worker | **21.2.24**             |
| PrimeNG                | **21.1.10**             |
| Transloco              | **8.4.0**               |
| PrimeUIX Themes        | **3.x**                 |
| PrimeIcons             | **7.x**                 |
| Dexie                  | **4.4.6** instalada     |
| MapLibre GL            | **5.24.0** instalada    |
| ESLint                 | **9.39.5** instalado    |
| Prettier               | **3.9.9** instalado     |

El proyecto utiliza:

- TypeScript `strict: true`.
- `strictTemplates: true`.
- Componentes standalone.
- Angular Signals para estado sincrono.
- RxJS para operaciones asincronas.
- Control flow moderno de Angular.
- Reactive Forms.
- Carga diferida por funcionalidad.
- PWA con service worker.
- IndexedDB mediante Dexie.
- MapLibre GL para mapas.
- PrimeNG para componentes visuales y formularios.
- Transloco para centralizar los textos de interfaz y permitir traduccion futura.

## Estructura

La organizacion principal sigue las funcionalidades del dominio:

```text
src/app/
├── core/
│   ├── guards/
│   ├── interceptors/
│   ├── models/
│   └── services/
├── shared/
│   └── components/
├── features/
│   ├── admin/
│   ├── dashboard/
│   ├── formulario/
│   ├── foro/
│   ├── inspecciones/
│   ├── mantenimiento/
│   ├── puentes/
│   ├── revision/
│   └── usuario/
├── offline/
│   ├── db/
│   ├── pages/
│   └── services/
├── layouts/
├── theme/
├── app.config.ts
└── app.routes.ts
```

### Responsabilidad de cada area

`core/`
: Servicios singleton, autenticacion, estado de sesion, guards, interceptores y modelos globales.

`shared/`
: Componentes reutilizables y controles visuales que no pertenecen a una funcionalidad concreta.

`features/`
: Funcionalidades del sistema organizadas por dominio.

`offline/`
: IndexedDB, conectividad, persistencia local, cola de sincronizacion y diagnostico offline.

`layouts/`
: Estructuras visuales globales, como el shell autenticado y la barra lateral.

`theme/`
: Configuracion visual de PrimeNG y del sistema.

`core/i18n/`
: Cargador y configuracion de internacionalizacion con Transloco.

## PrimeNG

PrimeNG se utiliza como capa de componentes visuales, pero no reemplaza la arquitectura Angular.

Los formularios deben seguir utilizando Reactive Forms y, para el formulario SIECA, los controles deben generarse dinamicamente desde el JSON Schema recibido del backend.

Ejemplo conceptual:

```text
JSON Schema
    -> motor de formulario
    -> FormGroup dinamico
    -> componente PrimeNG apropiado
```

Mapeo esperado de controles:

```text
string       -> InputText
number       -> InputNumber
date         -> DatePicker
enum         -> Select
boolean      -> Checkbox
texto largo  -> Textarea
archivo      -> FileUpload
estado       -> Tag
```

No codificar las ocho secciones del formulario SIECA como plantillas fijas.

## Textos e internacionalizacion con Transloco

Los textos visibles de la interfaz deben centralizarse en:

```text
public/i18n/es.json
```

Los componentes utilizan claves mediante `TranslocoPipe`, por ejemplo:

```html
<span>{{ 'nav.home' | transloco }}</span>
```

No escribir nuevos textos repetidos directamente en las plantillas si corresponden a etiquetas, titulos, botones, mensajes base o navegacion. Agregar una clave al catalogo y reutilizarla.

Actualmente el idioma base es espanol. La estructura permite agregar otros idiomas posteriormente sin reescribir los componentes.

## Diseno visual

La interfaz actual utiliza una identidad visual orientada a una plataforma tecnica para ingenieria civil:

- Sidebar principal en el lado izquierdo en escritorio.
- Navegacion adaptable en dispositivos pequenos.
- Azul marino para estructura y navegacion.
- Azul tecnico para acciones principales.
- Verde, amarillo y rojo para estados, siempre acompanados de texto.
- PrimeNG como base de componentes.
- Controles tactiles adecuados para trabajo de campo.
- Diseno responsive.

La accesibilidad debe mantener como minimo WCAG 2.1 AA.

## Autenticacion

El frontend ya incluye la base para:

- Login.
- Verificacion OTP durante login cuando 2FA esta habilitado.
- Registro de estudiante.
- Verificacion de registro.
- Recuperacion de contrasena.
- Restablecimiento de contrasena.
- Activacion y desactivacion de 2FA.
- Refresh de sesion.
- Logout.
- Guardias por autenticacion y rol.

### Manejo de tokens

El access token se mantiene **solo en memoria** mediante el estado de autenticacion.

No utilizar:

```text
localStorage
sessionStorage
```

para guardar el access token.

El refresh token se maneja mediante cookie HttpOnly emitida por el backend.

## Trabajo offline

La base incluye:

- PWA con `@angular/service-worker`.
- `manifest.webmanifest`.
- `ngsw-config.json`.
- IndexedDB con Dexie.
- Servicio de conectividad.
- Generacion UUID v7 del lado cliente.
- Base de diagnostico offline.

Los almacenes previstos incluyen:

```text
puentes
inspecciones
fotos
esquemas_formulario
cola_sync
```

Los datos de inspeccion nunca deben guardarse en `localStorage`.

## Mapas

La aplicacion utiliza MapLibre GL.

No introducir Google Maps de pago ni otras dependencias que requieran facturacion por uso sin una decision de arquitectura documentada.

## Desarrollo local

Requisitos:

```text
Node.js 24.x
npm 11.x
```

Instalar dependencias:

```bash
npm install
```

Ejecutar:

```bash
npm start
```

Frontend:

```text
http://localhost:4200
```

El proxy local envia `/api` a:

```text
http://localhost:8090
```

por medio de:

```text
proxy.conf.json
```

## Scripts disponibles

```bash
npm start
npm run build
npm test
npm run lint
npm run format
```

### Build de produccion

```bash
npm run build
```

El proyecto tiene un presupuesto inicial configurado:

```text
Warning: 450 kB
Error:   500 kB
```

Por esta razon se deben importar solo los componentes PrimeNG necesarios y mantener lazy loading por funcionalidad.

## Reglas para nuevos componentes

- Usar componentes standalone.
- No crear nuevos `NgModule`.
- Usar `@if`, `@for` y `@switch` en lugar de directivas estructurales heredadas cuando aplique.
- Usar Signals para estado sincrono simple.
- Usar RxJS para HTTP y flujos asincronos.
- Usar Reactive Forms.
- Mantener la funcionalidad dentro de su carpeta de dominio.
- No colocar logica de negocio en componentes visuales.
- No duplicar servicios que ya pertenecen a `core/`.
- Mantener los textos visibles centralizados con Transloco en `public/i18n/es.json`.

## Ejemplo de una nueva funcionalidad

```text
features/puentes/
├── pages/
│   ├── bridge-list/
│   ├── bridge-detail/
│   └── bridge-form/
├── components/
├── services/
├── models/
└── bridge.routes.ts
```

La funcionalidad debe cargarse de forma diferida.

## Calidad

Actualmente se encuentran configurados ESLint y Prettier. El CI valida ambos antes de compilar el frontend.

Antes de integrar cambios:

```bash
npm run lint
npm run format:check
npm run build
```

Las pruebas E2E del modulo offline con red degradada deben incorporarse con Playwright o Cypress conforme avance el proyecto.
