-- Versiones de formulario y configuración reproducible del índice de condición.

-- Definición versionada del formulario de inspección.
CREATE TABLE version_formulario (
    id UUID PRIMARY KEY,
    codigo VARCHAR(50) NOT NULL UNIQUE,
    estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    activa BOOLEAN NOT NULL DEFAULT FALSE,
    esquema JSONB NOT NULL,
    vinculos_almacenamiento JSONB NOT NULL DEFAULT '{}'::jsonb,
    reglas_evaluacion_ic JSONB NOT NULL DEFAULT '{}'::jsonb,
    hash_contenido VARCHAR(64),
    version_algoritmo_ic VARCHAR(50),
    version_anterior_id UUID REFERENCES version_formulario(id),
    creado_por_id UUID NOT NULL REFERENCES usuario(id),
    publicado_por_id UUID REFERENCES usuario(id),
    publicado_en TIMESTAMPTZ,
    calibrado_por_id UUID REFERENCES usuario(id),
    calibrado_en TIMESTAMPTZ,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT version_formulario_estado_valido CHECK (estado IN ('BORRADOR', 'PUBLICADA')),
    CONSTRAINT version_formulario_activa_publicada CHECK (NOT activa OR estado = 'PUBLICADA'),
    CONSTRAINT version_formulario_publicacion_completa CHECK (
        estado <> 'PUBLICADA'
        OR (
            hash_contenido IS NOT NULL
            AND version_algoritmo_ic IS NOT NULL
            AND publicado_por_id IS NOT NULL
            AND publicado_en IS NOT NULL
        )
    ),
    CONSTRAINT version_formulario_anterior_distinta CHECK (version_anterior_id IS NULL OR version_anterior_id <> id)
);

CREATE UNIQUE INDEX uq_version_formulario_activa ON version_formulario ((TRUE)) WHERE activa;

-- Correspondencia declarada entre versiones de formulario.
CREATE TABLE mapeo_campo_formulario (
    id UUID PRIMARY KEY,
    version_origen_id UUID NOT NULL REFERENCES version_formulario(id),
    version_destino_id UUID NOT NULL REFERENCES version_formulario(id),
    elemento_ref_origen VARCHAR(300),
    elemento_ref_destino VARCHAR(300),
    tipo VARCHAR(20) NOT NULL,
    observacion TEXT,
    creado_por_id UUID NOT NULL REFERENCES usuario(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT mapeo_versiones_distintas CHECK (version_origen_id <> version_destino_id),
    CONSTRAINT mapeo_tipo_valido CHECK (tipo IN ('EQUIVALENTE', 'RENOMBRADO', 'NUEVO', 'ELIMINADO')),
    CONSTRAINT mapeo_referencias_coherentes CHECK (
        (tipo = 'NUEVO' AND elemento_ref_origen IS NULL AND elemento_ref_destino IS NOT NULL)
        OR (tipo = 'ELIMINADO' AND elemento_ref_origen IS NOT NULL AND elemento_ref_destino IS NULL)
        OR (
            tipo IN ('EQUIVALENTE', 'RENOMBRADO')
            AND elemento_ref_origen IS NOT NULL
            AND elemento_ref_destino IS NOT NULL
        )
    )
);

CREATE UNIQUE INDEX uq_mapeo_origen ON mapeo_campo_formulario (version_origen_id, version_destino_id, elemento_ref_origen) WHERE elemento_ref_origen IS NOT NULL;
CREATE UNIQUE INDEX uq_mapeo_destino ON mapeo_campo_formulario (version_origen_id, version_destino_id, elemento_ref_destino) WHERE elemento_ref_destino IS NOT NULL;

-- Pesos por elemento, congelados al publicar el formulario.
CREATE TABLE configuracion_ic_elemento (
    id UUID PRIMARY KEY,
    version_formulario_id UUID NOT NULL REFERENCES version_formulario(id),
    elemento_ref VARCHAR(300) NOT NULL,
    peso NUMERIC(12, 6) NOT NULL,
    descripcion VARCHAR(500) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT configuracion_ic_peso_positivo CHECK (peso > 0),
    CONSTRAINT uq_configuracion_ic_version_elemento UNIQUE (version_formulario_id, elemento_ref)
);
