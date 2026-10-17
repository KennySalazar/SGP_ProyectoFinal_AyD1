# HU-019: Integracion pendiente del acta de calibracion

## Estado

Implementacion parcial: configuracion de pesos y escala por version,
validaciones de publicacion, inmutabilidad y auditoria implementadas.

## Dependencia pendiente

Integrar el servicio de almacenamiento de HU-028 cuando este disponible.

## Trabajo pendiente

- Adjuntar el acta PDF y asociarla a la version del formulario.
- Validar que el contenido real del archivo corresponde a un PDF.
- Permitir su consulta mediante acceso autorizado.
- Indicar explicitamente cuando la version no tiene acta de calibracion.
- Auditar la asociacion del acta con usuario, fecha y valores anteriores
  y posteriores.
- Probar el rechazo con 403 para usuarios no administradores.

HU-019 no debe marcarse como completada hasta verificar esta integracion.
Los valores utilizados en pruebas no representan calibracion academica aprobada.
