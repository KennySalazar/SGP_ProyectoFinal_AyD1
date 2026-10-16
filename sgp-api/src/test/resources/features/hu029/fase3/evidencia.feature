# language: es
Característica: Diagnóstico de evidencia fotográfica y validación transaccional
  La propuesta académica exige una fotografía disponible por categoría obligatoria.
  El diagnóstico consulta únicamente evidencia confirmada de la inspección autorizada.

  Antecedentes:
    Dado una inspección propia en estado "BORRADOR"
    Y una sesión de "CATEDRATICO" como "autor"

  Escenario: Un inventario vacío informa las cuatro categorías faltantes
    Cuando consulto el diagnóstico fotográfico
    Entonces la respuesta HTTP es 200
    Y la evidencia es inválida con 0 fotografías disponibles
    Y faltan las categorías "ACCESO,CAUCE,SUBESTRUCTURA,SUPERESTRUCTURA"
    Y la respuesta es privada y no contiene claves físicas

  Escenario: Solo las fotografías disponibles de esta inspección cumplen los mínimos
    Dado las siguientes fotografías registradas:
      | categoria       | estado     |
      | ACCESO          | DISPONIBLE |
      | SUPERESTRUCTURA | DISPONIBLE |
      | SUBESTRUCTURA   | DISPONIBLE |
      | CAUCE           | RETIRADO   |
      | CAUCE           | PURGADO    |
      | OTRA            | DISPONIBLE |
    Y un documento PDF asociado a la inspección
    Y una fotografía disponible de otra inspección
    Cuando consulto el diagnóstico fotográfico
    Entonces la respuesta HTTP es 200
    Y la evidencia es inválida con 4 fotografías disponibles
    Y faltan las categorías "CAUCE"

  Escenario: La evidencia completa no necesita la categoría OTRA
    Dado evidencia completa con una fotografía disponible por categoría obligatoria
    Cuando consulto el diagnóstico fotográfico
    Entonces la respuesta HTTP es 200
    Y la evidencia es válida con 4 fotografías disponibles
    Y no faltan categorías

  Esquema del escenario: Cada actor obtiene el diagnóstico según su relación
    Dado una sesión de "<rol>" como "<relacion>"
    Cuando consulto el diagnóstico fotográfico
    Entonces la respuesta HTTP es <estado>
    Ejemplos:
      | rol                 | relacion  | estado |
      | CATEDRATICO         | autor     | 200    |
      | PROFESIONAL_EXTERNO | autor     | 200    |
      | CATEDRATICO         | asignado  | 200    |
      | CATEDRATICO         | ajeno     | 404    |
      | PROFESIONAL_EXTERNO | ajeno     | 404    |
      | ADMINISTRADOR       | ajeno     | 404    |
      | ESTUDIANTE          | ajeno     | 404    |
      | VISITANTE           | visitante | 401    |

  Escenario: Una inspección eliminada no expone el diagnóstico
    Dado la inspección fue eliminada
    Cuando consulto el diagnóstico fotográfico
    Entonces la respuesta HTTP es 404

  Escenario: El error de transición incluye faltantes y revierte la transacción
    Dado las siguientes fotografías registradas:
      | categoria       | estado     |
      | ACCESO          | DISPONIBLE |
      | SUPERESTRUCTURA | DISPONIBLE |
      | SUBESTRUCTURA   | DISPONIBLE |
    Cuando el llamador transaccional de prueba exige la evidencia
    Entonces la respuesta HTTP es 422
    Y el código de error es "evidencia_fotografica_incompleta"
    Y faltan las categorías "CAUCE"
    Y el error informa 3 fotografías y un máximo de 60
    Y la actividad de la inspección no cambió

  Escenario: La validación interna exige una transacción existente
    Cuando exijo evidencia sin transacción
    Entonces se rechaza por ausencia de transacción

  Escenario: Una evidencia completa permite continuar dentro de la transacción
    Dado evidencia completa con una fotografía disponible por categoría obligatoria
    Cuando el llamador transaccional de prueba exige la evidencia
    Entonces la respuesta HTTP es 204

  Escenario: El exceso histórico informa capacidad y no inventa faltantes
    Dado evidencia completa con una fotografía disponible por categoría obligatoria
    Y 57 fotografías disponibles adicionales en OTRA
    Cuando consulto el diagnóstico fotográfico
    Entonces la respuesta HTTP es 200
    Y la evidencia es inválida con 61 fotografías disponibles
    Y no faltan categorías
    Cuando el llamador transaccional de prueba exige la evidencia
    Entonces la respuesta HTTP es 422
    Y el código de error es "limite_fotografias_excedido"
