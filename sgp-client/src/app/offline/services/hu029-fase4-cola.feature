# language: es
Característica: Conservar rechazos de carga en la cola existente
  Antecedentes:
    Dado una fotografía pendiente del autor con su operación original

  Escenario: El rechazo se conserva al volver a consultar los pendientes
    Cuando registra el rechazo "formato" para el autor
    Entonces el pendiente recuperado conserva blob UUID clave y rechazo "formato"
    Y la cola incrementa los intentos sin eliminar ni reemplazar la operación

  Escenario: Otro usuario no puede escribir el error del autor
    Cuando otro usuario intenta registrar un rechazo
    Entonces rechaza el acceso y conserva la cola original

  Escenario: La consulta del lector no lee errores ni fotos del autor
    Cuando otro usuario consulta los pendientes
    Entonces no obtiene fotografías ni lee sus operaciones
