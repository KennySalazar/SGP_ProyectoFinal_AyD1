-- Inventario oficial de puentes y asignaciones académicas.

-- Activo físico de infraestructura.
CREATE TABLE puente (
    id UUID PRIMARY KEY,
    codigo VARCHAR(40) NOT NULL UNIQUE,
    correlativo_municipal INTEGER NOT NULL,
    municipio_id UUID NOT NULL REFERENCES municipio(id),
    nombre VARCHAR(200) NOT NULL,
    ruta VARCHAR(100) NOT NULL,
    kilometraje NUMERIC(10, 3),
    ubicacion GEOGRAPHY(POINT, 4326) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    inactivado_en TIMESTAMPTZ,
    inactivado_por_id UUID REFERENCES usuario(id),
    motivo_inactivacion VARCHAR(1000),
    creado_por_id UUID NOT NULL REFERENCES usuario(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT puente_correlativo_rango CHECK (correlativo_municipal BETWEEN 1 AND 9999),
    CONSTRAINT puente_kilometraje_no_negativo CHECK (kilometraje IS NULL OR kilometraje >= 0),
    CONSTRAINT puente_inactivacion_completa CHECK (
        (activo AND inactivado_en IS NULL AND inactivado_por_id IS NULL AND motivo_inactivacion IS NULL)
        OR (
            NOT activo
            AND inactivado_en IS NOT NULL
            AND inactivado_por_id IS NOT NULL
            AND motivo_inactivacion IS NOT NULL
        )
    ),
    CONSTRAINT uq_puente_municipio_correlativo UNIQUE (municipio_id, correlativo_municipal),
    CONSTRAINT uq_puente_id_municipio UNIQUE (id, municipio_id)
);

CREATE INDEX idx_puente_ubicacion_gist ON puente USING GIST (ubicacion);
CREATE INDEX idx_puente_nombre_trgm ON puente USING GIN (nombre gin_trgm_ops);
CREATE INDEX idx_puente_municipio_activo ON puente (municipio_id, activo);
CREATE INDEX idx_puente_ruta ON puente (ruta);

-- Solicitudes que preceden al alta del inventario.
CREATE TABLE solicitud_alta_puente (
    id UUID PRIMARY KEY,
    solicitado_por_id UUID NOT NULL REFERENCES usuario(id),
    nombre_propuesto VARCHAR(200) NOT NULL,
    municipio_id UUID NOT NULL REFERENCES municipio(id),
    ruta VARCHAR(100) NOT NULL,
    kilometraje NUMERIC(10, 3),
    ubicacion GEOGRAPHY(POINT, 4326) NOT NULL,
    justificacion TEXT,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    revisado_por_id UUID REFERENCES usuario(id),
    revisado_en TIMESTAMPTZ,
    motivo_decision TEXT,
    puente_creado_id UUID REFERENCES puente(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT solicitud_kilometraje_no_negativo CHECK (kilometraje IS NULL OR kilometraje >= 0),
    CONSTRAINT solicitud_estado_valido CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA', 'CANCELADA')),
    CONSTRAINT solicitud_aprobada_completa CHECK (
        estado <> 'APROBADA' OR (revisado_por_id IS NOT NULL AND revisado_en IS NOT NULL AND puente_creado_id IS NOT NULL)
    ),
    CONSTRAINT solicitud_rechazada_completa CHECK (
        estado <> 'RECHAZADA' OR (revisado_por_id IS NOT NULL AND revisado_en IS NOT NULL AND motivo_decision IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_solicitud_puente_creado ON solicitud_alta_puente (puente_creado_id) WHERE puente_creado_id IS NOT NULL;
CREATE INDEX idx_solicitud_estado_creado ON solicitud_alta_puente (estado, creado_en);
CREATE INDEX idx_solicitud_solicitante ON solicitud_alta_puente (solicitado_por_id);
CREATE INDEX idx_solicitud_ubicacion_gist ON solicitud_alta_puente USING GIST (ubicacion);

-- Asignaciones históricas de puentes a estudiantes inscritos.
CREATE TABLE asignacion_puente (
    id UUID PRIMARY KEY,
    curso_estudiante_id UUID NOT NULL REFERENCES curso_estudiante(id),
    puente_id UUID NOT NULL REFERENCES puente(id),
    asignado_por_id UUID NOT NULL REFERENCES usuario(id),
    asignado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revocado_en TIMESTAMPTZ,
    motivo_revocacion VARCHAR(1000),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT asignacion_revocacion_completa CHECK (
        (revocado_en IS NULL AND motivo_revocacion IS NULL) OR (revocado_en IS NOT NULL AND motivo_revocacion IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_asignacion_puente_activa ON asignacion_puente (curso_estudiante_id, puente_id) WHERE revocado_en IS NULL;
CREATE INDEX idx_asignacion_puente_puente ON asignacion_puente (puente_id);
