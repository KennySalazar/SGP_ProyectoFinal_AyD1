ALTER TABLE puente
    ADD COLUMN estado_actual VARCHAR(20) NOT NULL DEFAULT 'Sin evaluar',
    ADD COLUMN indice_condicion_actual NUMERIC(8,5),
    ADD COLUMN fecha_ultima_inspeccion DATE,
    ADD COLUMN inspeccion_vigente_id UUID,
    ADD COLUMN resultado_ic_vigente_id UUID,
    ADD COLUMN inspeccion_vigente_publicada_en TIMESTAMPTZ;

WITH ultima_publicada AS (
    SELECT DISTINCT ON (i.puente_id)
    i.puente_id,
    i.id AS inspeccion_id,
    i.fecha_inspeccion,
    i.publicado_en
FROM inspeccion i
WHERE i.estado = 'PUBLICADA'
ORDER BY i.puente_id, i.publicado_en DESC, i.id DESC
    )
UPDATE puente p
SET estado_actual = CASE r.estado_confirmado
                        WHEN 'BUENO' THEN 'Bueno'
                        WHEN 'REGULAR' THEN 'Regular'
                        WHEN 'MALO' THEN 'Malo'
    END,
    indice_condicion_actual = r.indice_calculado,
    fecha_ultima_inspeccion = i.fecha_inspeccion,
    inspeccion_vigente_id = i.inspeccion_id,
    resultado_ic_vigente_id = r.id,
    inspeccion_vigente_publicada_en = i.publicado_en
    FROM ultima_publicada i
JOIN resultado_ic r
ON r.inspeccion_id = i.inspeccion_id
    AND r.tipo = 'PUBLICACION'
WHERE p.id = i.puente_id;

ALTER TABLE puente
    ADD CONSTRAINT puente_estado_actual_valido CHECK (
        estado_actual IN ('Sin evaluar', 'Bueno', 'Regular', 'Malo')
        ),
    ADD CONSTRAINT puente_ic_actual_rango CHECK (
        indice_condicion_actual IS NULL
        OR indice_condicion_actual BETWEEN 0 AND 100
    ),
    ADD CONSTRAINT puente_estado_vigente_completo CHECK (
        (
            estado_actual = 'Sin evaluar'
            AND indice_condicion_actual IS NULL
            AND fecha_ultima_inspeccion IS NULL
            AND inspeccion_vigente_id IS NULL
            AND resultado_ic_vigente_id IS NULL
            AND inspeccion_vigente_publicada_en IS NULL
        )
        OR (
            estado_actual IN ('Bueno', 'Regular', 'Malo')
            AND indice_condicion_actual IS NOT NULL
            AND fecha_ultima_inspeccion IS NOT NULL
            AND inspeccion_vigente_id IS NOT NULL
            AND resultado_ic_vigente_id IS NOT NULL
            AND inspeccion_vigente_publicada_en IS NOT NULL
        )
    ),
    ADD CONSTRAINT fk_puente_inspeccion_vigente
        FOREIGN KEY (inspeccion_vigente_id, id)
        REFERENCES inspeccion (id, puente_id),
    ADD CONSTRAINT fk_puente_resultado_ic_vigente
        FOREIGN KEY (resultado_ic_vigente_id, inspeccion_vigente_id)
        REFERENCES resultado_ic (id, inspeccion_id);