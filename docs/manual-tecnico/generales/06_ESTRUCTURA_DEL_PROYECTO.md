# Estructura del proyecto

El repositorio separa la aplicación en cliente, API, infraestructura y documentación.

| Área | Organización | Responsabilidad |
|---|---|---|
| `sgp-api/` | Backend Spring Boot | API REST y lógica por dominios funcionales. |
| `sgp-client/` | Aplicación Angular PWA | Interfaz, navegación, acceso a API y experiencia offline. |
| `infra/` | PostgreSQL, RDS, staging, producción y respaldos | Inicialización y automatización de operación. |
| `docs/` | Arquitectura, pruebas, base de datos, API y manual técnico | Documentación del sistema y de sus procesos. |
| `.github/workflows/` | Flujos de CI y publicación | Validación automatizada y construcción de imágenes. |

## Backend

El código Java se organiza por dominios, entre ellos usuarios, cursos, puentes, formularios, inspecciones, revisión, mantenimiento, archivos, foro, notificaciones y sincronización. Dentro de cada dominio se separan según necesidad los controladores REST, DTO, entidades, repositorios, servicios, mapeadores y validaciones. `common/` reúne capacidades transversales como configuración, seguridad, errores y auditoría.

## Cliente

La aplicación organiza pantallas y servicios por `features/`. `core/` aloja navegación y capacidades transversales; `offline/` encapsula la persistencia local y sincronización; `shared/` mantiene componentes reutilizables; y `layouts/` y `theme/` contienen la estructura y estilos comunes. Las rutas cargan funcionalidades de forma diferida cuando corresponde.

## Persistencia y documentación

Las migraciones Flyway del backend describen la evolución de PostgreSQL/PostGIS. La documentación API, las guías de prueba y el diccionario de datos acompañan el código. El manual técnico agrupa capítulos generales y módulos funcionales para que puedan consultarse juntos o por separado.
