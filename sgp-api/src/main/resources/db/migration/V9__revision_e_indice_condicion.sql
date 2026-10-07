-- Revisión académica, historial de estados y resultados del índice de condición.

-- Un ciclo por cada envío de una inspección.
CREATE TABLE revision (
    id UUID PRIMARY KEY,
    inspeccion_id UUID NOT NULL REFERENCES inspeccion(id),
    numero_ciclo INTEGER NOT NULL,
    revisor_id UUID NOT NULL REFERENCES usuario(id),
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    veredicto VARCHAR(30),
    observacion_general TEXT,
    calificacion NUMERIC(6,2),
    enviada_en TIMESTAMPTZ NOT NULL,
    iniciada_en TIMESTAMPTZ,
    resuelta_en TIMESTAMPTZ,
    version_inspeccion_enviada BIGINT NOT NULL,
    contenido_enviado JSONB NOT NULL,
    hash_contenido VARCHAR(64) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT revision_ciclo_positivo CHECK (numero_ciclo > 0),
    CONSTRAINT revision_estado_valido CHECK (
        estado IN ('PENDIENTE', 'EN_CURSO', 'RESUELTA')
    ),
    CONSTRAINT revision_veredicto_valido CHECK (
        veredicto IS NULL
        OR veredicto IN ('APROBADA', 'CAMBIOS_SOLICITADOS', 'RECHAZADA')
    ),
    CONSTRAINT revision_resolucion_completa CHECK (
        estado <> 'RESUELTA'
        OR (veredicto IS NOT NULL AND resuelta_en IS NOT NULL)
    ),
    CONSTRAINT revision_motivo_veredicto CHECK (
        veredicto NOT IN ('CAMBIOS_SOLICITADOS', 'RECHAZADA')
        OR nullif(btrim(observacion_general), '') IS NOT NULL
    ),
    CONSTRAINT uq_revision_inspeccion_ciclo UNIQUE (inspeccion_id, numero_ciclo)
);
CREATE UNIQUE INDEX uq_revision_abierta_por_inspeccion ON revision (inspeccion_id) WHERE estado IN ('PENDIENTE','EN_CURSO');
CREATE INDEX idx_revision_revisor_estado_fecha ON revision (revisor_id, estado, enviada_en);

-- Bitácora inmutable de transiciones de una inspección.
CREATE TABLE historial_estado_inspeccion (
    id UUID PRIMARY KEY,
    inspeccion_id UUID NOT NULL REFERENCES inspeccion(id),
    secuencia INTEGER NOT NULL,
    estado_anterior VARCHAR(30),
    estado_nuevo VARCHAR(30) NOT NULL,
    realizado_por_id UUID REFERENCES usuario(id),
    revision_id UUID REFERENCES revision(id),
    evento VARCHAR(60) NOT NULL,
    motivo TEXT,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT historial_estado_inspeccion_secuencia_positiva CHECK (secuencia > 0),
    CONSTRAINT historial_estado_inspeccion_valido CHECK (
        estado_nuevo IN (
            'BORRADOR', 'ENVIADA', 'EN_REVISION', 'CAMBIOS_SOLICITADOS',
            'PUBLICADA', 'RECHAZADA'
        )
    ),
    CONSTRAINT uq_historial_estado_inspeccion_secuencia UNIQUE (inspeccion_id, secuencia)
);
CREATE INDEX idx_historial_estado_inspeccion_fecha ON historial_estado_inspeccion (inspeccion_id, creado_en);

-- Observaciones puntuales dentro de un ciclo de revisión.
CREATE TABLE observacion_revision (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL REFERENCES revision(id),
    elemento_ref VARCHAR(300),
    instancia_elemento_id UUID,
    contenido TEXT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    creado_por_id UUID NOT NULL REFERENCES usuario(id),
    resuelto_por_id UUID REFERENCES usuario(id),
    resuelto_en TIMESTAMPTZ,
    respuesta_autor TEXT,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT observacion_estado_valido CHECK (estado IN ('PENDIENTE','RESUELTA')),
    CONSTRAINT observacion_instancia_requiere_ref CHECK (instancia_elemento_id IS NULL OR elemento_ref IS NOT NULL),
    CONSTRAINT observacion_resolucion_completa CHECK (
        (estado = 'PENDIENTE' AND resuelto_por_id IS NULL AND resuelto_en IS NULL)
        OR (
            estado = 'RESUELTA'
            AND resuelto_por_id IS NOT NULL
            AND resuelto_en IS NOT NULL
        )
    )
);
CREATE INDEX idx_observacion_revision_estado ON observacion_revision (revision_id, estado);

-- Resultados inmutables del índice de condición.
CREATE TABLE resultado_ic (
    id UUID PRIMARY KEY,
    inspeccion_id UUID NOT NULL REFERENCES inspeccion(id),
    version_configuracion_id UUID NOT NULL REFERENCES version_formulario(id),
    tipo VARCHAR(20) NOT NULL,
    indice_calculado NUMERIC(8,5) NOT NULL,
    estado_por_rango VARCHAR(20) NOT NULL,
    estado_calculado VARCHAR(20) NOT NULL,
    estado_confirmado VARCHAR(20) NOT NULL,
    condiciones_anulacion JSONB NOT NULL DEFAULT '[]'::jsonb,
    desglose JSONB NOT NULL,
    hash_entrada VARCHAR(64) NOT NULL,
    version_algoritmo VARCHAR(50) NOT NULL,
    justificacion_ajuste TEXT,
    confirmado_por_id UUID REFERENCES usuario(id),
    confirmado_en TIMESTAMPTZ,
    solicitado_por_id UUID REFERENCES usuario(id),
    calculado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT resultado_ic_tipo_valido CHECK (tipo IN ('PUBLICACION', 'RECALCULO')),
    CONSTRAINT resultado_ic_rango CHECK (indice_calculado BETWEEN 0 AND 100),
    CONSTRAINT resultado_ic_estados_validos CHECK (
        estado_por_rango IN ('BUENO', 'REGULAR', 'MALO')
        AND estado_calculado IN ('BUENO', 'REGULAR', 'MALO')
        AND estado_confirmado IN ('BUENO', 'REGULAR', 'MALO')
    ),
    CONSTRAINT resultado_ic_ajuste_completo CHECK (
        (
            estado_confirmado = estado_calculado
            AND justificacion_ajuste IS NULL
            AND confirmado_por_id IS NULL
            AND confirmado_en IS NULL
        )
        OR (
            estado_confirmado <> estado_calculado
            AND justificacion_ajuste IS NOT NULL
            AND confirmado_por_id IS NOT NULL
            AND confirmado_en IS NOT NULL
        )
    )
);
CREATE UNIQUE INDEX uq_resultado_ic_publicacion ON resultado_ic (inspeccion_id) WHERE tipo = 'PUBLICACION';
ALTER TABLE resultado_ic
    ADD CONSTRAINT uq_resultado_ic_id_inspeccion UNIQUE (id, inspeccion_id);
CREATE INDEX idx_resultado_ic_inspeccion_fecha ON resultado_ic (inspeccion_id, calculado_en DESC);
