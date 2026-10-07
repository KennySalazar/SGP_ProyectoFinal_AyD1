
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE rol (
    id UUID PRIMARY KEY,
    nombre VARCHAR(40) NOT NULL UNIQUE,
    descripcion VARCHAR(255) NOT NULL
);

CREATE TABLE usuario (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    verificado BOOLEAN NOT NULL DEFAULT FALSE,
    activado BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    two_factor_habilitado BOOLEAN NOT NULL DEFAULT FALSE,
    token_version INTEGER NOT NULL DEFAULT 0,
    intentos_login_fallidos INTEGER NOT NULL DEFAULT 0,
    ventana_intentos_iniciada_en TIMESTAMPTZ,
    bloqueado_hasta TIMESTAMPTZ,
    rol_id UUID NOT NULL REFERENCES rol(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT usuario_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT usuario_token_version_nonnegative CHECK (token_version >= 0),
    CONSTRAINT usuario_intentos_nonnegative CHECK (intentos_login_fallidos >= 0)
);

CREATE TABLE desafio_otp (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    proposito VARCHAR(40) NOT NULL,
    codigo_hash VARCHAR(100) NOT NULL,
    expira_en TIMESTAMPTZ NOT NULL,
    intentos INTEGER NOT NULL DEFAULT 0,
    max_intentos INTEGER NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consumido_en TIMESTAMPTZ,
    CONSTRAINT desafio_otp_intentos_nonnegative CHECK (intentos >= 0),
    CONSTRAINT desafio_otp_max_intentos_positive CHECK (max_intentos > 0),
    CONSTRAINT desafio_otp_proposito_valido CHECK (
        proposito IN ('REGISTRO', 'LOGIN_2FA', 'RECUPERACION_PASSWORD', 'ACTIVAR_2FA', 'DESACTIVAR_2FA')
    )
);

CREATE TABLE token_refresco (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expira_en TIMESTAMPTZ NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revocado_en TIMESTAMPTZ
);

CREATE TABLE auditoria (
    id UUID PRIMARY KEY,
    usuario_id UUID REFERENCES usuario(id),
    accion VARCHAR(100) NOT NULL,
    entidad VARCHAR(100) NOT NULL,
    entidad_id UUID,
    valores_anteriores JSONB,
    valores_posteriores JSONB,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

CREATE INDEX idx_usuario_email_trgm ON usuario USING GIN (email gin_trgm_ops);
CREATE INDEX idx_desafio_otp_usuario_proposito_creado ON desafio_otp (usuario_id, proposito, creado_en DESC);
CREATE INDEX idx_desafio_otp_expira ON desafio_otp (expira_en);
CREATE INDEX idx_token_refresco_usuario ON token_refresco (usuario_id, expira_en);
CREATE INDEX idx_auditoria_usuario_creado ON auditoria (usuario_id, creado_en DESC);
CREATE INDEX idx_auditoria_posteriores_gin ON auditoria USING GIN (valores_posteriores);
