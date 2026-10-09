# Sistema de Gestión de Puentes (SGP)

El Sistema de Gestión de Puentes (SGP) es una aplicación web progresiva orientada al inventario, inspección técnica, revisión académica, colaboración y mantenimiento de puentes.

El sistema está diseñado para permitir trabajo en campo con conectividad limitada, utilizando una aplicación Angular PWA y una API REST desarrollada con Spring Boot.

## Estructura del repositorio

```text
SGP_ProyectoFinal_AyD1/
├── sgp-api/                  # Backend Spring Boot
├── sgp-client/               # Frontend Angular PWA
├── infra/                    # Inicialización de infraestructura
│   ├── postgres/
│   └── staging/              # Compose y operación de staging
├── docs/                     # Documentación técnica
├── docker-compose.yml
├── .gitignore
└── README.md
```

## Stack principal

### Backend

- Java 21.0.12 LTS
- Spring Boot 3.3.13
- Maven
- Spring Web
- Spring Data JPA
- Spring Security
- Spring Validation
- Spring Boot Actuator
- Flyway 10.10.0
- PostgreSQL Driver 42.7.7
- Hibernate Spatial 6.5.3.Final
- Hypersistence Utils 3.9.4
- JJWT 0.12.6
- MapStruct 1.6.3
- Springdoc OpenAPI 2.6.0
- NetworkNT JSON Schema Validator 1.5.6
- ShedLock 6.3.0
- Testcontainers 1.21.4
- JaCoCo 0.8.12

### Base de datos

- PostgreSQL 16.9
- PostGIS 3.5.2
- pg_trgm 1.6
- uuid-ossp 1.1

### Frontend

- Angular 21.2.24
- Angular CLI 21.2.24
- TypeScript 5.9.3
- Node.js 24
- RxJS 7.8.2
- PrimeNG 21.x
- PrimeIcons
- PrimeUIX Themes
- @jsverse/transloco 8.4.0
- Angular Signals
- Reactive Forms
- Angular Service Worker / PWA
- Dexie 4.4.6
- IndexedDB
- MapLibre GL 5.24.0
- ESLint 9.x
- Prettier 3.9.9

### Infraestructura

- Docker
- Docker Compose
- PostgreSQL + PostGIS
- MinIO
- Nginx

## Desarrollo local

La guía completa para configurar y ejecutar el proyecto se encuentra en:

[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)

Flujo básico:

```text
PostgreSQL/PostGIS
        ↓
Backend Spring Boot
        ↓
Frontend Angular
```

Puertos principales de desarrollo:

| Servicio | Dirección |
|---|---|
| Frontend | `http://localhost:4200` |
| Backend | `http://localhost:8090` |
| Health | `http://localhost:8090/actuator/health` |
| Swagger UI | `http://localhost:8090/swagger-ui/index.html` |
| PostgreSQL | `localhost:55432` |

## Documentación

Antes de desarrollar una funcionalidad, revisar la documentación correspondiente:

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — organización del backend y frontend, capas, servicios y rutas.
- [`docs/UI_GUIDELINES.md`](docs/UI_GUIDELINES.md) — reglas visuales y de experiencia de usuario.
- [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) — preparación y ejecución del entorno local.
- [`docs/deployment/STAGING_AWS.md`](docs/deployment/STAGING_AWS.md) — arquitectura, configuración y operación de staging en AWS.
- [`docs/deployment/GUIA_IMPLEMENTACION_PRODUCCION.md`](docs/deployment/GUIA_IMPLEMENTACION_PRODUCCION.md) — contratos y decisiones para el responsable de producción.
- [`docs/adr/`](docs/adr/) — decisiones de arquitectura relevantes.

## Reglas principales

- Mantener la organización por dominios y funcionalidades definida en `ARCHITECTURE.md`.
- No acceder desde un controller directamente a un repository.
- No exponer entidades JPA directamente mediante la API.
- Mantener la lógica de negocio en los servicios.
- Centralizar las llamadas HTTP del frontend en servicios de feature o servicios transversales de `core/`.
- Mantener las rutas específicas dentro de cada feature.
- No modificar migraciones Flyway ya aplicadas; crear una nueva migración para cada cambio.
- No versionar archivos `.env`, credenciales, tokens ni secretos.
- No introducir cambios arquitectónicos relevantes sin documentarlos mediante un ADR.

## Flujo Git

El equipo trabaja con:

```text
main
develop
feature/*
```

Las funcionalidades se desarrollan en ramas `feature/*` y se integran mediante Pull Request hacia `develop`.

Ejemplo:

```bash
git switch develop
git pull origin develop
git switch -c feature/nombre-funcionalidad
```

Se utilizan mensajes de commit siguiendo **Conventional Commits**.

Ejemplos:

```text
feat: add bridge registration
fix: correct authentication refresh
refactor: move api calls to feature service
test: add bridge integration tests
docs: update development guide
```

## Proyecto académico

**Curso:** Análisis y Diseño de Sistemas 1  
**Proyecto:** Sistema de Gestión de Puentes (SGP)  
**Segundo semestre 2026**
