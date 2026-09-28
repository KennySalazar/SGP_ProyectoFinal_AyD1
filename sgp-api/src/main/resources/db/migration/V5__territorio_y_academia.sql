CREATE TABLE departamento (
    id UUID PRIMARY KEY,
    codigo_ine VARCHAR(10) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_departamento_nombre_lower ON departamento (lower(nombre));

CREATE TABLE municipio (
    id UUID PRIMARY KEY,
    departamento_id UUID NOT NULL REFERENCES departamento(id),
    codigo_ine VARCHAR(10) NOT NULL UNIQUE,
    nombre VARCHAR(120) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    ultimo_correlativo_puente INTEGER NOT NULL DEFAULT 0,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT municipio_correlativo_rango CHECK (ultimo_correlativo_puente BETWEEN 0 AND 9999)
);

CREATE UNIQUE INDEX uq_municipio_departamento_nombre_lower
    ON municipio (departamento_id, lower(nombre));
CREATE INDEX idx_municipio_departamento ON municipio (departamento_id);

CREATE TABLE curso (
    id UUID PRIMARY KEY,
    codigo VARCHAR(50) NOT NULL,
    nombre VARCHAR(150) NOT NULL,
    periodo VARCHAR(50) NOT NULL,
    seccion VARCHAR(30) NOT NULL,
    catedratico_id UUID NOT NULL REFERENCES usuario(id),
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT curso_fechas_validas CHECK (fecha_fin >= fecha_inicio),
    CONSTRAINT uq_curso_oferta UNIQUE (codigo, periodo, seccion)
);

CREATE INDEX idx_curso_catedratico_activo ON curso (catedratico_id, activo);

CREATE TABLE curso_estudiante (
    id UUID PRIMARY KEY,
    curso_id UUID NOT NULL REFERENCES curso(id),
    estudiante_id UUID NOT NULL REFERENCES usuario(id),
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    inscrito_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    inscrito_por_id UUID NOT NULL REFERENCES usuario(id),
    retirado_en TIMESTAMPTZ,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT curso_estudiante_estado_valido CHECK (estado IN ('ACTIVO', 'RETIRADO')),
    CONSTRAINT curso_estudiante_retiro_coherente CHECK (
        (estado = 'ACTIVO' AND retirado_en IS NULL) OR (estado = 'RETIRADO' AND retirado_en IS NOT NULL)
    ),
    CONSTRAINT uq_curso_estudiante UNIQUE (curso_id, estudiante_id)
);

CREATE INDEX idx_curso_estudiante_estudiante_estado ON curso_estudiante (estudiante_id, estado);
