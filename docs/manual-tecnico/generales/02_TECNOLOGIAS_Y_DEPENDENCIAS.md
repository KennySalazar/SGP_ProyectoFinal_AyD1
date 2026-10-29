# Tecnologías y dependencias principales

La tabla resume las tecnologías presentes en la configuración del proyecto. Las versiones exactas se fijan en Maven, npm o las imágenes de contenedor según corresponda.

| Capa | Tecnología | Función principal |
|---|---|---|
| Backend | Java 21, Spring Boot 3.3 | API REST, lógica de negocio, validación y seguridad. |
| Persistencia | Spring Data JPA, Hibernate Spatial, MapStruct | Acceso ORM, consultas geográficas y mapeo entre DTO y entidades. |
| Contratos | Springdoc OpenAPI, JSON Schema Validator | Descripción de API y validación de esquemas dinámicos. |
| Seguridad | Spring Security, JJWT, BCrypt | Autorización, tokens y protección de credenciales. |
| Base de datos | PostgreSQL 16, PostGIS 3.5, Flyway | Datos relacionales y espaciales, con evolución versionada del esquema. |
| Cliente | Angular 21, TypeScript 5.9, RxJS 7 | Aplicación web y coordinación de operaciones asíncronas. |
| Interfaz y mapas | PrimeNG, PrimeIcons, MapLibre GL | Componentes de interfaz y visualización geográfica. |
| PWA y trabajo local | Angular Service Worker, Dexie, IndexedDB | Caché de recursos y persistencia local estructurada. |
| Archivos | AWS SDK for Java S3, Apache PDFBox, ImageIO/WebP | Almacenamiento de objetos, generación/validación de PDF y procesamiento de imágenes. |
| Operación | Docker Compose, Nginx, MinIO, ShedLock | Entorno contenedorizado, proxy, almacenamiento compatible con S3 y coordinación de tareas. |
| Pruebas | JUnit/Spring Test, Testcontainers, Playwright | Pruebas de backend, integración con PostgreSQL y pruebas de navegador. |

El backend se compila con Maven. El cliente utiliza npm y el archivo de bloqueo para instalar las dependencias declaradas. En CI se usa Node.js 24; la imagen de compilación del cliente también utiliza Node 24.
