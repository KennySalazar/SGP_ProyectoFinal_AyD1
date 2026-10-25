-- HU-068: planes rutinarios sin flujo de aprobación y actividades estructuradas.
ALTER TABLE plan_mantenimiento_rutinario
    ALTER COLUMN responsable_usuario_id DROP NOT NULL,
    ALTER COLUMN aprobado_por_id DROP NOT NULL,
    ALTER COLUMN aprobado_en DROP NOT NULL;

CREATE TABLE plan_mantenimiento_rutinario_actividad (
    id UUID PRIMARY KEY,
    plan_mantenimiento_rutinario_id UUID NOT NULL
        REFERENCES plan_mantenimiento_rutinario(id),
    posicion INTEGER NOT NULL CHECK (posicion > 0),
    descripcion TEXT NOT NULL CHECK (length(trim(descripcion)) > 0),
    CONSTRAINT uq_plan_actividad_posicion
        UNIQUE (plan_mantenimiento_rutinario_id, posicion)
        DEFERRABLE INITIALLY DEFERRED
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM orden_mantenimiento
        WHERE plan_mantenimiento_rutinario_id IS NOT NULL
          AND ciclo_plan IS NULL
    ) THEN
        RAISE EXCEPTION
            'HU-068: plan cycle orders without a cycle exist; repair their cycle identifiers before migration';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM orden_mantenimiento
        WHERE plan_mantenimiento_rutinario_id IS NOT NULL
        GROUP BY plan_mantenimiento_rutinario_id, ciclo_plan
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'HU-068: duplicate plan cycle orders exist; resolve without deleting order history before migration';
    END IF;
END $$;

ALTER TABLE orden_mantenimiento
    DROP CONSTRAINT orden_plan_ciclo_coherente,
    ADD CONSTRAINT orden_plan_ciclo_coherente CHECK (
        (plan_mantenimiento_rutinario_id IS NULL AND ciclo_plan IS NULL)
        OR (
            plan_mantenimiento_rutinario_id IS NOT NULL
            AND ciclo_plan IS NOT NULL
            AND ciclo_plan > 0
            AND tipo = 'RUTINARIO'
        )
    );

DROP INDEX uq_orden_plan_ciclo_activo;
CREATE UNIQUE INDEX uq_orden_plan_ciclo
    ON orden_mantenimiento (plan_mantenimiento_rutinario_id, ciclo_plan)
    WHERE plan_mantenimiento_rutinario_id IS NOT NULL;
