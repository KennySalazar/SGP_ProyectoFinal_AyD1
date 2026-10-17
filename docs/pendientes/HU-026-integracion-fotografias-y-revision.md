# HU-026: Integracion pendiente con fotografias y revision

## Estado

Maquina de estados, unica puerta de entrada (`TransicionInspeccionService`),
historial, publicacion atomica con IC y concurrencia implementados y probados.

## Fotografias (HU-028 y HU-029)

Enviar y publicar exigen al menos una fotografia `DISPONIBLE` de cada categoria
`ACCESO`, `SUPERESTRUCTURA`, `SUBESTRUCTURA` y `CAUCE` (RN-INS-06). Se leen de las
tablas `archivo` y `archivo_inspeccion` de V10. Hasta que HU-028 permita subir
fotografias, ninguna inspeccion real puede enviarse ni publicarse (responde 422).

HU-028/HU-029 deben:
- Registrar cada fotografia en `archivo` (tipo `FOTO_INSPECCION`, con categoria)
  y vincularla en `archivo_inspeccion`.
- Rechazar con 409 la eliminacion de fotografias cuando la inspeccion no este en
  BORRADOR (RN-ARC-05, inspeccion publicada inmutable).

## Revision (Sprint 3)

Las transiciones `iniciarRevision`, `aprobar`, `solicitarCambios`, `rechazar` y
`devolverABorrador` existen y estan probadas, pero no tienen endpoints. Las
pantallas de revision deben invocarlas, sin cambiar el estado por otra via, y
agregar la autorizacion por fila: solo el Catedratico del curso del estudiante.
