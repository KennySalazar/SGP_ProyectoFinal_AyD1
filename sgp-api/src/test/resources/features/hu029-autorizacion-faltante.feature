# language: es
@HU029 @DT-CAL-03
Característica: Cobertura HTTP faltante de autorización y confidencialidad fotográfica
  Los permisos y errores se prueban con controlador, filtros y servicio reales.
  Los repositorios y el almacenamiento son dobles; esto no verifica una instancia S3.
  Los ejemplos de esta característica alimentan FotografiaAutorizacionGherkinTest.

  Esquema del escenario: Denegar escrituras ajenas y ocultar detalles del almacenamiento
    Dado el actor <actor> frente a una inspección en BORRADOR de otro usuario
    Y para AUTOR la inspección pertenece al actor autenticado
    Y el almacenamiento presenta la condición <almacenamiento>
    Cuando solicita la operación <operacion> por HTTP
    Entonces recibe HTTP <http>
    Y la respuesta y sus cabeceras no revelan claves ni rutas internas
    Y las escrituras rechazadas no procesan ni guardan fotografías

    Ejemplos:
      | actor                 | operacion | almacenamiento | http |
      | VISITANTE             | POST      | NO_USADO       | 401  |
      | CATEDRATICO_ASIGNADO   | POST      | NO_USADO       | 404  |
      | CATEDRATICO_ASIGNADO   | DELETE    | NO_USADO       | 404  |
      | CATEDRATICO_AJENO      | POST      | NO_USADO       | 404  |
      | CATEDRATICO_AJENO      | DELETE    | NO_USADO       | 404  |
      | ADMINISTRADOR         | POST      | NO_USADO       | 404  |
      | ADMINISTRADOR         | DELETE    | NO_USADO       | 404  |
      | ESTUDIANTE            | POST      | NO_USADO       | 404  |
      | ESTUDIANTE            | DELETE    | NO_USADO       | 404  |
      | PROFESIONAL_EXTERNO    | POST      | NO_USADO       | 404  |
      | PROFESIONAL_EXTERNO    | DELETE    | NO_USADO       | 404  |
      | AUTOR                 | ORIGINAL  | FALLO_INTERNO  | 503  |
      | AUTOR                 | MINIATURA | FALLO_INTERNO  | 503  |
