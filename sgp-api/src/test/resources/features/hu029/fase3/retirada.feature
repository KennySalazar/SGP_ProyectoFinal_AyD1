# language: es
Característica: Retirada lógica y privada de fotografías

  Antecedentes:
    Dado una inspección propia en estado "BORRADOR"
    Y una sesión de "CATEDRATICO" como "autor"
    Y evidencia completa con una fotografía disponible por categoría obligatoria

  Escenario: Retirar la única foto de una categoría vuelve a incumplir su mínimo
    Cuando retiro la fotografía "CAUCE"
    Entonces la respuesta HTTP es 204
    Y la fotografía "CAUCE" está "RETIRADO" y conserva su asociación
    Cuando consulto el diagnóstico fotográfico
    Entonces la evidencia es inválida con 3 fotografías disponibles
    Y faltan las categorías "CAUCE"

  Escenario: Una retirada repetida conserva la fecha original
    Dado la fotografía "CAUCE" fue retirada hace una hora
    Cuando retiro la fotografía "CAUCE"
    Entonces la respuesta HTTP es 204
    Y la fecha original de retirada se conserva

  Esquema del escenario: Únicamente el autor puede retirar una foto del borrador
    Dado una sesión de "<rol>" como "<relacion>"
    Cuando retiro la fotografía "CAUCE"
    Entonces la respuesta HTTP es <estado>
    Ejemplos:
      | rol                 | relacion  | estado |
      | CATEDRATICO         | autor     | 204    |
      | PROFESIONAL_EXTERNO | autor     | 204    |
      | CATEDRATICO         | asignado  | 404    |
      | CATEDRATICO         | ajeno     | 404    |
      | PROFESIONAL_EXTERNO | ajeno     | 404    |
      | ADMINISTRADOR       | ajeno     | 404    |
      | ESTUDIANTE          | ajeno     | 404    |
      | VISITANTE           | visitante | 401    |

  Esquema del escenario: Un estado no editable impide retirar incluso al autor
    Dado la inspección pasó al estado "<estado>"
    Cuando retiro la fotografía "CAUCE"
    Entonces la respuesta HTTP es 409
    Y el código de error es "estado_inspeccion_no_permite_archivos"
    Y la fotografía "CAUCE" está "DISPONIBLE" y conserva su asociación
    Ejemplos:
      | estado              |
      | ENVIADA             |
      | EN_REVISION         |
      | CAMBIOS_SOLICITADOS |
      | PUBLICADA           |
      | RECHAZADA           |

  Escenario: El UUID de una foto ajena no permite retirarla de esta inspección
    Dado una fotografía disponible de otra inspección
    Cuando retiro la fotografía "AJENA"
    Entonces la respuesta HTTP es 404
    Y la fotografía "AJENA" está "DISPONIBLE" y conserva su asociación

  Escenario: Una foto purgada no admite retirada
    Dado la fotografía "CAUCE" está purgada
    Cuando retiro la fotografía "CAUCE"
    Entonces la respuesta HTTP es 404

  Escenario: Una retirada participa en el rollback de su llamador
    Cuando el llamador retira "CAUCE" y falla antes del commit
    Entonces la fotografía "CAUCE" está "DISPONIBLE" y conserva su asociación
    Y la fotografía "CAUCE" no tiene fecha de retirada

  Escenario: El reintento de una carga retirada no reactiva el archivo ni borra sus bytes
    Dado registro una fotografía real mediante HU-028 en "OTRA"
    Cuando retiro la fotografía "OTRA"
    Entonces la respuesta HTTP es 204
    Y el original y la miniatura permanecen almacenados
    Y el original y la miniatura retirados responden 404
    Cuando repito la carga original con la misma clave
    Entonces la respuesta HTTP es 201
    Y la fotografía "OTRA" está "RETIRADO" y conserva su asociación
    Y el listado excluye la fotografía "OTRA"

  Escenario: Una retirada libera una plaza según el conteo de disponibles
    Dado 56 fotografías disponibles adicionales en OTRA
    Cuando retiro la fotografía "CAUCE"
    Entonces la respuesta HTTP es 204
    Dado registro una fotografía real mediante HU-028 en "CAUCE"
    Cuando consulto el diagnóstico fotográfico
    Entonces la evidencia es válida con 60 fotografías disponibles
