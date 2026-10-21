# Historias de Usuario — Jira actualizado

**Fuente:** exportación Jira del 20/Oct/2026, 11:29 PM.
**Cobertura:** 75 incidencias de tipo `Story`, conservando la descripción completa y todos los campos que contienen datos; se omiten los campos vacíos.

## Índice
- [HU-001 — Administrador invita a un Catedrático](#scrum-6)
- [HU-002 — Administrador invita a un Profesional Externo](#scrum-7)
- [HU-003 — Administrador gestiona el catálogo de cursos](#scrum-8)
- [HU-004 — Catedrático activa y vincula un Estudiante a un curso](#scrum-9)
- [HU-005 — Catedrático asigna puentes a un Estudiante](#scrum-10)
- [HU-006 — Sistema registra auditoría de acciones relevantes](#scrum-11)
- [HU-007 — Administrador consulta la bitácora de auditoría](#scrum-12)
- [HU-008 — Administrador gestiona usuarios](#scrum-13)
- [HU-009 — Administrador registra un puente nuevo](#scrum-15)
- [HU-010 — Catedrático solicita el alta de un puente](#scrum-16)
- [HU-011 — Administrador revisa solicitudes de alta de puentes](#scrum-17)
- [HU-012 — Administrador edita los datos de un puente](#scrum-18)
- [HU-013 — Administrador da de baja un puente](#scrum-19)
- [HU-014 — Usuario consulta el catálogo de puentes en mapa y lista](#scrum-20)
- [HU-015 — Usuario consulta la ficha de un puente](#scrum-21)
- [HU-016 — Modelar digitalmente el Formulario de Evaluación Técnica SIECA](#scrum-23)
- [HU-017 — Diseñar la estructura de configuración de pesos del IC y preparar el borrador de calibración académica](#scrum-24)
- [HU-14— Creación de la vista de Lista](#scrum-25)
- [HU-018 — Sistema calcula el Índice de Condición de una inspección](#scrum-27)
- [HU-019 — Administrador configura y versiona los pesos del IC y su acta de calibracion](#scrum-32)
- [HU-020 — Sistema integra el IC con la publicación y actualiza el estado del puente](#scrum-33)
- [HU-033 — Usuario ve el estado y el IC del puente con etiqueta textual en ficha, catálogo y mapa](#scrum-34)
- [HU-032 — Evaluador confirma o ajusta el estado calculado con justificación](#scrum-35)
- [HU-021 — Administrador gestiona y publica versiones del formulario](#scrum-36)
- [HU-022 — Inspector utiliza el formulario generado dinámicamente](#scrum-37)
- [HU-034 — Inspector registra daños mediante captura táctil](#scrum-38)
- [HU-023 — Sistema valida las respuestas contra la versión del formulario](#scrum-39)
- [HU-024 — Inspector inicia una inspección de un puente](#scrum-40)
- [HU-025 — Inspector completa y guarda una inspección en borrador](#scrum-41)
- [HU-026 — Sistema controla la máquina de estados y publica la inspección](#scrum-42)
- [HU-030 — Usuario autenticado consulta una inspección publicada](#scrum-43)
- [HU-031 — Inspector gestiona sus inspecciones](#scrum-44)
- [HU-027 — Inspector genera una corrección de una inspección publicada](#scrum-45)
- [HU-028 — Inspector adjunta fotografías a una inspección](#scrum-46)
- [HU-029 — Sistema valida la evidencia fotográfica y la seguridad de los archivos](#scrum-47)
- [HU-035 — Estudiante levanta y envía inspecciones de sus puentes asignados](#scrum-52)
- [HU-036 — Catedrático consulta su bandeja de revisión](#scrum-53)
- [HU-037 — Catedrático revisa una inspección y ancla observaciones a un elemento](#scrum-54)
- [HU-038 — Catedrático emite el veredicto y la calificación de una revisión](#scrum-55)
- [HU-039 — Estudiante atiende las observaciones y reenvía su inspección](#scrum-56)
- [HU-040 — Usuario recibe y consulta avisos dentro de la aplicación](#scrum-57)
- [HU-041 — Inspector prepara su salida](#scrum-58)
- [HU-042 — Inspector captura una inspección sin conexión](#scrum-59)
- [HU-043 — Sistema reconoce las operaciones repetidas mediante una clave de idempotencia](#scrum-60)
- [HU-044 — Inspector sincroniza sus inspecciones al recuperar la conexión](#scrum-61)
- [HU-045 — Administrador gestiona el catálogo provisional de reglas daño → intervención](#scrum-62)
- [HU-046 — Sistema propone órdenes de mantenimiento al publicarse una inspección](#scrum-63)
- [HU-047 — Catedrático o Administrador gestiona el ciclo de vida de las órdenes](#scrum-64)
- [HU-048 — Catedrático o Administrador ejecuta una orden y la cierra con evidencia](#scrum-65)
- [HU-049 — Usuario consulta y gestiona el mantenimiento desde pantallas](#scrum-66)
- [HU-050 — Administrador carga la plantilla SIECA con un IC completo provisional y la publica sin scripts](#scrum-73)
- [HU-051 — Sistema siembra una versión activa del formulario en el primer arranque](#scrum-74)
- [HU-052 — Catedrático abre el detalle de una revisión aunque el IC no pueda calcularse](#scrum-75)
- [HU-053 — Catedrático configura la calibración del IC desde la aplicación](#scrum-76)
- [HU-054 — Catedrático aprueba la calibración del IC](#scrum-77)
- [HU-055 — Catedrático valida las reglas de mantenimiento desde la aplicación](#scrum-78)
- [HU-056 — Catedrático y Administrador reciben avisos de calibración y validación pendientes](#scrum-79)
- [HU-057 — Inspector guarda fotografías comprimidas en el dispositivo sin conexión](#scrum-80)
- [HU-058 — Inspector sube sus fotografías una por una con reintento y reanudación](#scrum-81)
- [HU-059 — Inspector conserva su versión y la copia del servidor ante un conflicto de sincronización](#scrum-82)
- [HU-060 — Inspector ve el estado de sincronización y usa la vista de diagnóstico](#scrum-83)
- [HU-061 — Inspector instala la aplicación, la usa sin red y envía o publica sin conexión](#scrum-84)
- [HU-062 — Sistema se prueba de punta a punta con red degradada](#scrum-85)
- [HU-063 — Usuario abre hilos por puente y comenta en ellos](#scrum-86)
- [HU-064 — Usuario comenta un elemento concreto de una inspección publicada](#scrum-87)
- [HU-065 — Catedrático o Administrador modera el foro con motivo auditado](#scrum-88)
- [HU-066 — Usuario menciona a otros y recibe alertas de puentes en mal estado](#scrum-89)
- [HU-067 — Catedrático o Administrador recibe la prioridad sugerida por IC y peso del elemento](#scrum-90)
- [HU-068 — Catedrático o Administrador gestiona el plan de mantenimiento rutinario anual de cada puente](#scrum-91)
- [HU-069 — Sistema ejecuta las alertas y limpiezas programadas con bloqueo distribuido](#scrum-92)
- [HU-070 — Inspector adjunta documentos PDF a su inspección](#scrum-93)
- [HU-071 — Catedrático o Administrador recalcula el IC de inspecciones publicadas sin alterar el original](#scrum-94)
- [HU-072 — Usuario consulta, exporta e imprime inspecciones de cualquier versión del formulario](#scrum-95)
- [HU-073 — Sistema demuestra con pruebas la autorización por rol y el cumplimiento de seguridad del despliegue](#scrum-96)
- [HU-074 — Administrador protege las coordenadas de los puentes en estado Malo](#scrum-97)

---

## HU-001 — Administrador invita a un Catedrático {#scrum-6}
**Jira:** [SCRUM-6](https://ayd1-g1-team.atlassian.net/browse/SCRUM-6)

### Issue Type

Story

### Description

*Historia*

_Como Administrador, quiero invitar a un Catedrático por correo electrónico, para que pueda acceder al sistema con los permisos de su rol sin poder autorregistrarse (RN-USR-03)._

*Dependencias:* bloquea HU-002 y HU-004 (mismo patrón de invitación); depende de HU-006 para el registro del evento.

*Criterios de aceptación*

Característica: Invitación de Catedrático

  Antecedentes:

    Dado un usuario Administrador autenticado

  Escenario: Invitación exitosa

    Cuando el Administrador invita a "[catedratico@usac.edu.gt|mailto:catedratico@usac.edu.gt]" con rol Catedrático

    Entonces el sistema crea una cuenta en estado "pendiente de invitación"

    Y se envía un correo con un enlace de activación de un solo uso y vigencia limitada

    Y la cuenta no puede iniciar sesión hasta completar la activación

  Escenario: Correo ya registrado

    Dado que "[catedratico@usac.edu.gt|mailto:catedratico@usac.edu.gt]" ya tiene una cuenta activa

    Cuando el Administrador intenta invitar ese correo

    Entonces el sistema rechaza la invitación con un error 409 en formato RFC 7807

  Escenario: El invitado completa su activación

    Dado un enlace de invitación válido y no vencido

    Cuando el invitado define su contraseña (mínimo 10 caracteres, letra y dígito — RN-USR-06)

    Entonces la cuenta pasa a estado activo con rol Catedrático

    Y el evento queda registrado en la bitácora de auditoría (RN-USR-09)

  Escenario: Enlace de invitación vencido

    Dado un enlace de invitación vencido

    Cuando el invitado intenta usarlo

    Entonces el sistema lo rechaza y solo el Administrador puede reenviar la invitación

  Escenario: Rol no autorizado intenta invitar

    Dado un usuario autenticado con rol distinto de Administrador

    Cuando intenta invitar a un Catedrático

    Entonces el sistema responde 403 Forbidden

### Status

Finalizado

### Status category

Done

### Priority

High

### Story point estimate

5

### Sprint

Sprint 1

### Labels

Administrador

### Parent

Cierre de Usuario y Permisos

### Assignee

Angel Abelino Son Vicente

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 12:46 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Last Viewed

01/Oct/26 8:17 AM

### Watchers

1

### Votes

0

### Rank

0|i0000t:

---

## HU-002 — Administrador invita a un Profesional Externo {#scrum-7}
**Jira:** [SCRUM-7](https://ayd1-g1-team.atlassian.net/browse/SCRUM-7)

### Issue Type

Story

### Description

*Historia*

_Como Administrador, quiero invitar a un Profesional Externo indicando su número de colegiado, para verificar su credencial antes de habilitar completamente su cuenta (RN-USR-03, RN-USR-04)._

*Dependencias:* reutiliza el flujo de invitación de HU-001; depende de HU-006.

*Criterios de aceptación*

Característica: Invitación de Profesional Externo

  Escenario: Invitación con número de colegiado

    Cuando el Administrador invita a un correo con rol Profesional Externo y número de colegiado "12345"

    Entonces el sistema crea la cuenta en estado "pendiente de verificación de colegiado"

    Y se envía el correo de invitación

  Escenario: Activación sin verificación de colegiado

    Dado que el invitado ya completó su activación de contraseña

    Pero el Administrador aún no verificó el número de colegiado

    Cuando el Profesional Externo inicia sesión

    Entonces el sistema le permite autenticarse pero le restringe las acciones de inspección hasta la verificación (RN-USR-04)

  Escenario: Administrador verifica el colegiado

    Dado una cuenta de Profesional Externo con colegiado sin verificar

    Cuando el Administrador marca el colegiado como verificado

    Entonces la cuenta queda completamente habilitada y el cambio se registra en auditoría

  Escenario: Número de colegiado duplicado

    Dado que el número de colegiado "12345" ya está asociado a otra cuenta

    Cuando el Administrador intenta invitar con el mismo número

    Entonces el sistema rechaza la operación con un error 409

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

5

### Sprint

Sprint 1

### Labels

Administrador, Profesional-Externo

### Parent

Cierre de Usuario y Permisos

### Assignee

Angel Abelino Son Vicente

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 12:49 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Watchers

1

### Votes

0

### Rank

0|i0000q:

---

## HU-003 — Administrador gestiona el catálogo de cursos {#scrum-8}
**Jira:** [SCRUM-8](https://ayd1-g1-team.atlassian.net/browse/SCRUM-8)

### Issue Type

Story

### Description

*Historia*

_Como Administrador, quiero crear y mantener el catálogo de cursos vigentes, para que los Catedráticos puedan vincular Estudiantes a un curso (soporta RN-USR-02)._

*Dependencias:* bloquea HU-004 y HU-005.

*Criterios de aceptación*

Característica: Gestión de cursos

  Escenario: Crear un curso

    Cuando el Administrador crea un curso con nombre "Puentes 2026", periodo "2026-2" y Catedrático responsable

    Entonces el curso queda registrado como "vigente"

  Escenario: Marcar un curso como finalizado

    Dado un curso vigente

    Cuando el Administrador lo marca como finalizado

    Entonces ya no aparece como opción para vincular nuevos Estudiantes

    Pero las asignaciones e inspecciones históricas del curso permanecen consultables

  Escenario: Nombre de curso duplicado en el mismo periodo

    Dado un curso "Puentes 2026" ya registrado en el periodo "2026-2"

    Cuando el Administrador intenta crear otro curso con el mismo nombre y periodo

    Entonces el sistema rechaza la operación con un error 409

### Status

Finalizado

### Status category

Done

### Priority

High

### Story point estimate

3

### Sprint

Sprint 1

### Labels

Administrador

### Parent

Cierre de Usuario y Permisos

### Assignee

Dany Noe DeLeon Santizo

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 12:51 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Last Viewed

06/Oct/26 11:39 AM

### Watchers

1

### Votes

0

### Rank

0|i0000n:4

---

## HU-004 — Catedrático activa y vincula un Estudiante a un curso {#scrum-9}
**Jira:** [SCRUM-9](https://ayd1-g1-team.atlassian.net/browse/SCRUM-9)

### Issue Type

Story

### Description

*Historia*

_Como Catedrático, quiero activar las cuentas de Estudiante pendientes y vincularlas a mi curso vigente, para que puedan levantar inspecciones dentro de ese curso (RN-USR-02)._

*Dependencias:* depende de HU-003; bloquea que cualquier Estudiante use el sistema.

*Criterios de aceptación*

Característica: Activación de Estudiante

  Escenario: Activación exitosa

    Dado un Estudiante en estado "pendiente de activación" tras el autorregistro

    Cuando el Catedrático lo activa y lo vincula al curso "Puentes 2026"

    Entonces la cuenta pasa a estado activo, el Estudiante puede iniciar sesión y el evento queda auditado

  Escenario: Rol no autorizado intenta activar

    Dado un usuario con rol Estudiante o Profesional Externo

    Cuando intenta activar a otro Estudiante

    Entonces el sistema responde 403 Forbidden

  Escenario: Vincular a un curso finalizado

    Cuando el Catedrático intenta vincular un Estudiante a un curso ya finalizado

    Entonces el sistema rechaza la operación

  Escenario: Estudiante ya activo

    Dado un Estudiante ya activo y vinculado a un curso

    Cuando el Catedrático intenta activarlo de nuevo

    Entonces el sistema informa que la cuenta ya está activa y no duplica la vinculación

### Status

Finalizado

### Status category

Done

### Priority

Highest

### Story point estimate

5

### Sprint

Sprint 1

### Labels

Catedrático, Estudiante

### Parent

Cierre de Usuario y Permisos

### Assignee

Dany Noe DeLeon Santizo

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 12:53 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Watchers

1

### Votes

0

### Rank

0|i0000n:09

---

## HU-005 — Catedrático asigna puentes a un Estudiante {#scrum-10}
**Jira:** [SCRUM-10](https://ayd1-g1-team.atlassian.net/browse/SCRUM-10)

### Issue Type

Story

### Description

*Historia*

_Como Catedrático, quiero asignar uno o más puentes a un Estudiante dentro de mi curso, para que en Sprint 2 solo pueda inspeccionar los puentes que le correspondan._

*Dependencias:* depende de HU-004 y de HU-009 (Épica B, catálogo de puentes); prepara RN-INS-14 para Sprint 2.

*Criterios de aceptación*

Característica: Asignación de puentes a Estudiantes

  Escenario: Asignación exitosa

    Dado un Estudiante activo vinculado al curso "Puentes 2026" y un puente activo en el catálogo

    Cuando el Catedrático le asigna ese puente dentro del curso

    Entonces la asignación queda registrada y disponible para el futuro módulo de inspecciones

  Escenario: Asignar un puente inactivo

    Cuando el Catedrático intenta asignar un puente marcado como inactivo

    Entonces el sistema rechaza la operación

  Escenario: Asignar fuera del curso vigente

    Cuando el Catedrático intenta asignar un puente a un Estudiante de un curso ya finalizado

    Entonces el sistema rechaza la operación

  Escenario: Revocar una asignación

    Dado una asignación existente

    Cuando el Catedrático la revoca

    Entonces el Estudiante deja de verla entre sus asignaciones vigentes

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

3

### Sprint

Sprint 1

### Labels

Catedrático

### Parent

Cierre de Usuario y Permisos

### Assignee

Dany Noe DeLeon Santizo

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 12:55 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Watchers

1

### Votes

0

### Rank

0|i0000n:04

---

## HU-006 — Sistema registra auditoría de acciones relevantes {#scrum-11}
**Jira:** [SCRUM-11](https://ayd1-g1-team.atlassian.net/browse/SCRUM-11)

### Issue Type

Story

### Description

*Historia*

_Como Sistema, quiero registrar automáticamente cada acción relevante (crear, modificar, cambiar de estado, moderar) con usuario, fecha, acción y valores antes/después, para cumplir con RN-USR-09 y DT-BD-12._

*Dependencias:* infraestructura transversal; bloquea HU-001, HU-002, HU-004 y HU-007.

*Criterios de aceptación*

Característica: Registro de auditoría

  Escenario: Registrar una acción exitosa

    Cuando cualquier servicio ejecuta una acción auditable

    Entonces se crea un registro en la tabla auditoria con usuario, fecha/hora, acción, entidad, valores anteriores y posteriores

  Escenario: Acción ejecutada por un proceso automático

    Cuando un proceso @Scheduled ejecuta una acción auditable

    Entonces el registro se crea con usuario nulo y una marca que identifica al proceso automático

  Escenario: La auditoría nunca bloquea la operación de negocio

    Dado que el registro de auditoría falla por una causa técnica

    Cuando se ejecuta la acción de negocio asociada

    Entonces la operación de negocio se completa igual y el fallo de auditoría queda en el log técnico

  Escenario: Implementación por aspecto

    Entonces el mecanismo se implementa con un aspecto (@Around) en la capa de servicio (DT-BD-12), nunca con triggers de base de datos

### Status

Finalizado

### Status category

Done

### Priority

Highest

### Story point estimate

8

### Sprint

Sprint 1

### Labels

Sistema

### Parent

Cierre de Usuario y Permisos

### Assignee

Kevin Mushin

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 12:57 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Watchers

1

### Votes

0

### Rank

0|i0000n:004

---

## HU-007 — Administrador consulta la bitácora de auditoría {#scrum-12}
**Jira:** [SCRUM-12](https://ayd1-g1-team.atlassian.net/browse/SCRUM-12)

### Issue Type

Story

### Description

*Historia*

_Como Administrador, quiero consultar la bitácora de auditoría con filtros, para revisar quién hizo qué y cuándo._

*Dependencias:* depende de HU-006.

*Criterios de aceptación*

Característica: Consulta de bitácora de auditoría

  Escenario: Listado paginado

    Cuando el Administrador abre la bitácora de auditoría

    Entonces ve un listado paginado (máximo 100 por página, DT-BE-08) ordenado por fecha descendente

  Escenario: Filtrar por usuario y rango de fechas

    Cuando el Administrador filtra por usuario y por un rango de fechas

    Entonces solo se muestran los registros que cumplen ambos filtros

  Escenario: Ver el detalle de un registro

    Cuando el Administrador abre un registro de auditoría

    Entonces ve los valores anteriores y posteriores en formato legible, no como JSON crudo

  Escenario: Acceso restringido

    Dado un usuario con rol distinto de Administrador

    Cuando intenta acceder a la bitácora

    Entonces el sistema responde 403 Forbidden

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

5

### Sprint

Sprint 1

### Labels

Administrador

### Parent

Cierre de Usuario y Permisos

### Assignee

Kevin Mushin

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 12:58 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Watchers

1

### Votes

0

### Rank

0|i0000n:002

---

## HU-008 — Administrador gestiona usuarios {#scrum-13}
**Jira:** [SCRUM-13](https://ayd1-g1-team.atlassian.net/browse/SCRUM-13)

### Issue Type

Story

### Description

*Historia*

_Como Administrador, quiero listar, activar/desactivar y cambiar el rol de los usuarios existentes, para mantener el sistema de accesos al día._

*Dependencias:* depende de HU-006; reemplaza el placeholder actual del panel de administración.

*Criterios de aceptación*

Característica: Gestión de usuarios

  Escenario: Listar usuarios con filtros

    Cuando el Administrador abre el panel de usuarios

    Entonces ve un listado paginado, filtrable por rol y estado

  Escenario: Desactivar un usuario

    Cuando el Administrador desactiva a un usuario

    Entonces la cuenta queda inactiva, nunca eliminada físicamente (RN-USR-08)

    Y sus inspecciones y comentarios previos permanecen atribuidos a su nombre

  Escenario: Reactivar un usuario

    Dado un usuario inactivo

    Cuando el Administrador lo reactiva

    Entonces recupera acceso con el mismo rol que tenía

  Escenario: Cambiar el rol de un usuario

    Cuando el Administrador cambia el rol de un usuario a Profesional Externo

    Entonces el sistema exige el número de colegiado antes de confirmar el cambio (RN-USR-04)

  Escenario: Un usuario no puede desactivarse a sí mismo

    Cuando el Administrador intenta desactivar su propia cuenta

    Entonces el sistema rechaza la operación

### Status

Finalizado

### Status category

Done

### Priority

High

### Story point estimate

5

### Sprint

Sprint 1

### Labels

Administrador

### Parent

Cierre de Usuario y Permisos

### Assignee

Angel Abelino Son Vicente

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 1:00 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Watchers

1

### Votes

0

### Rank

0|i0000o:

---

## HU-009 — Administrador registra un puente nuevo {#scrum-15}
**Jira:** [SCRUM-15](https://ayd1-g1-team.atlassian.net/browse/SCRUM-15)

### Issue Type

Story

### Description

*Historia*

_Como Administrador, quiero registrar un puente nuevo con sus datos obligatorios y ubicación, para iniciar su ficha permanente en el catálogo (RN-INV-01 a RN-INV-06)._

*Dependencias:* requiere el catálogo INE de departamentos/municipios cargado; bloquea el resto de HU de esta épica y HU-005 de la Épica A.

*Criterios de aceptación*

Característica: Alta de puente

  Escenario: Alta exitosa con datos mínimos

    Cuando el Administrador registra un puente con nombre, departamento, municipio, ruta y coordenadas válidas

    Entonces el sistema genera un código único con formato GT-<DEPTO>-<MUNI>-<CORRELATIVO>

    Y el puente queda activo, sin inspecciones, con estado "Sin evaluar" (RN-INV-10)

  Escenario: Departamento y municipio incongruentes

    Cuando el Administrador selecciona un municipio que no pertenece al departamento elegido

    Entonces el sistema rechaza el formulario con un error de validación (RN-INV-04)

  Escenario: Coordenadas dentro de 100 metros de un puente existente

    Dado un puente ya registrado en unas coordenadas dadas

    Cuando el Administrador intenta registrar otro puente a menos de 100 metros (ST_DWithin)

    Entonces el sistema muestra una advertencia no bloqueante y permite continuar si el Administrador confirma (RN-INV-06)

  Escenario: Conversión y presentación en UTM

    Cuando se guarda la coordenada en WGS84 (GEOGRAPHY(POINT,4326))

    Entonces el sistema calcula y muestra la coordenada equivalente en UTM zona 15N o 16N según corresponda (RN-INV-05)

  Escenario: Campo obligatorio faltante

    Cuando el Administrador intenta guardar sin indicar la ruta

    Entonces el sistema responde 422 con el detalle del campo faltante en formato RFC 7807 (DT-BE-05)

  Escenario: Rol no autorizado

    Dado un usuario con rol distinto de Administrador

    Cuando intenta registrar un puente directamente

    Entonces el sistema responde 403 Forbidden (RN-INV-07)

### Status

Finalizado

### Status category

Done

### Priority

Highest

### Story point estimate

8

### Sprint

Sprint 1

### Labels

Administrador

### Parent

Épica B — Catálogo de Puentes

### Assignee

Mario Yancor

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 1:04 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Watchers

1

### Votes

0

### Rank

0|i0000n:1

---

## HU-010 — Catedrático solicita el alta de un puente {#scrum-16}
**Jira:** [SCRUM-16](https://ayd1-g1-team.atlassian.net/browse/SCRUM-16)

### Issue Type

Story

### Description

*Historia*

_Como Catedrático, quiero solicitar el alta de un puente que no está en el catálogo, para que el Administrador la evalúe y la apruebe (RN-INV-07)._

*Dependencias:* reutiliza el formulario de HU-009; bloquea HU-011.

*Criterios de aceptación*

Característica: Solicitud de alta de puente

  Escenario: Solicitud exitosa

    Cuando el Catedrático envía una solicitud de alta con los datos obligatorios del puente

    Entonces la solicitud queda en estado "pendiente", visible para el Administrador, y el puente aún no existe en el catálogo público

  Escenario: Catedrático consulta el estado de su solicitud

    Dado una solicitud pendiente

    Cuando el Catedrático la consulta

    Entonces ve el estado actual (pendiente, aprobada o rechazada)

  Escenario: Rol no autorizado

    Dado un usuario Estudiante o Profesional Externo

    Cuando intenta enviar una solicitud de alta de puente

    Entonces el sistema responde 403 Forbidden

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

3

### Sprint

Sprint 1

### Labels

Catedrático

### Parent

Épica B — Catálogo de Puentes

### Assignee

Herberth Julian Reyes Pacajoj

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 1:06 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Watchers

1

### Votes

0

### Rank

0|i0000n:001

---

## HU-011 — Administrador revisa solicitudes de alta de puentes {#scrum-17}
**Jira:** [SCRUM-17](https://ayd1-g1-team.atlassian.net/browse/SCRUM-17)

### Issue Type

Story

### Description

*Historia*

_Como Administrador, quiero revisar y aprobar o rechazar las solicitudes de alta de puentes, para mantener el control exclusivo sobre el catálogo (RN-INV-07)._

*Dependencias:* depende de HU-010.

*Criterios de aceptación*

Característica: Revisión de solicitudes de alta

  Escenario: Aprobar una solicitud

    Dado una solicitud pendiente

    Cuando el Administrador la aprueba

    Entonces el sistema crea el puente en el catálogo con código único generado y notifica al Catedrático solicitante

  Escenario: Rechazar una solicitud

    Cuando el Administrador rechaza una solicitud indicando un motivo

    Entonces la solicitud queda marcada como rechazada con el motivo visible para el solicitante, y no se crea ningún puente

  Escenario: Advertencia de duplicado también aplica a solicitudes

    Dado una solicitud cuyas coordenadas están a menos de 100 metros de un puente existente

    Cuando el Administrador la revisa

    Entonces ve la misma advertencia de posible duplicado que en el alta directa (RN-INV-06)

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

3

### Sprint

Sprint 1

### Labels

Administrador

### Parent

Épica B — Catálogo de Puentes

### Assignee

Herberth Julian Reyes Pacajoj

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 1:08 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Watchers

1

### Votes

0

### Rank

0|i0000n:000i

---

## HU-012 — Administrador edita los datos de un puente {#scrum-18}
**Jira:** [SCRUM-18](https://ayd1-g1-team.atlassian.net/browse/SCRUM-18)

### Issue Type

Story

### Description

*Historia*

_Como Administrador, quiero editar los datos generales de un puente existente, para mantener su ficha actualizada sin afectar los campos calculados (RN-INV-04, RN-INV-09)._

*Dependencias:* depende de HU-009.

*Criterios de aceptación*

Característica: Edición de puente

  Escenario: Edición exitosa de datos generales

    Cuando el Administrador edita el nombre, la ruta u otros datos generales de un puente

    Entonces los cambios se guardan y quedan auditados

  Escenario: Campos calculados no editables

    Cuando el Administrador abre el formulario de edición

    Entonces el estado actual, el Índice de Condición y la fecha de última inspección se muestran de solo lectura (RN-INV-09)

  Escenario: Código de puente inmutable

    Cuando el Administrador intenta modificar el código del puente

    Entonces el sistema lo impide (RN-INV-02)

  Escenario: Edición de coordenadas revalida duplicados

    Cuando el Administrador cambia las coordenadas de un puente

    Entonces el sistema vuelve a ejecutar la validación de cercanía (RN-INV-06)

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

3

### Sprint

Sprint 1

### Labels

Administrador

### Parent

Épica B — Catálogo de Puentes

### Assignee

Rony Rojas Aguilar

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 1:09 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Watchers

1

### Votes

0

### Rank

0|i0000n:0i

---

## HU-013 — Administrador da de baja un puente {#scrum-19}
**Jira:** [SCRUM-19](https://ayd1-g1-team.atlassian.net/browse/SCRUM-19)

### Issue Type

Story

### Description

*Historia*

_Como Administrador, quiero dar de baja lógica a un puente con un motivo documentado, para retirarlo del catálogo activo sin perder su historial (RN-INV-08, DT-BD-11)._

*Dependencias:* depende de HU-009.

*Criterios de aceptación*

Característica: Baja de puente

  Escenario: Baja exitosa con motivo

    Cuando el Administrador da de baja un puente indicando el motivo "demolido"

    Entonces el puente queda marcado como inactivo, nunca eliminado físicamente

    Y deja de aparecer en el catálogo público pero sigue siendo consultable con su historial

  Escenario: Baja sin motivo

    Cuando el Administrador intenta dar de baja un puente sin indicar motivo

    Entonces el sistema rechaza la operación con un error 422

  Escenario: Reactivar un puente dado de baja

    Dado un puente inactivo

    Cuando el Administrador lo reactiva

    Entonces vuelve a aparecer en el catálogo activo

### Status

Finalizado

### Status category

Done

### Priority

Low

### Story point estimate

2

### Sprint

Sprint 1

### Labels

Administrador

### Parent

Épica B — Catálogo de Puentes

### Assignee

Rony Rojas Aguilar

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 1:11 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Watchers

1

### Votes

0

### Rank

0|i0000n:00i

---

## HU-014 — Usuario consulta el catálogo de puentes en mapa y lista {#scrum-20}
**Jira:** [SCRUM-20](https://ayd1-g1-team.atlassian.net/browse/SCRUM-20)

### Issue Type

Story

### Description

*Historia*

_Como Visitante o usuario autenticado, quiero ver el catálogo de puentes en un mapa y en una lista, para ubicar y explorar los puentes registrados (RN-USR-10, DT-FE-14)._

*Dependencias:* depende de HU-009.

*Criterios de aceptación*

Característica: Catálogo público de puentes

  Escenario: Visitante sin autenticar consulta el mapa

    Dado un usuario no autenticado

    Cuando abre el catálogo de puentes

    Entonces ve el mapa con los puentes activos, su nombre, ubicación y estado semafórico (RN-USR-10)

    Pero no ve fotografías ni daños detallados

  Escenario: Estado comunicado con texto, no solo color

    Cuando se muestra el estado semafórico de un puente en el mapa o la lista

    Entonces siempre se acompaña de una etiqueta textual (Bueno/Regular/Malo/Sin evaluar), nunca solo el color (DT-FE-13)

  Escenario: Filtrar por departamento o estado

    Cuando el usuario filtra el listado por departamento o por estado semafórico

    Entonces solo se muestran los puentes que cumplen el filtro

  Escenario: Mapa con tecnología libre

    Entonces el mapa se implementa con MapLibre GL JS y teselas de OpenStreetMap, sin dependencias de pago (DT-FE-14)

  Escenario: Listado paginado

    Cuando el usuario navega el listado

    Entonces los resultados están paginados con un máximo de 100 por página (DT-BE-08)

### Status

Finalizado

### Status category

Done

### Priority

High

### Story point estimate

8

### Sprint

Sprint 1

### Labels

Administrador, Catedrático, Estudiante, Profesional-Externo, Visitante

### Parent

Épica B — Catálogo de Puentes

### Assignee

Melvin Ordoñez

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 1:13 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Watchers

1

### Votes

0

### Rank

0|i0000n:02

---

## HU-015 — Usuario consulta la ficha de un puente {#scrum-21}
**Jira:** [SCRUM-21](https://ayd1-g1-team.atlassian.net/browse/SCRUM-21)

### Issue Type

Story

### Description

*Historia*

_Como usuario, quiero ver la ficha de detalle de un puente, para conocer su estado actual, su Índice de Condición vigente y la fecha de su última inspección (RN-INV-09, RN-INV-10)._

*Dependencias:* depende de HU-009; se conecta a Inspecciones/IC en sprints futuros.

*Criterios de aceptación*

Característica: Ficha de detalle de puente

  Escenario: Puente sin inspecciones publicadas

    Dado un puente recién registrado, sin inspecciones publicadas

    Cuando un usuario abre su ficha

    Entonces ve el estado "Sin evaluar" en gris, nunca "Bueno" (RN-INV-10)

  Escenario: Puente con inspección publicada

    Dado un puente con al menos una inspección publicada (a futuro, Sprint 2)

    Cuando un usuario abre su ficha

    Entonces ve el estado, el IC y la fecha de la última inspección publicada, todos de solo lectura (RN-INV-09)

  Escenario: Información restringida a usuarios no autenticados

    Dado un usuario no autenticado

    Cuando abre la ficha de un puente

    Entonces ve los datos públicos pero no fotografías ni informes (RN-USR-10)

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

3

### Sprint

Sprint 1

### Labels

Administrador, Catedrático, Estudiante, Profesional-Externo, Visitante

### Parent

Épica B — Catálogo de Puentes

### Assignee

Rony Rojas Aguilar

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 1:16 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Watchers

1

### Votes

0

### Rank

0|i0000n:2

---

## HU-016 — Modelar digitalmente el Formulario de Evaluación Técnica SIECA {#scrum-23}
**Jira:** [SCRUM-23](https://ayd1-g1-team.atlassian.net/browse/SCRUM-23)

### Issue Type

Story

### Description

*Historia*

_Como equipo de desarrollo, quiero transcribir las 8 secciones del Formulario de Evaluación Técnica del Manual SIECA a un borrador de JSON Schema, para que el módulo de formulario dinámico (Sprint 2) tenga un contenido base sobre el cual trabajar (RN-FRM-01, RN-FRM-07)._

*Dependencias:* no depende de código previo; bloquea el módulo formulario de Sprint 2.

*Criterios de aceptación*

Característica: Borrador de JSON Schema del formulario SIECA

  Escenario: Cobertura de las 8 secciones

    Entonces el borrador incluye las 8 secciones exigidas por RN-FRM-07: Identificación, Datos generales, Tramos, Sub-estructura, Super-estructura, Elementos no estructurales, Características del cauce, y Otros

  Escenario: Cada pregunta define su tipo y validación

    Para cada pregunta del borrador

    Entonces se documenta su tipo de dato, si es obligatoria, y su regla de validación cuando aplique

  Escenario: Separación de campos consultables vs. JSONB

    Cuando se revisa el borrador junto al equipo de base de datos

    Entonces se marca explícitamente cuáles de los ~50 campos consultables/filtrables van como columnas tipadas y cuáles de los ~450 campos de daño van dentro de la columna JSONB (DT-BD-02)

  Escenario: Validación de que no falta ninguna pregunta del manual

    Cuando el borrador se compara sección por sección contra el manual SIECA

    Entonces no hay preguntas del documento original sin representar en el JSON Schema

### Status

Finalizado

### Status category

Done

### Priority

High

### Story point estimate

8

### Sprint

Sprint 1

### Labels

Interno-(equipo)

### Parent

Épica C — Preparación del Formulario SIECA

### Assignee

Miguel Angel Quemé Gómez

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 1:19 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Watchers

1

### Votes

0

### Rank

0|i0000n:i

---

## HU-017 — Diseñar la estructura de configuración de pesos del IC y preparar el borrador de calibración académica {#scrum-24}
**Jira:** [SCRUM-24](https://ayd1-g1-team.atlassian.net/browse/SCRUM-24)

### Issue Type

Story

### Description

*Historia*

_Como equipo de desarrollo, quiero diseñar la estructura de configuración y versionado de pesos del Índice de Condición y preparar una plantilla de calibración académica, para que los valores definitivos puedan ser validados y respaldados formalmente por el Catedrático del curso de Puentes._

*Dependencias:* depende de HU-016; es insumo directo de HU-019 (Sprint 2).

*Criterios de aceptación*

Característica: Borrador de pesos y acta de calibración

  Escenario: Tabla de pesos cubre todos los elementos del formulario

    Entonces, cada elemento evaluable posee un identificador para asociar posteriormente su peso de criticidad estructural, sin asumir como definitivos valores no aprobados académicamente.

  Escenario: Elementos portantes marcados

    Cuando se revisa la tabla de pesos

    Entonces los elementos portantes (vigas, columnas, cerchas) están señalados explícitamente, ya que participan en las reglas de anulación (RN-IC-05)

  Escenario: Acta de calibración documentada

    Entonces existe un documento de acta que describe el criterio usado para asignar cada peso, listo para revisión y firma del Catedrático

  Escenario: Pesos versionados junto al formulario

    Cuando se publique la primera versión del formulario en Sprint 2

    Entonces los pesos quedan asociados a esa misma versión (RN-IC-03)

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

5

### Sprint

Sprint 1

### Labels

Interno-(equipo)

### Parent

Épica C — Preparación del Formulario SIECA

### Assignee

Miguel Angel Quemé Gómez

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

01/Oct/26 1:21 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Watchers

1

### Votes

0

### Rank

0|i0000n:9

---

## HU-14— Creación de la vista de Lista {#scrum-25}
**Jira:** [SCRUM-25](https://ayd1-g1-team.atlassian.net/browse/SCRUM-25)

### Issue Type

Story

### Status

Finalizado

### Status category

Done

### Priority

High

### Sprint

Sprint 1

### Parent

Épica B — Catálogo de Puentes

### Assignee

Brígido Josías Alvarado Pec

### Reporter

Brígido Josías Alvarado Pec

### Creator

Brígido Josías Alvarado Pec

### Created

05/Oct/26 1:56 AM

### Updated

08/Oct/26 12:55 AM

### Resolved

08/Oct/26 12:55 AM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

08/Oct/26 12:55 AM

### Watchers

1

### Votes

0

### Rank

0|i0000n:01

---

## HU-018 — Sistema calcula el Índice de Condición de una inspección {#scrum-27}
**Jira:** [SCRUM-27](https://ayd1-g1-team.atlassian.net/browse/SCRUM-27)

### Issue Type

Story

### Description

*Historia*

Como Sistema, quiero calcular automáticamente el Índice de Condición a partir de las calificaciones de daño y los pesos configurados, para obtener un estado semafórico objetivo y reproducible (RN-IC-01, RN-IC-02, RN-IC-04, RN-IC-05; DT-ARQ-06; cobertura 100 % según DT-CAL-01).

*Dependencias:* HU-016 y HU-017, finalizadas en Sprint 1. Se construye como un servicio de dominio aislado utilizando el esquema {{sieca-v1}} como referencia. Los pesos reales dependen de la calibración aprobada por el Catedrático.

*Criterios de aceptación*

*Característica: Cálculo del Índice de Condición*

*Antecedentes:*

Dado una versión de formulario con sus pesos, su escala de severidad y sus reglas de evaluación del IC.

*Escenario: Sin daños*

Dado todos los elementos evaluados con calificación 0

Cuando se calcula el IC

Entonces el IC es 100

Y el estado es "Bueno"

*Escenario: Cálculo ponderado*

Dado elementos evaluados con calificaciones entre 0 y 4 y sus pesos

Cuando se calcula el IC

Entonces el resultado se obtiene mediante la fórmula definida en RN-IC-02

Y el resultado está entre 0 y 100

*Escenario: Daño máximo*

Dado todos los elementos evaluados con calificación 4

Cuando se calcula el IC

Entonces el IC es 0

Y el estado es "Malo"

*Escenario: Estado según el rango del IC*

Dado un IC calculado

Cuando el IC es mayor o igual a 75

Entonces el estado es "Bueno"

Cuando el IC es mayor o igual a 50 y menor a 75

Entonces el estado es "Regular"

Cuando el IC es menor a 50

Entonces el estado es "Malo"

*Escenario: Elementos que no aplican*

Dado un elemento marcado como {{NO_APLICA}}

Cuando se calcula el IC

Entonces dicho elemento no participa en la calificación ni en la suma total de pesos

*Escenario: Elementos repetibles*

Dado un elemento repetible con varias instancias, como tramos o pilas

Cuando se calcula el IC

Entonces se aplica la regla de agregación definida para la versión del formulario

*Escenario: Anulación por condición crítica*

Dado un IC calculado distinto de "Malo"

Cuando existe socavación con exposición de cimentación, asentamiento de estructura, un elemento portante cortado o con pérdida de sección, o ausencia total de un elemento portante

Entonces el estado final se fuerza a "Malo"

Y el valor numérico del IC calculado no cambia

Y se registra la condición que provocó la anulación

*Escenario: Reglas asociadas a la versión del formulario*

Dado dos versiones de formulario con distintas reglas de evaluación del IC

Cuando se calcula utilizando cada versión

Entonces cada cálculo utiliza las reglas correspondientes a su propia versión

*Escenario: Pesos o escala sin calibrar*

Dado un elemento evaluado sin peso o un nivel de severidad sin valor configurado

Cuando se intenta calcular el IC

Entonces el sistema rechaza el cálculo

Y señala el elemento o nivel cuya configuración está incompleta

*Escenario: Nada que calcular*

Dado una inspección sin elementos evaluados

Cuando se intenta calcular el IC

Entonces el sistema rechaza la operación

Y no produce un resultado

*Escenario: Calificación fuera de rango*

Dado una calificación menor que 0 o mayor que 4

Cuando se intenta calcular el IC

Entonces el sistema rechaza la operación

*Escenario: Resultado reproducible y trazable*

Dado la misma entrada y la misma versión del formulario

Cuando el IC se calcula más de una vez

Entonces el resultado es idéntico

Y se conserva información suficiente para identificar la versión y el desglose utilizado

*Escenario: Cobertura del servicio*

Dado la suite de pruebas del Índice de Condición

Cuando se ejecutan las pruebas

Entonces el servicio de cálculo alcanza 100 % de cobertura

### Status

Finalizado

### Status category

Done

### Priority

Highest

### Story point estimate

8

### Sprint

Sprint 2

### Labels

Sistema

### Parent

Épica D — Índice de Condición

### Assignee

KENNY SALAZAR

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:13 PM

### Updated

16/Oct/26 11:09 PM

### Resolved

16/Oct/26 11:09 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

16/Oct/26 11:09 PM

### Watchers

1

### Votes

0

### Rank

0|i0004d:

---

## HU-019 — Administrador configura y versiona los pesos del IC y su acta de calibracion {#scrum-32}
**Jira:** [SCRUM-32](https://ayd1-g1-team.atlassian.net/browse/SCRUM-32)

### Issue Type

Story

### Description

*Historia*

Como Administrador, quiero configurar y versionar los pesos y la escala de severidad del Índice de Condición, y adjuntar el acta de calibración correspondiente, para que cada versión del formulario utilice una configuración respaldada académicamente (RN-IC-03, RN-IC-08).

*Dependencias:* HU-017 del Sprint 1; HU-021 para asociar la configuración a una versión del formulario; HU-028 para utilizar el servicio de almacenamiento de archivos al adjuntar el acta de calibración.

*Criterios de aceptación*

*Característica: Configuración y versionado de pesos del Índice de Condición*

*Escenario: Configurar los pesos de una versión en borrador*

Dado una versión del formulario en estado BORRADOR

Cuando el Administrador configura el peso de cada elemento evaluable y los valores de la escala de severidad

Entonces la configuración queda asociada a esa versión

*Escenario: Peso no válido*

Cuando el Administrador intenta guardar un peso menor o igual a cero o vacío

Entonces el sistema rechaza la operación con un error de validación

*Escenario: Una versión no se publica con pesos incompletos*

Dado una versión en BORRADOR con al menos un elemento evaluable sin peso configurado

Cuando el Administrador intenta publicarla

Entonces el sistema rechaza la operación

Y señala cuáles elementos tienen la configuración incompleta

*Escenario: Los pesos de una versión publicada son inmutables*

Dado una versión de formulario PUBLICADA

Cuando el Administrador intenta modificar uno de sus pesos

Entonces el sistema rechaza la operación con un conflicto

Y los cambios deben realizarse mediante una nueva versión del formulario

*Escenario: Cambiar los pesos no modifica resultados anteriores*

Dado inspecciones publicadas utilizando los pesos de una versión anterior

Cuando se publica una nueva versión con pesos diferentes

Entonces los Índices de Condición ya publicados conservan sus resultados originales

*Escenario: Adjuntar el acta de calibración*

Dado una versión con los pesos configurados

Cuando el Administrador adjunta el acta de calibración firmada en PDF

Entonces el archivo queda asociado a esa versión

Y puede consultarse mediante acceso autorizado

*Escenario: Archivo de acta no válido*

Cuando el Administrador intenta adjuntar un archivo cuyo contenido real no corresponde a un PDF

Entonces el sistema rechaza el archivo aunque su extensión sea {{.pdf}}

*Escenario: Versión sin acta de calibración*

Dado una versión que todavía no tiene un acta asociada

Cuando se consulta su configuración

Entonces el sistema indica claramente que se encuentra "sin acta de calibración"

*Escenario: Los cambios quedan registrados en auditoría*

Cuando el Administrador modifica un peso o adjunta un acta de calibración

Entonces se registra el usuario, la fecha y los valores anterior y posterior correspondientes

*Escenario: Acceso restringido*

Dado un usuario con rol distinto de Administrador

Cuando intenta modificar pesos o adjuntar un acta de calibración

Entonces el sistema responde 403 Forbidden

### Status

Finalizado

### Status category

Done

### Priority

High

### Story point estimate

5

### Sprint

Sprint 2

### Parent

Épica D — Índice de Condición

### Assignee

KENNY SALAZAR

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:30 PM

### Updated

18/Oct/26 5:35 PM

### Resolved

18/Oct/26 5:35 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 5:35 PM

### Watchers

1

### Votes

0

### Rank

0|i0004a:

---

## HU-020 — Sistema integra el IC con la publicación y actualiza el estado del puente {#scrum-33}
**Jira:** [SCRUM-33](https://ayd1-g1-team.atlassian.net/browse/SCRUM-33)

### Issue Type

Story

### Description

*Historia*

Como Sistema, quiero calcular el Índice de Condición definitivo cuando una inspección se publica y actualizar con ese resultado los datos derivados del puente, para que el estado vigente siempre provenga de la inspección publicada más reciente (RN-INV-09, RN-INV-10, RN-IC-07, RN-IC-08).

*Dependencias:* HU-018 — Sistema calcula el Índice de Condición; HU-019 — Administrador configura y versiona los pesos del IC; HU-026 — Sistema controla la máquina de estados y publica la inspección.

*Criterios de aceptación*

*Característica: Integración del IC con la publicación*

*Escenario: Publicar calcula y guarda el IC definitivo*

Dado una inspección lista para publicarse

Cuando la inspección se publica

Entonces el sistema calcula el IC utilizando los pesos correspondientes a la versión del formulario

Y guarda el resultado como cálculo definitivo de publicación

Y conserva su desglose y la versión del algoritmo utilizada

*Escenario: La publicación actualiza el puente*

Dado una inspección que acaba de alcanzar el estado PUBLICADA

Cuando finaliza correctamente el proceso de publicación

Entonces el puente actualiza su estado actual

Y actualiza su Índice de Condición vigente

Y actualiza la fecha de última inspección

*Escenario: Un fallo del cálculo revierte la publicación*

Dado una inspección lista para publicarse

Y ocurre un fallo durante el cálculo del IC

Cuando se procesa la publicación

Entonces la inspección permanece en su estado anterior

Y el puente conserva sus datos derivados anteriores

Y no queda información parcialmente actualizada

*Escenario: Una condición crítica fuerza el estado Malo*

Dado una inspección cuyo IC por rango corresponde a "Bueno" o "Regular"

Y contiene una condición crítica definida en RN-IC-05

Cuando la inspección se publica

Entonces el estado final del puente es "Malo"

Y el valor numérico del IC calculado se conserva

*Escenario: Puente sin inspecciones publicadas*

Dado un puente que todavía no posee inspecciones publicadas

Cuando se consulta su estado actual

Entonces el estado es "Sin evaluar"

Y nunca se muestra automáticamente como "Bueno"

*Escenario: La inspección publicada más reciente determina el estado*

Dado un puente con más de una inspección publicada

Cuando se consulta su estado actual

Entonces el estado, el IC y la fecha de última inspección corresponden a la inspección publicada más reciente

*Escenario: El IC de un borrador no cambia el puente*

Dado una inspección en estado BORRADOR con un IC preliminar calculado

Cuando se guarda o consulta dicho borrador

Entonces el estado vigente del puente no cambia

Y el IC de la inspección se muestra identificado como "Preliminar"

*Escenario: Un cambio posterior de pesos no modifica publicaciones anteriores*

Dado una inspección publicada cuyo IC fue calculado con una versión determinada de pesos

Cuando posteriormente se publica una nueva versión del formulario con pesos diferentes

Entonces el IC previamente publicado no cambia

Y el estado histórico asociado a esa publicación se conserva

*Escenario: Actualización atómica*

Dado una inspección lista para ser publicada

Cuando se ejecuta la publicación

Entonces el cambio de estado de la inspección, el cálculo definitivo del IC y la actualización de los datos derivados del puente se realizan de forma consistente

Y si cualquiera de esas operaciones falla, no queda un estado parcial

*Escenario: Auditoría*

Cuando la publicación provoca un cambio en el estado, IC o fecha de última inspección del puente

Entonces el sistema registra en auditoría el usuario responsable de la publicación

Y conserva los valores anteriores y posteriores correspondientes

### Status

Finalizado

### Status category

Done

### Priority

Highest

### Story point estimate

5

### Sprint

Sprint 2

### Parent

Épica D — Índice de Condición

### Assignee

KENNY SALAZAR

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:32 PM

### Updated

18/Oct/26 5:35 PM

### Resolved

18/Oct/26 5:35 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 5:35 PM

### Watchers

1

### Votes

0

### Rank

0|i00049:

---

## HU-033 — Usuario ve el estado y el IC del puente con etiqueta textual en ficha, catálogo y mapa {#scrum-34}
**Jira:** [SCRUM-34](https://ayd1-g1-team.atlassian.net/browse/SCRUM-34)

### Issue Type

Story

### Description

*Historia*

Como usuario del sistema, quiero ver el estado actual y el Índice de Condición de cada puente con una etiqueta de texto además del color, para comprender su condición sin depender únicamente de distinguir colores (RN-IC-04, RN-INV-09, RN-INV-10, RN-USR-10, DT-FE-13).

*Dependencias:* HU-020 — Sistema integra el IC con la publicación y actualiza el estado del puente; HU-015 — Usuario consulta la ficha de un puente; vista del mapa del catálogo.

*Criterios de aceptación*

*Característica: Estado del puente visible con texto*

*Escenario: Estado Bueno*

Dado un puente con estado "Bueno"

Cuando se muestra en la ficha, catálogo o mapa

Entonces aparece la etiqueta textual "Bueno"

Y se representa con el color correspondiente

*Escenario: Estado Regular*

Dado un puente con estado "Regular"

Cuando se muestra en la ficha, catálogo o mapa

Entonces aparece la etiqueta textual "Regular"

Y se representa con el color correspondiente

*Escenario: Estado Malo*

Dado un puente con estado "Malo"

Cuando se muestra en la ficha, catálogo o mapa

Entonces aparece la etiqueta textual "Malo"

Y se representa con el color correspondiente

*Escenario: Puente sin evaluar*

Dado un puente sin inspecciones publicadas

Cuando se muestra en la ficha, catálogo o mapa

Entonces aparece la etiqueta textual "Sin evaluar"

Y se representa con color gris

*Escenario: La ficha muestra los datos derivados*

Dado un puente con una inspección publicada

Cuando un usuario abre su ficha

Entonces se muestran el estado actual

Y el Índice de Condición vigente

Y la fecha de la última inspección publicada

*Escenario: El mapa explica los estados*

Cuando un usuario abre el mapa del catálogo

Entonces existe una leyenda que identifica el significado de cada estado

Y cada marcador comunica el estado mediante color y texto

*Escenario: Accesibilidad del estado*

Cuando se muestran los estados de los puentes

Entonces la información nunca depende únicamente del color

Y el contraste cumple los requisitos de accesibilidad definidos por DT-FE-13

*Escenario: Visitante consulta información pública*

Dado un visitante sin sesión iniciada

Cuando consulta un puente en el catálogo

Entonces puede ver su nombre, ubicación y estado público

Pero no puede consultar daños detallados, fotografías ni informes

*Escenario: Actualización después de publicar una inspección*

Dado una inspección que acaba de ser publicada

Cuando el usuario vuelve a consultar la ficha, catálogo o mapa

Entonces se muestra automáticamente el nuevo estado del puente

Y no requiere una modificación manual del registro

*Escenario: Los campos derivados no son editables*

Dado un Administrador editando los datos generales de un puente

Cuando intenta modificar manualmente el estado, el IC o la fecha de última inspección

Entonces el sistema no permite editar dichos campos

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

3

### Sprint

Sprint 2

### Parent

Épica D — Índice de Condición

### Assignee

KENNY SALAZAR

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:34 PM

### Updated

18/Oct/26 4:36 PM

### Resolved

18/Oct/26 4:36 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 4:36 PM

### Watchers

1

### Votes

0

### Rank

0|i0005s:

---

## HU-032 — Evaluador confirma o ajusta el estado calculado con justificación {#scrum-35}
**Jira:** [SCRUM-35](https://ayd1-g1-team.atlassian.net/browse/SCRUM-35)

### Issue Type

Story

### Description

*Historia*

Como evaluador, quiero confirmar o modificar el estado calculado justificándolo por escrito, para que mi criterio profesional quede registrado sin perder el valor automático calculado por el sistema (RN-IC-06).

*Dependencias:* HU-020 — Sistema integra el IC con la publicación y actualiza el estado del puente.

*Criterios de aceptación*

*Característica: Ajuste del estado calculado*

*Escenario: Confirmar el estado calculado*

Dado una inspección lista para publicar con estado calculado "Regular"

Cuando el evaluador confirma ese estado

Entonces el estado confirmado es igual al calculado

Y no se requiere una justificación adicional

*Escenario: Ajustar el estado con justificación*

Dado una inspección lista para publicar

Cuando el evaluador modifica el estado calculado

Y escribe una justificación

Entonces el sistema conserva el estado calculado automáticamente

Y conserva el estado confirmado por el evaluador

Y registra la justificación, el autor y la fecha del ajuste

*Escenario: Ajustar el estado sin justificación*

Dado una inspección con un estado calculado

Cuando el evaluador intenta modificarlo sin escribir una justificación

Entonces el sistema rechaza la operación con un error de validación

*Escenario: El valor numérico del IC no cambia*

Dado un IC calculado para una inspección

Cuando el evaluador modifica únicamente el estado confirmado

Entonces el valor numérico del IC permanece sin cambios

*Escenario: Ajustar un estado forzado por una condición crítica*

Dado un estado "Malo" forzado por una condición crítica de RN-IC-05

Cuando el evaluador modifica el estado confirmado

Entonces la justificación es obligatoria

Y el ajuste queda registrado en auditoría

*Escenario: Solo el evaluador responsable puede ajustar*

Dado un usuario que no es el autor responsable de la inspección

Cuando intenta modificar el estado confirmado

Entonces el sistema responde 403 Forbidden

*Escenario: Una inspección publicada no se ajusta*

Dado una inspección en estado PUBLICADA

Cuando un usuario intenta cambiar su estado confirmado

Entonces el sistema rechaza la operación con 409

Y cualquier corrección debe realizarse mediante una nueva inspección de corrección

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

3

### Sprint

Sprint 2

### Parent

Épica D — Índice de Condición

### Assignee

KENNY SALAZAR

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:35 PM

### Updated

18/Oct/26 4:36 PM

### Resolved

18/Oct/26 4:36 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 4:36 PM

### Watchers

1

### Votes

0

### Rank

0|i0005t:

---

## HU-021 — Administrador gestiona y publica versiones del formulario {#scrum-36}
**Jira:** [SCRUM-36](https://ayd1-g1-team.atlassian.net/browse/SCRUM-36)

### Issue Type

Story

### Description

*Historia*

Como Administrador, quiero gestionar y publicar versiones del Formulario SIECA definidas mediante JSON Schema, para modificar su estructura sin codificar las preguntas directamente en la aplicación y conservar la trazabilidad histórica de las inspecciones.

*Dependencias:* HU-016 — Modelar digitalmente el Formulario de Evaluación Técnica SIECA, finalizada en Sprint 1. HU-019 utiliza las versiones creadas en esta HU para asociar los pesos del Índice de Condición. HU-024 necesita consultar la versión activa para iniciar una inspección.

*Criterios de aceptación*

*Característica: Gestión y publicación de versiones del formulario*

*Escenario: Crear una nueva versión en borrador*

Dado un Administrador autenticado

Cuando crea una nueva versión del formulario

Entonces la versión queda registrada en estado BORRADOR

Y puede modificar su JSON Schema mientras no haya sido publicada

*Escenario: Consultar la versión activa*

Dado que existe una versión activa del formulario

Cuando un módulo solicita el formulario vigente

Entonces el sistema devuelve la versión activa

Y devuelve su identificador y el JSON Schema correspondiente

*Escenario: Solo existe una versión activa*

Dado una versión actualmente activa

Cuando el Administrador publica una nueva versión

Entonces la nueva versión pasa a ser la versión activa

Y la versión anterior deja de utilizarse para nuevas inspecciones

*Escenario: Una versión publicada es inmutable*

Dado una versión PUBLICADA

Cuando el Administrador intenta modificar su estructura

Entonces el sistema rechaza la operación

Y cualquier cambio debe realizarse mediante una nueva versión

*Escenario: Las inspecciones anteriores conservan su versión*

Dado una inspección creada con una versión anterior del formulario

Cuando se publica una versión nueva

Entonces la inspección conserva el identificador de la versión con la que fue creada

Y continúa siendo consultable utilizando la estructura original

*Escenario: Publicar una nueva versión con mapeo respecto de la anterior*

Dado una versión anterior publicada

Y una nueva versión en BORRADOR

Cuando el Administrador publica la nueva versión

Entonces puede declarar para cada campo su relación con la versión anterior como equivalente, renombrado, nuevo o eliminado

Y el mapeo queda almacenado para preservar la comparabilidad histórica

*Escenario: Versión inválida*

Dado una versión cuyo JSON Schema no es válido

Cuando el Administrador intenta publicarla

Entonces el sistema rechaza la publicación

Y muestra los errores encontrados

*Escenario: Publicación requiere configuración necesaria del IC*

Dado una versión con elementos evaluables

Cuando se intenta publicar sin la configuración necesaria de pesos o escala de severidad

Entonces el sistema rechaza la publicación

Y señala qué configuración está incompleta

*Escenario: Acceso restringido*

Dado un usuario con rol distinto de Administrador

Cuando intenta crear, modificar o publicar una versión del formulario

Entonces el sistema responde 403 Forbidden

*Escenario: Auditoría*

Cuando el Administrador crea o publica una versión del formulario

Entonces la operación queda registrada en auditoría

Y se conserva el usuario, fecha y cambio realizado

### Status

Finalizado

### Status category

Done

### Priority

Highest

### Story point estimate

8

### Sprint

Sprint 2

### Parent

Épica E — Formulario dinámico

### Assignee

Rony Rojas Aguilar

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:37 PM

### Updated

18/Oct/26 5:35 PM

### Resolved

18/Oct/26 5:35 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 5:35 PM

### Watchers

1

### Votes

0

### Rank

0|i00048:4

---

## HU-022 — Inspector utiliza el formulario generado dinámicamente {#scrum-37}
**Jira:** [SCRUM-37](https://ayd1-g1-team.atlassian.net/browse/SCRUM-37)

### Issue Type

Story

### Description

*Historia*

Como inspector, quiero utilizar un formulario generado dinámicamente a partir de la versión vigente del esquema SIECA, para completar una inspección sin que las preguntas y campos estén codificados directamente en la interfaz.

*Dependencias:* HU-021 — Administrador gestiona y publica versiones del formulario; HU-016 — Modelo digital del formulario SIECA realizado en Sprint 1. Esta HU proporciona el motor que luego utilizarán HU-025 para guardar el borrador y HU-030 para mostrar una inspección publicada en modo de solo lectura. Propuestas Sprints — SGP (1) Propuestas Sprints — SGP (1)

*Criterios de aceptación*

*Característica: Renderizado dinámico del formulario SIECA*

*Escenario: Cargar la versión correspondiente del formulario*

Dado una inspección asociada a una versión del formulario

Cuando el inspector abre la inspección

Entonces el sistema carga el JSON Schema correspondiente a esa versión

Y genera el formulario a partir de dicho esquema

*Escenario: El formulario no contiene preguntas SIECA codificadas en Angular*

Dado el motor de formulario dinámico

Cuando se modifica el JSON Schema agregando, modificando o eliminando un campo compatible

Entonces la interfaz refleja el cambio sin modificar el código del componente Angular

*Escenario: Mostrar las ocho secciones del formulario*

Dado la versión activa del formulario SIECA

Cuando el inspector abre una inspección

Entonces puede navegar por las ocho secciones definidas en el esquema

Y cada sección muestra únicamente los campos que le corresponden

*Escenario: Renderizar controles según el esquema*

Dado un campo definido en el JSON Schema

Cuando el formulario es generado

Entonces el sistema representa el control correspondiente según su tipo

Y respeta las opciones, restricciones y metadatos definidos por la versión

*Escenario: Campos obligatorios identificados*

Dado un campo marcado como obligatorio en el esquema

Cuando se muestra el formulario

Entonces el inspector puede identificar visualmente que el campo es requerido

*Escenario: Campos opcionales*

Dado un campo no marcado como obligatorio

Cuando se muestra el formulario

Entonces puede permanecer vacío durante el guardado de un borrador

*Escenario: Navegación sin pérdida de datos*

Dado un inspector que ha ingresado información en una sección

Cuando navega hacia otra sección y posteriormente regresa

Entonces conserva los valores introducidos durante la sesión

*Escenario: La versión queda fija para la inspección*

Dado una inspección iniciada con una versión determinada del formulario

Cuando posteriormente se publica una nueva versión

Entonces la inspección continúa utilizando la versión con la que fue creada

*Escenario: Formulario en modo edición*

Dado una inspección propia en estado BORRADOR

Cuando el inspector abre el formulario

Entonces puede modificar los campos permitidos por el esquema

*Escenario: Formulario en modo solo lectura*

Dado una inspección que debe consultarse sin editarse

Cuando se utiliza el motor del formulario

Entonces los mismos campos y secciones se muestran con la estructura de su versión original

Y no permiten modificaciones

*Escenario: Esquema no disponible o inválido*

Dado una inspección cuya versión del formulario no puede cargarse correctamente

Cuando se intenta abrir

Entonces el sistema muestra un error controlado

Y no genera un formulario parcial o inconsistente

*Escenario: Compatibilidad con los datos de la inspección*

Dado una inspección que ya tiene respuestas guardadas

Cuando el inspector vuelve a abrirla

Entonces el motor carga los valores existentes en los campos correspondientes

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

8

### Sprint

Sprint 2

### Parent

Épica E — Formulario dinámico

### Assignee

Herberth Julian Reyes Pacajoj

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:38 PM

### Updated

18/Oct/26 5:35 PM

### Resolved

18/Oct/26 5:35 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 5:35 PM

### Watchers

1

### Votes

0

### Rank

0|i00048:2

---

## HU-034 — Inspector registra daños mediante captura táctil {#scrum-38}
**Jira:** [SCRUM-38](https://ayd1-g1-team.atlassian.net/browse/SCRUM-38)

### Issue Type

Story

### Description

*Historia*

Como inspector, quiero registrar los daños de cada elemento mediante controles táctiles claros y rápidos, para completar la evaluación técnica del puente de forma práctica desde un dispositivo móvil o tableta.

*Dependencias:* HU-022 — Inspector utiliza el formulario generado dinámicamente. Utiliza la estructura de campos definida en la versión activa del formulario y sus valores serán validados posteriormente por HU-023.

*Criterios de aceptación*

*Característica: Captura táctil de daños*

*Escenario: Mostrar los elementos evaluables*

Dado una sección del formulario que contiene elementos sujetos a evaluación

Cuando el inspector abre dicha sección

Entonces el sistema muestra los elementos definidos por la versión del formulario

Y permite registrar su condición sin escribir manualmente valores técnicos internos

*Escenario: Registrar severidad de un daño*

Dado un elemento evaluable

Cuando el inspector selecciona una severidad permitida

Entonces el valor queda asociado al elemento correspondiente

Y puede modificarse mientras la inspección permanezca en BORRADOR

*Escenario: Marcar un elemento sin daños*

Dado un elemento que no presenta deterioro

Cuando el inspector selecciona "Sin daños en este elemento"

Entonces el sistema registra explícitamente que el elemento fue evaluado sin daños

Y no lo interpreta como un campo omitido

*Escenario: La opción sin daños limpia daños incompatibles*

Dado un elemento con daños previamente registrados

Cuando el inspector marca "Sin daños en este elemento"

Entonces el sistema solicita confirmación si corresponde

Y elimina o invalida las selecciones de daño incompatibles

*Escenario: Registrar varios elementos repetibles*

Dado una sección con elementos repetibles, como tramos o pilas

Cuando el inspector registra daños para cada instancia

Entonces cada evaluación queda asociada a la instancia correcta

*Escenario: Navegación táctil*

Dado un inspector utilizando un dispositivo táctil

Cuando interactúa con los controles de daño

Entonces puede seleccionar las opciones sin depender de entrada precisa de teclado

Y los controles tienen un tamaño adecuado para interacción táctil

*Escenario: Persistencia durante la sesión*

Dado un inspector que ha registrado daños

Cuando cambia de sección y posteriormente regresa

Entonces las selecciones realizadas permanecen visibles

*Escenario: Integración con el formulario dinámico*

Dado una versión del formulario con distinta cantidad o estructura de elementos evaluables

Cuando el formulario es renderizado

Entonces la captura de daños utiliza los elementos definidos en esa versión

Y no una lista de daños codificada directamente en Angular

*Escenario: Datos listos para el cálculo del IC*

Dado una evaluación de daños completada

Cuando los datos se entregan al backend

Entonces cada elemento conserva la información necesaria para que el servicio de IC pueda interpretar su evaluación

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

5

### Sprint

Sprint 2

### Parent

Épica E — Formulario dinámico

### Assignee

Herberth Julian Reyes Pacajoj

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:41 PM

### Updated

18/Oct/26 5:35 PM

### Resolved

18/Oct/26 5:35 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 5:35 PM

### Watchers

1

### Votes

0

### Rank

0|i00048:0i

---

## HU-023 — Sistema valida las respuestas contra la versión del formulario {#scrum-39}
**Jira:** [SCRUM-39](https://ayd1-g1-team.atlassian.net/browse/SCRUM-39)

### Issue Type

Story

### Description

*Historia*

Como Sistema, quiero validar las respuestas de una inspección contra la versión del formulario con la que fue creada, tanto en el cliente como en el servidor, para impedir que se guarden o publiquen datos incompatibles con su estructura.

*Dependencias:* HU-021 — Gestión y publicación de versiones del formulario; HU-022 — Motor de formulario dinámico. HU-025 utiliza esta validación durante el guardado y HU-026 la utiliza antes de publicar.

*Criterios de aceptación*

*Característica: Validación de respuestas mediante JSON Schema*

*Escenario: Validar utilizando la versión correcta*

Dado una inspección asociada a una versión específica del formulario

Cuando se validan sus respuestas

Entonces el sistema utiliza el JSON Schema de esa misma versión

Y no la versión activa actual si son diferentes

*Escenario: Validación en el cliente*

Dado un campo cuyo valor no cumple las restricciones del esquema

Cuando el inspector lo completa

Entonces la interfaz muestra un mensaje de validación comprensible

Y permite corregir el valor antes de continuar

*Escenario: El backend vuelve a validar*

Dado datos que fueron enviados desde el frontend

Cuando el backend recibe la solicitud

Entonces valida nuevamente los datos contra el JSON Schema correspondiente

Y no confía únicamente en la validación del cliente

*Escenario: Campo obligatorio faltante al publicar*

Dado una inspección con uno o más campos obligatorios incompletos

Cuando se intenta publicar

Entonces el backend rechaza la operación

Y devuelve el detalle de los campos faltantes

*Escenario: Tipo de dato incorrecto*

Dado un campo definido como numérico, enumerado o booleano

Cuando se envía un valor incompatible con su tipo

Entonces el backend rechaza dicho valor

*Escenario: Valor fuera de restricciones*

Dado un campo con límites o valores permitidos definidos en el esquema

Cuando se envía un valor fuera de dichas restricciones

Entonces la validación falla

Y el sistema identifica el campo afectado

*Escenario: Guardado parcial de un borrador*

Dado una inspección en BORRADOR

Cuando existen campos obligatorios todavía sin completar

Entonces se permite guardar los datos válidos ingresados

Y los campos pendientes permanecen identificados como incompletos

*Escenario: Respuesta de error estándar*

Dado uno o más errores de validación detectados por el backend

Cuando la solicitud es rechazada

Entonces se devuelve una respuesta en formato RFC 7807

Y se incluye la lista de errores correspondiente

*Escenario: Versión publicada permanece reproducible*

Dado una inspección creada con una versión anterior del formulario

Cuando posteriormente existe una versión nueva

Entonces la inspección continúa validándose con su versión original

*Escenario: Esquema inexistente o inválido*

Dado una inspección cuya versión no puede ser recuperada o cuyo esquema es inválido

Cuando se intenta validar

Entonces la operación se rechaza de forma controlada

Y no se guardan datos inconsistentes

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

5

### Sprint

Sprint 2

### Parent

Épica E — Formulario dinámico

### Assignee

Rony Rojas Aguilar

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:42 PM

### Updated

18/Oct/26 5:35 PM

### Resolved

18/Oct/26 5:35 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 5:35 PM

### Watchers

1

### Votes

0

### Rank

0|i00048:1

---

## HU-024 — Inspector inicia una inspección de un puente {#scrum-40}
**Jira:** [SCRUM-40](https://ayd1-g1-team.atlassian.net/browse/SCRUM-40)

### Issue Type

Story

### Description

La distribución acordada es importante: *Angel y Dany* trabajan HU-024, HU-025, HU-026 y HU-031; *Mushin y Melvin* toman HU-030 para equilibrar la carga, aunque HU-030 siga perteneciendo a la Épica F. HU-027 es estirado. Propuestas Sprints — SGP (1)

----

h1. Épica F — Inspecciones

Si todavía no la creaste:

{noformat}Tipo:

Épica

Resumen:

Épica F — Inspecciones{noformat}

*Descripción:*

{quote}Implementación del ciclo completo de inspección técnica en línea, desde la creación de una inspección en BORRADOR hasta su publicación, incluyendo captura de datos, máquina de estados, consulta de inspecciones publicadas y gestión de las inspecciones propias del inspector.{quote}

Sin Story Points.

----

h1. HU-024 — Inspector inicia una inspección de un puente

Esta es de las más importantes del Sprint.

h3. Configuración en Jira

{noformat}Tipo:

Historia

Resumen:

HU-024 — Inspector inicia una inspección de un puente

Principal:

Épica F — Inspecciones

Sprint:

Sprint 2

Prioridad:

Highest

Etiquetas:

Catedrático

ProfesionalExterno

Story point estimate:

8

Estado:

Por hacer

Persona asignada:

Angel o Dany{noformat}

El documento establece exactamente estos 8 puntos y dependencias de HU-021, HU-009, HU-015 y las cuentas verificadas creadas por HU-001/HU-002. Propuestas Sprints — SGP (1)

h3. Descripción

*Historia*

Como Catedrático o Profesional externo verificado, quiero iniciar una inspección sobre un puente activo, para evaluar su condición técnica utilizando el formulario vigente (RN-INS-02, RN-INS-03, RN-INS-09, RN-INS-10, RN-INS-12, RN-INS-13, RN-INS-14, RN-FRM-02, RN-FRM-04).

*Dependencias:* HU-021 — versión activa del formulario; HU-009 — registro de puentes; HU-015 — ficha del puente desde donde se inicia la inspección; cuentas verificadas provenientes de HU-001 y HU-002.

*Criterios de aceptación*

*Característica: Inicio de una inspección*

*Escenario: Inicio exitoso*

Dado un Catedrático o un Profesional externo verificado

Y un puente activo

Cuando inicia una inspección

Entonces se crea una inspección en estado {{BORRADOR}}

Y se registra el autor con su rol y nombre

Y se registra la fecha y hora de creación

Y la versión del formulario

Y el identificador del dispositivo

Y las coordenadas GPS disponibles

*Escenario: Solo se inspeccionan puentes activos*

Dado un puente inactivo

Cuando un inspector intenta iniciar una inspección

Entonces el sistema responde 409

Y no se crea la inspección

*Escenario: Catedrático autorizado*

Dado un usuario con rol Catedrático

Cuando intenta iniciar una inspección sobre un puente activo

Entonces la operación es permitida

*Escenario: Profesional externo verificado autorizado*

Dado un Profesional externo verificado

Cuando intenta iniciar una inspección sobre un puente activo

Entonces la operación es permitida

*Escenario: Profesional externo sin verificar*

Dado un Profesional externo sin verificar

Cuando intenta iniciar una inspección

Entonces el sistema responde 403 Forbidden

*Escenario: Estudiante durante Sprint 2*

Dado un usuario Estudiante

Cuando intenta iniciar una inspección

Entonces el sistema responde 403 Forbidden

*Escenario: Administrador no inspecciona*

Dado un usuario Administrador

Cuando intenta iniciar una inspección

Entonces el sistema responde 403 Forbidden

*Escenario: Visitante sin sesión*

Dado un Visitante sin autenticación

Cuando intenta iniciar una inspección

Entonces el sistema responde 401 Unauthorized

*Escenario: Autor único y acompañantes*

Dado una inspección en curso

Cuando el autor registra uno o varios acompañantes

Entonces la responsabilidad del registro sigue perteneciendo únicamente al autor

*Escenario: Fecha válida de inspección*

Cuando se introduce una fecha futura

O una fecha anterior al año de construcción registrado del puente

Entonces el sistema rechaza la fecha con un error de validación

*Escenario: Datos permanentes precargados*

Dado un puente con una inspección publicada anteriormente

Cuando se inicia una nueva inspección

Entonces se precargan dimensiones, materiales, tipología, número de tramos y año de construcción desde la última inspección publicada

Y el inspector debe confirmarlos explícitamente antes de continuar

*Escenario: Los daños nunca se precargan*

Dado un puente con una inspección anterior

Cuando se inicia una nueva inspección

Entonces grietas, socavación, oxidación, asentamiento y demás campos de daño comienzan vacíos

*Escenario: Primera inspección del puente*

Dado un puente sin inspecciones publicadas

Cuando se inicia una inspección

Entonces los datos permanentes comienzan vacíos

Y deben ser capturados y confirmados por el inspector

*Escenario: GPS lejano*

Dado las coordenadas del dispositivo

Cuando se encuentran a más de 500 metros de las coordenadas registradas del puente

Entonces el sistema muestra una advertencia

Pero no bloquea la inspección

Y registra ubicación, precisión y distancia calculada

*Escenario: GPS no disponible*

Dado un dispositivo sin permiso o señal de ubicación

Cuando se inicia la inspección

Entonces la inspección puede crearse sin ubicación

Y queda registrado que no se obtuvo GPS

*Escenario: La versión del formulario queda fija*

Dado una inspección iniciada con determinada versión del formulario

Cuando posteriormente el Administrador publica una versión nueva

Entonces la inspección conserva la versión con la que fue creada

*Escenario: Idempotencia*

Dado una petición con un UUID generado por el cliente y una {{Idempotency-Key}}

Cuando se repite la misma petición

Entonces se devuelve la misma inspección

Y no se crea un duplicado

*Escenario: Auditoría*

Cuando se crea una inspección

Entonces se registra en auditoría el usuario, la fecha y los valores iniciales.

### Status

Finalizado

### Status category

Done

### Priority

Highest

### Story point estimate

8

### Sprint

Sprint 2

### Parent

Épica F — Inspecciones

### Assignee

Dany Noe DeLeon Santizo

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:44 PM

### Updated

18/Oct/26 5:35 PM

### Resolved

18/Oct/26 5:35 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 5:35 PM

### Last Viewed

16/Oct/26 7:21 AM

### Watchers

1

### Votes

0

### Rank

0|i00048:09

---

## HU-025 — Inspector completa y guarda una inspección en borrador {#scrum-41}
**Jira:** [SCRUM-41](https://ayd1-g1-team.atlassian.net/browse/SCRUM-41)

### Issue Type

Story

### Description

*Historia*

Como inspector, quiero completar el formulario por partes y guardarlo como borrador, para poder continuar posteriormente hasta que toda la información de la inspección esté lista (RN-INS-03, RN-INS-05, RN-IC-07; DT-BD-02, DT-ARQ-06).

*Dependencias:* HU-022 — formulario dinámico; HU-023 — validación; HU-024 — creación de la inspección; HU-034 — captura de daños.

*Criterios de aceptación*

*Característica: Borrador de inspección*

*Escenario: Guardado parcial*

Dado una inspección en estado {{BORRADOR}}

Cuando el inspector guarda aun teniendo campos obligatorios sin completar

Entonces se guardan todos los datos válidos ingresados

Y los campos incompletos permanecen pendientes

*Escenario: Retomar un borrador*

Dado una inspección previamente guardada

Cuando su autor vuelve a abrirla

Entonces ve toda la información registrada anteriormente

Y puede continuar desde cualquier sección

*Escenario: Indicador de avance*

Cuando el inspector consulta su borrador

Entonces visualiza el avance de cada una de las ocho secciones

Y el total de campos obligatorios completados

*Escenario: Un borrador es privado*

Dado una inspección en {{BORRADOR}}

Cuando intenta consultarla un usuario distinto de su autor o del Catedrático autorizado

Entonces el sistema responde 404

Y el borrador no aparece públicamente en mapas, reportes o foros

*Escenario: IC preliminar*

Dado una inspección con información suficiente para calcular parcialmente el IC

Cuando el inspector guarda el avance

Entonces el backend calcula un IC

Y la interfaz lo muestra claramente como {{Preliminar}}

Y el estado vigente del puente no cambia

*Escenario: Solo se edita en BORRADOR*

Dado una inspección en cualquier estado diferente de {{BORRADOR}}

Cuando se intenta modificar su información

Entonces el sistema responde 409

*Escenario: Solo el autor puede editar*

Dado un usuario distinto del autor

Cuando intenta modificar el borrador

Entonces el sistema responde 403 Forbidden

*Escenario: Conflicto de edición*

Dado un borrador que fue actualizado desde otra pestaña o dispositivo

Cuando se intenta guardar una versión desactualizada

Entonces el sistema responde 409

Y no sobrescribe silenciosamente la información nueva

*Escenario: Fecha inválida al editar*

Cuando el inspector modifica la fecha a una fecha futura

O anterior al año de construcción registrado

Entonces el sistema rechaza el cambio

*Escenario: Registro de actividad*

Cuando el borrador se guarda correctamente

Entonces se actualiza la fecha de última actividad de la inspección.

### Status

Finalizado

### Status category

Done

### Priority

Highest

### Story point estimate

5

### Sprint

Sprint 2

### Parent

Épica F — Inspecciones

### Assignee

Dany Noe DeLeon Santizo

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:45 PM

### Updated

18/Oct/26 5:35 PM

### Resolved

18/Oct/26 5:35 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 5:35 PM

### Last Viewed

16/Oct/26 2:45 PM

### Watchers

1

### Votes

0

### Rank

0|i00048:04

---

## HU-026 — Sistema controla la máquina de estados y publica la inspección {#scrum-42}
**Jira:** [SCRUM-42](https://ayd1-g1-team.atlassian.net/browse/SCRUM-42)

### Issue Type

Story

### Description

*Historia*

Como Sistema, quiero controlar las transiciones válidas de una inspección desde una única puerta de entrada, para evitar que una inspección alcance estados inconsistentes y garantizar una publicación válida (RN-INS-02, RN-INS-04, RN-INS-06, RN-INS-07, RN-INS-13, RN-REV-02; DT-BE-07).

*Dependencias:* HU-025 — borrador; HU-028 y HU-029 — fotografías; HU-018 — servicio de cálculo del IC; HU-020 — integración del IC con la publicación.

*Criterios de aceptación*

*Característica: Máquina de estados y publicación*

*Escenario: Enviar inspección*

Dado una inspección en {{BORRADOR}}

Cuando se ejecuta la acción enviar

Entonces pasa a {{ENVIADA}}

*Escenario: Iniciar revisión*

Dado una inspección {{ENVIADA}}

Cuando se inicia su revisión

Entonces pasa a {{EN_REVISION}}

*Escenario: Aprobar revisión*

Dado una inspección {{EN_REVISION}}

Cuando se aprueba

Entonces pasa a {{PUBLICADA}}

*Escenario: Solicitar cambios*

Dado una inspección {{EN_REVISION}}

Cuando se solicitan cambios

Entonces pasa a {{CAMBIOS_SOLICITADOS}}

*Escenario: Rechazar*

Dado una inspección {{EN_REVISION}}

Cuando se rechaza

Entonces pasa a {{RECHAZADA}}

*Escenario: Devolver para corrección*

Dado una inspección {{CAMBIOS_SOLICITADOS}}

Cuando vuelve al inspector

Entonces regresa a {{BORRADOR}}

*Escenario: Transición inválida*

Dado cualquier estado de una inspección

Cuando se intenta realizar una transición no permitida

Entonces el sistema responde 409

Y conserva el estado actual

*Escenario: Estados terminales protegidos*

Dado una inspección {{PUBLICADA}} o {{RECHAZADA}}

Cuando se intenta realizar una transición no permitida

Entonces el sistema responde 409

*Escenario: Única puerta de entrada*

Dado el backend

Cuando cambia el estado de una inspección

Entonces ningún controlador modifica el estado directamente

Y todos los cambios pasan por el servicio de transiciones

*Escenario: Publicación de Catedrático o Profesional externo*

Dado una inspección en {{BORRADOR}} cuyo autor es Catedrático o Profesional externo verificado

Cuando el autor la publica

Entonces pasa por {{ENVIADA}} y finaliza {{PUBLICADA}} en una misma operación

Sin requerir revisión humana previa

Y quedan registradas las fechas de envío y publicación

*Escenario: Estados de revisión disponibles*

Aunque las pantallas de revisión se implementen en Sprint 3

Entonces {{EN_REVISION}}, {{CAMBIOS_SOLICITADOS}} y {{RECHAZADA}} existen desde Sprint 2

Y sus transiciones están probadas

*Escenario: Campos requeridos para publicar*

Dado una inspección con campos obligatorios incompletos

Cuando el autor intenta publicarla

Entonces el sistema responde 422

Y detalla qué campos faltan

Y la inspección permanece en {{BORRADOR}}

*Escenario: Fotografías requeridas para publicar*

Dado una inspección sin evidencia fotográfica mínima

Cuando se intenta publicar

Entonces el sistema responde 422

Y detalla qué categorías de fotografía faltan

*Escenario: Publicación atómica*

Cuando una inspección se publica

Entonces se calcula el IC definitivo

Y se actualiza el estado derivado del puente

Y si cualquiera de esas operaciones falla, no queda información parcialmente publicada

*Escenario: Inspección publicada inmutable*

Dado una inspección {{PUBLICADA}}

Cuando alguien intenta modificarla, eliminarla o eliminar sus fotografías

Entonces el sistema responde 409

*Escenario: Solo el autor puede publicar*

Dado un usuario distinto del autor

Cuando intenta enviar o publicar una inspección

Entonces el sistema responde 403 Forbidden

*Escenario: Publicaciones simultáneas*

Dado dos solicitudes simultáneas para publicar la misma inspección

Cuando ambas son procesadas

Entonces solamente una produce la publicación

Y la segunda recibe 409

*Escenario: Historial de estados*

Cuando cambia el estado de una inspección

Entonces se registra usuario

Fecha

Estado anterior

Y estado posterior

*Escenario: Cobertura de máquina de estados*

Dado la suite de pruebas

Cuando se ejecuta

Entonces todas las transiciones válidas e inválidas están cubiertas

Y la máquina de estados alcanza 100 % de cobertura.

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

8

### Sprint

Sprint 2

### Parent

Épica F — Inspecciones

### Assignee

Angel Abelino Son Vicente

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:46 PM

### Updated

18/Oct/26 5:35 PM

### Resolved

18/Oct/26 5:35 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 5:35 PM

### Last Viewed

16/Oct/26 7:21 AM

### Watchers

1

### Votes

0

### Rank

0|i00048:9

---

## HU-030 — Usuario autenticado consulta una inspección publicada {#scrum-43}
**Jira:** [SCRUM-43](https://ayd1-g1-team.atlassian.net/browse/SCRUM-43)

### Issue Type

Story

### Description

*Historia*

Como usuario autenticado, quiero consultar el detalle de una inspección publicada de un puente, con sus datos, fotografías e Índice de Condición, para conocer su condición y consultar la evidencia técnica que respalda la evaluación (RN-INS-01, RN-INS-05, RN-INS-08, RN-INS-13, RN-FRM-05, RN-USR-10; DT-SEC-09).

*Dependencias:* HU-026 — publicación; HU-028 — fotografías; HU-022 — motor del formulario para modo solo lectura.

*Criterios de aceptación*

*Característica: Consulta de una inspección publicada*

*Escenario: Historial de inspecciones del puente*

Dado un puente con inspecciones publicadas

Cuando un usuario autenticado abre su ficha

Entonces visualiza el listado paginado de inspecciones publicadas

Ordenadas desde la más reciente hasta la más antigua

Con autor, fecha y estado

*Escenario: Detalle conserva la estructura original*

Dado una inspección publicada con una versión determinada del formulario

Cuando un usuario abre el detalle

Entonces se muestra utilizando la estructura de esa versión

Y no la versión actualmente activa

*Escenario: Solo lectura*

Dado una inspección {{PUBLICADA}}

Cuando un usuario autorizado consulta su detalle

Entonces toda la información aparece en modo de solo lectura

*Escenario: Mostrar IC y estado*

Cuando se abre el detalle

Entonces se muestran el IC y el estado con etiqueta textual

*Escenario: Estado ajustado*

Si el estado fue ajustado mediante criterio profesional

Entonces se muestran el estado calculado, estado confirmado y justificación correspondiente cuando aplique

*Escenario: Fotografías protegidas*

Cuando el usuario consulta las fotografías

Entonces visualiza miniaturas

Y las fotografías se sirven mediante URL firmada temporal o endpoint autorizado

*Escenario: Metadatos de la inspección*

Cuando se abre el detalle

Entonces se muestran autor

Versión del formulario

Fecha de creación

Fecha de envío

Fecha de publicación

Y dispositivo utilizado

*Escenario: Visitante no autorizado*

Dado un visitante sin sesión

Cuando intenta consultar el detalle o una fotografía

Entonces recibe 401 Unauthorized

*Escenario: Los borradores ajenos no aparecen*

Dado un borrador perteneciente a otro inspector

Cuando un usuario intenta acceder mediante el flujo de consulta pública

Entonces recibe 404

*Escenario: Inspección superada por una corrección*

Dado una inspección publicada que posteriormente fue reemplazada mediante una corrección

Cuando se consulta la original

Entonces aparece visualmente marcada como {{Superada}}

Y existe un enlace hacia la inspección que la sustituyó.

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

5

### Sprint

Sprint 2

### Parent

Épica F — Inspecciones

### Assignee

Kevin Mushin

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:46 PM

### Updated

18/Oct/26 5:35 PM

### Resolved

18/Oct/26 5:35 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 5:35 PM

### Watchers

1

### Votes

0

### Rank

0|i00048:009

---

## HU-031 — Inspector gestiona sus inspecciones {#scrum-44}
**Jira:** [SCRUM-44](https://ayd1-g1-team.atlassian.net/browse/SCRUM-44)

### Issue Type

Story

### Description

*Historia*

Como inspector, quiero ver mis inspecciones, iniciar una nueva desde un puente activo, retomar un borrador y eliminar los borradores que ya no necesito, para llevar el control de mi trabajo (RN-INS-01, RN-INS-15, RN-INS-04; DT-BD-11).

*Dependencias:* HU-024 — inicio de inspección; HU-025 — guardado y edición de borradores.

*Criterios de aceptación*

*Característica: Mis inspecciones*

*Escenario: Lista de mis inspecciones*

Dado un inspector con inspecciones en diferentes estados

Cuando abre {{Mis inspecciones}}

Entonces visualiza únicamente sus propias inspecciones

Y la lista está paginada

Y puede filtrar por estado

*Escenario: Estado visible*

Cuando se muestra cada inspección

Entonces su estado aparece mediante una etiqueta textual

*Escenario: Acciones según estado*

Dado una inspección en determinado estado

Cuando aparece en la lista

Entonces únicamente se muestran las acciones permitidas para ese estado

*Escenario: Continuar un borrador*

Dado un borrador propio

Cuando el inspector lo abre

Entonces puede retomar el formulario exactamente con los datos previamente guardados

*Escenario: Iniciar desde la ficha del puente*

Dado un inspector autorizado consultando un puente activo

Cuando visualiza sus acciones

Entonces aparece la opción {{Iniciar inspección}}

*Escenario: Acción no visible a usuarios no autorizados*

Dado un Visitante, Estudiante o Administrador

Cuando consulta la ficha del puente

Entonces no se muestra la acción {{Iniciar inspección}}

*Escenario: Buscar puente para inspeccionar*

Cuando el inspector busca por nombre, código o departamento

Entonces puede seleccionar un puente activo e iniciar una nueva inspección

*Escenario: Eliminar un borrador propio*

Dado un borrador propio

Cuando el inspector decide eliminarlo

Entonces se realiza una eliminación lógica

Y deja de aparecer en sus listas activas

*Escenario: No eliminar inspecciones publicadas*

Dado una inspección {{PUBLICADA}}

Cuando se intenta eliminar

Entonces el sistema responde 409

*Escenario: No acceder a borradores ajenos*

Dado un borrador de otro usuario

Cuando el inspector intenta verlo o eliminarlo

Entonces el sistema responde 404.

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

3

### Sprint

Sprint 2

### Parent

Épica F — Inspecciones

### Assignee

Dany Noe DeLeon Santizo

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:47 PM

### Updated

18/Oct/26 5:35 PM

### Resolved

18/Oct/26 5:35 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 5:35 PM

### Last Viewed

16/Oct/26 7:09 AM

### Watchers

1

### Votes

0

### Rank

0|i00048:02

---

## HU-027 — Inspector genera una corrección de una inspección publicada {#scrum-45}
**Jira:** [SCRUM-45](https://ayd1-g1-team.atlassian.net/browse/SCRUM-45)

### Issue Type

Story

### Description

*Historia*

Como inspector, quiero corregir un error de una inspección publicada mediante una nueva inspección relacionada, para conservar intacta la evidencia histórica de la inspección original (RN-INS-07, RN-INS-08, RN-INV-09).

*Dependencias:* HU-026 — publicación de inspecciones; HU-030 — consulta de inspecciones publicadas.

*Criterios de aceptación*

*Característica: Corrección de una inspección publicada*

*Escenario: Crear una corrección*

Dado una inspección {{PUBLICADA}}

Cuando su autor o un Catedrático inicia una corrección indicando el motivo

Entonces se crea una nueva inspección de tipo {{CORRECCION}}

Y queda en estado {{BORRADOR}}

Y queda relacionada con la original mediante {{supersede_a}}

*Escenario: Motivo obligatorio*

Cuando se intenta crear una corrección sin indicar motivo

Entonces el sistema rechaza la operación

*Escenario: La inspección original no cambia*

Dado una corrección en {{BORRADOR}}

Cuando se consulta la inspección original

Entonces sigue {{PUBLICADA}}

Y permanece intacta

*Escenario: La corrección sigue el flujo normal*

Dado una corrección en {{BORRADOR}}

Cuando se completa y publica

Entonces debe cumplir las mismas validaciones que una inspección normal

Incluyendo campos obligatorios y fotografías mínimas

*Escenario: Original superada*

Dado una corrección {{PUBLICADA}}

Cuando se consulta la inspección original

Entonces aparece marcada como {{Superada}}

Y enlaza hacia la corrección publicada

*Escenario: La corrección actualiza el estado del puente*

Dado una corrección publicada

Cuando se consulta el puente

Entonces su estado, IC y fecha vigente provienen de la inspección publicada más reciente

*Escenario: Una sola corrección publicada*

Dado una inspección que ya tiene una corrección publicada

Cuando se intenta publicar otra corrección de esa misma inspección

Entonces el sistema responde 409

*Escenario: Usuario no autorizado*

Dado un usuario que no es el autor ni Catedrático

Cuando intenta crear una corrección

Entonces el sistema responde 403 Forbidden

*Escenario: Un borrador no se corrige*

Dado una inspección en {{BORRADOR}}

Cuando se intenta crear una corrección

Entonces el sistema responde 409

Porque el borrador debe editarse directamente

*Escenario: Auditoría de la corrección*

Cuando se crea una corrección

Entonces se registra el usuario

Fecha

Motivo

Y la inspección original relacionada.

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

5

### Sprint

Sprint 2

### Parent

Épica F — Inspecciones

### Assignee

Angel Abelino Son Vicente

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:49 PM

### Updated

18/Oct/26 5:35 PM

### Resolved

18/Oct/26 5:35 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 5:35 PM

### Last Viewed

16/Oct/26 7:21 AM

### Watchers

1

### Votes

0

### Rank

0|i00048:i

---

## HU-028 — Inspector adjunta fotografías a una inspección {#scrum-46}
**Jira:** [SCRUM-46](https://ayd1-g1-team.atlassian.net/browse/SCRUM-46)

### Issue Type

Story

### Description

*Historia*

Como inspector, quiero tomar o seleccionar fotografías, clasificarlas y asociarlas a mi inspección, para documentar visualmente el estado del puente y los daños observados (RN-ARC-01, RN-ARC-02, RN-ARC-03; DT-ALM-01 a DT-ALM-04, DT-OFF-08, DT-SEC-08).

*Dependencias:* HU-024 — la inspección debe existir antes de asociar fotografías; esquema de archivos existente en la base de datos. El servicio creado en esta HU también será utilizado por HU-019 para almacenar el acta de calibración.

*Criterios de aceptación*

*Característica: Fotografías de la inspección*

*Escenario: Capturar una fotografía*

Dado una inspección propia en estado {{BORRADOR}}

Cuando el inspector toma una fotografía utilizando la cámara del dispositivo

Entonces la fotografía queda asociada a la inspección

*Escenario: Seleccionar una fotografía de la galería*

Dado una inspección propia en {{BORRADOR}}

Cuando el inspector selecciona una fotografía disponible en el dispositivo

Entonces la fotografía puede ser incorporada a la inspección

*Escenario: Compresión en el dispositivo*

Cuando el inspector adjunta una fotografía

Entonces la imagen se comprime antes de ser enviada

Y el lado mayor no supera 1600 px

Y se almacena en formato WebP o JPEG

Y se busca un tamaño aproximado entre 200 y 400 KB

*Escenario: Clasificar fotografía como acceso*

Dado una fotografía

Cuando el inspector selecciona la categoría {{ACCESO}}

Entonces el sistema acepta la clasificación

*Escenario: Clasificar fotografía como superestructura*

Dado una fotografía

Cuando selecciona {{SUPERESTRUCTURA}}

Entonces el sistema acepta la clasificación

*Escenario: Clasificar fotografía como subestructura*

Dado una fotografía

Cuando selecciona {{SUBESTRUCTURA}}

Entonces el sistema acepta la clasificación

*Escenario: Clasificar fotografía como cauce*

Dado una fotografía

Cuando selecciona {{CAUCE}}

Entonces el sistema acepta la clasificación

*Escenario: Clasificar fotografía adicional*

Dado una fotografía que no corresponde a las cuatro categorías mínimas

Cuando selecciona {{OTRA}}

Entonces puede guardarla como evidencia adicional

*Escenario: Fotografía sin categoría*

Dado una fotografía

Cuando el inspector intenta adjuntarla sin seleccionar una categoría

Entonces el sistema rechaza la operación con un error de validación

*Escenario: Anclar fotografía a un elemento*

Dado una fotografía tomada desde un grupo de daño específico

Cuando se guarda

Entonces puede quedar relacionada con ese elemento mediante {{elemento_ref}}

*Escenario: Fotografía general sin elemento asociado*

Dado una fotografía agregada desde la sección general de fotografías

Cuando se guarda

Entonces puede quedar asociada únicamente a la inspección sin {{elemento_ref}}

*Escenario: Conservar geolocalización y fecha*

Cuando se guarda una fotografía

Entonces se conserva su ubicación disponible

Y su fecha y hora de captura

Aunque otros metadatos EXIF puedan eliminarse

*Escenario: Fotografía sin ubicación disponible*

Dado un dispositivo sin información de ubicación para la fotografía

Cuando el inspector intenta subirla

Entonces el sistema rechaza la operación

Y muestra un mensaje indicando que se requiere ubicación y fecha de captura

*Escenario: Crear miniatura*

Cuando una fotografía se almacena correctamente

Entonces se conserva el original comprimido

Y se genera una miniatura de aproximadamente 300 px

*Escenario: Abstracción de almacenamiento*

Dado el backend

Cuando almacena o recupera un archivo

Entonces utiliza la interfaz {{AlmacenamientoArchivos}}

Y el dominio no depende directamente de MinIO ni del sistema de archivos

*Escenario: Almacenamiento intercambiable*

Dado la implementación de {{AlmacenamientoArchivos}}

Cuando se utiliza MinIO o un sistema de archivos local

Entonces el comportamiento de las clases de dominio no cambia

*Escenario: Nombre generado por el servidor*

Cuando un usuario envía una fotografía con cualquier nombre de archivo

Entonces el servidor genera el nombre con el que será almacenada

Y no confía en el nombre proporcionado por el cliente

*Escenario: Solo el autor puede agregar fotografías*

Dado una inspección perteneciente a otro usuario

Cuando alguien intenta subir una fotografía

Entonces el sistema impide la operación

*Escenario: Solo se agregan fotografías en BORRADOR*

Dado una inspección en un estado distinto de {{BORRADOR}}

Cuando se intenta agregar una fotografía

Entonces el sistema responde con el error correspondiente

*Escenario: Evitar fotografías duplicadas por reintento*

Dado una fotografía con UUID generado por el cliente y una {{Idempotency-Key}}

Cuando la misma petición de subida se repite

Entonces la fotografía no se almacena dos veces

*Escenario: Límite de tamaño de la petición*

Cuando se envía una petición de archivos que supera 25 MB

Entonces el servidor rechaza la solicitud

Y devuelve un mensaje comprensible

*Escenario: Soporte para acta de calibración*

Dado el uso {{ACTA_CALIBRACION}}

Cuando el Administrador sube un PDF de hasta 20 MB para HU-019

Entonces el servicio valida el archivo

Y lo asocia a la versión correspondiente del formulario.

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

8

### Sprint

Sprint 2

### Parent

Épica G — Archivo básico

### Assignee

Melvin Ordoñez

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:52 PM

### Updated

18/Oct/26 5:35 PM

### Resolved

18/Oct/26 5:35 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 5:35 PM

### Watchers

1

### Votes

0

### Rank

0|i00048:01

---

## HU-029 — Sistema valida la evidencia fotográfica y la seguridad de los archivos {#scrum-47}
**Jira:** [SCRUM-47](https://ayd1-g1-team.atlassian.net/browse/SCRUM-47)

### Issue Type

Story

### Description

*Historia*

Como Sistema, quiero validar que una inspección posea la evidencia fotográfica mínima requerida y que los archivos sean recibidos, almacenados y servidos de forma segura, para impedir la publicación de inspecciones incompletas y evitar la exposición de información protegida (RN-INS-06, RN-ARC-04, RN-ARC-05, RN-USR-10; DT-SEC-08, DT-SEC-09, DT-CAL-03).

*Dependencias:* HU-028 — carga y almacenamiento de fotografías. HU-026 utiliza esta validación antes de permitir la publicación de una inspección.

*Criterios de aceptación*

*Característica: Evidencia fotográfica y seguridad de archivos*

*Escenario: Falta fotografía de accesos*

Dado una inspección sin una fotografía clasificada como {{ACCESO}}

Cuando se intenta enviar o publicar

Entonces el sistema rechaza la operación con 422

Y señala que falta la categoría {{ACCESO}}

*Escenario: Falta fotografía de superestructura*

Dado una inspección sin fotografía {{SUPERESTRUCTURA}}

Cuando se intenta publicar

Entonces se rechaza

Y se informa la categoría faltante

*Escenario: Falta fotografía de subestructura*

Dado una inspección sin fotografía {{SUBESTRUCTURA}}

Cuando se intenta publicar

Entonces se rechaza

Y se informa la categoría faltante

*Escenario: Falta fotografía de cauce*

Dado una inspección sin fotografía {{CAUCE}}

Cuando se intenta publicar

Entonces se rechaza

Y se informa la categoría faltante

*Escenario: Evidencia mínima completa*

Dado al menos una fotografía de {{ACCESO}}, {{SUPERESTRUCTURA}}, {{SUBESTRUCTURA}} y {{CAUCE}}

Cuando se valida la evidencia

Entonces el requisito mínimo fotográfico se considera cumplido

*Escenario: Máximo de fotografías*

Dado una inspección que ya tiene 60 fotografías

Cuando se intenta agregar la fotografía número 61

Entonces el sistema responde 422

Y no almacena la nueva fotografía

*Escenario: Tipo real del archivo*

Dado un archivo llamado {{foto.jpg}} cuyo contenido real no corresponde a JPEG ni WebP

Cuando se intenta subir

Entonces el servidor lo rechaza

Y no almacena el archivo

*Escenario: MIME validado por contenido*

Cuando se recibe un archivo

Entonces el backend determina el tipo real mediante su firma binaria

Y no confía únicamente en la extensión o el {{Content-Type}} enviado por el cliente

*Escenario: Eliminar fotografía de un borrador*

Dado una inspección en {{BORRADOR}}

Cuando su autor elimina una fotografía

Entonces la fotografía deja de formar parte de la inspección

*Escenario: Eliminar la única foto de una categoría*

Dado una inspección con exactamente una fotografía de una categoría obligatoria

Cuando el autor la elimina

Entonces la inspección vuelve a incumplir el requisito de evidencia mínima

*Escenario: No eliminar fotografías publicadas*

Dado una inspección {{PUBLICADA}}

Cuando se intenta eliminar una de sus fotografías

Entonces el sistema responde 409

*Escenario: Acceso protegido*

Cuando un usuario autorizado solicita una fotografía

Entonces se sirve mediante una URL firmada de vigencia limitada

O mediante un endpoint autorizado

*Escenario: El almacenamiento no es público*

Dado un archivo almacenado

Cuando un usuario intenta acceder directamente a la ruta física o bucket sin autorización

Entonces el archivo no queda públicamente accesible

*Escenario: Fotografías de borradores privadas*

Dado una fotografía perteneciente a una inspección en {{BORRADOR}}

Cuando intenta consultarla un usuario distinto del autor o del Catedrático autorizado

Entonces el sistema responde 404

*Escenario: Visitante no puede acceder a fotografías*

Dado un visitante sin sesión iniciada

Cuando solicita una fotografía de una inspección

Entonces el sistema responde 401 Unauthorized

*Escenario: Autorización por rol*

Dado cada endpoint relacionado con archivos

Cuando se ejecutan las pruebas de seguridad

Entonces existe al menos una prueba que verifica qué roles pueden acceder

Y cuáles deben ser rechazados.

### Status

Finalizado

### Status category

Done

### Priority

Medium

### Story point estimate

5

### Sprint

Sprint 2

### Parent

Épica G — Archivo básico

### Assignee

Kevin Mushin

### Reporter

Mario Yancor

### Creator

Mario Yancor

### Created

08/Oct/26 11:52 PM

### Updated

18/Oct/26 5:35 PM

### Resolved

18/Oct/26 5:35 PM

### Resolution

Done

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 5:35 PM

### Watchers

1

### Votes

0

### Rank

0|i00048:00i

---

## HU-035 — Estudiante levanta y envía inspecciones de sus puentes asignados {#scrum-52}
**Jira:** [SCRUM-52](https://ayd1-g1-team.atlassian.net/browse/SCRUM-52)

### Issue Type

Story

### Description

*Historia*

Como Estudiante, quiero iniciar, completar y enviar inspecciones de los puentes que mi Catedrático me asignó dentro de un curso vigente, para que mi trabajo de campo sea revisado y, si se aprueba, quede publicado (RN-INS-02, RN-INS-04, RN-INS-05, RN-INS-06, RN-INS-13, RN-INS-14, RN-INS-15, RN-REV-01).

*Dependencias:* HU-024, HU-025, HU-026 y HU-031 (Sprint 2); HU-004 y HU-005 (inscripción y asignación de puentes, Sprint 1); HU-040 (aviso al Catedrático). En Sprint 2 el Estudiante recibía 403; esta HU abre esa puerta reutilizando el mismo flujo del inspector, sin duplicarlo.

*Criterios de aceptación*

{noformat}Característica: Inspecciones del Estudiante

  Escenario: Iniciar una inspección de un puente asignado (RN-INS-14)

    Dado un Estudiante con inscripción ACTIVA en un curso vigente

    Y un puente activo que su Catedrático le asignó en ese curso y no revocó

    Cuando inicia una inspección de ese puente

    Entonces se crea una inspección en estado BORRADOR con el Estudiante como único autor, la asignación de la que proviene, la versión activa del formulario, el dispositivo y el GPS (RN-INS-02, RN-INS-12, RN-INS-13)

    Y se aplican las mismas reglas de HU-024: datos permanentes precargados con confirmación explícita y daños siempre vacíos (RN-INS-09, RN-INS-10)

  Esquema del escenario: Puentes que el Estudiante no puede inspeccionar (RN-INS-14)

    Dado un Estudiante

    Cuando intenta iniciar una inspección de un puente en esta situación: <situacion>

    Entonces el sistema responde <resultado>

    Ejemplos:

      | situacion                                 | resultado     |

      | no tiene asignación para ese puente       | 403 Forbidden |

      | la asignación fue revocada                | 403 Forbidden |

      | el curso de la asignación ya finalizó     | 403 Forbidden |

      | su inscripción en el curso está RETIRADA  | 403 Forbidden |

      | el puente está inactivo                   | 409 Conflict  |

  Escenario: Un Estudiante nunca publica (RN-REV-01)

    Dado un Estudiante con una inspección completa en BORRADOR

    Cuando ejecuta la acción de enviar

    Entonces la inspección pasa a ENVIADA y se crea el ciclo de revisión 1 con el Catedrático del curso como revisor

    Y se guardan el contenido enviado, su hash y la fecha y hora de envío (RN-INS-13)

    Y ninguna acción del Estudiante puede dejarla en PUBLICADA

  Escenario: Requisitos para enviar (RN-INS-06)

    Dado una inspección del Estudiante con campos obligatorios sin llenar o sin fotografía de alguna categoría

    Cuando intenta enviarla

    Entonces el sistema responde 422 en formato RFC 7807 con el detalle de lo que falta

    Y la inspección permanece en BORRADOR y no se crea ningún ciclo de revisión

  Esquema del escenario: Editar y eliminar solo los propios borradores (RN-INS-15)

    Dado una inspección en estado <estado> cuyo autor es <autor>

    Cuando el Estudiante intenta editarla o eliminarla

    Entonces el resultado es <resultado>

    Ejemplos:

      | estado              | autor           | resultado                                      |

      | BORRADOR            | el Estudiante   | permitido; la eliminación es lógica            |

      | BORRADOR            | otro Estudiante | 404 Not Found                                  |

      | ENVIADA             | el Estudiante   | 409 Conflict                                   |

      | EN_REVISION         | el Estudiante   | 409 Conflict                                   |

      | CAMBIOS_SOLICITADOS | el Estudiante   | 409 hasta que la devuelva a borrador (HU-039)  |

      | RECHAZADA           | el Estudiante   | 409 Conflict                                   |

      | PUBLICADA           | el Estudiante   | 409 Conflict (RN-INS-07)                       |

  Escenario: Un borrador es privado (RN-INS-05)

    Dado un borrador de un Estudiante

    Cuando lo consulta su Catedrático asignado

    Entonces lo ve en solo lectura

    Pero cuando lo consulta otro Estudiante, otro Catedrático, un Profesional externo o un Administrador

    Entonces el sistema responde 404 Not Found

    Y el borrador no aparece en el foro, el mapa ni los reportes

  Escenario: Pantallas del Estudiante

    Cuando el Estudiante abre «Mis puentes asignados»

    Entonces ve solo los puentes de sus asignaciones vigentes, con búsqueda por nombre o código y el estado de su inspección escrito en texto: sin iniciar, borrador, enviada, en revisión, cambios solicitados, rechazada o publicada

    Y «Mis inspecciones» (HU-031) le muestra únicamente las suyas, con las acciones válidas para cada estado: continuar, enviar, eliminar y atender cambios

    Y la ficha de un puente que no le fue asignado no le ofrece «Iniciar inspección»

  Escenario: Aviso al Catedrático

    Cuando el Estudiante envía una inspección

    Entonces el Catedrático revisor recibe un aviso «Inspección enviada para revisión» con enlace a ella (HU-040)

  Escenario: Reintento seguro (RN-OFF-08)

    Dado un envío repetido con la misma Idempotency-Key

    Entonces se devuelve la misma respuesta y existe un solo ciclo de revisión

  Escenario: Auditoría (RN-USR-09)

    Cuando el Estudiante inicia, envía o elimina una inspección

    Entonces se registran el usuario, la fecha y los valores anterior y posterior{noformat}

### Status

En revisión

### Status category

In Progress

### Priority

Highest

### Story point estimate

5

### Sprint

Sprint 3

### Labels

Estudiante

### Parent

Épica H — Revisión académica y flujo del Estudiante

### Assignee

Brígido Josías Alvarado Pec

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

18/Oct/26 1:02 AM

### Updated

18/Oct/26 12:44 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 12:44 PM

### Watchers

1

### Votes

0

### Rank

0|i000a0:

---

## HU-036 — Catedrático consulta su bandeja de revisión {#scrum-53}
**Jira:** [SCRUM-53](https://ayd1-g1-team.atlassian.net/browse/SCRUM-53)

### Issue Type

Story

### Description

*Historia*

Como Catedrático, quiero ver en una bandeja las inspecciones que enviaron mis Estudiantes y tomarlas para revisarlas, para saber qué está pendiente, cuánto lleva esperando y no tocar nada que no me corresponda (RN-REV-01, RN-REV-03, RN-INS-04, RN-INS-05; DT-SEC-04, DT-BE-08).

*Dependencias:* HU-035 (crea los ciclos de revisión), HU-026 (máquina de estados), HU-040 (avisos).

*Criterios de aceptación*

{noformat}Característica: Bandeja de revisión del Catedrático

  Escenario: Solo mis Estudiantes (DT-SEC-04)

    Dado un Catedrático responsable de uno o más cursos

    Cuando abre la bandeja de revisión

    Entonces ve únicamente las inspecciones ENVIADAS y EN_REVISION cuyo ciclo de revisión lo tiene a él como revisor

    Y las inspecciones de Estudiantes de otros Catedráticos no aparecen

  Escenario: Listado paginado y ordenado (DT-BE-08)

    Cuando la bandeja tiene muchas inspecciones

    Entonces se muestran paginadas, con un máximo de 100 por página, de la más antigua a la más reciente según la fecha de envío

  Escenario: Datos de cada fila

    Cuando se muestra una inspección

    Entonces se ven el puente (nombre y código), el Estudiante autor, el curso, el número de ciclo, la fecha de envío, los días en espera y el estado con etiqueta textual

  Escenario: Filtros

    Cuando el Catedrático filtra por estado, puente, Estudiante, curso o rango de fechas de envío

    Entonces la lista muestra solo lo que cumple todos los filtros combinados

  Escenario: Espera prolongada visible

    Dado una inspección que lleva más de 15 días naturales sin revisarse

    Cuando se muestra en la bandeja

    Entonces aparece marcada con el texto «Más de 15 días en espera» y un ícono, no solo con color (DT-FE-13)

    Pero la alerta automática al Catedrático (RN-REV-08) se construye en Sprint 4

  Escenario: Contador de pendientes

    Cuando el Catedrático tiene inspecciones por revisar

    Entonces el menú muestra cuántas son, con texto accesible para lectores de pantalla

  Escenario: Iniciar la revisión

    Dado una inspección ENVIADA cuyo revisor es el Catedrático

    Cuando ejecuta «Iniciar revisión»

    Entonces la inspección pasa a EN_REVISION y su ciclo pasa a EN_CURSO con la fecha de inicio

    Y la transición queda en el historial de estados (RN-INS-04)

  Escenario: Reanudar una revisión ya iniciada

    Dado una inspección EN_REVISION cuyo revisor es el mismo Catedrático

    Cuando vuelve a abrirla

    Entonces continúa donde la dejó y el estado no cambia

  Esquema del escenario: Quién puede iniciar una revisión (RN-REV-03)

    Dado una inspección ENVIADA

    Cuando <usuario> intenta iniciar su revisión

    Entonces el resultado es <resultado>

    Ejemplos:

      | usuario                                   | resultado        |

      | el Catedrático revisor del ciclo          | permitido        |

      | otro Catedrático                          | 404 Not Found    |

      | el autor de la inspección                 | 403 Forbidden    |

      | un Profesional externo o un Administrador | 403 Forbidden    |

      | un Visitante                              | 401 Unauthorized |

  Escenario: Solo se toma lo que está ENVIADA

    Dado una inspección en cualquier otro estado

    Cuando se intenta iniciar su revisión

    Entonces el sistema responde 409 y el estado no cambia

  Escenario: Borradores de mis Estudiantes (RN-INS-05)

    Cuando el Catedrático abre la pestaña «Borradores en curso»

    Entonces ve en solo lectura los BORRADOR de los Estudiantes de sus cursos

    Y no puede editarlos ni revisarlos

  Escenario: Dos peticiones a la vez

    Dado dos peticiones simultáneas para iniciar la misma revisión

    Entonces solo una tiene efecto y la otra recibe 409

  Escenario: Estado vacío

    Cuando no hay nada pendiente

    Entonces la bandeja muestra «No tienes inspecciones por revisar»{noformat}

### Status

En revisión

### Status category

In Progress

### Priority

Highest

### Story point estimate

5

### Sprint

Sprint 3

### Labels

Catedrático

### Parent

Épica H — Revisión académica y flujo del Estudiante

### Assignee

Brígido Josías Alvarado Pec

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

18/Oct/26 1:03 AM

### Updated

18/Oct/26 12:44 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 12:44 PM

### Watchers

1

### Votes

0

### Rank

0|i000a7:

---

## HU-037 — Catedrático revisa una inspección y ancla observaciones a un elemento {#scrum-54}
**Jira:** [SCRUM-54](https://ayd1-g1-team.atlassian.net/browse/SCRUM-54)

### Issue Type

Story

### Description

*Historia*

Como Catedrático revisor, quiero abrir la inspección tal como la envió el Estudiante y dejar observaciones ancladas a un elemento o campo concreto, para señalar exactamente qué debe corregir y que cada señalamiento quede como un pendiente del autor (RN-REV-05, RN-ARC-01, RN-FRM-05, RN-IC-07; DT-FE-07, DT-FE-13).

*Dependencias:* HU-036; HU-030 y HU-034 (vista de solo lectura con una tarjeta por elemento); HU-028 (fotografías con URL firmadas). Es la HU de mayor riesgo técnico de la épica: ancla el comentario al dato exacto del formulario dinámico, y esa misma ancla la reutiliza el foro en Sprint 4 (RN-FOR-02).

*Criterios de aceptación*

{noformat}Característica: Observaciones ancladas durante la revisión

  Escenario: Abrir la inspección tal como fue enviada

    Dado una inspección EN_REVISION cuyo revisor es el Catedrático

    Cuando la abre

    Entonces ve el formulario completo en solo lectura, con las ocho secciones y la estructura de la versión con la que se levantó (RN-FRM-05)

    Y los datos mostrados provienen del contenido enviado en el ciclo y no de ediciones posteriores

    Y ve las fotografías por categoría con acceso protegido y el IC rotulado «Preliminar» (RN-IC-07)

  Escenario: Anclar una observación a un elemento (RN-REV-05)

    Dado la tarjeta de un elemento evaluable, un campo, una sección o una categoría de fotografías

    Cuando el Catedrático pulsa «Agregar observación» y escribe el texto

    Entonces se guarda con el elemento_ref, que es la ruta del elemento en el esquema de la versión (por ejemplo subestructura.estribo_entrada)

    Y, si el elemento está dentro de una lista repetible como los tramos o las pilas, se guarda también su instancia_elemento_id

    Y nace en estado PENDIENTE a nombre del autor de la inspección

  Escenario: El ancla debe existir en el esquema

    Cuando se intenta guardar una observación con un elemento_ref que no existe en el esquema de esa versión

    Entonces el sistema responde 422 en formato RFC 7807 y no se guarda nada

  Escenario: Texto obligatorio y acotado

    Cuando el texto está vacío o supera los 2000 caracteres

    Entonces el sistema responde 422 con el mensaje del campo

  Escenario: Varias observaciones y una observación general

    Cuando el Catedrático agrega más de una observación al mismo elemento o a elementos distintos

    Entonces todas se conservan, cada una con su ancla

    Y puede escribir además la observación general del ciclo

  Escenario: Marcadores junto al dato

    Cuando el revisor o el autor ven una inspección con observaciones

    Entonces cada elemento observado muestra un marcador con ícono y con el texto «N observaciones», no solo con color (DT-FE-13)

    Y un panel lateral las lista todas y permite saltar al elemento correspondiente

  Escenario: Editar o eliminar mientras la revisión sigue abierta

    Dado una observación creada por el revisor en un ciclo EN_CURSO

    Cuando la edita o la elimina

    Entonces el cambio se aplica y queda en la bitácora

  Escenario: Congeladas al emitir el veredicto

    Dado un ciclo con veredicto emitido

    Cuando alguien intenta editar o eliminar sus observaciones

    Entonces el sistema responde 409

  Escenario: Quién puede observar (DT-SEC-04)

    Dado una inspección EN_REVISION

    Cuando intenta observar un usuario que no es su Catedrático revisor, incluido el autor

    Entonces el sistema responde 403 Forbidden o 404 Not Found según corresponda y no se guarda nada

  Escenario: Solo durante la revisión

    Dado una inspección que no está EN_REVISION

    Cuando se intenta agregar una observación

    Entonces el sistema responde 409

  Escenario: Contador de pendientes

    Cuando el Catedrático consulta la revisión

    Entonces ve cuántas observaciones tiene el ciclo y cuántas siguen PENDIENTE

  Escenario: Uso en el teléfono

    Cuando el Catedrático revisa desde un teléfono de 375 px de ancho

    Entonces todos los controles miden al menos 44 × 44 px y el panel de observaciones se abre como hoja inferior (DT-FE-09)

  Escenario: Auditoría (RN-USR-09)

    Cuando se crea, edita o elimina una observación

    Entonces se registran el usuario, la fecha y los valores anterior y posterior{noformat}

### Status

En curso

### Status category

In Progress

### Priority

Highest

### Story point estimate

8

### Sprint

Sprint 3

### Labels

Catedrático

### Parent

Épica H — Revisión académica y flujo del Estudiante

### Assignee

Kevin Mushin

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

18/Oct/26 1:03 AM

### Updated

18/Oct/26 1:38 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 1:38 PM

### Watchers

1

### Votes

0

### Rank

0|i000al:

---

## HU-038 — Catedrático emite el veredicto y la calificación de una revisión {#scrum-55}
**Jira:** [SCRUM-55](https://ayd1-g1-team.atlassian.net/browse/SCRUM-55)

### Issue Type

Story

### Description

*Historia*

Como Catedrático revisor, quiero emitir un veredicto sobre la inspección de mi Estudiante y registrar una calificación, para decidir si se publica, si debe corregirse o si se rechaza (RN-REV-01, RN-REV-03, RN-REV-04, RN-REV-06, RN-REV-07, RN-INS-04, RN-INS-07, RN-IC-06).

*Dependencias:* HU-037; HU-026 (máquina de estados y publicación); HU-020 y HU-032 (IC definitivo y confirmación del estado); HU-040 (aviso al autor). Aprobar usa la misma transacción de publicación que ya existe para el Catedrático, no una distinta.

*Criterios de aceptación*

{noformat}Característica: Veredicto y calificación de una revisión

  Esquema del escenario: Veredictos posibles (RN-REV-04)

    Dado una inspección EN_REVISION cuyo ciclo está EN_CURSO

    Cuando el Catedrático revisor emite el veredicto <veredicto>

    Entonces <observacion> y la inspección pasa a <destino>

    Ejemplos:

      | veredicto           | observacion                             | destino             |

      | APROBADA            | la observación general es opcional      | PUBLICADA           |

      | CAMBIOS_SOLICITADOS | la observación general es obligatoria   | CAMBIOS_SOLICITADOS |

      | RECHAZADA           | la observación general es obligatoria   | RECHAZADA           |

  Escenario: Cambios solicitados y rechazo exigen texto (RN-REV-04)

    Cuando se emite CAMBIOS_SOLICITADOS o RECHAZADA con la observación general vacía

    Entonces el sistema responde 422 y la inspección sigue EN_REVISION

  Escenario: Aprobar publica en una sola operación (RN-REV-01)

    Dado una inspección de un Estudiante EN_REVISION

    Cuando el revisor la aprueba

    Entonces, en la misma transacción de HU-026, pasa a PUBLICADA, se calcula el IC definitivo con las reglas críticas (HU-020), se actualizan el estado, el IC y la fecha de última inspección del puente y se registra el historial

    Y si cualquier paso falla no queda ningún cambio a medias

  Escenario: El revisor confirma o ajusta el estado calculado (RN-IC-06)

    Cuando el revisor aprueba

    Entonces puede confirmar el estado calculado o ajustarlo con una justificación obligatoria

    Y se conservan el estado calculado, el confirmado, la justificación y quién lo confirmó (HU-032)

  Escenario: No se aprueba con pendientes del propio ciclo

    Dado un ciclo con observaciones PENDIENTE creadas en este mismo ciclo

    Cuando el revisor intenta aprobar

    Entonces el sistema responde 422 pidiendo resolverlas, eliminarlas o solicitar cambios

  Escenario: Cambios solicitados deja la inspección en espera del autor (RN-REV-06)

    Cuando el veredicto es CAMBIOS_SOLICITADOS

    Entonces la inspección queda en CAMBIOS_SOLICITADOS hasta que el autor la devuelva a borrador (HU-039)

    Y el ciclo se conserva completo en el historial

  Escenario: Rechazada es terminal (RN-INS-04)

    Dado una inspección RECHAZADA

    Cuando se intenta cualquier transición, edición o eliminación

    Entonces el sistema responde 409

    Y el autor sigue viendo la inspección con el motivo del rechazo

  Escenario: Calificación numérica (RN-REV-07)

    Cuando el revisor emite el veredicto

    Entonces puede registrar una calificación de 0 a 100 con hasta dos decimales

    Y un valor fuera de rango o no numérico se rechaza con 422

    Pero la escala es provisional hasta que el Catedrático de Puentes la confirme

  Esquema del escenario: Quién ve la calificación (RN-REV-07)

    Dado una calificación registrada

    Cuando la consulta <usuario>

    Entonces <resultado>

    Ejemplos:

      | usuario                | resultado                           |

      | el Estudiante autor    | la ve                               |

      | el Catedrático revisor | la ve                               |

      | otro Catedrático       | el campo no viene en la respuesta   |

      | un Administrador       | el campo no viene en la respuesta   |

      | un Profesional externo | el campo no viene en la respuesta   |

  Escenario: La calificación no sale de ese círculo

    Dado una calificación registrada

    Entonces no aparece en el foro, el mapa, los reportes, los avisos ni la bitácora consultable

  Escenario: El revisor no puede ser el autor (RN-REV-03)

    Dado una inspección cuyo autor y cuyo revisor del ciclo coinciden

    Cuando se intenta emitir un veredicto

    Entonces el sistema responde 403 Forbidden

    Y el sistema nunca asigna al autor como revisor de su propia inspección

  Escenario: Solo el revisor del ciclo y solo desde EN_REVISION

    Dado un usuario distinto del revisor, o una inspección que no está EN_REVISION

    Cuando intenta emitir un veredicto

    Entonces el sistema responde 403, 404 o 409 según corresponda y no cambia nada

  Escenario: El veredicto es definitivo

    Dado un ciclo con veredicto emitido

    Cuando se intenta emitir otro veredicto sobre el mismo ciclo

    Entonces el sistema responde 409

    Y el ciclo queda RESUELTA con su fecha de resolución

  Escenario: Veredictos simultáneos

    Dado dos peticiones simultáneas con veredictos distintos

    Entonces solo una tiene efecto y la otra recibe 409

  Escenario: Aviso al autor

    Cuando se emite el veredicto

    Entonces el Estudiante recibe un aviso con el veredicto y un enlace a la inspección, sin incluir la calificación (HU-040)

  Escenario: Cobertura de las transiciones (DT-CAL-01)

    Entonces las transiciones de EN_REVISION a PUBLICADA, a CAMBIOS_SOLICITADOS y a RECHAZADA se suman a la suite de la máquina de estados y mantienen su 100 % de cobertura

  Escenario: Auditoría (RN-USR-09)

    Cuando se emite un veredicto

    Entonces se registran el usuario, la fecha, el veredicto y la transición de estado{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

Highest

### Story point estimate

5

### Sprint

Sprint 3

### Labels

Catedrático

### Parent

Épica H — Revisión académica y flujo del Estudiante

### Assignee

Melvin Ordoñez

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

18/Oct/26 1:03 AM

### Updated

18/Oct/26 1:19 AM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 1:03 AM

### Watchers

1

### Votes

0

### Rank

0|i000ar:

---

## HU-039 — Estudiante atiende las observaciones y reenvía su inspección {#scrum-56}
**Jira:** [SCRUM-56](https://ayd1-g1-team.atlassian.net/browse/SCRUM-56)

### Issue Type

Story

### Description

*Historia*

Como Estudiante, quiero ver las observaciones que me anclaron, marcarlas como resueltas al corregirlas y reenviar mi inspección, para que se revise de nuevo hasta que sea aprobada, y poder consultar el historial completo de las revisiones (RN-REV-05, RN-REV-06, RN-REV-07, RN-INS-04, RN-INS-15).

*Dependencias:* HU-037 y HU-038 (observaciones y veredicto); HU-035 (envío); HU-040 (aviso al revisor).

*Criterios de aceptación*

{noformat}Característica: Atender cambios y reenviar

  Escenario: Ver lo que se me pidió (RN-REV-05)

    Dado una inspección en CAMBIOS_SOLICITADOS

    Cuando el Estudiante autor la abre

    Entonces ve el veredicto, la observación general, su calificación y cuántas observaciones siguen PENDIENTE

    Y el formulario muestra un marcador junto a cada elemento observado, con el texto de la observación

  Escenario: Atender cambios devuelve el borrador (RN-REV-06)

    Dado una inspección en CAMBIOS_SOLICITADOS

    Cuando el autor ejecuta «Atender cambios»

    Entonces la inspección vuelve a BORRADOR y el formulario se puede editar

    Y la transición queda en el historial

  Escenario: Marcar una observación como resuelta

    Dado una observación PENDIENTE de la inspección devuelta a borrador

    Cuando el autor la marca como resuelta, con o sin una respuesta escrita

    Entonces pasa a RESUELTA y se guardan quién la resolvió, cuándo y su respuesta

  Escenario: Solo el autor resuelve

    Dado una observación de la inspección de otro Estudiante

    Cuando alguien intenta resolverla

    Entonces el sistema responde 404 Not Found

  Escenario: Solo se resuelve en BORRADOR

    Dado una inspección ENVIADA, EN_REVISION, RECHAZADA o PUBLICADA

    Cuando el autor intenta resolver una observación

    Entonces el sistema responde 409

  Escenario: No se reenvía con pendientes (RN-REV-05)

    Dado una inspección con observaciones PENDIENTE

    Cuando el autor intenta reenviarla

    Entonces el sistema responde 422 con la lista de las observaciones que faltan y la inspección sigue en BORRADOR

  Escenario: Reenviar abre un nuevo ciclo (RN-REV-06)

    Dado una inspección en BORRADOR con todas las observaciones resueltas y los requisitos de envío cumplidos (RN-INS-06)

    Cuando el autor la reenvía

    Entonces pasa a ENVIADA y se crea el ciclo n+1 con el mismo revisor, el contenido enviado y su hash

    Y el revisor recibe un aviso de reenvío (HU-040)

  Escenario: El revisor ve cómo se atendió el ciclo anterior

    Dado un ciclo n+1 en revisión

    Cuando el revisor lo abre

    Entonces ve las observaciones del ciclo anterior con su respuesta y su estado

    Y puede verificar lo corregido contra el contenido enviado antes

  Escenario: Historial completo de ciclos (RN-REV-06)

    Cuando el autor o el revisor consultan el historial de la inspección

    Entonces ven todos los ciclos en orden, cada uno con el número, la fecha de envío, el revisor, el veredicto, la observación general, sus observaciones con su estado y su respuesta, y la calificación

    Y ningún ciclo anterior se modifica ni se elimina

  Escenario: Calificación solo para el autor y el revisor (RN-REV-07)

    Cuando otro usuario consulta el historial

    Entonces la calificación no viene en la respuesta

  Escenario: Rechazada no se reenvía

    Dado una inspección RECHAZADA

    Cuando el autor intenta atender cambios o reenviarla

    Entonces el sistema responde 409

    Y ve el motivo del rechazo y puede iniciar una inspección nueva del puente si sigue asignado

  Escenario: Una publicada ya no cambia (RN-INS-07)

    Dado una inspección PUBLICADA tras la aprobación

    Cuando el autor intenta editarla o eliminarla

    Entonces el sistema responde 409

  Escenario: Reintento seguro

    Dado un reenvío repetido con la misma Idempotency-Key

    Entonces existe un solo ciclo n+1

  Escenario: Auditoría (RN-USR-09)

    Cuando el autor atiende cambios, resuelve una observación o reenvía

    Entonces se registran el usuario, la fecha y los valores anterior y posterior{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

High

### Story point estimate

5

### Sprint

Sprint 3

### Labels

Estudiante

### Parent

Épica H — Revisión académica y flujo del Estudiante

### Assignee

Melvin Ordoñez

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

18/Oct/26 1:03 AM

### Updated

18/Oct/26 1:19 AM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 1:03 AM

### Watchers

1

### Votes

0

### Rank

0|i000b4:

---

## HU-040 — Usuario recibe y consulta avisos dentro de la aplicación {#scrum-57}
**Jira:** [SCRUM-57](https://ayd1-g1-team.atlassian.net/browse/SCRUM-57)

### Issue Type

Story

### Description

*Historia*

Como usuario autenticado, quiero recibir y consultar avisos dentro de la aplicación, para enterarme de lo que requiere mi atención sin revisar cada pantalla (soporte de RN-REV-08, RN-FOR-07, RN-FOR-10, RN-MTO-08 y RN-ARC-06; RN-USR-09, DT-SEC-04, DT-SEC-12).

*Dependencias:* ninguna para empezar. La firma del servicio {{notificar(...)}} se acuerda el día 0 porque la usan seis HU de otros frentes (HU-035, 036, 038, 039, 046 y 047).

*Criterios de aceptación*

{noformat}Característica: Avisos dentro de la aplicación

  Escenario: Un único punto para avisar

    Dado cualquier módulo del backend

    Cuando necesita avisar a un usuario

    Entonces lo hace solo a través del servicio de notificaciones, con tipo, título, mensaje, entidad, id de la entidad y clave de evento

    Y no escribe en la tabla de notificaciones por su cuenta

  Escenario: Un mismo evento avisa una sola vez

    Dado un aviso ya creado para un usuario con una clave de evento

    Cuando se intenta crear otro con la misma clave para el mismo usuario

    Entonces no se duplica y la operación que lo originó no falla

  Escenario: Avisos de este sprint

    Cuando ocurre uno de estos eventos

    Entonces se avisa a quien corresponde

      | evento                                              | destinatario                                          |

      | un Estudiante envía una inspección (HU-035)         | el Catedrático revisor                                |

      | un Estudiante reenvía una inspección (HU-039)       | el Catedrático revisor                                |

      | se emite un veredicto (HU-038)                      | el Estudiante autor                                   |

      | se genera una propuesta de mantenimiento (HU-046)   | los Administradores y el Catedrático que publicó o revisó |

      | una orden cambia de estado (HU-047)                 | quien la creó o la aceptó                             |

  Escenario: Campana con contador

    Cuando el usuario tiene avisos sin leer

    Entonces la barra superior muestra una campana con el número de avisos sin leer y el texto «N avisos sin leer» para lectores de pantalla (DT-FE-13)

    Y el contador se actualiza al navegar y cada 60 segundos mientras haya conexión

  Escenario: Lista de avisos

    Cuando el usuario abre la campana o la página de avisos

    Entonces ve sus avisos de más reciente a más antiguo, paginados con un máximo de 100 por página, con el filtro «Solo no leídos»

  Escenario: Leer y navegar

    Cuando el usuario pulsa un aviso

    Entonces se marca como leído y la aplicación lo lleva a la entidad que lo originó: la inspección, la revisión o la orden

    Y «Marcar todos como leídos» marca todos los suyos

  Escenario: Cada quien ve solo lo suyo (DT-SEC-04)

    Cuando un usuario consulta o marca el aviso de otra persona

    Entonces el sistema responde 404 Not Found

  Escenario: Visitante

    Cuando un visitante pide avisos

    Entonces el sistema responde 401 Unauthorized

  Escenario: Sin datos sensibles (DT-SEC-12)

    Cuando se redacta un aviso

    Entonces contiene títulos e identificadores, y nunca la calificación de una revisión, tokens ni datos personales completos

  Escenario: Sin conexión

    Dado un usuario sin conexión

    Cuando abre la lista

    Entonces ve el último contador conocido y un texto que indica que no se pudo actualizar

  Escenario: Accesibilidad y uso táctil

    Entonces la campana se alcanza con el teclado, el contador se anuncia con aria-live y cada aviso mide al menos 44 px de alto (DT-FE-09, DT-FE-13)

  Escenario: Estado vacío

    Cuando el usuario no tiene avisos

    Entonces ve «No tienes avisos»{noformat}

### Status

En revisión

### Status category

In Progress

### Priority

Highest

### Story point estimate

5

### Sprint

Sprint 3

### Parent

Épica I — Notificaciones

### Assignee

Rony Rojas Aguilar

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

18/Oct/26 1:04 AM

### Updated

18/Oct/26 5:49 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 1:51 PM

### Watchers

1

### Votes

0

### Rank

0|i000b9:

---

## HU-041 — Inspector prepara su salida {#scrum-58}
**Jira:** [SCRUM-58](https://ayd1-g1-team.atlassian.net/browse/SCRUM-58)

### Issue Type

Story

### Description

*Historia*

Como inspector, quiero ejecutar «Preparar salida» con conexión antes de ir a campo, para llevar en mi dispositivo la versión activa del formulario, los puentes que puedo inspeccionar y la última inspección publicada de cada uno (RN-OFF-02, RN-OFF-03, RN-OFF-07, RN-INS-09, RN-INS-14; DT-OFF-01, DT-OFF-03, DT-OFF-04, DT-OFF-09).

*Dependencias:* HU-021 (versión activa), HU-024 (modelo de inspección), HU-043 (idempotencia); HU-004 y HU-005 (inscripción y asignaciones, Sprint 1). La tabla {{preparacion_salida}} ya existe.

*Criterios de aceptación*

{noformat}Característica: Preparar salida

  Escenario: Descargar lo necesario con conexión (RN-OFF-02)

    Dado un inspector con conexión

    Cuando ejecuta «Preparar salida»

    Entonces se descargan la versión activa del formulario con su esquema, los puentes que puede inspeccionar y la última inspección publicada de cada uno, con sus datos permanentes

    Y todo se guarda en el dispositivo en los almacenes esquemas_formulario, puentes e inspecciones de Dexie (DT-OFF-03, DT-OFF-04)

  Esquema del escenario: Qué puentes recibe cada rol (RN-INS-14, DT-SEC-04)

    Dado un usuario con rol <rol>

    Cuando ejecuta «Preparar salida»

    Entonces el resultado es <resultado>

    Ejemplos:

      | rol                               | resultado                                                                |

      | Estudiante                        | recibe solo los puentes de sus asignaciones vigentes                     |

      | Catedrático                       | recibe los puentes activos que elija, hasta 200 por salida (provisional) |

      | Profesional externo verificado    | recibe los puentes activos que elija, hasta 200 por salida (provisional) |

      | Profesional externo sin verificar | 403 Forbidden                                                            |

      | Administrador                     | 403 Forbidden                                                            |

      | Visitante                         | 401 Unauthorized                                                         |

  Escenario: Elegir los puentes de la salida

    Dado un Catedrático o un Profesional externo

    Cuando elige los puentes por búsqueda o por departamento

    Entonces la descarga incluye solo esos puentes y muestra cuántos son y cuánto espacio ocuparán aproximadamente

  Escenario: Registro en el servidor

    Cuando la preparación termina

    Entonces el servidor registra la salida con usuario, dispositivo, versión del formulario, contexto autorizado y fecha

    Y repetirla con la misma Idempotency-Key no crea una segunda salida

  Escenario: Todo o nada

    Dado una descarga que se interrumpe a la mitad

    Cuando termina con error

    Entonces no se guarda nada parcial: lo que había antes sigue intacto y el usuario puede reintentar

  Escenario: Progreso y resultado

    Cuando la preparación está en curso

    Entonces se muestra el avance por pasos: formulario, puentes e inspecciones previas

    Y al terminar aparece «Listo para trabajar sin conexión» con la versión del formulario, el número de puentes y la fecha y hora de la preparación

  Escenario: Almacenamiento persistente (DT-OFF-09)

    Cuando termina la preparación

    Entonces la aplicación solicita navigator.storage.persist() y muestra si se concedió y cuánto espacio queda

    Y advierte con texto si queda poco espacio para una jornada de trabajo

  Escenario: El formulario cambió desde que preparé

    Dado una preparación hecha con la versión 3 del formulario

    Cuando el Administrador publica la versión 4

    Entonces, con conexión, la aplicación avisa «Hay una versión nueva del formulario; vuelve a preparar tu salida»

    Pero las inspecciones ya iniciadas conservan la versión con la que empezaron (RN-FRM-02)

  Escenario: Sin conexión no se puede preparar

    Dado un dispositivo sin conexión

    Entonces el botón queda deshabilitado con la explicación «Necesitas conexión para preparar la salida» y se muestra la fecha de la última preparación

  Escenario: Quitar datos del dispositivo (RN-OFF-07)

    Dado datos preparados en el dispositivo

    Cuando el usuario elige «Quitar datos de este dispositivo»

    Entonces se borran los puentes y los esquemas preparados

    Pero si hay inspecciones o archivos pendientes de sincronizar, la acción se bloquea y explica por qué

  Escenario: Cambio de usuario en el mismo dispositivo

    Dado datos preparados por otro usuario en el mismo dispositivo

    Cuando inicia sesión una persona distinta

    Entonces no ve ni usa lo preparado por el anterior

    Y los pendientes del anterior se conservan hasta que vuelva a entrar y sincronice

  Escenario: Auditoría y uso táctil

    Cuando se registra una preparación

    Entonces queda en la bitácora con usuario, fecha y dispositivo

    Y los controles miden al menos 44 × 44 px (DT-FE-09){noformat}

### Status

En revisión

### Status category

In Progress

### Priority

Highest

### Story point estimate

8

### Sprint

Sprint 3

### Labels

Catedrático, Estudiante, Profesional-Externo

### Parent

Épica J — Trabajo sin conexión (fase 1)

### Assignee

KENNY SALAZAR

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

18/Oct/26 1:04 AM

### Updated

20/Oct/26 12:03 AM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 10:20 PM

### Last Viewed

19/Oct/26 10:19 PM

### Watchers

1

### Votes

0

### Rank

0|i000bl:

---

## HU-042 — Inspector captura una inspección sin conexión {#scrum-59}
**Jira:** [SCRUM-59](https://ayd1-g1-team.atlassian.net/browse/SCRUM-59)

### Issue Type

Story

### Description

*Historia*

Como inspector, quiero crear y llenar una inspección sin conexión, de modo que lo que capture sobreviva al cierre de la aplicación o al apagado del teléfono, para trabajar en puentes donde no hay cobertura (RN-OFF-01, RN-OFF-03, RN-INS-03, RN-INS-09, RN-INS-10, RN-INS-12, RN-INS-13, RN-FRM-02; DT-OFF-01, DT-OFF-03, DT-OFF-05, DT-FE-07, DT-FE-08).

*Dependencias:* HU-041 (datos preparados), HU-022 y HU-034 (motor del formulario y grilla táctil, que deben poder leer el esquema desde Dexie), HU-024 (reglas de inicio de la inspección).

*Criterios de aceptación*

{noformat}Característica: Captura de una inspección sin conexión

  Escenario: Iniciar sin conexión (RN-OFF-01)

    Dado un inspector que preparó su salida

    Y un dispositivo sin conexión

    Cuando inicia una inspección de un puente preparado

    Entonces el identificador de la inspección es un UUID v7 generado en el dispositivo antes de cualquier contacto con el servidor (DT-OFF-05)

    Y se guardan localmente el autor, la fecha y hora de creación, el identificador del dispositivo, la versión del formulario preparada y las coordenadas GPS (RN-INS-12, RN-INS-13)

  Escenario: GPS lejano o ausente (RN-INS-12)

    Dado las coordenadas del dispositivo al iniciar

    Cuando están a más de 500 m de la ubicación guardada del puente

    Entonces se muestra una advertencia sin bloquear la inspección

    Pero si no hay permiso o señal de ubicación, la inspección se crea sin ubicación y queda registrado que no hubo GPS

  Escenario: Datos permanentes precargados y confirmados (RN-INS-09, RN-INS-10)

    Dado un puente con una inspección publicada guardada en el dispositivo

    Cuando se inicia la inspección

    Entonces se precargan solo los datos permanentes y el usuario debe confirmarlos de forma explícita antes de continuar

    Y los campos de daño inician siempre vacíos

  Escenario: El mismo formulario, sin red (DT-FE-07, DT-FE-08)

    Cuando el inspector llena la inspección

    Entonces el formulario se genera desde el esquema guardado en Dexie, con la grilla táctil y «Sin daños en este elemento», sin ninguna llamada a la red

    Y la validación de comodidad del cliente funciona igual que con conexión (RN-FRM-08)

  Escenario: Cada cambio se guarda en el dispositivo (RN-OFF-03)

    Cuando el inspector modifica un campo

    Entonces el cambio queda guardado en Dexie en menos de un segundo y se ve «Guardado en este dispositivo a las hh:mm»

    Y, si cierra el navegador, apaga el teléfono o pierde la batería, al volver a abrir la aplicación la inspección está tal como la dejó

  Escenario: Pendiente de sincronizar a la vista

    Dado una inspección guardada solo en el dispositivo

    Cuando el inspector abre «Mis inspecciones»

    Entonces aparece junto a las del servidor con la etiqueta «Pendiente de sincronizar», con texto y ícono, no solo con color (DT-FE-13)

  Escenario: Fecha de la inspección (RN-INS-03)

    Cuando la fecha es futura o anterior al año de construcción del puente

    Entonces el formulario la rechaza antes de guardar

  Escenario: La versión del formulario queda fija (RN-FRM-02)

    Dado una inspección iniciada sin conexión con la versión 3

    Cuando el Administrador publica la versión 4

    Entonces la inspección se completa y se sincroniza con la versión 3

  Escenario: IC preliminar no disponible (DT-ARQ-06)

    Cuando el inspector consulta el IC sin conexión

    Entonces ve «El IC preliminar se calcula en el servidor y estará disponible al sincronizar»

    Y la fórmula del IC no existe en el código de Angular

  Escenario: Fotografías en este sprint

    Cuando el inspector intenta adjuntar una fotografía sin conexión

    Entonces la sección de fotografías le indica que se habilita con conexión hasta Sprint 4

    Y enviar a revisión o publicar exige conexión

  Escenario: Eliminar un borrador que nunca se sincronizó

    Dado una inspección que solo existe en el dispositivo

    Cuando el autor la elimina

    Entonces se borra del dispositivo y no se crea nada en el servidor

  Escenario: Espacio insuficiente

    Dado un dispositivo sin espacio para guardar un cambio

    Entonces la aplicación avisa con un texto claro y conserva lo último guardado

  Escenario: Solo lo autorizado

    Dado un Estudiante

    Cuando intenta iniciar sin conexión un puente que no está en su salida preparada

    Entonces la aplicación no lo ofrece y no puede crearlo

  Escenario: Persistencia comprobada

    Entonces una prueba automatizada recarga la página en medio del llenado y comprueba que ningún dato se perdió{noformat}

### Status

En revisión

### Status category

In Progress

### Priority

Highest

### Story point estimate

8

### Sprint

Sprint 3

### Labels

Catedrático, Estudiante, Profesional-Externo

### Parent

Épica J — Trabajo sin conexión (fase 1)

### Assignee

Dany Noe DeLeon Santizo

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

18/Oct/26 1:04 AM

### Updated

20/Oct/26 10:35 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 10:20 PM

### Last Viewed

19/Oct/26 10:20 PM

### Watchers

1

### Votes

0

### Rank

0|i000bp:

---

## HU-043 — Sistema reconoce las operaciones repetidas mediante una clave de idempotencia {#scrum-60}
**Jira:** [SCRUM-60](https://ayd1-g1-team.atlassian.net/browse/SCRUM-60)

### Issue Type

Story

### Description

*Historia*

Como Sistema, quiero reconocer las operaciones repetidas mediante una clave de idempotencia y devolver el resultado de la original, para que un reintento tras un corte de red nunca duplique una inspección ni repita un envío (RN-OFF-08, RN-OFF-09; DT-OFF-07, DT-BD-05, DT-BE-06).

*Dependencias:* HU-024 y HU-025 (las operaciones que se protegen). La tabla {{operacion_idempotente}} ya existe, con restricción única por usuario y clave. Es la base de la que dependen HU-041 y HU-044, por eso arranca hoy, día 0.

*Criterios de aceptación*

{noformat}Característica: Operaciones idempotentes

  Escenario: Operaciones protegidas

    Dado las operaciones que escriben datos de inspección

    Entonces crear una inspección, guardar sus datos, enviarla, reenviarla y registrar una salida preparada aceptan el encabezado Idempotency-Key

    Y las que envía la cola de sincronización siempre lo llevan

  Escenario: Primera vez

    Dado una petición con una Idempotency-Key nueva

    Cuando se procesa

    Entonces se reserva la clave con usuario, método, ruta y huella de la solicitud, se ejecuta la operación y se guardan el código y el cuerpo de la respuesta

  Escenario: Repetición exacta

    Dado una clave ya completada y una solicitud idéntica

    Cuando llega de nuevo

    Entonces se devuelve el mismo código y el mismo cuerpo con el encabezado Idempotent-Replay: true

    Y la operación no se ejecuta otra vez ni crea duplicados

  Escenario: Misma clave, solicitud distinta

    Dado una clave ya usada con otra solicitud

    Cuando llega con un cuerpo o una ruta diferentes

    Entonces el sistema responde 422 en formato RFC 7807 explicando que la clave ya se usó con otra solicitud

  Escenario: Dos peticiones simultáneas con la misma clave

    Dado una clave cuya operación todavía está EN_PROCESO

    Cuando llega otra petición con esa clave

    Entonces el sistema responde 409 con el encabezado Retry-After y no ejecuta nada

    Y una prueba con PostgreSQL real lanza dos peticiones a la vez y comprueba que solo una tiene efecto

  Escenario: La clave pertenece al usuario

    Dado la misma clave usada por dos usuarios distintos

    Entonces cada uno tiene su propia operación y ninguno ve la respuesta del otro

  Escenario: Identificador generado por el cliente (DT-BD-05)

    Dado una inspección creada con un id UUID v7 del cliente

    Cuando se repite la creación con el mismo id, el mismo autor y la misma solicitud

    Entonces se devuelve la inspección existente y no se crea otra

    Pero si el id pertenece a otro usuario, el sistema responde 409 sin revelar datos ajenos

  Escenario: Qué respuestas se recuerdan

    Cuando la operación termina con éxito o con un error de validación determinista (2xx o 4xx)

    Entonces la respuesta se guarda para las repeticiones

    Pero si termina con un error del servidor (5xx), la clave se libera para que el reintento sí se ejecute

  Escenario: Vigencia de la clave

    Cuando se reserva una clave

    Entonces se guarda su fecha de expiración, 7 días después (valor provisional)

    Y la limpieza de claves vencidas queda programada para Sprint 4

  Escenario: Divergencia de versión al guardar un borrador

    Dado un borrador guardado con una versión desactualizada

    Cuando llega la petición

    Entonces el sistema responde 409 y no pierde datos, como base de la resolución de conflictos de Sprint 4 (RN-OFF-09)

  Escenario: Sin datos sensibles en la bitácora (DT-SEC-12)

    Cuando se registra una operación idempotente

    Entonces la bitácora y los logs guardan la clave y el resultado, nunca el cuerpo de la solicitud

  Escenario: Contrato documentado

    Entonces el encabezado y sus respuestas 409 y 422 aparecen en OpenAPI{noformat}

### Status

En revisión

### Status category

In Progress

### Priority

Highest

### Story point estimate

5

### Sprint

Sprint 3

### Labels

Sistema

### Parent

Épica J — Trabajo sin conexión (fase 1)

### Assignee

KENNY SALAZAR

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

18/Oct/26 1:04 AM

### Updated

19/Oct/26 1:19 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 1:19 AM

### Last Viewed

18/Oct/26 9:50 PM

### Watchers

1

### Votes

0

### Rank

0|i000c0:

---

## HU-044 — Inspector sincroniza sus inspecciones al recuperar la conexión {#scrum-61}
**Jira:** [SCRUM-61](https://ayd1-g1-team.atlassian.net/browse/SCRUM-61)

### Issue Type

Story

### Description

*Historia*

Como inspector, quiero que mis inspecciones guardadas sin conexión se suban solas cuando recupere la señal y ver siempre cuántas faltan, para no perder trabajo ni tener que recordar qué ya subí (RN-OFF-04, RN-OFF-05, RN-OFF-06, RN-OFF-07, RN-OFF-08; DT-OFF-06, DT-OFF-07, DT-FE-10, DT-CAL-04).

*Dependencias:* HU-042 (lo que se sincroniza), HU-043 (idempotencia). Incluye la base de las pruebas E2E con Playwright (DT-CAL-04); los escenarios con red degradada son de Sprint 4.

*Criterios de aceptación*

{noformat}Característica: Sincronización de datos de inspección

  Escenario: Encolar lo que se guarda (DT-OFF-06)

    Dado una inspección guardada o modificada en el dispositivo

    Cuando se confirma el guardado local

    Entonces se registra una operación en la cola de sincronización de Dexie con su propia Idempotency-Key (UUID v7)

    Y la cola es persistente y ordenada: sobrevive al cierre de la aplicación y respeta el orden de creación

  Escenario: Sincronización automática (RN-OFF-04)

    Dado operaciones pendientes y un dispositivo que recupera la conexión

    Cuando la aplicación detecta que hay red y el servidor responde

    Entonces empieza a subirlas sola, y también al abrir la aplicación con conexión

  Escenario: Sincronización a demanda (RN-OFF-04)

    Cuando el usuario pulsa «Sincronizar ahora»

    Entonces se procesa la cola de inmediato y se muestra el resultado de cada operación

  Escenario: Orden de subida (RN-OFF-05)

    Dado una inspección nueva con cambios posteriores

    Cuando se sincroniza

    Entonces primero se crea la inspección y después se suben sus datos, en ese orden

    Y las fotografías se suben después de los datos, una por una, a partir de Sprint 4

  Escenario: Reintento con espera creciente (DT-OFF-06)

    Dado una operación que falla por red o por un error 5xx

    Cuando se reintenta

    Entonces espera 1, 2, 4, 8 y 16 segundos, con un pequeño margen aleatorio, hasta 5 intentos

    Y al quinto intento fallido queda marcada «Requiere intervención» con el mensaje del error y el botón «Reintentar»

  Escenario: Errores que no se reintentan solos

    Dado una operación rechazada con un 4xx de validación o de autorización

    Entonces no se reintenta automáticamente y queda «Requiere intervención» con el mensaje RFC 7807 traducido

    Pero un 401 renueva el token una vez y reintenta

  Escenario: Reanudar tras un corte

    Dado la aplicación cerrada o la red perdida a mitad de una subida

    Cuando se vuelve a abrir con conexión

    Entonces la operación se retoma con la misma Idempotency-Key y no se duplica nada (RN-OFF-08)

  Escenario: Lo no confirmado nunca se borra (RN-OFF-07)

    Cuando una operación no ha recibido confirmación del servidor

    Entonces la copia local se conserva siempre

    Y solo lo confirmado se marca como sincronizado

  Escenario: Liberar lo sincronizado (RN-OFF-07)

    Dado inspecciones ya sincronizadas

    Cuando el usuario elige «Liberar espacio»

    Entonces se borran del dispositivo solo las confirmadas

  Escenario: Indicador permanente (RN-OFF-06)

    Cuando el usuario está en cualquier pantalla

    Entonces el encabezado muestra el estado de conexión y lo pendiente en texto: «Sin conexión · 2 inspecciones y 0 archivos por subir» o «Todo sincronizado»

    Y pulsarlo lleva a la página de diagnóstico existente

  Escenario: Peticiones hechas sin conexión (DT-FE-10)

    Dado un intento de guardar una inspección sin conexión

    Entonces el interceptor lo encola en lugar de fallar y la pantalla confirma «Guardado para sincronizar»

  Escenario: Repetir una respuesta perdida no duplica

    Dado una creación que llegó al servidor pero cuya respuesta se perdió

    Cuando la cola repite la petición con la misma clave

    Entonces existe una sola inspección y la operación se marca sincronizada

  Escenario: Prueba E2E base con Playwright (DT-CAL-04)

    Dado el backend y el frontend levantados

    Entonces una prueba prepara la salida, pone el navegador sin conexión, crea y llena una inspección, reconecta y verifica que existe una sola en el servidor y que el indicador queda en cero

    Y el comando para ejecutarla queda documentado; su ejecución en el CI se decide en Sprint 4

  Escenario: Cola de otro usuario

    Dado operaciones pendientes de otra persona en el mismo dispositivo

    Cuando inicia sesión alguien distinto

    Entonces no se envían con su sesión y esperan a que su dueño vuelva a entrar{noformat}

### Status

En revisión

### Status category

In Progress

### Priority

Highest

### Story point estimate

8

### Sprint

Sprint 3

### Labels

Catedrático, Estudiante, Profesional-Externo

### Parent

Épica J — Trabajo sin conexión (fase 1)

### Assignee

KENNY SALAZAR

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

18/Oct/26 1:04 AM

### Updated

20/Oct/26 10:35 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

20/Oct/26 11:21 AM

### Last Viewed

20/Oct/26 10:55 AM

### Watchers

1

### Votes

0

### Rank

0|i000c3:

---

## HU-045 — Administrador gestiona el catálogo provisional de reglas daño → intervención {#scrum-62}
**Jira:** [SCRUM-62](https://ayd1-g1-team.atlassian.net/browse/SCRUM-62)

### Issue Type

Story

### Description

*Historia*

Como Administrador, quiero un catálogo de reglas que relacione cada tipo de daño con una intervención sugerida, para que el sistema pueda proponer órdenes de mantenimiento cuando se publique una inspección (RN-MTO-02, RN-MTO-03; DT-BD-02, DT-BE-12).

*Dependencias:* HU-016 y HU-021 (esquema {{sieca-v1}} y versión activa). La tabla {{regla_mantenimiento}} ya existe y es versionada por código, revisión y versión del formulario.

*Criterios de aceptación*

{noformat}Característica: Catálogo provisional de reglas daño a intervención

  Escenario: Semilla inicial rotulada como provisional

    Dado la versión activa del formulario SIECA

    Cuando el sistema arranca sobre una base sin reglas para esa versión

    Entonces carga una semilla de unas 18 reglas derivadas de los indicadores de daño del esquema

    Y cada regla queda marcada «Provisional · sin validar» porque no tiene validador

  Escenario: Cada regla es completa

    Cuando se consulta una regla

    Entonces trae un código, su número de revisión, la versión del formulario, el elemento_ref, la condición, el tipo de mantenimiento, la intervención sugerida, la regla de prioridad base y si está activa

  Escenario: Condición expresada como dato

    Dado una condición como «el indicador de severidad del elemento es MODERADO o peor»

    Entonces se guarda como JSON con el indicador y el umbral, no como código

    Y el formato de la condición queda documentado en el contrato entre frentes

  Esquema del escenario: Tipología del manual (RN-MTO-03)

    Cuando se revisa el catálogo sembrado

    Entonces existe al menos una regla de tipo <tipo>

    Ejemplos:

      | tipo       |

      | RUTINARIO  |

      | PREVENTIVO |

      | CORRECTIVO |

      | EMERGENCIA |

  Escenario: Los daños graves proponen emergencia

    Dado un daño crítico del esquema, como socavación con cimentación expuesta o asentamiento de la estructura

    Entonces su regla es de tipo EMERGENCIA con la prioridad base más alta

  Escenario: Consultar el catálogo

    Cuando un Administrador o un Catedrático abre el catálogo

    Entonces ve las reglas paginadas, con filtros por tipo, elemento, versión del formulario y activa

    Y la lista indica con texto cuáles son provisionales

  Escenario: Cambiar una regla crea una revisión nueva

    Dado una regla existente

    Cuando el Administrador la edita

    Entonces se crea una revisión nueva con el número siguiente y la anterior se conserva

    Y solo una revisión por código y versión puede estar activa

  Escenario: Activar o desactivar

    Cuando el Administrador desactiva una regla

    Entonces deja de generar propuestas nuevas, pero las órdenes ya creadas no cambian

  Escenario: Validar una regla

    Dado una regla que el Catedrático de Puentes ya aprobó fuera del sistema

    Cuando el Administrador la marca como validada

    Entonces se guardan quién y cuándo, y deja de rotularse como provisional

  Escenario: La regla debe apuntar a algo real

    Cuando se guarda una regla con un elemento_ref o un indicador que no existen en el esquema de su versión

    Entonces el sistema responde 422 en formato RFC 7807

  Escenario: Versión sin catálogo

    Dado una versión del formulario sin reglas activas

    Entonces la publicación de inspecciones sigue funcionando y no se generan propuestas

    Y el Administrador ve el aviso «Esta versión no tiene reglas de mantenimiento activas»

  Escenario: Autorización

    Cuando un Catedrático intenta crear, editar, activar o validar una regla

    Entonces el sistema responde 403 Forbidden, porque solo el Administrador escribe y el Catedrático consulta

    Y los demás roles reciben 403 y el Visitante 401

  Escenario: Auditoría (RN-USR-09)

    Cuando se crea, edita, activa o valida una regla

    Entonces se registran el usuario, la fecha y los valores anterior y posterior

  Escenario: Decisión documentada

    Entonces un ADR registra que el catálogo es provisional, cómo se construyó la semilla y qué falta para validarlo{noformat}

### Status

En revisión

### Status category

In Progress

### Priority

High

### Story point estimate

5

### Sprint

Sprint 3

### Labels

Administrador

### Parent

Épica K — Mantenimiento (fase 1)

### Assignee

Rony Rojas Aguilar

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

18/Oct/26 1:05 AM

### Updated

18/Oct/26 8:10 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 8:10 PM

### Watchers

1

### Votes

0

### Rank

0|i000cd:

---

## HU-046 — Sistema propone órdenes de mantenimiento al publicarse una inspección {#scrum-63}
**Jira:** [SCRUM-63](https://ayd1-g1-team.atlassian.net/browse/SCRUM-63)

### Issue Type

Story

### Description

*Historia*

Como Sistema, quiero proponer órdenes de mantenimiento a partir de los daños de una inspección recién publicada, para que el Catedrático o el Administrador decidan cuáles se ejecutan sin tener que revisar cada inspección a mano (RN-MTO-01, RN-MTO-02, RN-MTO-03; RN-INS-07, RN-USR-09).

*Dependencias:* HU-045 (reglas), HU-026 y HU-020 (publicación y resultado del IC), HU-040 (aviso). Se ejecuta después de la confirmación de la transacción de publicación, para que un fallo del mantenimiento nunca bloquee una publicación.

*Criterios de aceptación*

{noformat}Característica: Propuestas de mantenimiento al publicar

  Escenario: Se dispara después de publicar

    Dado una inspección que acaba de pasar a PUBLICADA

    Cuando la transacción de publicación se confirma

    Entonces el sistema evalúa las reglas en una transacción propia

    Y si la evaluación falla, la publicación no se revierte, el error se registra y la generación se puede repetir

  Escenario: Evaluar las reglas activas

    Dado las reglas activas de la versión del formulario con la que se levantó la inspección

    Cuando se evalúan sobre los datos de la inspección

    Entonces cada condición que se cumple genera una orden en estado PROPUESTA, nunca aceptada ni programada (RN-MTO-02)

  Escenario: Qué trae cada propuesta (RN-MTO-01, RN-MTO-03)

    Cuando se crea una propuesta

    Entonces guarda el puente, la inspección de origen, el resultado del IC de origen, la regla y su revisión, el elemento_ref y la instancia si aplica

    Y guarda el tipo (RUTINARIO, PREVENTIVO, CORRECTIVO o EMERGENCIA), la descripción de la intervención y el fundamento: qué valor se encontró y qué regla lo disparó

  Escenario: Prioridad inicial

    Cuando se crea una propuesta

    Entonces su prioridad sugerida y su prioridad confirmada empiezan con la base que declara la regla: BAJA, MEDIA, ALTA o CRITICA

    Pero el cálculo con el IC y el peso del elemento llega en Sprint 4 (RN-MTO-06)

  Escenario: Los elementos sanos no generan nada

    Dado un elemento marcado «Sin daños en este elemento» o con el valor sano de todos sus indicadores

    Entonces no se genera ninguna propuesta

  Escenario: Una inspección sin daños no es un error

    Dado una inspección sin ninguna condición cumplida

    Entonces no se crea ninguna propuesta y el proceso termina sin error

  Escenario: Sin duplicados

    Dado una orden abierta (PROPUESTA, PROGRAMADA o EN_EJECUCION) del mismo puente, elemento y regla

    Cuando una inspección nueva vuelve a cumplir esa condición

    Entonces no se crea otra orden y la existente queda enlazada como «ya existe» en el fundamento

    Y repetir la generación de la misma inspección tampoco duplica nada, por la clave de generación única

  Escenario: Regenerar

    Dado una publicación cuya generación falló

    Cuando un Administrador pide regenerar las propuestas de esa inspección

    Entonces se evalúan de nuevo y solo se crean las que faltan

  Escenario: Aviso a quien decide (RN-MTO-02)

    Cuando se crean propuestas

    Entonces los Administradores activos y el Catedrático que publicó o revisó la inspección reciben un aviso con el número de propuestas y un enlace (HU-040)

    Y el aviso es uno solo por inspección

  Escenario: Se ven desde la inspección

    Cuando un usuario autenticado abre una inspección publicada

    Entonces ve la lista de órdenes que originó, con su estado en texto

  Escenario: Las correcciones no duplican

    Dado una corrección publicada de una inspección superada

    Entonces solo genera propuestas para lo que no tenga ya una orden abierta

  Escenario: Rendimiento

    Dado una inspección con los 45 elementos evaluables

    Entonces la evaluación completa tarda menos de un segundo

  Escenario: Pruebas con datos reales

    Entonces las pruebas usan PostgreSQL con Testcontainers y una inspección publicada de ejemplo, no una simulación de la base

  Escenario: Auditoría (RN-USR-09)

    Cuando se crean propuestas

    Entonces se registra el proceso como actor, la inspección de origen y las órdenes creadas{noformat}

### Status

En revisión

### Status category

In Progress

### Priority

Highest

### Story point estimate

8

### Sprint

Sprint 3

### Labels

Sistema

### Parent

Épica K — Mantenimiento (fase 1)

### Assignee

Herberth Julian Reyes Pacajoj

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

18/Oct/26 1:05 AM

### Updated

18/Oct/26 10:22 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 6:33 PM

### Watchers

1

### Votes

0

### Rank

0|i0009o:

---

## HU-047 — Catedrático o Administrador gestiona el ciclo de vida de las órdenes {#scrum-64}
**Jira:** [SCRUM-64](https://ayd1-g1-team.atlassian.net/browse/SCRUM-64)

### Issue Type

Story

### Description

*Historia*

Como Catedrático o Administrador, quiero aceptar y programar, iniciar, descartar o cancelar órdenes de mantenimiento, y crear órdenes manuales, para gestionar las intervenciones de cada puente y conservar el motivo de las que no se hacen (RN-MTO-01, RN-MTO-03, RN-MTO-04, RN-MTO-09; DT-BE-07, DT-BD-11, DT-CAL-01).

*Dependencias:* HU-046 (órdenes propuestas), HU-040 (avisos). La máquina de las órdenes es la segunda con 100 % de cobertura obligatoria (DT-CAL-01); se empieza hoy, día 0, porque HU-048 y HU-049 dependen de ella.

*Criterios de aceptación*

{noformat}Característica: Ciclo de vida de las órdenes de mantenimiento

  Esquema del escenario: Transiciones válidas (RN-MTO-04)

    Dado una orden en estado <origen>

    Cuando se ejecuta la acción <accion>

    Entonces pasa al estado <destino>, cumpliendo <requisito>

    Ejemplos:

      | origen       | accion                | destino      | requisito                                                      |

      | PROPUESTA    | aceptar y programar   | PROGRAMADA   | fecha programada no pasada; se guardan quién aceptó y cuándo   |

      | PROPUESTA    | descartar             | DESCARTADA   | motivo obligatorio                                             |

      | PROGRAMADA   | iniciar ejecución     | EN_EJECUCION | se crea el registro de ejecución con la fecha de inicio        |

      | PROGRAMADA   | cancelar              | CANCELADA    | motivo obligatorio                                             |

      | EN_EJECUCION | cerrar como ejecutada | EJECUTADA    | fecha, responsable y evidencia (HU-048)                        |

  Escenario: Transiciones no permitidas

    Dado una orden en cualquier estado

    Cuando se intenta una transición que no está en la tabla anterior

    Entonces el sistema responde 409 y el estado no cambia

    Y se prueban, entre otras, PROPUESTA a EN_EJECUCION directo, PROPUESTA a CANCELADA, EN_EJECUCION a CANCELADA y, desde EJECUTADA, DESCARTADA o CANCELADA, cualquier transición

  Escenario: Única puerta de entrada (DT-BE-07)

    Dado el código del backend

    Entonces ningún controlador asigna el estado de una orden

    Y todo cambio pasa por el servicio de transiciones, verificado por una prueba

  Escenario: Las órdenes no se borran (RN-MTO-09, DT-BD-11)

    Dado una orden DESCARTADA o CANCELADA

    Entonces se conserva con su motivo, quién la cerró y cuándo

    Y no existe ninguna operación que la elimine

  Escenario: Motivo obligatorio

    Cuando se descarta o se cancela sin motivo

    Entonces el sistema responde 422 y el estado no cambia

  Escenario: Programar con fecha válida

    Cuando se acepta una propuesta con una fecha anterior a hoy

    Entonces el sistema responde 422

  Esquema del escenario: Tipología obligatoria (RN-MTO-03)

    Cuando se crea una orden con tipo <tipo>

    Entonces el resultado es <resultado>

    Ejemplos:

      | tipo       | resultado         |

      | RUTINARIO  | válido            |

      | PREVENTIVO | válido            |

      | CORRECTIVO | válido            |

      | EMERGENCIA | válido            |

      | URGENTE    | 422 de validación |

  Escenario: Crear una orden manual (RN-MTO-01)

    Dado un Catedrático o un Administrador

    Cuando crea una orden para un puente activo con tipo, descripción y prioridad, y opcionalmente un elemento_ref y la inspección que la originó

    Entonces nace en estado PROPUESTA con registro de quien la creó

    Y si indica una inspección, esta debe ser del mismo puente, o el sistema responde 409

  Escenario: Puente inactivo

    Dado un puente dado de baja

    Cuando se intenta crear una orden nueva

    Entonces el sistema responde 409

    Pero las órdenes que ya tenía se pueden consultar

  Escenario: Historial de estados

    Cuando una orden cambia de estado

    Entonces se agrega una fila al historial con secuencia, estado anterior y nuevo, usuario, motivo y fecha

    Y el historial se consulta ordenado y nunca se modifica

  Escenario: Cambios simultáneos

    Dado dos usuarios que actúan a la vez sobre la misma orden

    Entonces solo una transición tiene efecto y la otra recibe 409, por la versión optimista

  Esquema del escenario: Quién puede cambiar el estado

    Cuando <usuario> intenta una transición

    Entonces el resultado es <resultado>

    Ejemplos:

      | usuario             | resultado        |

      | Catedrático         | permitido        |

      | Administrador       | permitido        |

      | Profesional externo | 403 Forbidden    |

      | Estudiante          | 403 Forbidden    |

      | Visitante           | 401 Unauthorized |

  Escenario: Avisos

    Cuando una orden se programa, se descarta o se cancela

    Entonces quien la creó o la propuso recibe un aviso (HU-040)

  Escenario: Cobertura de la máquina (DT-CAL-01)

    Entonces la suite cubre todas las transiciones válidas e inválidas, y la regla de JaCoCo exige 100 % de cobertura de la máquina de estados de las órdenes

  Escenario: Auditoría (RN-USR-09)

    Cuando cambia el estado de una orden o se crea una manual

    Entonces se registran el usuario, la fecha y los valores anterior y posterior{noformat}

### Status

En revisión

### Status category

In Progress

### Priority

Highest

### Story point estimate

8

### Sprint

Sprint 3

### Labels

Administrador, Catedrático

### Parent

Épica K — Mantenimiento (fase 1)

### Assignee

Miguel Angel Quemé Gómez

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

18/Oct/26 1:05 AM

### Updated

18/Oct/26 8:27 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

18/Oct/26 6:27 PM

### Watchers

1

### Votes

0

### Rank

0|i0009s:

---

## HU-048 — Catedrático o Administrador ejecuta una orden y la cierra con evidencia {#scrum-65}
**Jira:** [SCRUM-65](https://ayd1-g1-team.atlassian.net/browse/SCRUM-65)

### Issue Type

Story

### Description

*Historia*

Como Catedrático o Administrador, quiero iniciar una orden y cerrarla como ejecutada registrando la fecha, el responsable y fotografías de evidencia, para dejar constancia verificable de que la intervención se realizó (RN-MTO-05, RN-MTO-09, RN-ARC-02; DT-SEC-08, DT-SEC-09, DT-ALM-03).

*Dependencias:* HU-047 (ciclo de vida) y HU-028 (servicio de archivos, miniaturas y URL firmadas, con el tipo de uso {{EVIDENCIA_MANTENIMIENTO}}). Aunque RN-MTO-05 parece de cierre, se hace en este sprint para que la máquina de estados de las órdenes quede completa con su condición de salida y alcance el 100 % de cobertura.

*Criterios de aceptación*

{noformat}Característica: Ejecución de una orden con evidencia

  Escenario: Iniciar la ejecución

    Dado una orden PROGRAMADA

    Cuando el Catedrático o el Administrador la inicia

    Entonces pasa a EN_EJECUCION y se crea su registro de ejecución con la fecha y hora de inicio

    Y puede indicar desde ese momento al responsable de la intervención

  Escenario: Responsable de la intervención (RN-MTO-05)

    Cuando registra al responsable

    Entonces puede elegir un usuario del sistema o escribir el nombre de una persona o empresa externa

    Y debe existir al menos uno de los dos para poder cerrar la orden

  Escenario: Adjuntar evidencia fotográfica

    Dado una orden EN_EJECUCION

    Cuando el usuario sube una fotografía

    Entonces la imagen se comprime en el dispositivo, el servidor valida su tipo real por la firma binaria (JPEG o WebP), genera su propio nombre y crea la miniatura

    Y queda ligada a la orden como EVIDENCIA_MANTENIMIENTO, con un elemento_ref opcional

  Escenario: Archivos inválidos (DT-SEC-08)

    Cuando se sube un archivo que no es una imagen JPEG o WebP aunque su extensión lo diga, o que excede el tamaño permitido

    Entonces el sistema responde 415 o 422 y no guarda nada

  Escenario: Retirar evidencia antes de cerrar

    Dado una orden EN_EJECUCION con una evidencia subida por error

    Cuando el usuario la retira

    Entonces el archivo queda RETIRADO con su motivo y deja de contar como evidencia

  Escenario: Cerrar como ejecutada (RN-MTO-05)

    Dado una orden EN_EJECUCION con fecha de ejecución, responsable y al menos una fotografía de evidencia DISPONIBLE

    Cuando se cierra como ejecutada

    Entonces pasa a EJECUTADA, se guardan la fecha de ejecución, el responsable y quién la cerró, y se agrega al historial

  Esquema del escenario: Cierre incompleto (RN-MTO-05)

    Dado una orden EN_EJECUCION a la que le falta <faltante>

    Cuando se intenta cerrar como ejecutada

    Entonces el sistema responde 422 con el detalle de lo que falta y la orden sigue EN_EJECUCION

    Ejemplos:

      | faltante                   |

      | la fecha de ejecución      |

      | el responsable             |

      | la fotografía de evidencia |

  Escenario: Fecha de ejecución coherente

    Cuando la fecha de ejecución es futura o anterior al día en que se inició

    Entonces el sistema responde 422

  Escenario: Lo ejecutado es inmutable

    Dado una orden EJECUTADA

    Cuando alguien intenta cambiar su fecha, su responsable o retirar su evidencia

    Entonces el sistema responde 409

  Escenario: Evidencia con acceso protegido (DT-SEC-09)

    Cuando se abre una orden con evidencia

    Entonces cada fotografía se sirve con una URL firmada de vigencia limitada o por un extremo autorizado

    Y un visitante recibe 401

  Escenario: Almacenamiento no disponible

    Dado que el almacenamiento de objetos no responde

    Cuando se sube una evidencia o se cierra la orden

    Entonces el sistema responde 503 y el estado de la orden no cambia

  Escenario: Cierre atómico

    Cuando se cierra la orden

    Entonces el cambio de estado, el registro de ejecución y el historial se guardan en una sola transacción

  Escenario: Autorización

    Entonces solo el Catedrático y el Administrador inician, adjuntan y cierran; los demás roles reciben 403 y el Visitante 401

  Escenario: Auditoría (RN-USR-09)

    Cuando se inicia, se adjunta evidencia o se cierra

    Entonces se registran el usuario, la fecha y los valores anterior y posterior{noformat}

### Status

En revisión

### Status category

In Progress

### Priority

High

### Story point estimate

5

### Sprint

Sprint 3

### Labels

Administrador, Catedrático

### Parent

Épica K — Mantenimiento (fase 1)

### Assignee

Angel Abelino Son Vicente

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

18/Oct/26 1:05 AM

### Updated

19/Oct/26 8:41 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 6:00 PM

### Watchers

1

### Votes

0

### Rank

0|i0009m:

---

## HU-049 — Usuario consulta y gestiona el mantenimiento desde pantallas {#scrum-66}
**Jira:** [SCRUM-66](https://ayd1-g1-team.atlassian.net/browse/SCRUM-66)

### Issue Type

Story

### Description

*Historia*

Como Catedrático o Administrador, quiero pantallas para ver las propuestas y las órdenes, filtrarlas y gestionarlas desde una bandeja general y desde la ficha de cada puente, y como usuario autenticado quiero consultar las órdenes de un puente, para dar seguimiento al mantenimiento sin recurrir a la base de datos (RN-MTO-01 a RN-MTO-05, RN-MTO-09, RN-USR-10; DT-FE-06, DT-FE-09, DT-FE-11, DT-FE-12, DT-FE-13).

*Dependencias:* HU-045, HU-046, HU-047 y HU-048. Se arranca el sábado contra datos de ejemplo y se conecta al backend real el domingo.

*Criterios de aceptación*

{noformat}Característica: Pantallas de mantenimiento

  Escenario: Bandeja general

    Dado un Catedrático o un Administrador

    Cuando abre «Mantenimiento»

    Entonces ve las órdenes en pestañas: Propuestas, Programadas, En ejecución, Ejecutadas y Descartadas o canceladas

    Y cada pestaña muestra su total y está paginada con un máximo de 100 por página

  Escenario: Filtros

    Cuando filtra por tipo, puente, prioridad o rango de fechas

    Entonces la lista muestra solo lo que cumple todos los filtros combinados

  Escenario: Estado y tipo con texto (DT-FE-13)

    Cuando se muestra una orden

    Entonces su estado y su tipo aparecen con etiqueta textual, además del color

    Y una orden que nació de una regla sin validar muestra «Provisional»

  Escenario: Detalle de una orden

    Cuando abre una orden

    Entonces ve el puente, la inspección de origen con enlace, el elemento, el fundamento de la propuesta, el tipo, la prioridad sugerida y la confirmada, la descripción y la línea de tiempo del historial

    Y, si hubo ejecución, su fecha, su responsable y las miniaturas de la evidencia

  Escenario: Solo las acciones válidas

    Dado una orden en cierto estado

    Cuando el usuario autorizado abre su detalle

    Entonces ve únicamente los botones que la máquina de estados permite: Programar, Descartar, Iniciar ejecución, Cancelar, Subir evidencia o Cerrar como ejecutada

  Escenario: Programar, descartar y cancelar

    Cuando elige Programar, Descartar o Cancelar

    Entonces se abre un diálogo: Programar pide la fecha (dd/MM/yyyy) y Descartar y Cancelar piden el motivo, que es obligatorio

    Y los errores del servidor se muestran junto al campo, traducidos

  Escenario: Cerrar como ejecutada

    Cuando elige «Cerrar como ejecutada»

    Entonces el formulario pide fecha de ejecución, responsable y evidencia, y muestra qué falta antes de enviar

  Escenario: Ficha del puente

    Cuando un usuario autenticado abre la ficha de un puente

    Entonces ve la pestaña «Mantenimiento» con las órdenes abiertas y el historial de las cerradas

    Y solo el Catedrático y el Administrador ven los botones de acción

  Escenario: Orden manual

    Cuando el Catedrático o el Administrador pulsa «Nueva orden» en la ficha de un puente

    Entonces completa tipo, descripción, prioridad y, si quiere, elemento e inspección de origen, y la orden aparece como propuesta

  Escenario: Catálogo de reglas

    Cuando el Administrador abre «Catálogo de mantenimiento»

    Entonces ve las reglas con el rótulo «Provisional» o «Validada» y puede crear una revisión, activar, desactivar y validar (HU-045)

  Escenario: Estados vacío, de carga y de error

    Entonces cada lista y cada detalle muestran un esqueleto mientras cargan, un mensaje claro cuando no hay datos y un mensaje con opción de reintentar cuando falla la carga

  Escenario: Visitante, Estudiante y Profesional externo (RN-USR-10)

    Cuando un visitante intenta abrir la bandeja

    Entonces es enviado al inicio de sesión

    Y un Estudiante o un Profesional externo ven las órdenes en la ficha del puente, pero no la bandeja ni las acciones

  Escenario: Guardia de ruta (DT-FE-11)

    Entonces la ruta de la bandeja y la del catálogo usan guardias por rol que replican la autorización del backend sin sustituirla

  Escenario: Carga diferida y tamaño (DT-FE-06)

    Entonces el módulo de mantenimiento se carga bajo demanda y el paquete inicial sigue por debajo de 500 kB

  Escenario: Uso en el teléfono (DT-FE-09)

    Cuando se usa a 375 px de ancho

    Entonces todos los controles miden al menos 44 × 44 px, las tablas se convierten en tarjetas y los diálogos caben sin desplazamiento horizontal

  Escenario: Textos y fechas (DT-FE-12)

    Entonces todos los textos viven en es.json y las fechas se muestran como dd/MM/yyyy en America/Guatemala{noformat}

### Status

En revisión

### Status category

In Progress

### Priority

High

### Story point estimate

5

### Sprint

Sprint 3

### Labels

Catedrático

### Parent

Épica K — Mantenimiento (fase 1)

### Assignee

Angel Abelino Son Vicente

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

18/Oct/26 1:05 AM

### Updated

19/Oct/26 11:34 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 8:41 PM

### Watchers

1

### Votes

0

### Rank

0|i000c1:

---

## HU-050 — Administrador carga la plantilla SIECA con un IC completo provisional y la publica sin scripts {#scrum-73}
**Jira:** [SCRUM-73](https://ayd1-g1-team.atlassian.net/browse/SCRUM-73)

### Issue Type

Story

### Description

*Historia*

Como Administrador, quiero cargar la plantilla SIECA y publicarla sin completar a mano decenas de valores, para probar el sistema desde la interfaz tal como se entregaría (RN-IC-03, RN-IC-05, RN-FRM-03, RN-FRM-06).

*Dependencias:* HU-018 y HU-019 (cálculo y configuración del IC), HU-021 (versiones del formulario y mapeo de campos). Es la base de HU-051 y desbloquea las pruebas de todos los bloques del Sprint 4. Los valores son provisionales y los puede reemplazar el Catedrático desde la aplicación (HU-053 y HU-054).

*Criterios de aceptación*

{noformat}Característica: Plantilla SIECA con IC completo y provisional

  Escenario: La plantilla trae todos los valores

    Dado la plantilla del IC del formulario SIECA

    Entonces trae un peso para cada uno de los 45 elementos evaluables

    Y trae la agregación, la escala de severidad, los rangos de estado y las equivalencias de todos los indicadores (booleanos, enumerados y umbrales numéricos)

    Y ningún valor queda en null

  Escenario: Pesos provisionales válidos

    Dado los 45 elementos de la plantilla

    Entonces cada peso es positivo, con hasta 6 dígitos enteros y 6 decimales

    Y los elementos portantes pesan más que el resto

  Escenario: Escala de severidad y rangos de estado (RN-IC-04)

    Entonces la escala es NO_PRESENTA 0, LEVE 1, MODERADO 2 y SEVERO 4

    Y los rangos son BUENO 75, REGULAR 50 y MALO 0, que cumplen MALO = 0 < REGULAR < BUENO ≤ 100

  Escenario: Los valores son provisionales y se ven como tales

    Dado una versión creada desde la plantilla

    Entonces su calibración se rotula «Calibración provisional · sin aprobar» en el editor y en el detalle de la versión

    Y el rótulo permanece hasta que el Catedrático la apruebe (HU-054)

  Escenario: Las reglas de anulación no impiden publicar (RN-IC-05)

    Dado las reglas RN-IC-05a y RN-IC-05c con decisiones pendientes

    Entonces no bloquean la publicación de la versión

    Y su nota de decisión pendiente sigue visible para quien la revise

    Y las cuatro reglas RN-IC-05a, 05b, 05c y 05d permanecen en la versión

  Escenario: Cargar y publicar en dos pasos

    Dado un usuario autorizado en el editor de versiones, sin scripts ni acceso a la base de datos

    Cuando carga la plantilla y publica la versión

    Entonces la versión queda activa y la anterior deja de estarlo (RN-FRM-04)

  Escenario: Mapeo de campos con una versión anterior (RN-FRM-06)

    Dado que ya hay una versión activa

    Cuando se publica la versión nueva

    Entonces el editor completa el mapeo de campos equivalentes, nuevos y eliminados

    Y no deja publicar mientras falte declarar alguno

  Escenario: La versión calcula el IC

    Dado la versión publicada desde la plantilla

    Cuando se calcula el IC de una inspección completa de ejemplo

    Entonces devuelve un IC y un estado

    Y una socavación con exposición de cimentación fuerza el estado MALO (RN-IC-05a)

  Escenario: Reemplazar los valores provisionales

    Dado que el Catedrático entrega valores reales

    Entonces se reemplazan en un solo archivo de la plantilla, sin cambiar código

  Escenario: Quién puede cargar la plantilla

    Cuando un Estudiante o un Profesional externo intenta cargarla o publicarla

    Entonces recibe 403

  Escenario: Auditoría (RN-USR-09)

    Cuando se carga o se publica una versión desde la plantilla

    Entonces ambas acciones quedan en la bitácora con el usuario que las hizo

  Escenario: Pruebas

    Entonces una prueba verifica que ningún valor de la plantilla queda en null

    Y otra publica la versión resultante y calcula el IC con PostgreSQL real (Testcontainers)

    Y la cobertura del cálculo del IC sigue en 100 %{noformat}

*Punto de partida:* la calibración de prueba que ya existe en el repositorio.

### Status

Por hacer

### Status category

To Do

### Priority

Highest

### Story point estimate

5

### Sprint

Sprint 4

### Labels

Administrador

### Parent

Épica L — Cierre de provisionales y aprobación académica

### Assignee

KENNY SALAZAR

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

19/Oct/26 11:57 PM

### Updated

20/Oct/26 12:17 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 11:57 PM

### Watchers

1

### Votes

0

### Rank

0|i000ed:

---

## HU-051 — Sistema siembra una versión activa del formulario en el primer arranque {#scrum-74}
**Jira:** [SCRUM-74](https://ayd1-g1-team.atlassian.net/browse/SCRUM-74)

### Issue Type

Story

### Description

*Historia*

Como Sistema, quiero que una instalación nueva arranque con una versión activa del formulario, para que la plataforma no esté vacía y se pueda probar de inmediato (RN-FRM-03, RN-FRM-04).

*Dependencias:* HU-050 (plantilla completa) y HU-045 (la siembra del catálogo de reglas sigue el mismo patrón). *Decisión del responsable:* si se siembra también en producción; se recomienda que sí, con el rótulo de provisional visible.

*Criterios de aceptación*

{noformat}Característica: Versión activa sembrada en el primer arranque

  Escenario: Base sin versión activa

    Dado una base de datos sin ninguna versión activa del formulario

    Cuando el sistema arranca

    Entonces crea y publica SIECA desde la plantilla de HU-050, con su IC provisional

    Y la versión queda activa

  Escenario: Contenido de la versión sembrada

    Cuando se siembra la versión

    Entonces trae los pesos, la escala, los rangos, las equivalencias y las reglas de anulación de la plantilla

    Y se puede calcular el IC de una inspección completa desde el primer momento

  Escenario: Ya existe una versión activa

    Dado una base con una versión activa

    Cuando el sistema arranca

    Entonces no hace nada y nunca la reemplaza (RN-FRM-04)

  Escenario: Hay versiones en borrador pero ninguna activa

    Dado una base con versiones en BORRADOR y ninguna activa

    Cuando el sistema arranca

    Entonces no modifica ni publica esos borradores

    Y siembra la versión nueva sin pisar sus datos

  Escenario: El código de versión ya existe

    Dado una versión inactiva con el código que se iba a usar

    Cuando el sistema arranca

    Entonces usa el siguiente código libre y no falla

  Escenario: Reglas de mantenimiento

    Cuando se siembra la versión

    Entonces las 18 reglas provisionales de mantenimiento se siembran sobre ella (HU-045)

  Escenario: Dos réplicas arrancan a la vez

    Dado dos instancias del servidor que arrancan al mismo tiempo

    Entonces solo una crea la versión y nunca quedan dos versiones activas (RN-FRM-04)

  Escenario: Un fallo no impide arrancar

    Dado un error al sembrar la versión

    Entonces el sistema arranca igual y registra el error

    Y el Administrador puede cargar la plantilla desde el editor (HU-050)

  Escenario: Auditoría (RN-USR-09)

    Cuando se siembra la versión

    Entonces la acción queda en la bitácora como proceso automático

  Escenario: Configurable por ambiente

    Dado la configuración del ambiente

    Entonces la siembra se puede activar o desactivar sin cambiar código

    Y el valor por defecto de cada ambiente queda documentado

  Escenario: Pruebas con datos reales

    Dado una base vacía con Testcontainers

    Cuando el sistema arranca dos veces

    Entonces tras el primer arranque hay una versión activa y el segundo no cambia nada

    Y una prueba cubre el arranque concurrente{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

Highest

### Story point estimate

3

### Sprint

Sprint 4

### Labels

Sistema

### Parent

Épica L — Cierre de provisionales y aprobación académica

### Assignee

KENNY SALAZAR

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

19/Oct/26 11:57 PM

### Updated

20/Oct/26 12:17 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 11:57 PM

### Watchers

1

### Votes

0

### Rank

0|i000eh:

---

## HU-052 — Catedrático abre el detalle de una revisión aunque el IC no pueda calcularse {#scrum-75}
**Jira:** [SCRUM-75](https://ayd1-g1-team.atlassian.net/browse/SCRUM-75)

### Issue Type

Story

### Description

*Historia*

Como Catedrático, quiero abrir el detalle de una revisión aunque el IC preliminar no se pueda calcular, para revisar la inspección de todos modos (RN-IC-07).

*Dependencias:* HU-037 (detalle de revisión, que hoy calcula el IC sin protección). El borrador de inspección ya tolera este error y se toma como referencia.

*Criterios de aceptación*

{noformat}Característica: IC preliminar tolerante en el detalle de revisión

  Escenario: La configuración del IC está incompleta

    Dado una inspección enviada cuya versión del formulario no tiene el IC completo

    Cuando el Catedrático abre el detalle de la revisión

    Entonces el detalle se abre

    Y el IC aparece como «no disponible», sin error 422

  Escenario: Los datos no alcanzan para calcularlo

    Dado una inspección enviada cuyos datos no alcanzan para calcular el IC

    Cuando el Catedrático abre el detalle de la revisión

    Entonces el detalle se abre

    Y el IC aparece como «no disponible»

  Escenario: El detalle sigue completo

    Cuando el IC no está disponible

    Entonces el formulario enviado, las observaciones y las fotografías se muestran igual que siempre (HU-037)

  Escenario: No se muestra el detalle técnico

    Cuando el cálculo falla

    Entonces el motivo queda en el registro del servidor

    Y el usuario no ve el mensaje técnico ni un código de error

  Escenario: El IC sí se puede calcular

    Dado una versión con el IC completo y datos suficientes

    Cuando el Catedrático abre el detalle

    Entonces ve el IC preliminar rotulado «PRELIMINAR», como hasta ahora (RN-IC-07)

  Escenario: Un fallo inesperado del cálculo

    Dado un error inesperado dentro del cálculo

    Entonces el detalle se abre con el IC «no disponible»

    Y el error se registra sin exponer datos de la inspección

  Escenario: La autorización no cambia

    Cuando un Catedrático que no es el revisor asignado intenta abrir el detalle

    Entonces recibe el mismo rechazo que hoy

  Escenario: Pruebas

    Entonces hay pruebas de integración con una versión sin IC completo, con datos incompletos y con una versión que sí lo tiene

    Y una prueba de regresión comprueba que el resto del detalle no cambia{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

Highest

### Story point estimate

2

### Sprint

Sprint 4

### Labels

Catedrático

### Parent

Épica L — Cierre de provisionales y aprobación académica

### Assignee

KENNY SALAZAR

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

19/Oct/26 11:57 PM

### Updated

20/Oct/26 12:17 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 11:57 PM

### Watchers

1

### Votes

0

### Rank

0|i000es:

---

## HU-053 — Catedrático configura la calibración del IC desde la aplicación {#scrum-76}
**Jira:** [SCRUM-76](https://ayd1-g1-team.atlassian.net/browse/SCRUM-76)

### Issue Type

Story

### Description

*Historia*

Como Catedrático, quiero editar los pesos, las equivalencias, las reglas de cálculo y las reglas de anulación del IC en una versión en borrador, para no depender de entrevistas ni de terceros (RN-IC-03, RN-FRM-03).

*Dependencias:* HU-019 y HU-021 (editor de versiones). *Decisión 1 del responsable:* se recomienda que editen tanto el Administrador como el Catedrático; RN-IC-03 dice que el Administrador la configura, así que se amplía sin quitarle nada.

*Criterios de aceptación*

{noformat}Característica: Calibración del IC editable por el Catedrático

  Escenario: Roles permitidos

    Dado los endpoints de pesos, escala, reglas de cálculo, reglas críticas y acta

    Cuando los invoca un Administrador o un Catedrático

    Entonces responden con éxito

  Escenario: Roles no permitidos

    Cuando los invoca un Estudiante, un Profesional externo o un usuario sin sesión

    Entonces recibe 403 o 401, según corresponda

  Escenario: Pantallas

    Dado un Catedrático autenticado

    Entonces ve el menú «Formulario SIECA» y accede al editor, a los pesos, a las reglas de cálculo y a la calibración adicional

    Y las guardas de ruta de esas pantallas lo permiten

  Escenario: Los demás roles no ven las pantallas

    Dado un Estudiante o un Profesional externo

    Entonces el menú no muestra «Formulario SIECA» y las rutas lo redirigen

    Y la guarda del frontend replica, sin sustituir, la autorización del backend (DT-FE-11)

  Escenario: Una versión publicada es inmutable (RN-FRM-03)

    Dado una versión publicada

    Cuando cualquiera de los dos roles intenta modificarla

    Entonces recibe un conflicto y debe crear una versión nueva

  Escenario: Edición simultánea

    Dado dos usuarios que editan la misma versión en borrador

    Cuando el segundo guarda después del primero sin haber recargado

    Entonces recibe un conflicto de versión y no pisa los cambios del primero

  Escenario: Validaciones al guardar

    Cuando el usuario guarda un peso no positivo, un rango fuera de MALO = 0 < REGULAR < BUENO ≤ 100 o un umbral numérico fuera de orden

    Entonces recibe 422 con el campo y el motivo

    Y no se guarda nada parcial

  Escenario: Quién calibró

    Cuando un Catedrático guarda cambios

    Entonces la versión conserva su usuario y la fecha como responsable de la calibración

  Escenario: Acta adjunta

    Dado un Catedrático o un Administrador

    Cuando adjunta el acta de calibración en PDF

    Entonces queda asociada a la versión como respaldo opcional

  Escenario: Auditoría (RN-USR-09)

    Cuando se cambia un valor de la calibración

    Entonces queda en la bitácora con el usuario, el valor anterior y el posterior

  Escenario: Pruebas

    Entonces cada endpoint modificado tiene su prueba de autorización por rol

    Y hay una prueba del conflicto por edición simultánea y otra de las validaciones{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

Highest

### Story point estimate

3

### Sprint

Sprint 4

### Labels

Catedrático

### Parent

Épica L — Cierre de provisionales y aprobación académica

### Assignee

KENNY SALAZAR

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

19/Oct/26 11:57 PM

### Updated

20/Oct/26 12:17 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 11:57 PM

### Watchers

1

### Votes

0

### Rank

0|i000ev:

---

## HU-054 — Catedrático aprueba la calibración del IC {#scrum-77}
**Jira:** [SCRUM-77](https://ayd1-g1-team.atlassian.net/browse/SCRUM-77)

### Issue Type

Story

### Description

*Historia*

Como Catedrático, quiero aprobar la calibración del IC de una versión desde la aplicación, para que conste mi respaldo académico sin firmar un acta en papel (RN-IC-03).

*Dependencias:* HU-053; HU-019 (el acta en PDF queda como adjunto opcional). *Decisión 2 del responsable:* se recomienda que baste la aprobación de cualquier usuario con el rol Catedrático, y que la bitácora registre cuál fue.

*Criterios de aceptación*

{noformat}Característica: Aprobación académica de la calibración

  Escenario: Aprobar la calibración

    Dado una versión publicada con calibración provisional

    Cuando un Catedrático pulsa «Aprobar calibración», con observaciones opcionales

    Entonces se guardan su usuario, la fecha y las observaciones en un registro aparte (migración de Flyway)

    Y la versión publicada no se modifica

  Escenario: El rótulo cambia

    Cuando la calibración queda aprobada

    Entonces el rótulo pasa de «Calibración provisional · sin aprobar» a «Aprobada por <nombre> el dd/MM/yyyy»

    Y se ve en el editor, en el detalle de la versión y donde se muestra el IC

  Escenario: Aprobar una versión ya activa

    Dado una versión activa con calibración provisional

    Entonces se puede aprobar sin republicarla

  Escenario: Una versión en borrador no se aprueba

    Dado una versión en BORRADOR

    Cuando un Catedrático intenta aprobarla

    Entonces recibe un conflicto: la aprobación se da sobre la versión publicada

  Escenario: Una sola aprobación vigente

    Dado una versión ya aprobada

    Cuando otro Catedrático intenta aprobarla de nuevo

    Entonces recibe un conflicto y la aprobación original no se sobrescribe

  Escenario: Aprobaciones simultáneas

    Dado dos Catedráticos que aprueban la misma versión al mismo tiempo

    Entonces solo se registra una aprobación y la otra recibe un conflicto

  Escenario: Cambios posteriores

    Dado una calibración aprobada

    Cuando se cambian valores

    Entonces se crea una versión nueva

    Y la aprobación no se hereda

  Escenario: Historial consultable

    Cuando un Administrador o un Catedrático abre el detalle de la versión

    Entonces ve quién la aprobó, cuándo y con qué observaciones

  Escenario: Solo el Catedrático aprueba

    Cuando un Administrador, un Estudiante o un Profesional externo intenta aprobar

    Entonces recibe 403

  Escenario: El acta en PDF es opcional

    Entonces el PDF del acta sigue disponible como adjunto, pero no es requisito para aprobar

  Escenario: Auditoría y aviso (RN-USR-09)

    Cuando se aprueba una calibración

    Entonces queda en la bitácora con el usuario y la versión

    Y se avisa a los Administradores según HU-056

  Escenario: Pruebas

    Entonces hay una prueba de autorización por rol

    Y una prueba de la migración y de la restricción de aprobación única sobre PostgreSQL real{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

Highest

### Story point estimate

5

### Sprint

Sprint 4

### Labels

Catedrático

### Parent

Épica L — Cierre de provisionales y aprobación académica

### Assignee

KENNY SALAZAR

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

19/Oct/26 11:57 PM

### Updated

20/Oct/26 12:17 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 11:57 PM

### Watchers

1

### Votes

0

### Rank

0|i000f5:

---

## HU-055 — Catedrático valida las reglas de mantenimiento desde la aplicación {#scrum-78}
**Jira:** [SCRUM-78](https://ayd1-g1-team.atlassian.net/browse/SCRUM-78)

### Issue Type

Story

### Description

*Historia*

Como Catedrático, quiero validar las reglas del catálogo de mantenimiento, para retirar el rótulo «Provisional · sin validar» sin pasar por el Administrador (RN-MTO-02, ADR-0005).

*Dependencias:* HU-045 (catálogo de reglas). *Decisión 3 del responsable:* se recomienda que el Catedrático solo valide; crear, editar y desactivar siguen siendo del Administrador, salvo que el responsable decida abrirlo.

*Criterios de aceptación*

{noformat}Característica: Validación del catálogo por el Catedrático

  Escenario: Validar una regla

    Dado una regla del catálogo en estado «Provisional · sin validar»

    Cuando un Catedrático la valida

    Entonces se guardan quién y cuándo, en validado_por_id y validado_en

    Y la regla pasa a «Validada»

  Escenario: Se muestra quién la validó

    Cuando un usuario abre el catálogo

    Entonces la regla validada muestra «Validada por <nombre> el dd/MM/yyyy»

    Y el botón «Validar» solo aparece al Catedrático

  Escenario: El resumen se actualiza

    Cuando se valida una regla

    Entonces los contadores de reglas provisionales y validadas del resumen cambian

  Escenario: Una regla ya validada

    Dado una regla validada

    Cuando un Catedrático intenta validarla de nuevo

    Entonces no se duplica la validación ni se cambia quién la validó

  Escenario: Solo la revisión vigente se valida

    Dado una revisión anterior de una regla, que ya no es la activa

    Cuando alguien intenta validarla

    Entonces recibe un conflicto

  Escenario: Crear, editar y desactivar

    Dado un Catedrático

    Entonces sigue sin poder crear, editar ni desactivar reglas y recibe 403, salvo decisión contraria del responsable

  Escenario: Una edición posterior

    Dado una regla validada

    Cuando el Administrador la edita

    Entonces se crea una revisión nueva que vuelve a quedar sin validar (ADR-0005)

  Escenario: Auditoría (RN-USR-09)

    Cuando se valida una regla

    Entonces queda en la bitácora con el usuario y las instantáneas antes y después

  Escenario: Pruebas de autorización

    Entonces el endpoint de validar tiene su prueba por rol: Catedrático permitido y los demás roles rechazados

    Y hay una prueba de la regla ya validada y otra de la revisión no vigente{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

High

### Story point estimate

2

### Sprint

Sprint 4

### Labels

Catedrático

### Parent

Épica L — Cierre de provisionales y aprobación académica

### Assignee

KENNY SALAZAR

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

19/Oct/26 11:57 PM

### Updated

20/Oct/26 12:17 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 11:57 PM

### Watchers

1

### Votes

0

### Rank

0|i000f7:

---

## HU-056 — Catedrático y Administrador reciben avisos de calibración y validación pendientes {#scrum-79}
**Jira:** [SCRUM-79](https://ayd1-g1-team.atlassian.net/browse/SCRUM-79)

### Issue Type

Story

### Description

*Historia*

Como Catedrático o Administrador, quiero recibir un aviso cuando haya algo pendiente de aprobar o validar, para no depender de que alguien me lo recuerde.

*Dependencias:* HU-054, HU-055 y HU-040 (avisos dentro de la aplicación, con {{NotificacionService}}, que deduplica por usuario y clave de evento).

*Criterios de aceptación*

{noformat}Característica: Avisos de calibración y validación pendientes

  Escenario: Calibración pendiente

    Cuando se siembra o se publica una versión con calibración provisional

    Entonces los Catedráticos activos reciben un aviso de tipo CALIBRACION_PENDIENTE

  Escenario: Calibración aprobada

    Cuando un Catedrático aprueba la calibración

    Entonces los Administradores activos reciben un aviso de tipo CALIBRACION_APROBADA

  Escenario: Reglas por validar

    Dado que hay reglas del catálogo sin validar

    Entonces los Catedráticos activos reciben una sola vez un aviso de tipo REGLAS_PENDIENTES_VALIDAR

  Escenario: Destinatarios

    Entonces solo reciben el aviso los usuarios activos del rol que corresponde

    Y un usuario desactivado no recibe nada

  Escenario: Sin repeticiones

    Dado que los avisos usan una clave de evento por usuario

    Entonces no se repiten en cada arranque ni en cada publicación

  Escenario: El aviso lleva al lugar correcto

    Cuando el usuario abre el aviso

    Entonces va al editor de la versión o al catálogo de reglas según el tipo

    Y el aviso queda marcado como leído

  Escenario: Se pueden volver a avisar cambios nuevos

    Dado una versión nueva con calibración provisional

    Entonces se genera un aviso nuevo aunque ya se hubiera avisado de la versión anterior

  Escenario: Sin datos sensibles

    Entonces ningún aviso contiene datos sensibles ni valores de la calibración

  Escenario: Pruebas

    Entonces hay pruebas con PostgreSQL real de los tres tipos de aviso, de la deduplicación entre varias ejecuciones y del usuario desactivado{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

High

### Story point estimate

2

### Sprint

Sprint 4

### Labels

Administrador, Catedrático

### Parent

Épica L — Cierre de provisionales y aprobación académica

### Assignee

KENNY SALAZAR

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

19/Oct/26 11:57 PM

### Updated

20/Oct/26 12:17 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 11:57 PM

### Watchers

1

### Votes

0

### Rank

0|i000fg:

---

## HU-057 — Inspector guarda fotografías comprimidas en el dispositivo sin conexión {#scrum-80}
**Jira:** [SCRUM-80](https://ayd1-g1-team.atlassian.net/browse/SCRUM-80)

### Issue Type

Story

### Description

*Historia*

Como Inspector sin conexión, quiero tomar o adjuntar fotografías que se comprimen y se guardan en el dispositivo, para completar la inspección sin red (RN-OFF-01, RN-OFF-03, RN-ARC-01, RN-ARC-02, RN-ARC-03, RN-ARC-04, RN-ARC-05; DT-OFF-03, DT-OFF-04, DT-OFF-05, DT-OFF-08, DT-OFF-09).

*Dependencias:* HU-042 (captura sin conexión), HU-028 y HU-029 (fotografías en línea y su evidencia mínima; el procesador de fotografías ya existe y se reutiliza). *Decisión del responsable:* qué se hace con una fotografía sin ubicación, porque el servidor exige ubicación y hora para dar una fotografía por disponible.

*Criterios de aceptación*

{noformat}Característica: Fotografías comprimidas y guardadas en el dispositivo

  Escenario: Compresión al capturar

    Dado una fotografía tomada o adjuntada sin conexión

    Cuando se guarda en el dispositivo

    Entonces se comprime antes de persistirse: lado mayor de 1600 px como máximo, WebP o JPEG, con un objetivo de 200 a 400 KB (RN-ARC-02, DT-OFF-08)

  Escenario: Formato según el navegador

    Dado un navegador que no codifica WebP

    Entonces la fotografía se guarda en JPEG con el mismo límite de tamaño

  Escenario: Se conservan la ubicación y la hora

    Cuando se comprime la fotografía

    Entonces conserva la geolocalización y la marca de tiempo de captura aunque se eliminen los demás metadatos (RN-ARC-03)

  Escenario: Una fotografía sin ubicación

    Dado un dispositivo que no entrega ubicación al capturar

    Entonces la aplicación avisa al usuario y aplica la regla que decida el responsable, por ejemplo usar la ubicación de inicio de la inspección

    Y nunca guarda una fotografía que el servidor rechazaría por no tener ubicación y hora

  Escenario: Almacén local

    Cuando se guarda la fotografía

    Entonces queda en el almacén fotos de Dexie con un UUID v7 generado en el cliente, su categoría y, si se capturó desde un grupo de daño, su elemento_ref (RN-ARC-01, DT-OFF-04, DT-OFF-05)

  Escenario: Categoría obligatoria y mínimos

    Cuando el usuario guarda una fotografía

    Entonces debe elegir ACCESO, SUPERESTRUCTURA, SUBESTRUCTURA, CAUCE u OTRA

    Y la pantalla muestra cuántas hay por categoría frente a los mínimos que exige la inspección (HU-029)

  Escenario: Almacenamiento persistente (DT-OFF-09)

    Cuando la aplicación pide almacenamiento persistente con navigator.storage.persist()

    Entonces, si no se concede, avisa al usuario de que el sistema podría desalojar los datos

  Escenario: Límites (RN-ARC-04)

    Dado una inspección con 60 fotografías

    Cuando se intenta agregar otra

    Entonces se rechaza con un mensaje claro

    Y se avisa cuando queda poco espacio en el dispositivo

  Escenario: Se agota el espacio

    Dado un dispositivo sin cuota de almacenamiento

    Cuando se intenta guardar una fotografía

    Entonces la aplicación avisa que no se pudo guardar y conserva intactas las demás

  Escenario: Eliminar una fotografía de un borrador (RN-ARC-05)

    Dado una fotografía guardada localmente en un borrador sin sincronizar

    Cuando el usuario la elimina

    Entonces desaparece del dispositivo y se libera su espacio

  Escenario: Persistencia

    Entonces la fotografía sobrevive al cierre del navegador y al apagado del dispositivo (RN-OFF-03)

  Escenario: La interfaz no se congela (RNF-09)

    Dado un dispositivo mínimo con Android 10 y 3 GB de RAM

    Cuando se comprime una fotografía

    Entonces la compresión corre fuera del hilo principal y la pantalla sigue respondiendo

  Escenario: Pruebas

    Entonces hay pruebas de la compresión (dimensión, formato y tamaño), de los metadatos, de los límites y de la persistencia en el almacén local{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

Highest

### Story point estimate

8

### Sprint

Sprint 4

### Labels

Catedrático, Estudiante, Profesional-Externo

### Parent

Épica M — Trabajo sin conexión (fase 2)

### Assignee

Melvin Ordoñez

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

19/Oct/26 11:58 PM

### Updated

20/Oct/26 12:18 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 11:58 PM

### Watchers

1

### Votes

0

### Rank

0|i000fh:

---

## HU-058 — Inspector sube sus fotografías una por una con reintento y reanudación {#scrum-81}
**Jira:** [SCRUM-81](https://ayd1-g1-team.atlassian.net/browse/SCRUM-81)

### Issue Type

Story

### Description

*Historia*

Como Inspector, quiero que mis fotografías se suban solas al recuperar la conexión, una por una y sin duplicarse, para no repetir trabajo si la red falla (RN-OFF-04, RN-OFF-05, RN-OFF-07, RN-OFF-08; DT-OFF-06, DT-OFF-07; DT-FE-10; DT-SEC-08; DT-ALM-04).

*Dependencias:* HU-057, HU-043 (idempotencia), HU-044 (sincronización de datos) y HU-028 (subida de fotografías). *Decisión del responsable:* cómo se reanuda una subida cortada: reintentando la fotografía completa de forma idempotente o por partes.

*Criterios de aceptación*

{noformat}Característica: Subida de fotografías con reintento y reanudación

  Escenario: Orden de sincronización (RN-OFF-05)

    Dado una inspección con datos y fotografías pendientes

    Cuando se recupera la conexión

    Entonces primero se sincronizan los datos de la inspección

    Y después las fotografías, una por una

  Escenario: Cola persistente y ordenada (DT-OFF-06)

    Dado fotografías pendientes y la aplicación cerrada

    Cuando se vuelve a abrir

    Entonces la cola conserva sus elementos y su orden

    Y retoma la subida donde quedó

  Escenario: Sin duplicados (DT-OFF-07, RN-OFF-08)

    Cuando se sube una fotografía

    Entonces la petición lleva su Idempotency-Key

    Y un reintento no duplica la fotografía

  Escenario: Reintento con retroceso exponencial (DT-OFF-06)

    Dado una subida que falla

    Cuando se reintenta

    Entonces el intervalo crece de forma exponencial con un máximo de 5 intentos

    Y al agotarlos la fotografía queda marcada para intervención manual

  Escenario: Corte a mitad de una subida

    Dado una subida interrumpida

    Cuando vuelve la conexión

    Entonces se reanuda sin duplicar la fotografía ni dejar un archivo incompleto en el servidor

  Escenario: El token expira durante la sincronización (DT-FE-10)

    Dado una sincronización larga

    Cuando el token de acceso expira

    Entonces se renueva con el token de renovación y la cola continúa

    Y si no se puede renovar, la cola se pausa y pide iniciar sesión sin perder nada

  Escenario: Validación en el servidor (DT-SEC-08, DT-ALM-04)

    Cuando el servidor recibe la fotografía

    Entonces valida el tipo MIME real, el tamaño y la cantidad

    Y genera el original comprimido y la miniatura de 300 px

  Escenario: Una fotografía inválida no detiene a las demás

    Dado una fotografía que el servidor rechaza

    Entonces queda marcada con el motivo y las demás siguen subiendo

  Escenario: La inspección ya no admite fotografías

    Dado una inspección que dejó de estar en borrador mientras sus fotografías estaban pendientes

    Cuando se intenta subir una

    Entonces el servidor responde con conflicto y la fotografía queda marcada para revisión manual

  Escenario: Dos pestañas abiertas

    Dado la aplicación abierta en dos pestañas

    Entonces la misma fotografía no se sube dos veces

  Escenario: Liberación del dispositivo (RN-OFF-07)

    Dado una fotografía sincronizada con éxito

    Entonces puede liberarse del almacenamiento local

    Pero una fotografía pendiente nunca se borra automáticamente

  Escenario: Pruebas

    Entonces hay pruebas con reintentos, con una subida cortada, con una repetición de la misma petición, con un token vencido y con una fotografía inválida{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

Highest

### Story point estimate

8

### Sprint

Sprint 4

### Labels

Catedrático, Estudiante, Profesional-Externo

### Parent

Épica M — Trabajo sin conexión (fase 2)

### Assignee

Melvin Ordoñez

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

19/Oct/26 11:58 PM

### Updated

20/Oct/26 12:18 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 11:58 PM

### Watchers

1

### Votes

0

### Rank

0|i000fp:

---

## HU-059 — Inspector conserva su versión y la copia del servidor ante un conflicto de sincronización {#scrum-82}
**Jira:** [SCRUM-82](https://ayd1-g1-team.atlassian.net/browse/SCRUM-82)

### Issue Type

Story

### Description

*Historia*

Como Inspector, quiero que, si el servidor detecta una divergencia, prevalezca mi versión y se conserve la copia del servidor, para no perder trabajo ni mezclar datos (RN-OFF-09).

*Dependencias:* HU-044 (sincronización de datos). La tabla {{conflicto_sincronizacion}} ya existe (migración V11).

*Criterios de aceptación*

{noformat}Característica: Conflictos de sincronización

  Escenario: Divergencia detectada

    Dado una inspección que cambió en el servidor respecto de la base del cliente

    Cuando el cliente sincroniza

    Entonces prevalece la versión del cliente

    Y se conserva una copia de la versión del servidor

  Escenario: Sin divergencia no hay conflicto

    Dado una inspección que no cambió en el servidor

    Cuando el cliente sincroniza

    Entonces se aplica sin registrar ningún conflicto

  Escenario: El conflicto queda registrado

    Cuando se resuelve un conflicto

    Entonces queda en conflicto_sincronizacion con la inspección, el autor, la fecha y la copia del servidor

  Escenario: Visible para el autor

    Dado un conflicto registrado

    Entonces el autor lo ve en su inspección y en la vista de diagnóstico (HU-060)

  Escenario: Consultar la copia del servidor

    Cuando el autor abre el conflicto

    Entonces ve la copia del servidor en modo de solo lectura junto a su versión

  Escenario: Sin mezclas automáticas

    Entonces nunca se mezclan datos automáticamente ni se pierde información

  Escenario: Un reintento no duplica el conflicto

    Dado la misma sincronización repetida con su clave de idempotencia

    Entonces no se crea un segundo registro de conflicto

  Escenario: Privacidad (RN-INS-05)

    Cuando otro usuario que no es el autor intenta consultar el conflicto

    Entonces recibe 403 o 404 y no ve la copia

  Escenario: Auditoría (RN-USR-09)

    Cuando se registra un conflicto

    Entonces queda en la bitácora de auditoría

  Escenario: Pruebas

    Entonces hay pruebas con una divergencia simulada, con una sincronización repetida y con un usuario ajeno, tanto en el servidor como en la interfaz{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

Highest

### Story point estimate

5

### Sprint

Sprint 4

### Labels

Catedrático, Estudiante, Profesional-Externo

### Parent

Épica M — Trabajo sin conexión (fase 2)

### Assignee

Melvin Ordoñez

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

19/Oct/26 11:58 PM

### Updated

20/Oct/26 12:18 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 11:58 PM

### Watchers

1

### Votes

0

### Rank

0|i000g4:

---

## HU-060 — Inspector ve el estado de sincronización y usa la vista de diagnóstico {#scrum-83}
**Jira:** [SCRUM-83](https://ayd1-g1-team.atlassian.net/browse/SCRUM-83)

### Issue Type

Story

### Description

*Historia*

Como Inspector, quiero ver siempre cuánto falta por sincronizar y poder reintentar a mano, para saber qué está a salvo (RN-OFF-04, RN-OFF-06, RN-OFF-07; DT-OFF-09, DT-OFF-10; DT-SEC-12).

*Dependencias:* HU-057, HU-058 y HU-059. La sincronización manual también es la salida cuando el navegador no permite procesos en segundo plano, como Safari en iOS (riesgo 8 de la propuesta).

*Criterios de aceptación*

{noformat}Característica: Estado de sincronización y vista de diagnóstico

  Escenario: Indicador permanente (RN-OFF-06)

    Dado un usuario con trabajo pendiente

    Entonces un indicador siempre visible muestra el número de inspecciones y de archivos pendientes

  Escenario: Todo sincronizado

    Dado un usuario sin trabajo pendiente

    Entonces el indicador lo dice con un texto claro y no solo con un color

  Escenario: Se actualiza solo

    Cuando cambia la conectividad o termina una subida

    Entonces el indicador y los contadores se actualizan sin recargar la pantalla

  Escenario: Vista de diagnóstico (DT-OFF-10)

    Cuando el usuario abre la vista de diagnóstico

    Entonces ve los elementos pendientes, el espacio ocupado, el último intento de sincronización y los errores

    Y puede reintentar a mano

  Escenario: Reintento manual

    Dado un elemento con error

    Cuando el usuario pulsa reintentar

    Entonces se vuelve a intentar sin duplicarlo y el resultado se refleja en la vista

  Escenario: Sincronización automática y bajo demanda (RN-OFF-04)

    Cuando se recupera la conectividad

    Entonces la sincronización corre sola

    Y también se puede lanzar bajo demanda

  Escenario: Elementos agotados

    Dado un elemento que agotó sus 5 intentos

    Entonces aparece como pendiente de intervención manual con el motivo del último error

  Escenario: Conflictos

    Dado un conflicto de sincronización registrado

    Entonces aparece en la vista de diagnóstico (HU-059)

  Escenario: Lo pendiente nunca se borra solo (RN-OFF-07)

    Entonces ninguna acción de la vista elimina un elemento pendiente sin que el usuario lo confirme

  Escenario: Sin datos sensibles (DT-SEC-12)

    Entonces la vista no muestra tokens ni contraseñas, y los errores usan identificadores y no valores completos

  Escenario: Accesibilidad e idioma

    Entonces es accesible por teclado y todos sus textos están en español en es.json

  Escenario: Pruebas

    Entonces hay pruebas del indicador, de los contadores y del reintento manual con elementos pendientes, con error y agotados{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

Highest

### Story point estimate

5

### Sprint

Sprint 4

### Labels

Catedrático, Estudiante, Profesional-Externo

### Parent

Épica M — Trabajo sin conexión (fase 2)

### Assignee

Melvin Ordoñez

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

19/Oct/26 11:58 PM

### Updated

20/Oct/26 12:18 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 11:58 PM

### Watchers

1

### Votes

0

### Rank

0|i000gb:

---

## HU-061 — Inspector instala la aplicación, la usa sin red y envía o publica sin conexión {#scrum-84}
**Jira:** [SCRUM-84](https://ayd1-g1-team.atlassian.net/browse/SCRUM-84)

### Issue Type

Story

### Description

*Historia*

Como Inspector, quiero instalar la aplicación, abrirla sin red y enviar o publicar mi inspección aunque no tenga conexión, para terminar el trabajo en el sitio (RN-OFF-01; RN-INS-06; DT-OFF-01, DT-OFF-02; DT-FE-06, DT-FE-10; DT-DEP-03; DT-ARQ-06).

*Dependencias:* HU-035 (envío), HU-041 a HU-044 (offline fase 1) y HU-057 a HU-060. Ya existen el manifiesto de la PWA, {{ngsw-config.json}} y la base local con Dexie. Hasta el Sprint 3 enviar y publicar exigían conexión.

*Criterios de aceptación*

{noformat}Característica: PWA, service worker y envío o publicación sin conexión

  Escenario: Instalable y con arranque sin red (DT-OFF-01)

    Dado la aplicación con manifiesto, íconos y @angular/service-worker

    Entonces se puede instalar y arranca sin conexión

  Escenario: Estrategia de caché (DT-OFF-02)

    Entonces el app shell se precarga

    Y los datos de referencia (formulario activo, catálogos y puentes asignados) usan stale-while-revalidate

    Y los datos de inspección los gestiona la capa de sincronización y no el service worker

  Escenario: La inspección se valida antes de encolarla (RN-INS-06)

    Dado un Estudiante que intenta enviar, o un Catedrático o Profesional externo que intenta publicar, sin conexión

    Cuando la inspección tiene campos obligatorios sin llenar o le faltan fotografías mínimas por categoría

    Entonces la aplicación lo indica y no la encola

  Escenario: Enviar o publicar sin conexión

    Dado una inspección completa

    Cuando el usuario confirma la acción sin conexión

    Entonces se encola con su clave de idempotencia y el usuario ve el estado «pendiente»

    Y se ejecuta al recuperar la conexión

  Escenario: Falla al ejecutarse la acción encolada

    Dado una acción de enviar o publicar encolada

    Cuando el servidor la rechaza al recuperar la conexión

    Entonces el usuario ve el motivo y la inspección vuelve a quedar editable sin perder datos

  Escenario: El IC sin conexión (DT-ARQ-06)

    Dado un usuario sin conexión

    Entonces el IC se muestra como «disponible al sincronizar»

    Y la fórmula no se copia a Angular: vive solo en el backend

  Escenario: Sesión sin conexión (DT-FE-10)

    Dado un usuario sin conexión con trabajo pendiente

    Cuando cierra sesión o expira su token

    Entonces sus datos pendientes se conservan y se sincronizan al volver a iniciar sesión

  Escenario: Otro usuario en el mismo dispositivo

    Dado un dispositivo con datos pendientes de un usuario

    Cuando inicia sesión otro usuario

    Entonces no ve ni sincroniza esos datos bajo su cuenta

  Escenario: Actualizaciones de la PWA (DT-DEP-03)

    Entonces index.html no se almacena en caché

    Y el usuario recibe el aviso de nueva versión disponible

  Escenario: Tamaño del paquete inicial (DT-FE-06)

    Entonces el paquete inicial sigue por debajo de 500 KB comprimidos

  Escenario: Carga en red lenta (RNF-02)

    Dado una red 3G simulada

    Entonces la carga inicial tarda menos de 5 s

  Escenario: Pruebas

    Entonces hay pruebas del service worker, de la validación previa, de la cola de envío y publicación, del rechazo posterior y del arranque sin red{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

Highest

### Story point estimate

8

### Sprint

Sprint 4

### Labels

Catedrático, Estudiante, Profesional-Externo

### Parent

Épica M — Trabajo sin conexión (fase 2)

### Assignee

Melvin Ordoñez

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

19/Oct/26 11:58 PM

### Updated

20/Oct/26 12:18 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 11:58 PM

### Watchers

1

### Votes

0

### Rank

0|i000gp:

---

## HU-062 — Sistema se prueba de punta a punta con red degradada {#scrum-85}
**Jira:** [SCRUM-85](https://ayd1-g1-team.atlassian.net/browse/SCRUM-85)

### Issue Type

Story

### Description

*Historia*

Como equipo, queremos pruebas automatizadas de la sincronización en redes malas, para no probar solo los casos «con internet» y «sin internet» (DT-OFF-11, DT-CAL-04).

*Dependencias:* HU-057 a HU-061. *Decisión 6 del responsable:* la herramienta; se recomienda Cypress, que ya tiene una prueba en {{sgp-client/e2e}} aunque no está declarado en {{package.json}}, con su ADR. La autonomía de 8 horas sin conexión (RNF-07) no se automatiza: se mide a mano el domingo 25.

*Criterios de aceptación*

{noformat}Característica: Pruebas E2E con red degradada

  Escenario: Referencia con red normal

    Dado una inspección con datos y fotografías pendientes

    Cuando se sincroniza con red normal

    Entonces todo queda sincronizado y sirve de línea base para los demás escenarios

  Escenario: Sin red

    Dado una inspección creada sin conexión

    Cuando la aplicación permanece sin red

    Entonces todo queda pendiente en el dispositivo y nada se pierde

  Escenario: Latencia alta

    Dado una inspección con datos y fotografías pendientes

    Cuando la red tiene latencia alta

    Entonces todo se sincroniza sin duplicados

  Escenario: Pérdida de paquetes

    Cuando la red pierde paquetes durante la sincronización

    Entonces los reintentos terminan con todo sincronizado y sin duplicados

  Escenario: Corte a mitad de una subida

    Cuando la conexión se corta a mitad de una subida

    Entonces la subida se reanuda al volver la red, sin duplicar ni dejar archivos incompletos

  Escenario: Nada pendiente se pierde

    Entonces en todos los escenarios lo pendiente permanece en el dispositivo hasta sincronizarse (RN-OFF-07)

  Escenario: Resultado verificable en el servidor

    Entonces cada prueba comprueba en el servidor el número de inspecciones y de fotografías, sin duplicados ni archivos incompletos

  Escenario: Condiciones reproducibles

    Entonces las condiciones de red se simulan con perfiles fijos y las pruebas no dependen de la velocidad de la máquina

  Escenario: Evidencia cuando falla

    Cuando una prueba falla

    Entonces quedan capturas y trazas para entender el motivo

  Escenario: Herramienta y ejecución

    Entonces la herramienta queda documentada con su ADR

    Y si ejecutarla en el CI no es viable, se documenta cómo correrla

  Escenario: Documentación

    Entonces el resultado se describe en el manual técnico{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

Highest

### Story point estimate

5

### Sprint

Sprint 4

### Labels

Sistema

### Parent

Épica M — Trabajo sin conexión (fase 2)

### Assignee

Melvin Ordoñez

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

19/Oct/26 11:58 PM

### Updated

20/Oct/26 12:18 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

19/Oct/26 11:58 PM

### Watchers

1

### Votes

0

### Rank

0|i000gv:

---

## HU-063 — Usuario abre hilos por puente y comenta en ellos {#scrum-86}
**Jira:** [SCRUM-86](https://ayd1-g1-team.atlassian.net/browse/SCRUM-86)

### Issue Type

Story

### Description

*Historia*

Como usuario autenticado, quiero abrir hilos sobre un puente y comentar en ellos, para discutir su estado entre ciclos académicos (RN-FOR-01, RN-FOR-04, RN-FOR-06, RN-FOR-09; RN-INS-05; RN-USR-09; DT-BE-08, DT-BE-09).

*Dependencias:* HU-040 (avisos), HU-014 y HU-015 (catálogo y ficha del puente). Las tablas {{foro_hilo}} y {{foro_comentario}} ya existen (migración V11); el backend aún no tiene el módulo {{foro}}. *Decisión del responsable:* los límites de longitud del título y del comentario; se recomienda 120 y 2000 caracteres.

*Criterios de aceptación*

{noformat}Característica: Hilos por puente con categorías y comentarios de un nivel

  Escenario: Hilos permanentes por puente (RN-FOR-01)

    Dado un puente

    Entonces tiene sus propios hilos, permanentes y compartidos entre inspecciones y ciclos académicos

  Escenario: Crear un hilo

    Dado un usuario autenticado en la ficha de un puente

    Cuando crea un hilo con título, categoría y primer comentario

    Entonces el hilo queda asociado al puente y a su autor, con su fecha

  Escenario: Categorías (RN-FOR-06)

    Cuando se crea un hilo

    Entonces pertenece a una de cinco categorías: Discusión técnica, Propuesta de intervención, Consulta, Referencia documental o Alerta

  Escenario: Validaciones

    Cuando el título o el comentario están vacíos, superan el límite o la categoría no existe

    Entonces recibe 422 con el campo y el motivo y no se crea nada

  Escenario: Un solo nivel de respuesta (RN-FOR-04)

    Dado un comentario

    Cuando alguien lo responde

    Entonces la respuesta se guarda a un solo nivel

    Y un intento de responder a una respuesta se rechaza

  Escenario: Quién participa (RN-FOR-09)

    Dado un usuario autenticado y activo

    Entonces puede crear hilos y comentar

    Pero un visitante solo lee los hilos públicos y no participa

    Y una cuenta desactivada no participa

  Escenario: El contenido se muestra de forma segura

    Cuando un comentario incluye HTML o scripts

    Entonces se guarda como texto y se muestra escapado, sin ejecutar nada

  Escenario: Orden y paginación (DT-BE-08, DT-BE-09)

    Cuando se lista una colección del foro

    Entonces va paginada con un máximo de 100 por página, ordenada por actividad reciente y sin consultas N+1

  Escenario: Los borradores no aparecen (RN-INS-05)

    Dado una inspección en borrador

    Entonces nada de ella aparece en el foro

  Escenario: Auditoría (RN-USR-09)

    Cuando se crea un hilo o un comentario

    Entonces queda en la bitácora con el usuario y la entidad creada

  Escenario: Interfaz

    Entonces la ficha del puente muestra sus hilos, el detalle de cada uno y el formulario para comentar

    Y los textos están en español y los controles son accesibles por teclado

  Escenario: Pruebas de autorización

    Entonces cada endpoint tiene su prueba por rol, con y sin sesión, y con una cuenta desactivada

  Escenario: Pruebas con datos reales

    Entonces hay pruebas con PostgreSQL real de la paginación, del límite de respuestas y del contenido con HTML{noformat}

### Status

En curso

### Status category

In Progress

### Priority

High

### Story point estimate

8

### Sprint

Sprint 4

### Parent

Épica N — Foro

### Assignee

Herberth Julian Reyes Pacajoj

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

20/Oct/26 12:00 AM

### Updated

20/Oct/26 4:03 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

20/Oct/26 4:03 PM

### Watchers

1

### Votes

0

### Rank

0|i000h8:

---

## HU-064 — Usuario comenta un elemento concreto de una inspección publicada {#scrum-87}
**Jira:** [SCRUM-87](https://ayd1-g1-team.atlassian.net/browse/SCRUM-87)

### Issue Type

Story

### Description

*Historia*

Como usuario autenticado, quiero comentar un campo concreto de una inspección publicada, para que la observación quede junto al dato (RN-FOR-02, RN-FOR-03; RN-USR-09; DT-FE-13).

*Dependencias:* HU-063 y el evento {{InspeccionPublicadaEvent}} del ADR-0006. Si hay que reducir, el marcador visual es lo primero que se recorta (regla de corte de la propuesta). *Decisión del responsable:* cómo se abre el hilo de las inspecciones que ya estaban publicadas antes de esta HU; se recomienda crearlo al primer acceso.

*Criterios de aceptación*

{noformat}Característica: Comentarios anclados a un elemento y el hilo de la inspección

  Escenario: El hilo se abre al publicar (RN-FOR-03)

    Dado una inspección que acaba de pasar a PUBLICADA

    Cuando la transacción de publicación se confirma

    Entonces el foro abre el hilo de esa inspección en su propia transacción

    Y un fallo del foro nunca bloquea ni revierte la publicación

  Escenario: El evento llega dos veces

    Dado un hilo que ya existe

    Cuando el evento se repite

    Entonces el hilo no se duplica

  Escenario: Inspecciones publicadas antes de esta HU

    Dado una inspección publicada que aún no tiene hilo

    Cuando un usuario abre su foro o comenta

    Entonces el hilo se crea en ese momento

  Escenario: Solo inspecciones publicadas

    Dado una inspección que no está publicada

    Entonces no tiene hilo ni comentarios (RN-INS-05)

  Escenario: Comentario anclado (RN-FOR-02)

    Dado una inspección publicada

    Cuando un usuario comenta un campo o elemento

    Entonces el comentario guarda su elemento_ref y, si aplica, la instancia

    Y se muestra como marcador junto al dato correspondiente

  Escenario: El elemento debe existir

    Cuando el elemento_ref no existe en el esquema de la versión con que se levantó la inspección

    Entonces recibe 422 y no se guarda el comentario

  Escenario: Comentario general

    Cuando un usuario comenta la inspección sin elegir un elemento

    Entonces el comentario queda en el hilo de la inspección sin ancla

  Escenario: Varios comentarios en el mismo elemento

    Dado un elemento con varios comentarios

    Entonces el marcador indica cuántos hay y los muestra agrupados

  Escenario: Quién comenta y quién lee

    Entonces un usuario autenticado y activo comenta

    Y un visitante solo puede leer el hilo público

  Escenario: Accesibilidad (DT-FE-13)

    Entonces el marcador no se comunica solo con color y se alcanza con el teclado

  Escenario: Auditoría (RN-USR-09)

    Cuando se abre un hilo de inspección o se crea un comentario anclado

    Entonces queda en la bitácora

  Escenario: Pruebas

    Entonces hay pruebas con PostgreSQL real del evento repetido, de la publicación que no se bloquea si el foro falla y del elemento inexistente{noformat}

### Status

En curso

### Status category

In Progress

### Priority

High

### Story point estimate

5

### Sprint

Sprint 4

### Parent

Épica N — Foro

### Assignee

Herberth Julian Reyes Pacajoj

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

20/Oct/26 12:00 AM

### Updated

20/Oct/26 4:03 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

20/Oct/26 4:03 PM

### Watchers

1

### Votes

0

### Rank

0|i000hd:

---

## HU-065 — Catedrático o Administrador modera el foro con motivo auditado {#scrum-88}
**Jira:** [SCRUM-88](https://ayd1-g1-team.atlassian.net/browse/SCRUM-88)

### Issue Type

Story

### Description

*Historia*

Como Catedrático o Administrador, quiero ocultar comentarios y cerrar hilos con un motivo, para mantener el foro ordenado y trazable (RN-FOR-05, RN-FOR-08; RN-REV-02; RN-USR-09).

*Dependencias:* HU-063 y HU-064.

*Criterios de aceptación*

{noformat}Característica: Edición, eliminación y moderación auditada

  Escenario: Edición dentro de 15 minutos (RN-FOR-05)

    Dado un comentario recién publicado

    Cuando su autor lo edita dentro de los 15 minutos siguientes

    Entonces el cambio se guarda

    Pero pasado ese plazo el comentario queda fijo

  Escenario: El plazo lo decide el servidor

    Dado un cliente con el reloj adelantado o atrasado

    Cuando intenta editar fuera de plazo

    Entonces el servidor lo rechaza según su propia hora

  Escenario: Solo el autor edita y elimina

    Cuando otro usuario intenta editar o eliminar un comentario ajeno

    Entonces recibe 403

  Escenario: Eliminación con marca (RN-FOR-05)

    Dado un comentario

    Cuando su autor lo elimina

    Entonces queda la marca «comentario eliminado» y sus respuestas se conservan para no romper el hilo

  Escenario: Ocultar y cerrar con motivo (RN-FOR-08)

    Dado un Catedrático o un Administrador

    Cuando oculta un comentario o cierra un hilo

    Entonces debe indicar un motivo

    Y sin motivo recibe 422

  Escenario: Un comentario oculto

    Dado un comentario oculto por moderación

    Entonces los usuarios comunes ven «Oculto por moderación» en su lugar

    Y los moderadores ven el contenido y el motivo

  Escenario: Un hilo cerrado

    Dado un hilo cerrado

    Cuando alguien intenta comentar

    Entonces recibe un conflicto y el hilo sigue visible para lectura

  Escenario: Auditoría con motivo (RN-USR-09)

    Cuando se oculta un comentario, se cierra un hilo o se edita o elimina un comentario

    Entonces queda en la bitácora con el usuario, el motivo cuando aplica y los valores anterior y posterior

  Escenario: Moderación posterior (RN-REV-02)

    Dado inspecciones publicadas sin revisión previa por un Catedrático o un Profesional externo

    Entonces el Catedrático y el Administrador ven una lista de ellas, de las más recientes a las más antiguas, con acceso directo a su hilo para moderarlas a posteriori

  Escenario: Un usuario común no modera

    Cuando un Estudiante o un Profesional externo intenta ocultar un comentario o cerrar un hilo

    Entonces recibe 403

  Escenario: Pruebas

    Entonces cada acción de moderación tiene su prueba por rol

    Y hay pruebas del plazo de 15 minutos con reloj controlado y del hilo cerrado{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

High

### Story point estimate

5

### Sprint

Sprint 4

### Labels

Administrador, Catedrático

### Parent

Épica N — Foro

### Assignee

Herberth Julian Reyes Pacajoj

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

20/Oct/26 12:00 AM

### Updated

20/Oct/26 12:20 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

20/Oct/26 12:00 AM

### Watchers

1

### Votes

0

### Rank

0|i000hp:

---

## HU-066 — Usuario menciona a otros y recibe alertas de puentes en mal estado {#scrum-89}
**Jira:** [SCRUM-89](https://ayd1-g1-team.atlassian.net/browse/SCRUM-89)

### Issue Type

Story

### Description

*Historia*

Como usuario, quiero mencionar a otros con @usuario y que una alerta sobre un puente en mal estado llegue a quien debe verla, para que nada importante pase inadvertido (RN-FOR-07, RN-FOR-10; DT-SEC-07).

*Dependencias:* HU-063, HU-064 y HU-040 (avisos, con {{NotificacionService}}). Si hay que reducir, las menciones son lo primero que se recorta. *Decisión del responsable:* los límites de tasa; se recomienda unos pocos hilos y comentarios por minuto por usuario.

*Criterios de aceptación*

{noformat}Característica: Menciones, alerta en puente Malo y límite de tasa

  Escenario: Mencionar a un usuario (RN-FOR-10)

    Dado un comentario con @usuario

    Cuando se publica

    Entonces el mencionado recibe un aviso de tipo FORO_MENCION, una sola vez por comentario

    Y la mención se registra en comentario_mencion

  Escenario: Quién puede ser mencionado

    Cuando el comentario menciona a un usuario inexistente o desactivado

    Entonces el comentario se publica sin error y sin enviar aviso

  Escenario: Mencionarse a uno mismo

    Cuando un usuario se menciona a sí mismo

    Entonces no recibe aviso

  Escenario: Editar un comentario con menciones

    Dado un comentario editado dentro de su plazo

    Cuando la edición agrega una mención nueva

    Entonces solo el usuario nuevo recibe aviso y los ya avisados no se repiten

  Escenario: El aviso lleva al comentario

    Cuando el mencionado abre el aviso

    Entonces va al hilo y al comentario donde lo mencionaron

  Escenario: Alerta en un puente Malo (RN-FOR-07)

    Dado un hilo de categoría Alerta sobre un puente en estado Malo

    Cuando se crea

    Entonces el Catedrático y el Administrador reciben un aviso de tipo FORO_ALERTA_PUENTE_MALO

  Escenario: Alerta en un puente que no está Malo

    Dado un hilo de categoría Alerta sobre un puente que no está en estado Malo

    Entonces se crea sin enviar el aviso de puente Malo

  Escenario: Límite de tasa (DT-SEC-07)

    Dado un usuario que crea comentarios o hilos con demasiada frecuencia

    Cuando supera el límite

    Entonces recibe 429 con el encabezado Retry-After y un mensaje claro

  Escenario: El límite es por usuario

    Cuando un usuario supera su límite

    Entonces los demás usuarios no se ven afectados

  Escenario: Sin datos sensibles

    Entonces los avisos no incluyen datos sensibles ni el texto completo del comentario

  Escenario: Pruebas

    Entonces hay pruebas con PostgreSQL real de las menciones, de la alerta con y sin puente Malo y del límite de tasa{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

High

### Story point estimate

5

### Sprint

Sprint 4

### Parent

Épica N — Foro

### Assignee

Herberth Julian Reyes Pacajoj

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

20/Oct/26 12:00 AM

### Updated

20/Oct/26 12:20 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

20/Oct/26 12:00 AM

### Watchers

1

### Votes

0

### Rank

0|i000ht:

---

## HU-067 — Catedrático o Administrador recibe la prioridad sugerida por IC y peso del elemento {#scrum-90}
**Jira:** [SCRUM-90](https://ayd1-g1-team.atlassian.net/browse/SCRUM-90)

### Issue Type

Story

### Description

*Historia*

Como Catedrático o Administrador, quiero que la prioridad de cada propuesta se sugiera según la gravedad del puente y la importancia del elemento dañado, para atender primero lo que más pesa (RN-MTO-06; RN-USR-09).

*Dependencias:* HU-046 (generación de propuestas, que hoy toma la prioridad base de la regla), HU-047 (prioridad confirmada y justificación) y HU-019 (pesos de la calibración). *Decisión del responsable:* la fórmula que combina la prioridad base, el IC y el peso; debe ser determinista y documentarse en un ADR.

*Criterios de aceptación*

{noformat}Característica: Prioridad sugerida por IC y peso del elemento

  Escenario: Prioridad sugerida (RN-MTO-06)

    Cuando el sistema genera una propuesta

    Entonces su prioridad sugerida combina la prioridad base de la regla con el IC del puente y el peso del elemento afectado

    Y la combinación es determinista y nunca baja de la prioridad base

  Escenario: Se usa la calibración de la inspección

    Dado una inspección levantada con una versión del formulario

    Entonces el peso del elemento sale de la calibración de esa versión y el IC, de la publicación de esa inspección

  Escenario: Faltan datos para calcularla

    Dado una propuesta cuyo IC o cuyo peso no están disponibles

    Entonces la prioridad sugerida es la prioridad base de la regla

    Y la propuesta se genera igual, con el motivo en su fundamento

  Escenario: Fundamento visible

    Cuando el usuario abre el detalle de la orden

    Entonces ve la prioridad sugerida y su fundamento: la regla, el IC y el peso

  Escenario: Cambio con justificación

    Dado una orden con prioridad sugerida

    Cuando el usuario cambia la prioridad confirmada

    Entonces debe escribir una justificación

    Y sin ella el sistema responde 422

    Y se conservan la sugerida, la confirmada y la justificación

  Escenario: Solo quien gestiona las órdenes

    Cuando un Estudiante o un Profesional externo intenta cambiar la prioridad

    Entonces recibe 403

  Escenario: Estados en que se puede cambiar

    Dado una orden ya ejecutada, cancelada o descartada

    Cuando alguien intenta cambiar su prioridad

    Entonces recibe un conflicto según la máquina de estados de las órdenes

  Escenario: Las propuestas ya generadas no cambian

    Dado propuestas generadas antes de esta HU

    Entonces conservan la prioridad que tenían

  Escenario: Auditoría (RN-USR-09)

    Cuando cambia la prioridad confirmada

    Entonces queda en la bitácora con el usuario, la justificación y los valores anterior y posterior

  Escenario: Cobertura

    Entonces la máquina de estados de las órdenes conserva el 100 % de cobertura

    Y la fórmula de prioridad tiene pruebas unitarias de sus casos límite{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

High

### Story point estimate

5

### Sprint

Sprint 4

### Labels

Administrador, Catedrático

### Parent

Épica O — Mantenimiento (fase 2) y procesos programados

### Assignee

Dany Noe DeLeon Santizo

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

20/Oct/26 12:00 AM

### Updated

20/Oct/26 5:26 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

20/Oct/26 12:00 AM

### Watchers

1

### Votes

0

### Rank

0|i000i4:

---

## HU-068 — Catedrático o Administrador gestiona el plan de mantenimiento rutinario anual de cada puente {#scrum-91}
**Jira:** [SCRUM-91](https://ayd1-g1-team.atlassian.net/browse/SCRUM-91)

### Issue Type

Story

### Description

*Historia*

Como Catedrático o Administrador, quiero que cada puente tenga su plan de mantenimiento rutinario con periodicidad máxima anual, para cumplir lo que establece el manual (RN-MTO-07; RN-USR-09).

*Dependencias:* HU-047 (ciclo de vida de las órdenes) y HU-069 (convención de ShedLock). La tabla {{plan_mantenimiento_rutinario}} ya existe (migración V10) y las órdenes tienen {{plan_mantenimiento_rutinario_id}} y {{ciclo_plan}}. Si hay que reducir, queda con su periodicidad anual y una alerta simple.

*Criterios de aceptación*

{noformat}Característica: Plan de mantenimiento rutinario anual por puente

  Escenario: Todo puente activo tiene su plan (RN-MTO-07)

    Dado un puente activo

    Entonces tiene un plan rutinario con periodicidad de un año como máximo

    Y un puente nuevo recibe su plan por defecto

    Pero un puente inactivo no lo recibe

  Escenario: Los puentes que ya existen

    Dado puentes activos creados antes de esta HU

    Cuando se despliega el cambio

    Entonces reciben su plan por defecto sin duplicarlo

  Escenario: Ajustar el plan

    Dado un Catedrático o un Administrador

    Cuando ajusta la periodicidad o las actividades del plan

    Entonces el sistema lo permite sin superar el máximo anual

  Escenario: Validaciones

    Cuando la periodicidad es cero, negativa o mayor de un año

    Entonces recibe 422 con el motivo y el plan no cambia

  Escenario: Orden por ciclo

    Dado un plan cuyo ciclo venció

    Cuando corre el proceso programado

    Entonces genera una orden rutinaria en estado PROPUESTA, identificada por su ciclo

    Y si el proceso corre dos veces no la duplica

  Escenario: Ya hay una orden del ciclo

    Dado un ciclo que ya tiene su orden, aunque esté en curso o ejecutada

    Entonces no se genera otra para ese ciclo

  Escenario: Un puente que se da de baja

    Dado un puente que pasa a inactivo

    Entonces su plan deja de generar órdenes y se conserva el historial

  Escenario: Visibilidad

    Entonces el plan y su próximo ciclo se ven en la ficha del puente y en el listado de mantenimiento

  Escenario: Quién puede gestionarlo

    Cuando un Estudiante o un Profesional externo intenta ajustar un plan

    Entonces recibe 403 y solo puede consultarlo

  Escenario: Auditoría (RN-USR-09)

    Cuando se crea o se ajusta un plan, o se genera una orden de ciclo

    Entonces queda en la bitácora, la última como proceso automático

  Escenario: Pruebas

    Entonces hay pruebas con PostgreSQL real del ciclo no duplicado, del puente inactivo, de los puentes existentes y de las validaciones{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

High

### Story point estimate

5

### Sprint

Sprint 4

### Labels

Administrador, Catedrático

### Parent

Épica O — Mantenimiento (fase 2) y procesos programados

### Assignee

Dany Noe DeLeon Santizo

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

20/Oct/26 12:00 AM

### Updated

20/Oct/26 5:26 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

20/Oct/26 12:00 AM

### Watchers

1

### Votes

0

### Rank

0|i000i7:

---

## HU-069 — Sistema ejecuta las alertas y limpiezas programadas con bloqueo distribuido {#scrum-92}
**Jira:** [SCRUM-92](https://ayd1-g1-team.atlassian.net/browse/SCRUM-92)

### Issue Type

Story

### Description

*Historia*

Como Sistema, quiero ejecutar solo las alertas y limpiezas que vencen cada día, para que nadie tenga que acordarse de ellas (RN-REV-08, RN-MTO-04, RN-MTO-08, RN-ARC-06; RN-USR-09; DT-BE-14).

*Dependencias:* HU-040 (avisos) y la configuración de ShedLock. Ya existe un proceso programado en el módulo de archivos ({{RecuperacionCargasService}}); este es el primero de alertas y fija la convención para los demás. *Decisión del responsable:* con cuántos días de anticipación se recuerda una orden programada; se recomienda 3.

*Criterios de aceptación*

{noformat}Característica: Procesos programados con ShedLock

  Escenario: Bloqueo distribuido (DT-BE-14)

    Dado un proceso programado con @Scheduled

    Entonces usa un bloqueo de ShedLock para que dos réplicas del servidor no lo ejecuten a la vez

    Y el primer proceso fija la convención (nombre, duración máxima y mínima del bloqueo) que los demás reutilizan

  Escenario: Inspección sin revisar (RN-REV-08)

    Dado una inspección enviada y no revisada en 15 días naturales

    Cuando corre el proceso

    Entonces el Catedrático asignado recibe un aviso de tipo REVISION_SIN_REVISAR_15_DIAS, una sola vez

  Escenario: Puente sin inspección en 12 meses (RN-MTO-08)

    Dado un puente activo sin inspección publicada en más de 12 meses

    Cuando corre el proceso

    Entonces el Catedrático y el Administrador reciben un aviso de tipo PUENTE_SIN_INSPECCION_12_MESES

    Y el aviso no se repite cada día

  Escenario: Recordatorio de una orden programada (DT-BE-14)

    Dado una orden PROGRAMADA cuya fecha programada está próxima

    Cuando corre el proceso

    Entonces quien la aceptó y los Administradores reciben un aviso de tipo ORDEN_PROGRAMADA_PROXIMA, una sola vez

  Escenario: Orden programada vencida

    Dado una orden PROGRAMADA cuya fecha ya pasó sin iniciarse

    Cuando corre el proceso

    Entonces recibe un aviso de tipo ORDEN_PROGRAMADA_VENCIDA, una sola vez

    Y la orden no cambia de estado por sí sola (RN-MTO-04)

  Escenario: Borradores inactivos (RN-ARC-06)

    Dado un borrador sin actividad durante 180 días

    Entonces su autor recibe un aviso de tipo BORRADOR_180_DIAS

    Y a los 210 días se purgan sus archivos multimedia, conservando el registro de datos

    Y la purga queda auditada

  Escenario: La purga es segura

    Dado un archivo de un borrador purgado

    Entonces solo se eliminan archivos de borradores y nunca los de una inspección publicada

    Y un fallo al borrar un archivo no detiene la purga de los demás

  Escenario: Claves de idempotencia vencidas

    Cuando corre el proceso de limpieza

    Entonces se eliminan las claves de idempotencia vencidas

  Escenario: Auditoría y tolerancia a fallos (RN-USR-09)

    Cuando se ejecuta cualquier proceso

    Entonces queda en la bitácora como proceso automático con su nombre

    Y el fallo de un elemento no detiene a los demás

  Escenario: Destinatarios

    Entonces solo reciben avisos los usuarios activos y cada aviso usa su clave de evento para no repetirse

  Escenario: Pruebas

    Entonces hay pruebas con reloj controlado y PostgreSQL real

    Y una ejecución simultánea no duplica avisos{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

High

### Story point estimate

8

### Sprint

Sprint 4

### Labels

Sistema

### Parent

Épica O — Mantenimiento (fase 2) y procesos programados

### Assignee

Angel Abelino Son Vicente

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

20/Oct/26 12:00 AM

### Updated

20/Oct/26 5:26 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

20/Oct/26 12:00 AM

### Watchers

1

### Votes

0

### Rank

0|i000ih:

---

## HU-070 — Inspector adjunta documentos PDF a su inspección {#scrum-93}
**Jira:** [SCRUM-93](https://ayd1-g1-team.atlassian.net/browse/SCRUM-93)

### Issue Type

Story

### Description

*Historia*

Como Inspector, quiero adjuntar documentos PDF a mi inspección, para respaldar mis observaciones con informes o planos (RN-ARC-04, RN-ARC-05; RN-INS-05; RN-USR-09, RN-USR-10; DT-SEC-08, DT-SEC-09; DT-BE-13; DT-ALM-03).

*Dependencias:* HU-028 y HU-029 (fotografías y seguridad de los archivos). {{archivo_inspeccion}} ya admite el tipo {{DOCUMENTO_INSPECCION}} con su restricción de PDF de hasta 20 MB; falta el servicio, el endpoint y la pantalla. Los PDF se adjuntan con conexión: el modo sin conexión cubre datos y fotografías (RN-OFF-05).

*Criterios de aceptación*

{noformat}Característica: Documentos PDF de la inspección

  Escenario: Límites (RN-ARC-04)

    Dado una inspección

    Entonces admite hasta 10 documentos PDF de hasta 20 MB cada uno, además de sus fotografías

    Y el undécimo documento o uno de más de 20 MB se rechaza con un mensaje claro

  Escenario: Validación en el servidor (DT-SEC-08)

    Cuando se sube un documento

    Entonces el servidor valida el tipo real por firma binaria, el tamaño y la cantidad

    Y genera el nombre del archivo, sin usar nunca el nombre del cliente

  Escenario: Un archivo que no es PDF

    Dado un archivo con extensión .pdf pero contenido distinto o un tipo declarado falso

    Entonces el servidor lo rechaza con 422 y no lo guarda

  Escenario: Almacenamiento (DT-BE-13, DT-ALM-03)

    Entonces el archivo se guarda en el almacenamiento de objetos mediante la interfaz AlmacenamientoArchivos

    Y en la base de datos solo queda su referencia

  Escenario: Solo el autor adjunta

    Dado una inspección en borrador

    Cuando alguien que no es su autor intenta adjuntar un documento

    Entonces recibe 403

  Escenario: Acceso (DT-SEC-09, RN-INS-05, RN-USR-10)

    Entonces se sirve con una URL firmada de vigencia limitada o por un extremo autorizado

    Y los de un borrador solo los ven su autor y el Catedrático asignado

    Y los de una inspección publicada exigen una cuenta activa

  Escenario: Se descarga con su nombre original

    Cuando un usuario autorizado descarga un documento

    Entonces el archivo se entrega con su nombre original sanitizado y sin exponer la ruta del almacenamiento

  Escenario: Retiro

    Dado una inspección en borrador

    Entonces sus documentos se pueden retirar

    Y si tras la publicación quedan fijos, como las fotografías (RN-ARC-05), lo decide el responsable; se recomienda que sí

  Escenario: Un fallo del almacenamiento

    Dado un error al guardar el archivo en el almacenamiento

    Entonces no queda un registro huérfano en la base de datos y el usuario puede reintentar

  Escenario: Auditoría (RN-USR-09)

    Cuando se adjunta o se retira un documento

    Entonces queda en la bitácora con el usuario y la inspección

  Escenario: Interfaz

    Entonces la inspección tiene una sección de documentos con subida, lista y descarga, accesible por teclado y con textos en es.json

  Escenario: Pruebas

    Entonces hay pruebas con PostgreSQL real y almacenamiento de prueba de los límites, del tipo falso, del acceso por rol y del fallo de almacenamiento{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

High

### Story point estimate

5

### Sprint

Sprint 4

### Labels

Catedrático, Estudiante, Profesional-Externo

### Parent

Épica P — Archivos, IC y consulta

### Assignee

Angel Abelino Son Vicente

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

20/Oct/26 12:00 AM

### Updated

20/Oct/26 5:26 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

20/Oct/26 12:00 AM

### Watchers

1

### Votes

0

### Rank

0|i000ij:

---

## HU-071 — Catedrático o Administrador recalcula el IC de inspecciones publicadas sin alterar el original {#scrum-94}
**Jira:** [SCRUM-94](https://ayd1-g1-team.atlassian.net/browse/SCRUM-94)

### Issue Type

Story

### Description

*Historia*

Como Catedrático o Administrador, quiero recalcular el IC de inspecciones ya publicadas con una calibración nueva sin alterar el resultado original, para comparar antes y después (RN-IC-08, RN-FRM-03; RN-USR-09; DT-ARQ-06).

*Dependencias:* HU-018 (cálculo), HU-020 (resultado del IC en la publicación) y HU-053 (calibración editable: cada ajuste de pesos genera una versión nueva y las anteriores deben seguir siendo comparables). {{resultado_ic}} ya guarda el tipo de resultado, la versión de configuración, quién lo solicitó y cuándo se calculó. *Decisión del responsable:* si, además del recálculo por inspección, se ofrece uno por lote para todas las publicadas de una versión.

*Criterios de aceptación*

{noformat}Característica: Recálculo del IC fechado y comparable

  Escenario: No se altera lo publicado (RN-IC-08)

    Dado una inspección publicada

    Cuando cambian los pesos

    Entonces su IC original no se modifica retroactivamente

  Escenario: Pedir un recálculo

    Dado una inspección publicada

    Cuando un Catedrático o un Administrador pide el recálculo con la versión de configuración vigente

    Entonces el resultado se guarda como un recálculo fechado, con su versión de configuración y quien lo solicitó

    Y queda junto al resultado original

  Escenario: Solo inspecciones publicadas

    Dado una inspección que no está publicada

    Cuando alguien pide el recálculo

    Entonces recibe un conflicto, porque el IC solo es válido para inspecciones publicadas (RN-IC-07)

  Escenario: Misma configuración, mismo resultado

    Dado un recálculo con la misma versión de configuración y los mismos datos que uno anterior

    Entonces no se crea un segundo registro y se devuelve el existente

  Escenario: Recálculos simultáneos

    Dado dos usuarios que piden el mismo recálculo al mismo tiempo

    Entonces solo se registra uno

  Escenario: La configuración no permite calcular

    Dado una versión vigente sin el IC completo

    Cuando se pide el recálculo

    Entonces recibe un error claro y no se guarda nada

  Escenario: Comparar

    Cuando el usuario abre el detalle de la inspección

    Entonces ve el IC original y los recálculos con su fecha, rotulados, para poder compararlos

  Escenario: El puente no cambia

    Cuando se registra un recálculo

    Entonces el IC y el estado vigentes del puente siguen siendo los de la publicación original

  Escenario: El cálculo sigue en el backend (DT-ARQ-06)

    Entonces el recálculo reutiliza el mismo servicio de cálculo, que conserva el 100 % de cobertura

  Escenario: Quién puede pedirlo

    Cuando un Estudiante o un Profesional externo intenta pedir un recálculo

    Entonces recibe 403

  Escenario: Auditoría (RN-USR-09)

    Cuando se registra un recálculo

    Entonces queda en la bitácora con el usuario, la inspección y la versión de configuración

  Escenario: Pruebas

    Entonces hay pruebas con PostgreSQL real del recálculo repetido, del simultáneo, de la inspección no publicada y de la autorización por rol{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

High

### Story point estimate

5

### Sprint

Sprint 4

### Labels

Administrador, Catedrático

### Parent

Épica P — Archivos, IC y consulta

### Assignee

KENNY SALAZAR

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

20/Oct/26 12:00 AM

### Updated

20/Oct/26 12:21 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

20/Oct/26 12:00 AM

### Watchers

1

### Votes

0

### Rank

0|i000is:

---

## HU-072 — Usuario consulta, exporta e imprime inspecciones de cualquier versión del formulario {#scrum-95}
**Jira:** [SCRUM-95](https://ayd1-g1-team.atlassian.net/browse/SCRUM-95)

### Issue Type

Story

### Description

*Historia*

Como usuario autenticado, quiero consultar, exportar e imprimir una inspección publicada con la estructura del formulario con que se levantó, para conservar su valor histórico (RN-FRM-05, RN-FRM-06, RN-FRM-07; RN-USR-10; DT-FE-13).

*Dependencias:* HU-021 (versiones y mapeos, RN-FRM-06), HU-030 (consulta de inspecciones publicadas) y HU-028 (fotografías). Si hay que reducir, la exportación se apoya en la impresión del navegador. *Decisión del responsable:* el formato de la exportación; se recomienda un archivo JSON con los datos y el esquema de su versión, además de la impresión.

*Criterios de aceptación*

{noformat}Característica: Consulta, exportación e impresión de cualquier versión

  Escenario: Estructura de la versión original (RN-FRM-05)

    Dado una inspección publicada levantada con una versión anterior del formulario

    Cuando un usuario la consulta

    Entonces se muestra con el esquema de esa versión, aunque la versión activa sea otra

  Escenario: Comparar con la versión activa

    Dado una inspección de una versión anterior cuyo mapeo de campos está declarado

    Entonces el detalle indica con qué versión se levantó y cómo se relaciona con la activa (RN-FRM-06)

  Escenario: Impresión (RN-FRM-07)

    Cuando el usuario imprime la inspección

    Entonces la hoja incluye las ocho secciones del formulario, el reporte fotográfico y los documentos, las recomendaciones y las propuestas de mantenimiento

    Y usa una hoja de estilos de impresión legible, con saltos de página razonables

  Escenario: Exportación

    Cuando el usuario exporta la inspección

    Entonces descarga un archivo con los datos y el esquema de la versión con que se levantó

    Y el archivo no incluye datos de otros usuarios ni referencias internas

  Escenario: Fotografías y documentos

    Cuando la inspección tiene fotografías y documentos

    Entonces se listan en el reporte con su categoría y su elemento, y las fotografías se cargan solo con una URL autorizada

  Escenario: Quién puede verla (RN-USR-10)

    Entonces el detalle, las fotografías y los informes exigen una cuenta activa

    Y el visitante solo ve el inventario público

  Escenario: Solo inspecciones publicadas

    Dado una inspección en borrador

    Cuando alguien que no es su autor ni su Catedrático asignado intenta consultarla

    Entonces recibe 404 (RN-INS-05)

  Escenario: Etiqueta textual (DT-FE-13)

    Entonces el IC y el estado salen con su etiqueta textual y no solo con color

  Escenario: Una inspección grande

    Dado una inspección con muchas fotografías

    Cuando se abre para imprimir

    Entonces la vista sigue respondiendo y las imágenes se cargan de forma diferida

  Escenario: Alcance mínimo

    Dado que hay que reducir el alcance

    Entonces la exportación se apoya en la impresión del navegador con una hoja de estilos de impresión, sin generador de PDF propio

  Escenario: Pruebas

    Entonces hay una prueba con una inspección de una versión anterior cuyo mapeo de campos esté declarado

    Y otra del acceso sin sesión y de la inspección en borrador{noformat}

### Status

En curso

### Status category

In Progress

### Priority

High

### Story point estimate

5

### Sprint

Sprint 4

### Parent

Épica P — Archivos, IC y consulta

### Assignee

Rony Rojas Aguilar

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

20/Oct/26 12:00 AM

### Updated

20/Oct/26 10:41 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

20/Oct/26 10:41 PM

### Watchers

1

### Votes

0

### Rank

0|i000it:

---

## HU-073 — Sistema demuestra con pruebas la autorización por rol y el cumplimiento de seguridad del despliegue {#scrum-96}
**Jira:** [SCRUM-96](https://ayd1-g1-team.atlassian.net/browse/SCRUM-96)

### Issue Type

Story

### Description

*Historia*

Como equipo, queremos demostrar que cada actor solo accede a lo que le corresponde y que el despliegue cumple las directrices de seguridad, para cumplir el entregable de pruebas y evitar accesos indebidos (DT-CAL-03; DT-SEC-03, DT-SEC-04, DT-SEC-06, DT-SEC-10, DT-SEC-11, DT-SEC-12; DT-DEP-09, DT-DEP-10).

*Dependencias:* todos los endpoints existentes y los nuevos del Sprint 4, que se cubren en su propia HU; aquí solo se verifica que ninguno quedó fuera. Arranca el martes sobre lo ya fusionado y no espera al final. La parte B recoge brechas de despliegue detectadas en develop: bitácoras en JSON con retención de 90 días, el encabezado HSTS y la exposición de Actuator. *Alcance mínimo si hay que reducir:* la parte B se verifica el domingo 25 y lo que no se cierre queda documentado como desviación en un ADR, como permite el enunciado.

*Criterios de aceptación*

{noformat}Característica: Autorización por rol y cumplimiento de seguridad del despliegue

  # Parte A: autorización

  Escenario: Inventario de endpoints

    Dado la API completa

    Entonces hay un inventario de todos los endpoints con el rol esperado de cada uno

    Y una prueba recorre los endpoints registrados y falla si alguno no declara su autorización (@PreAuthorize) ni está en la lista de endpoints públicos (DT-SEC-03)

  Escenario: Cada endpoint por rol (DT-CAL-03)

    Dado cualquier endpoint

    Entonces tiene al menos una prueba que comprueba que los roles permitidos acceden

    Y que los demás reciben 403, o 401 sin sesión

  Escenario: Cuentas desactivadas y sesiones vencidas

    Cuando un usuario desactivado o con un token vencido invoca un endpoint protegido

    Entonces recibe 401 o 403 y no obtiene datos

  Escenario: Autorización a nivel de fila (DT-SEC-04)

    Entonces un Estudiante solo accede a sus borradores

    Y un Catedrático solo accede a los cursos que dirige

    Y ambas reglas tienen pruebas de integración propias

  Escenario: Un recurso ajeno no revela su existencia

    Cuando un usuario pide un recurso de otro usuario por su identificador

    Entonces recibe 404 o 403 sin información sobre el recurso

  Escenario: Huecos encontrados

    Dado un endpoint sin la autorización correcta

    Entonces se corrige con el cambio mínimo y queda documentado

  Escenario: Entregable de autorización

    Entonces se entrega una tabla de endpoint, roles permitidos y prueba para el manual técnico

  # Parte B: cumplimiento del despliegue

  Escenario: HTTPS y HSTS (DT-SEC-10)

    Dado el ambiente de producción

    Cuando se pide una página por HTTP

    Entonces se redirige a HTTPS

    Y las respuestas HTTPS llevan el encabezado Strict-Transport-Security

  Escenario: Encabezados de seguridad (DT-SEC-11)

    Entonces las respuestas llevan Content-Security-Policy, X-Content-Type-Options nosniff, X-Frame-Options DENY y Referrer-Policy strict-origin-when-cross-origin

    Y una prueba lo comprueba contra el ambiente desplegado

  Escenario: Actuator solo en la red interna (DT-DEP-10)

    Cuando alguien pide /actuator/health o /actuator/metrics desde fuera del servidor

    Entonces el proxy lo rechaza y solo responde en la red interna

  Escenario: CORS explícito (DT-SEC-06)

    Entonces solo se aceptan los orígenes de la lista del despliegue y nunca un comodín

  Escenario: Bitácoras estructuradas (DT-DEP-09)

    Entonces los registros del backend salen en formato JSON, rotados y conservados al menos 90 días

    Y la rotación por tamaño de los contenedores no los elimina antes de ese plazo

  Escenario: Sin datos sensibles en la bitácora (DT-SEC-12)

    Entonces los registros no contienen tokens, contraseñas ni datos personales completos

    Y una prueba lo comprueba con una petición de inicio de sesión

  Escenario: Pruebas

    Entonces hay una prueba automatizada para el inventario de endpoints y los roles

    Y una lista de comprobación ejecutada contra staging para la parte B, con su resultado documentado{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

Highest

### Story point estimate

5

### Sprint

Sprint 4

### Labels

Sistema

### Parent

Épica Q — Seguridad y calidad

### Assignee

Rony Rojas Aguilar

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

20/Oct/26 12:00 AM

### Updated

20/Oct/26 12:21 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

20/Oct/26 12:00 AM

### Watchers

1

### Votes

0

### Rank

0|i000j1:

---

## HU-074 — Administrador protege las coordenadas de los puentes en estado Malo {#scrum-97}
**Jira:** [SCRUM-97](https://ayd1-g1-team.atlassian.net/browse/SCRUM-97)

### Issue Type

Story

### Description

*Historia*

Como Administrador, quiero que la ubicación exacta de un puente en estado Malo no se publique sin sesión, para no exponer información sensible de infraestructura crítica (DT-SEC-13; RN-USR-10).

*Dependencias:* HU-014 y HU-015 (catálogo y ficha del puente) y HU-033 (estado con etiqueta textual). La directriz dice que las coordenadas «pueden restringirse»; se recomienda aplicarla y dejar la decisión en un ADR.

*Criterios de aceptación*

{noformat}Característica: Coordenadas de puentes en estado Malo solo para usuarios autenticados

  Escenario: Sin sesión

    Dado un puente en estado Malo

    Cuando se consulta el catálogo público o el mapa sin sesión

    Entonces no se devuelven sus coordenadas exactas

  Escenario: Con sesión

    Dado un usuario autenticado

    Cuando consulta el mismo puente

    Entonces ve sus coordenadas exactas

  Escenario: Todos los puntos de salida

    Entonces la restricción aplica al listado, al detalle, al mapa, a la búsqueda y a cualquier otro endpoint público que devuelva la ubicación

  Escenario: El mapa público

    Entonces no se dibuja el marcador del puente en estado Malo

    Y aparece en la lista con su municipio y su estado con etiqueta textual (DT-FE-13)

  Escenario: Otros estados

    Dado un puente en estado Bueno, Regular o sin evaluar

    Entonces conserva el comportamiento público de siempre, con su ubicación

  Escenario: Puentes cercanos

    Entonces la búsqueda de puentes cercanos para evitar duplicados no se ve afectada

  Escenario: Cambios de estado

    Dado un puente que pasa de Malo a otro estado y vuelve a Malo

    Entonces la restricción sigue al estado vigente en cada momento

  Escenario: Sin filtraciones por caché

    Entonces una respuesta con coordenadas dada a un usuario autenticado no se reutiliza para un visitante

  Escenario: Pruebas

    Entonces hay pruebas con y sin sesión, para cada punto de salida y al cambiar el estado de un puente de Malo a otro y de vuelta{noformat}

### Status

Por hacer

### Status category

To Do

### Priority

High

### Story point estimate

2

### Sprint

Sprint 4

### Labels

Administrador

### Parent

Épica Q — Seguridad y calidad

### Assignee

Dany Noe DeLeon Santizo

### Reporter

Herberth Julian Reyes Pacajoj

### Creator

Herberth Julian Reyes Pacajoj

### Created

20/Oct/26 12:00 AM

### Updated

20/Oct/26 5:26 PM

### Resolution

*Unresolved*

### Project

AyD1-Proyecto-Final

### Status category changed

20/Oct/26 12:00 AM

### Watchers

1

### Votes

0

### Rank

0|i000jg:

---
