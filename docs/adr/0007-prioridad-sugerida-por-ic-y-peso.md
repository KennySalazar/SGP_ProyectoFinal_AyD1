# ADR-0007: Prioridad sugerida por IC y peso del elemento

## Estado

Aceptada

## Contexto

HU-067 y RN-MTO-06 requieren sugerir la prioridad de una propuesta generada combinando la
prioridad base de la regla, el Índice de Condición (IC) publicado y el peso calibrado del elemento
afectado. El requisito exige un cálculo determinista que nunca reduzca la prioridad base, pero no
prescribe una ecuación ni umbrales.

## Decisiones

### 1. Cálculo determinista (Opción 3)

Se asignan índices `BAJA=0`, `MEDIA=1`, `ALTA=2` y `CRITICA=3`. Se calcula:

```text
D = (100 - IC) / 100
W = elementWeight / maxConfiguredWeightForExactFormVersion
suggestedIndex = baseIndex + roundHalfUp((3 - baseIndex) * D * W)
```

La implementación usa aritmética decimal y redondeo `HALF_UP`, incluidos los casos exactamente
intermedios. El máximo se obtiene de todos los pesos positivos configurados en la versión exacta
del formulario de la inspección, no solo de los elementos incluidos en el desglose publicado. Así
`W` queda normalizado respecto de la calibración de esa versión. La prioridad sugerida nunca baja
de la base.

### 2. Datos ausentes o inválidos

- Si falta el resultado IC o el peso del elemento afectado, se conserva la prioridad base y se
  agrega el motivo al fundamento de generación.
- Un IC fuera de `[0,100]`, un peso afectado no positivo, una versión inconsistente, un máximo
  positivo inexistente o un `W` fuera de `[0,1]` también conserva la base y registra un motivo;
  esos valores no se corrigen ni se recortan silenciosamente.
- La generación de otras propuestas continúa aunque una propuesta use el fallback.
- La prioridad base ausente o no interpretable continúa resolviéndose como `MEDIA` por el
  comportamiento existente de `ReglaMantenimiento`; HU-067 no cambia esa regla.

### 3. Cambios de prioridad confirmada

La prioridad sugerida de una orden generada es inmutable. Un Catedrático o Administrador puede
cambiar la confirmada con justificación obligatoria. El cambio conserva ambos valores y se audita
con actor, motivo y snapshots anterior/posterior. No se permite en `EJECUTADA`, `CANCELADA` ni
`DESCARTADA`; la máquina de estados permanece intacta.

### 4. Compatibilidad y persistencia

El cálculo solo se aplica a propuestas nuevas. Las propuestas ya persistidas no se recalculan.
Se reutilizan `prioridad_sugerida`, `prioridad_confirmada`, `justificacion_prioridad`,
`fundamento_generacion` y la bitácora existentes; no se requiere cambio de esquema.

## Alternativas descartadas

- Recalcular órdenes históricas: alteraría decisiones ya registradas y no es requerido por HU-067.
- Normalizar sobre los elementos presentes en cada desglose: haría depender `W` de la respuesta o
  ausencia de elementos en una inspección, aunque pertenezcan a la misma calibración.
- Recortar valores inválidos a los límites: ocultaría corrupción o mezcla de versiones.

## Consecuencias

- La fórmula es reproducible y sus factores quedan visibles en el fundamento de la propuesta.
- La calibración de una versión determina la normalización; cambios futuros de pesos aplican solo a
  inspecciones levantadas con la versión correspondiente.
- IC o peso ausente/inválido no bloquea la generación, pero mantiene la prioridad base e informa
  por qué no se calculó el ajuste.
