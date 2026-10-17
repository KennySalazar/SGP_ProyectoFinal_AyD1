CREATE TABLE confirmacion_estado_inspeccion (
    inspeccion_id UUID PRIMARY KEY REFERENCES inspeccion(id),
    version_formulario_id UUID NOT NULL REFERENCES version_formulario(id),
    hash_entrada VARCHAR(64) NOT NULL,
    version_algoritmo VARCHAR(50) NOT NULL,
    indice_calculado NUMERIC(8,5) NOT NULL,
    estado_calculado VARCHAR(20) NOT NULL,
    estado_confirmado VARCHAR(20) NOT NULL,
    justificacion_ajuste TEXT,
    realizado_por_id UUID NOT NULL REFERENCES usuario(id),
    realizado_en TIMESTAMPTZ NOT NULL,
    calculo JSONB NOT NULL,
    CONSTRAINT confirmacion_estado_ic_rango
        CHECK (indice_calculado BETWEEN 0 AND 100),
    CONSTRAINT confirmacion_estado_valores_validos
        CHECK (
            estado_calculado IN ('BUENO', 'REGULAR', 'MALO')
            AND estado_confirmado IN ('BUENO', 'REGULAR', 'MALO')
        ),
    CONSTRAINT confirmacion_estado_justificacion_coherente
        CHECK (
            (
                estado_confirmado = estado_calculado
                AND justificacion_ajuste IS NULL
            )
            OR (
                estado_confirmado <> estado_calculado
                AND nullif(btrim(justificacion_ajuste), '') IS NOT NULL
            )
        ),
    CONSTRAINT confirmacion_estado_hash_valido
        CHECK (hash_entrada ~ '^[0-9a-f]{64}$'),
    CONSTRAINT confirmacion_estado_algoritmo_no_vacio
        CHECK (nullif(btrim(version_algoritmo), '') IS NOT NULL),
    CONSTRAINT confirmacion_estado_calculo_objeto
        CHECK (jsonb_typeof(calculo) = 'object')
);
