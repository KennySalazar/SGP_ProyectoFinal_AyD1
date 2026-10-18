# HU-029: verificación de cierre

## Alcance y evidencia

Se revisaron los diez escenarios de `hus/hu29.md` (ubicado fuera del repositorio
de la aplicación). No se encontraron defectos reproducibles de producción en
esta revisión. Los nueve archivos Angular pendientes al comenzar se conservan.

Las 167 pruebas backend previamente aprobadas en nueve suites siguen siendo
evidencia aplicable: se compararon las fuentes actuales de `sgp-api/src/main`
con la copia de aquella ejecución y no hubo diferencias. No se repitieron.
Las 42 pruebas frontend de la iteración anterior tampoco se repitieron.

Las pruebas nuevas residen en
`sgp-api/src/test/java/gt/usac/cunoc/sgp/inspeccion/controller/FotografiaAutorizacionGherkinTest.java`.
JUnit toma los ejemplos del archivo
`sgp-api/src/test/resources/features/hu029-autorizacion-faltante.feature`.
No se añadió Cucumber: el adaptador únicamente lee la tabla `Ejemplos` de esa
característica. Se ejecutan los filtros, controlador y servicio reales con
repositorios, transacciones y almacenamiento aislados mediante dobles. Esto
no es una prueba de base de datos, bloqueo real, bucket o red S3.

Resultado de esta revisión: `FotografiaAutorizacionGherkinTest`, **13 ejemplos
aprobados, 0 fallos, 0 errores y 0 omitidos**, con Java 21 y Maven offline.
Se fijó un socket Docker inexistente para impedir cualquier acceso accidental
a infraestructura. No se ejecutaron otras suites. La compilación fue correcta.

## Trazabilidad de los diez criterios

Las clases de integración siguientes se encuentran en
`sgp-api/src/test/java/gt/usac/cunoc/sgp/integration/`.

| Criterio | Estado | Evidencia concreta |
|---|---|---|
| 1. Mínimo por categoría | Verificado mediante pruebas previas | `PublicacionInspeccionIntegrationTest#transicionesRechazanExactamenteLaCategoriaSinEvidenciaDisponible`: categorías ausentes, RETIRADO y PURGADO; enviar, publicar y aprobar; sin cambios parciales. |
| 2. Evidencia completa | Verificado mediante pruebas previas | `PublicacionInspeccionIntegrationTest#transicionesAceptanEvidenciaCompletaAunqueHayaFotosNoDisponibles`. OTRA no sustituye categorías obligatorias. |
| 3. Máximo de 60 | Verificado mediante pruebas previas | `FotografiaFlujoIntegrationTest#aceptaLaFotografiaNumero60`, `#rechazaLaFotografia61AntesDeProcesarSinGuardarRegistrosNiObjetos`, `#dosCargasDesde59ConfirmanSoloUnaYCompensanLosObjetosRechazados`, `#reproduceReintentoIdempotenteCon60FotografiasSinCrearObjetos`. |
| 4. Tipo real del archivo | Verificado mediante pruebas previas e inspección | `FotografiaFlujoIntegrationTest#rechazaMetadatosAusentesCategoriasGpsInvalidosYPng` y `#aceptaWebpYCincoCategorias`; `archivo/service/InspectorImagenesImageIo#decodificar` y `ProcesadorImagenes#procesar` normalizan a JPEG. |
| 5. Retirada de BORRADOR | Verificado mediante pruebas previas | `FotografiaFlujoIntegrationTest#autorRetiraFotografiaSinBorrarObjetosYNoPuedeListarlaNiDescargarla`; `PublicacionInspeccionIntegrationTest#retirarLaUnicaFotoDeCategoriaImpideElSiguienteEnvioOPublicacion`. |
| 6. PUBLICADA no admite retirada | Verificado mediante pruebas previas e inspección | `FotografiaFlujoIntegrationTest#retiradaDelAutorRechazaTodosLosEstadosDistintosDeBorrador`; `FotografiaService#retirar`: autor 409 y ajenos 404 antes de revelar el estado. Ninguno retira. |
| 7. Acceso protegido y almacenamiento inaccesible directamente | Parcial: pruebas e inspección; bucket efectivo pendiente | `FotografiaController#leer` sirve contenido autenticado con `no-store, private`; `AlmacenamientoLocal` usa un directorio fuera de recursos web. La prueba real S3 no se repite porque construye MinIO. No se acredita su privacidad efectiva con dobles. |
| 8. Privacidad de BORRADOR | Verificado mediante pruebas previas | `FotografiaFlujoIntegrationTest#autorYCatedraticoDelCursoConsultanLasTresRutasDeUnBorrador`, `#usuariosAjenosNoConsultanFotografiasPrivadas`, `#asignacionDeOtroEstudianteOPuenteNoConcedeLectura` y `#revocacionRetiraLaLecturaAcademicaSinAfectarAlAutor`. |
| 9. Visitante sin acceso | Verificado mediante pruebas previas y nuevas | `FotografiaControllerSecurityTest#noHayLecturaAnonimaDeListadoOriginalNiMiniatura`, `#retiradaSinAutenticacionResponde401SinInvocarServicio`; ejemplo VISITANTE/POST del archivo Gherkin aprobado. |
| 10. Autorización por actor y endpoint | Verificado mediante pruebas previas y nuevas | Ejemplos Gherkin aprobados de POST/DELETE para catedrático asignado/no asignado, administrador, estudiante y profesional ajenos; las lecturas y operaciones del autor ya tienen cobertura en las clases anteriores. |

