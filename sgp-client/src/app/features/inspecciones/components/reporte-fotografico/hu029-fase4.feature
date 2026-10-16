# language: es
Característica: Evidencia fotográfica en el panel de inspección

  Escenario: Mostrar el diagnóstico confirmado sin contar pendientes locales
    Dado un diagnóstico con 1 fotografía disponible y faltante en CAUCE
    Y una fotografía local pendiente
    Cuando el autor abre el panel en BORRADOR
    Entonces el contador muestra 1 de 60 y el faltante Cauce
    Y el pendiente se muestra separado sin aumentar la evidencia

  Escenario: La carga confirmada actualiza lista y diagnóstico
    Dado un diagnóstico con 1 fotografía disponible y faltante en CAUCE
    Cuando el autor abre el panel en BORRADOR
    Y adjunta una fotografía que el servidor confirma
    Entonces consulta nuevamente la lista y la evidencia
    Y muestra 2 disponibles sin pendientes y libera la miniatura anterior

  Escenario: Retirar requiere confirmación explícita
    Cuando el autor abre el panel en BORRADOR
    Y solicita retirar una fotografía
    Entonces muestra el diálogo de confirmación sin enviar DELETE
    Cuando cancela la retirada
    Entonces conserva la fotografía sin enviar DELETE

  Escenario: Retirada confirmada libera URL y actualiza la evidencia
    Cuando el autor abre el panel en BORRADOR
    Y solicita retirar una fotografía
    Y el servidor confirma la retirada
    Entonces desaparece la fotografía y se libera su miniatura
    Y muestra el nuevo faltante de Acceso

  Esquema del escenario: Una retirada rechazada conserva la fotografía
    Cuando el autor abre el panel en BORRADOR
    Y solicita retirar una fotografía
    Y la retirada falla con estado <estado> y código "<codigo>"
    Entonces mantiene la fotografía y muestra "<mensaje>" en el diálogo
    Ejemplos:
      | estado | codigo                                    | mensaje  |
      | 0      |                                           | retirada |
      | 500    |                                           | retirada |
      | 409    | estado_inspeccion_no_permite_archivos      | estado   |

  Escenario: No se encola la retirada sin conexión
    Cuando el autor abre el panel en BORRADOR
    Y solicita retirar una fotografía
    Y confirma la retirada sin conexión
    Entonces conserva la fotografía sin enviar DELETE
    Y muestra un error de retirada en el diálogo

  Esquema del escenario: La pérdida de acceso limpia datos privados
    Cuando el autor abre el panel en BORRADOR
    Y solicita retirar una fotografía
    Y la retirada falla con estado <estado> y código ""
    Entonces limpia fotografías evidencia y pendientes sin borrar el almacenamiento local
    Y muestra "<mensaje>" en el diálogo sin permitir edición
    Ejemplos:
      | estado | mensaje |
      | 401    | sesion  |
      | 404    | acceso  |

  Esquema del escenario: El rechazo de carga conserva identidad y bloquea reintentos semánticos
    Cuando el autor abre el panel en BORRADOR
    Y la carga falla con estado 422 y código "<codigo>"
    Entonces conserva el blob el UUID y la clave y registra "<mensaje>"
    Y no confirma ni reenvía automáticamente la fotografía
    Ejemplos:
      | codigo                           | mensaje  |
      | limite_fotografias_excedido       | limite   |
      | contenido_fotografico_invalido    | formato  |
      | anclaje_fotografico_invalido      | anclaje  |
      | error_semantico_desconocido       | rechazo  |

  Escenario: La capacidad recuperada permite repetir la misma carga
    Dado un diagnóstico con 60 fotografías disponibles
    Cuando el autor abre el panel en BORRADOR
    Y la carga falla con estado 422 y código "limite_fotografias_excedido"
    Entonces no permite reintentar con el inventario lleno
    Cuando el servidor informa 59 disponibles al actualizar
    Y reintenta la carga pendiente con éxito
    Entonces conserva exactamente la operación original al reintentar

  Escenario: El error de red permite reintento manual conservando la operación
    Cuando el autor abre el panel en BORRADOR
    Y la carga falla con estado 0 y código ""
    Y reintenta la carga pendiente con éxito
    Entonces conserva exactamente la operación original al reintentar

  Esquema del escenario: La edición está limitada al autor en borrador
    Cuando "<actor>" abre el panel en "<estado>"
    Entonces consulta fotografías y evidencia remotas
    Y no permite captura carga reintento ni retirada
    Ejemplos:
      | actor       | estado    |
      | catedratico | BORRADOR  |
      | autor       | ENVIADA   |
      | autor       | PUBLICADA |

  Escenario: El lector académico no usa datos locales del autor
    Dado una fotografía local pendiente
    Cuando "catedratico" abre el panel en "BORRADOR"
    Entonces no lee ni guarda contexto o cola local
    Y puede solicitar el original a la API protegida

  Escenario: Una respuesta tardía no repuebla el panel de una sesión cerrada
    Cuando el autor abre el panel en BORRADOR
    Y comienza una consulta que finaliza después de cerrar sesión
    Entonces quedan vacías las fotografías evidencia y previsualizaciones

  Escenario: Un error de diagnóstico no muestra aprobación previa
    Cuando el autor abre el panel en BORRADOR
    Y falla la actualización de evidencia
    Entonces no muestra diagnóstico válido ni totales inventados
