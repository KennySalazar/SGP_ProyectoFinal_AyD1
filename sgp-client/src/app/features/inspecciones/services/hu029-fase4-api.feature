# language: es
Característica: Contratos HTTP del panel HU-029
  Escenario: Consultar evidencia incompleta sin interpretarla como un fallo HTTP
    Cuando consulta la evidencia de la inspección
    Entonces realiza GET a evidencia-fotografica
    Cuando el servidor responde con faltante CAUCE y máximo 60
    Entonces conserva el diagnóstico incompleto del servidor

  Escenario: La retirada usa los identificadores de inspección y fotografía
    Cuando retira la fotografía
    Entonces realiza DELETE sobre el par de identificadores
    Cuando el servidor confirma con 204 sin cuerpo
    Entonces completa la retirada sin inventar una respuesta

  Esquema del escenario: Propagar errores para que el panel conserve las cargas y aplique permisos
    Cuando retira la fotografía
    Y el servidor responde con error <estado>
    Entonces propaga exactamente el estado <estado>
    Ejemplos:
      | estado |
      | 401    |
      | 404    |
      | 409    |
      | 422    |
