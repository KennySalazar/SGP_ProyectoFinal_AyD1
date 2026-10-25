# Integración HU-069 con HU-068

## Estado

- **HU-068:** integración completada en esta rama: el job de ciclos rutinarios usa la
  infraestructura compartida de ShedLock.
- **HU-069:** pendiente. Este documento registra los puntos de integración; no significa que sus
  alertas, limpiezas y purgas estén implementadas.

## Integración implementada por HU-068

- `GeneradorCiclosMantenimientoService` usa `SchedulingConfiguration` y el `LockProvider` JDBC
  compartido (hora de base de datos). Su lock se llama
  `generar-ciclos-mantenimiento-rutinario` y usa `lockAtMostFor = PT10M`, como
  `RecuperacionCargasService`.
- El job delega a `PlanMantenimientoService.generarCiclosVencidos()`, transaccional. Reconcilia
  planes faltantes para puentes activos y genera ciclos vencidos solo para planes y puentes
  activos.
- `PuenteService` crea el plan por defecto durante el alta y la reactivación. La reconciliación
  cubre puentes activos preexistentes de forma idempotente y audita esas altas con usuario nulo y
  proceso `hu068-backfill-planes-rutinarios`.
- `OrdenCicloPlanService` persiste la orden `RUTINARIO` en `PROPUESTA`, historial inicial y
  auditoría dentro de la transacción. La unicidad de ciclo incluye órdenes en cualquier estado.

## Trabajo de HU-069 aún pendiente

HU-069 debe implementar y verificar, además del bloqueo compartido: avisos por inspecciones sin
revisar a 15 días; avisos por puentes sin inspección publicada a 12 meses; recordatorios y avisos
de vencimiento de órdenes programadas; avisos de borradores a 180 días y purga de multimedia a
210 días con auditoría; limpieza de claves de idempotencia; destinatarios activos, deduplicación
por clave de evento y tolerancia a fallos por elemento. La anticipación de recordatorio de una
orden aún requiere la decisión definida en HU-069.

## Persistencia y compatibilidad pendientes de coordinación

- La migración de HU-068 en esta rama es `V25__planes_mantenimiento_rutinario.sql`. Hace nulos
  los metadatos opcionales preservando sus FK, crea actividades ordenadas y reemplaza la
  unicidad parcial de ciclos después de comprobar duplicados históricos.
- La rama integrada contiene las migraciones de `develop` hasta V24; HU-068 usa V25 para evitar
  la colisión de V23 con la migración de calibración. V25 conserva el SQL original de HU-068.

## Decisiones de HU-068 que afectan a HU-069

- No hay aprobación de plan como requisito ni como paso de generación. HU-047 mantiene la
  decisión humana sobre cada propuesta.
- Los ciclos se numeran por meses vencidos desde `fecha_inicio`, en múltiplos de la periodicidad;
  cambiar la periodicidad no reutiliza el número de ciclo.
- Las propuestas rutinarias usan prioridad `MEDIA`, necesaria por el contrato actual de órdenes;
  confirmar si HU-069 u otra regla futura requiere otra prioridad.
