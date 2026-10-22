# Inventario de Autorización de Endpoints (DT-CAL-03, DT-SEC-03)

Este documento forma parte del manual técnico del Sistema de Gestión de Puentes (SGP) y consolida el inventario exhaustivo de todos los endpoints expuestos bajo el prefijo `/api/v1`, sus políticas de control de acceso declarativo mediante anotaciones `@PreAuthorize`, la regla de seguridad a nivel de fila aplicable (DT-SEC-04), y la suite de pruebas unitarias o de integración que valida dicho comportamiento.

---

## 1. Directrices de Seguridad y Política de Acceso

1. **Denegación por Defecto (DT-SEC-03):**
   Todos los endpoints de la API deben declarar explícitamente su regla de autorización con `@PreAuthorize`. La prueba automatizada `EndpointsAuthorizationScanTest` analiza por introspección de classpath el 100% de los controladores y métodos mapeados (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping`). Falla si algún endpoint carece de regla de seguridad explícita.
2. **Autorización por Rol (DT-CAL-03):**
   Cada endpoint restringe el acceso según el rol (`ADMINISTRADOR`, `CATEDRATICO`, `ESTUDIANTE`), exige autenticación genérica (`isAuthenticated()`) o permite acceso público explícito (`permitAll()`). Los accesos no autorizados reciben invariablemente código de respuesta `403 Forbidden` (o `401 Unauthorized` si carecen de token de sesión).
3. **Seguridad a Nivel de Fila (DT-SEC-04):**
   - Un **Estudiante** únicamente puede ver y modificar sus propios borradores de inspección (`inspeccion.usuario_id == current_user.id`).
   - Un **Catedrático** únicamente puede gestionar y calificar estudiantes asignados a los cursos que él imparte.
   - Peticiones sobre recursos ajenos o inexistentes retornan invariablemente `404 Not Found` (o `403 Forbidden`), impidiendo la enumeración o fuga de información de existencia de recursos.
4. **Cuentas Inactivas y Tokens Vencidos:**
   Cuentas con `activo = false` o `email_verified = false` o tokens expirados/revocados son interceptadas antes de la ejecución de lógica de negocio, respondiendo con `401 Unauthorized`.

---

## 2. Tabla del Inventario de Endpoints por Módulo

| Módulo / Controlador | Método | Ruta (Ruta Base + Subruta) | Rol o Expresión `@PreAuthorize` | Prueba Automatizada |
| :--- | :--- | :--- | :--- | :--- |
| **Autenticación** (`AuthController`) | POST | `/api/v1/auth/register` | `permitAll()` | `AuthControllerSecurityTest`, `EndpointsAuthorizationScanTest` |
| | POST | `/api/v1/auth/resend-registration` | `permitAll()` | `AuthControllerSecurityTest`, `EndpointsAuthorizationScanTest` |
| | POST | `/api/v1/auth/verify-registration` | `permitAll()` | `AuthControllerSecurityTest`, `EndpointsAuthorizationScanTest` |
| | POST | `/api/v1/auth/login` | `permitAll()` | `AuthControllerSecurityTest`, `EndpointsAuthorizationScanTest` |
| | POST | `/api/v1/auth/verify-login` | `permitAll()` | `AuthControllerSecurityTest` |
| | POST | `/api/v1/auth/refresh` | `permitAll()` | `CuentasDesactivadasYSesionesVencidasSecurityTest` |
| | POST | `/api/v1/auth/logout` | `permitAll()` | `AuthControllerSecurityTest` |
| | POST | `/api/v1/auth/recovery-request` | `permitAll()` | `AuthControllerSecurityTest` |
| | POST | `/api/v1/auth/recovery-verify` | `permitAll()` | `AuthControllerSecurityTest` |
| **Invitaciones Públicas** (`InvitacionController`) | GET | `/api/v1/invitaciones/{token}` | `permitAll()` | `InvitacionAccesoPublicoSecurityTest` |
| | POST | `/api/v1/invitaciones/{token}/aceptar` | `permitAll()` | `InvitacionAccesoPublicoSecurityTest` |
| **Invitaciones Admin** (`InvitacionAdminController`) | GET | `/api/v1/admin/invitaciones` | `hasRole('ADMINISTRADOR')` | `InvitacionAdminControllerSecurityTest` |
| | POST | `/api/v1/admin/invitaciones` | `hasRole('ADMINISTRADOR')` | `InvitacionAdminControllerSecurityTest` |
| | POST | `/api/v1/admin/invitaciones/{id}/revocar` | `hasRole('ADMINISTRADOR')` | `InvitacionAdminControllerSecurityTest` |
| | POST | `/api/v1/admin/invitaciones/{id}/reenviar` | `hasRole('ADMINISTRADOR')` | `InvitacionAdminControllerSecurityTest` |
| **Gestión de Usuarios** (`GestionUsuariosController`) | GET | `/api/v1/admin/usuarios` | `hasRole('ADMINISTRADOR')` | `GestionUsuariosSecurityTest` |
| | POST | `/api/v1/admin/usuarios/{id}/bloquear` | `hasRole('ADMINISTRADOR')` | `GestionUsuariosSecurityTest` |
| | POST | `/api/v1/admin/usuarios/{id}/desbloquear` | `hasRole('ADMINISTRADOR')` | `GestionUsuariosSecurityTest` |
| | POST | `/api/v1/admin/usuarios/{id}/desactivar` | `hasRole('ADMINISTRADOR')` | `GestionUsuariosSecurityTest` |
| | POST | `/api/v1/admin/usuarios/{id}/reactivar` | `hasRole('ADMINISTRADOR')` | `GestionUsuariosSecurityTest` |
| | PUT | `/api/v1/admin/usuarios/{id}/rol` | `hasRole('ADMINISTRADOR')` | `GestionUsuariosSecurityTest` |
| **Gestión Estudiantes** (`GestionEstudiantesController`) | GET | `/api/v1/catedratico/estudiantes` | `hasRole('CATEDRATICO')` | `GestionEstudiantesSecurityTest` |
| | POST | `/api/v1/catedratico/estudiantes/{id}/activar` | `hasRole('CATEDRATICO')` | `GestionEstudiantesSecurityTest` |
| | POST | `/api/v1/catedratico/estudiantes/{id}/desactivar` | `hasRole('CATEDRATICO')` | `GestionEstudiantesSecurityTest` |
| **Auditoría** (`AuditoriaController`) | GET | `/api/v1/auditoria` | `hasRole('ADMINISTRADOR')` | `AuditoriaControllerSecurityTest` |
| | GET | `/api/v1/auditoria/{id}` | `hasRole('ADMINISTRADOR')` | `AuditoriaControllerSecurityTest` |
| **Perfil Usuario** (`PerfilUsuarioController`) | GET | `/api/v1/perfil` | `isAuthenticated()` | `PerfilUsuarioControllerSecurityTest` |
| | PUT | `/api/v1/perfil` | `isAuthenticated()` | `PerfilUsuarioControllerSecurityTest` |
| | PUT | `/api/v1/perfil/password` | `isAuthenticated()` | `PerfilUsuarioControllerSecurityTest` |
| **Catálogo de Cursos** (`CursoCatalogoController`) | GET | `/api/v1/cursos` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')` | `CursoCatalogoControllerSecurityTest` |
| | POST | `/api/v1/cursos` | `hasRole('ADMINISTRADOR')` | `CursoCatalogoControllerSecurityTest` |
| | PUT | `/api/v1/cursos/{id}` | `hasRole('ADMINISTRADOR')` | `CursoCatalogoControllerSecurityTest` |
| **Asignación Catedráticos** (`AsignacionCatedraticoController`) | GET | `/api/v1/cursos/{cursoId}/catedraticos` | `hasRole('ADMINISTRADOR')` | `AsignacionCatedraticoControllerSecurityTest` |
| | POST | `/api/v1/cursos/{cursoId}/catedraticos` | `hasRole('ADMINISTRADOR')` | `AsignacionCatedraticoControllerSecurityTest` |
| | DELETE | `/api/v1/cursos/{cursoId}/catedraticos/{catedraticoId}` | `hasRole('ADMINISTRADOR')` | `AsignacionCatedraticoControllerSecurityTest` |
| **Cursos Estudiante** (`CursoEstudianteController`) | GET | `/api/v1/estudiante/cursos` | `hasRole('ESTUDIANTE')` | `CursoEstudianteControllerSecurityTest` |
| | POST | `/api/v1/estudiante/cursos/solicitar` | `hasRole('ESTUDIANTE')` | `CursoEstudianteControllerSecurityTest` |
| **Cursos Catedrático** (`CursoCatedraticoController`) | GET | `/api/v1/catedratico/cursos` | `hasRole('CATEDRATICO')` | `CursoCatedraticoControllerSecurityTest` |
| | GET | `/api/v1/catedratico/cursos/{cursoId}/estudiantes` | `hasRole('CATEDRATICO')` | `AutorizacionNivelDeFilaSecurityTest` |
| | POST | `/api/v1/catedratico/cursos/{cursoId}/estudiantes/{estudianteId}/aprobar` | `hasRole('CATEDRATICO')` | `AutorizacionNivelDeFilaSecurityTest` |
| | POST | `/api/v1/catedratico/cursos/{cursoId}/estudiantes/{estudianteId}/rechazar` | `hasRole('CATEDRATICO')` | `AutorizacionNivelDeFilaSecurityTest` |
| **Catálogo Puentes** (`PuenteController`) | GET | `/api/v1/puentes` | `permitAll()` | `PuenteCatalogoControllerTest` |
| | GET | `/api/v1/puentes/{id}` | `permitAll()` | `PuenteCatalogoControllerTest` |
| | POST | `/api/v1/puentes` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')` | `PuenteCatalogoControllerTest` |
| | PUT | `/api/v1/puentes/{id}` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')` | `PuenteCatalogoControllerTest` |
| | DELETE | `/api/v1/puentes/{id}` | `hasRole('ADMINISTRADOR')` | `PuenteCatalogoControllerTest` |
| | GET | `/api/v1/puentes/{id}/historial` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO', 'ESTUDIANTE')` | `PuenteCatalogoControllerTest` |
| **Solicitud Alta Puente** (`SolicitudAltaPuenteController`) | GET | `/api/v1/puentes/solicitudes-alta` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')` | `SolicitudAltaPuenteControllerSecurityTest` |
| | POST | `/api/v1/puentes/solicitudes-alta` | `hasRole('ESTUDIANTE')` | `SolicitudAltaPuenteControllerSecurityTest` |
| | GET | `/api/v1/puentes/solicitudes-alta/{id}` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO', 'ESTUDIANTE')` | `SolicitudAltaPuenteControllerSecurityTest` |
| | POST | `/api/v1/puentes/solicitudes-alta/{id}/aprobar` | `hasRole('ADMINISTRADOR')` | `SolicitudAltaPuenteControllerSecurityTest` |
| | POST | `/api/v1/puentes/solicitudes-alta/{id}/rechazar` | `hasRole('ADMINISTRADOR')` | `SolicitudAltaPuenteControllerSecurityTest` |
| **Catálogo Territorial** (`CatalogoTerritorialController`) | GET | `/api/v1/catalogos/departamentos` | `permitAll()` | `CatalogoTerritorialControllerSecurityTest` |
| | GET | `/api/v1/catalogos/departamentos/{departamentoId}/municipios` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')` | `CatalogoTerritorialControllerSecurityTest` |
| **Ubicación Territorial** (`UbicacionTerritorialController`) | GET | `/api/v1/catalogos/ubicacion` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')` | `UbicacionTerritorialControllerSecurityTest` |
| **Borradores Inspección** (`InspeccionBorradorController`) | POST | `/api/v1/inspecciones/borradores` | `hasRole('ESTUDIANTE')` | `AutorizacionNivelDeFilaSecurityTest` |
| | GET | `/api/v1/inspecciones/borradores/{id}` | `hasRole('ESTUDIANTE')` | `AutorizacionNivelDeFilaSecurityTest` |
| | PUT | `/api/v1/inspecciones/borradores/{id}` | `hasRole('ESTUDIANTE')` | `AutorizacionNivelDeFilaSecurityTest` |
| | DELETE | `/api/v1/inspecciones/borradores/{id}` | `hasRole('ESTUDIANTE')` | `AutorizacionNivelDeFilaSecurityTest` |
| | POST | `/api/v1/inspecciones/borradores/{id}/enviar` | `hasRole('ESTUDIANTE')` | `AutorizacionNivelDeFilaSecurityTest` |
| **Consulta Inspecciones** (`InspeccionConsultaController`) | GET | `/api/v1/inspecciones` | `isAuthenticated()` | `InspeccionConsultaControllerSecurityTest` |
| | GET | `/api/v1/inspecciones/{id}` | `isAuthenticated()` | `InspeccionConsultaControllerSecurityTest` |
| | GET | `/api/v1/inspecciones/{id}/export` | `isAuthenticated()` | `InspeccionConsultaControllerSecurityTest` |
| **Fotografías Inspección** (`FotografiaInspeccionController`) | GET | `/api/v1/inspecciones/{inspeccionId}/fotografias` | `isAuthenticated()` | `FotografiaInspeccionControllerSecurityTest` |
| | POST | `/api/v1/inspecciones/{inspeccionId}/fotografias` | `hasRole('ESTUDIANTE')` | `FotografiaInspeccionControllerSecurityTest` |
| | DELETE | `/api/v1/inspecciones/{inspeccionId}/fotografias/{fotoId}` | `hasRole('ESTUDIANTE')` | `FotografiaInspeccionControllerSecurityTest` |
| **Bandeja de Revisión** (`BandejaRevisionController`) | GET | `/api/v1/revision/bandeja` | `hasRole('CATEDRATICO')` | `BandejaRevisionControllerSecurityTest` |
| | POST | `/api/v1/revision/bandeja/{inspeccionId}/aprobar` | `hasRole('CATEDRATICO')` | `BandejaRevisionControllerSecurityTest` |
| | POST | `/api/v1/revision/bandeja/{inspeccionId}/observar` | `hasRole('CATEDRATICO')` | `BandejaRevisionControllerSecurityTest` |
| **Observaciones Estudiante** (`ObservacionAutorController`) | GET | `/api/v1/observaciones/recibidas` | `hasRole('ESTUDIANTE')` | `ObservacionAutorControllerSecurityTest` |
| **Formulario SIECA** (`FormularioConfiguracionController`) | GET | `/api/v1/formularios/activo` | `permitAll()` | `FormularioConfiguracionControllerSecurityTest` |
| | GET | `/api/v1/formularios/versiones` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')` | `FormularioConfiguracionControllerSecurityTest` |
| | GET | `/api/v1/formularios/versiones/{versionId}` | `isAuthenticated()` | `FormularioConfiguracionControllerSecurityTest` |
| | POST | `/api/v1/formularios/versiones` | `hasRole('ADMINISTRADOR')` | `FormularioConfiguracionControllerSecurityTest` |
| | POST | `/api/v1/formularios/versiones/{versionId}/activar` | `hasRole('ADMINISTRADOR')` | `FormularioConfiguracionControllerSecurityTest` |
| **Reglas de Mantenimiento** (`ReglaMantenimientoController`) | GET | `/api/v1/reglas-mantenimiento` | `hasRole('ADMINISTRADOR')` | `ReglaMantenimientoControllerSecurityTest` |
| | POST | `/api/v1/reglas-mantenimiento` | `hasRole('ADMINISTRADOR')` | `ReglaMantenimientoControllerSecurityTest` |
| | GET | `/api/v1/reglas-mantenimiento/{id}` | `hasRole('ADMINISTRADOR')` | `ReglaMantenimientoControllerSecurityTest` |
| | PUT | `/api/v1/reglas-mantenimiento/{id}` | `hasRole('ADMINISTRADOR')` | `ReglaMantenimientoControllerSecurityTest` |
| | POST | `/api/v1/reglas-mantenimiento/{id}/validar` | `hasRole('ADMINISTRADOR')` | `ReglaMantenimientoControllerSecurityTest` |
| **Órdenes de Mantenimiento** (`OrdenMantenimientoController`) | GET | `/api/v1/mantenimiento/ordenes` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')` | `OrdenMantenimientoControllerSecurityTest` |
| | POST | `/api/v1/mantenimiento/ordenes` | `hasRole('ADMINISTRADOR')` | `OrdenMantenimientoControllerSecurityTest` |
| | GET | `/api/v1/mantenimiento/ordenes/{id}` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')` | `OrdenMantenimientoControllerSecurityTest` |
| | PUT | `/api/v1/mantenimiento/ordenes/{id}` | `hasRole('ADMINISTRADOR')` | `OrdenMantenimientoControllerSecurityTest` |
| **Foro Comunitario** (`ForoController`) | GET | `/api/v1/foro/hilos` | `permitAll()` | `ForoAccesoPublicoSecurityTest` |
| | GET | `/api/v1/foro/hilos/{id}` | `permitAll()` | `ForoAccesoPublicoSecurityTest` |
| | POST | `/api/v1/foro/hilos` | `isAuthenticated()` | `ForoControllerSecurityTest` |
| | POST | `/api/v1/foro/hilos/{id}/respuestas` | `isAuthenticated()` | `ForoControllerSecurityTest` |
| | DELETE | `/api/v1/foro/hilos/{id}` | `hasRole('ADMINISTRADOR')` | `ForoControllerSecurityTest` |
| | DELETE | `/api/v1/foro/respuestas/{id}` | `hasRole('ADMINISTRADOR')` | `ForoControllerSecurityTest` |
| **Notificaciones** (`NotificacionController`) | GET | `/api/v1/notificaciones` | `isAuthenticated()` | `NotificacionControllerSecurityTest` |
| | POST | `/api/v1/notificaciones/{id}/leer` | `isAuthenticated()` | `NotificacionControllerSecurityTest` |
| **Salidas de Campo** (`SalidaCampoController`) | GET | `/api/v1/salidas-campo` | `hasRole('CATEDRATICO')` | `SalidaCampoControllerSecurityTest` |
| | POST | `/api/v1/salidas-campo` | `hasRole('CATEDRATICO')` | `SalidaCampoControllerSecurityTest` |
| | GET | `/api/v1/salidas-campo/{id}` | `hasAnyRole('CATEDRATICO', 'ESTUDIANTE')` | `SalidaCampoControllerSecurityTest` |
| | PUT | `/api/v1/salidas-campo/{id}` | `hasRole('CATEDRATICO')` | `SalidaCampoControllerSecurityTest` |
| | POST | `/api/v1/salidas-campo/{id}/cerrar` | `hasRole('CATEDRATICO')` | `SalidaCampoControllerSecurityTest` |
| **Sincronización Offline** (`SyncOfflineController`) | POST | `/api/v1/offline/sincronizar` | `hasRole('ESTUDIANTE')` | `SyncOfflineControllerSecurityTest` |
| **Reportes de Daño** (`ReporteDanioController`) | GET | `/api/v1/reportes-danio` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')` | `ReporteDanioControllerSecurityTest` |
| | POST | `/api/v1/reportes-danio` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO', 'ESTUDIANTE')` | `ReporteDanioControllerSecurityTest` |
| | GET | `/api/v1/reportes-danio/{id}` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO', 'ESTUDIANTE')` | `ReporteDanioControllerSecurityTest` |
| **Dashboard y Métricas** (`DashboardController`) | GET | `/api/v1/dashboard/resumen` | `permitAll()` | `DashboardControllerSecurityTest` |
| | GET | `/api/v1/dashboard/metricas` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')` | `DashboardControllerSecurityTest` |
| **Exportación** (`ExportacionController`) | GET | `/api/v1/exportaciones/puentes` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')` | `ExportacionControllerSecurityTest` |
| | GET | `/api/v1/exportaciones/inspecciones` | `hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')` | `ExportacionControllerSecurityTest` |
| **Importación** (`ImportacionController`) | POST | `/api/v1/importaciones/puentes` | `hasRole('ADMINISTRADOR')` | `ImportacionControllerSecurityTest` |
| **Evaluación Estructural** (`EvaluacionEstructuralController`) | POST | `/api/v1/evaluacion-estructural/calcular` | `isAuthenticated()` | `EvaluacionEstructuralControllerSecurityTest` |

---

## 3. Pruebas Automatizadas de Soporte (Parte A)

La conformidad con este inventario está respaldada por las siguientes pruebas ejecutables con `mvn test`:

1. `EndpointsAuthorizationScanTest`: Valida que el 100% de métodos en todos los controladores tengan anotación `@PreAuthorize` o pertenezcan a la lista blanca de endpoints públicos explícitos (DT-SEC-03).
2. `CuentasDesactivadasYSesionesVencidasSecurityTest`: Comprueba el bloqueo inmediato ante cuentas con `activo = false`, cuentas no verificadas, tokens caducados o versiones de token inválidas.
3. `AutorizacionNivelDeFilaSecurityTest`: Comprueba el aislamiento por fila (Estudiante solo borradores propios, Catedrático solo cursos que dirige) y la respuesta 404 ante recursos ajenos (DT-SEC-04).
4. Pruebas unitarias de autorización por rol por endpoint (DT-CAL-03):
   - `CatalogoTerritorialControllerSecurityTest`: Valida endpoints públicos y restringidos por rol.
   - `UbicacionTerritorialControllerSecurityTest`: Valida autorización y rechazo anónimo o sin rol.
   - `ObservacionAutorControllerSecurityTest`: Valida restricción exclusiva a rol Estudiante.