## Matriz esperada y comprobada por las pruebas indicadas

Para BORRADOR, las tres columnas GET se prueban individualmente, no como un
único permiso genérico. En los actores ajenos se presupone que no son el autor.

| Actor | POST | GET listado | GET original | GET miniatura | DELETE |
|---|---:|---:|---:|---:|---:|
| Autor | 201 | 200 | 200 | 200 | 204 |
| Catedrático asignado | 404 | 200 | 200 | 200 | 404 |
| Catedrático no asignado | 404 | 404 | 404 | 404 | 404 |
| Administrador ajeno | 404 | 404 | 404 | 404 | 404 |
| Estudiante ajeno | 404 | 404 | 404 | 404 | 404 |
| Profesional externo ajeno | 404 | 404 | 404 | 404 | 404 |
| Visitante | 401 | 401 | 401 | 401 | 401 |

## Contratos y confidencialidad: inspección

- `FotografiaResponse` solo expone metadatos de la fotografía autorizada;
  no contiene claves, rutas internas ni URLs S3. `FotografiaController#leer` no
  devuelve redirecciones a objetos: transmite bytes. El nombre público es el UUID.
- `FotografiaService#contenido` convierte IOException en un 503 genérico. Los dos
  ejemplos nuevos de fallo de almacenamiento verifican cuerpo y cabeceras incluso
  si la excepción incluye una clave privada. No se prueba una conexión S3 real.
- `inspeccion-api.service.ts`: POST multipart con la misma clave idempotente,
  listado JSON, original/miniatura como Blob y DELETE sin cuerpo con 204.
- El reporte Angular cuenta solo confirmadas, separa pendientes, impide modificar
  en modo lectura y conserva los pendientes ante 422/409. El diálogo de transición
  reconoce `inspeccion_incompleta` y `errores[].campo = fotografias.CATEGORIA`.
- `sgp-client/nginx.conf` permite `blob:` para las miniaturas privadas; no se
  añadió acceso estático a archivos backend. No se hizo validación manual desplegada.

## Pendientes que impiden declarar cierre completo

1. Ejecutar `AlmacenamientoS3IntegrationTest` cuando exista infraestructura apta,
   sin descargar/reconstruirla en esta revisión, y comprobar la configuración
   efectiva del bucket del entorno de despliegue. El intento anterior falló al
   descargar MinIO, antes de probar la aplicación.
2. La identificación de asignaciones para inspecciones históricas sin vínculo
   sigue documentada en `HU-029-vinculacion-academica.md`. No se infieren permisos.

Los mínimos de cuatro categorías provienen de las instrucciones explícitas de
esta implementación; la nota académica abierta del texto original de la HU no
se usa para desactivar una regla ya autorizada e implementada.
