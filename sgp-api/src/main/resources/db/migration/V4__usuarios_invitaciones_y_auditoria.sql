-- Perfiles de usuario, invitaciones y consultas de auditoría.

-- Datos de perfil agregados sin alterar las columnas de seguridad existentes.
ALTER TABLE usuario
    ADD COLUMN nombre_completo VARCHAR(200),
    ADD COLUMN nombre_usuario VARCHAR(60),
    ADD COLUMN desactivado_en TIMESTAMPTZ,
    ADD COLUMN desactivado_por_id UUID REFERENCES usuario(id),
    ADD COLUMN motivo_desactivacion VARCHAR(1000);

-- Índices de administración y unicidad de perfiles.
CREATE UNIQUE INDEX uq_usuario_nombre_usuario_lower
    ON usuario (lower(nombre_usuario))
    WHERE nombre_usuario IS NOT NULL;
CREATE INDEX idx_usuario_rol_activo ON usuario (rol_id, activo);
CREATE INDEX idx_auditoria_entidad_id_creado ON auditoria (entidad, entidad_id, creado_en DESC);

-- Subtipo exclusivo para datos que solo aplican a profesionales externos.
-- Evita columnas de colegiación nulas para estudiantes, catedráticos y administradores.
CREATE TABLE usuario_profesional (
    usuario_id UUID PRIMARY KEY REFERENCES usuario(id),
    numero_colegiado VARCHAR(50) NOT NULL UNIQUE,
    colegiado_verificado_en TIMESTAMPTZ,
    colegiado_verificado_por_id UUID REFERENCES usuario(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT usuario_profesional_verificacion_completa CHECK (
        (colegiado_verificado_en IS NULL AND colegiado_verificado_por_id IS NULL)
        OR (colegiado_verificado_en IS NOT NULL AND colegiado_verificado_por_id IS NOT NULL)
    )
);

-- Invitaciones pendientes de aceptación para la creación controlada de cuentas.
CREATE TABLE invitacion_usuario (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    rol_id UUID NOT NULL REFERENCES rol(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    invitado_por_id UUID NOT NULL REFERENCES usuario(id),
    expira_en TIMESTAMPTZ NOT NULL,
    aceptado_en TIMESTAMPTZ,
    cancelado_en TIMESTAMPTZ,
    usuario_creado_id UUID REFERENCES usuario(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT invitacion_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT invitacion_expira_despues_creacion CHECK (expira_en > creado_en),
    CONSTRAINT invitacion_estado_excluyente CHECK (NOT (aceptado_en IS NOT NULL AND cancelado_en IS NOT NULL))
);

-- Una invitación pendiente por correo y rol.
CREATE UNIQUE INDEX uq_invitacion_pendiente_email_rol
    ON invitacion_usuario (email, rol_id)
    WHERE aceptado_en IS NULL AND cancelado_en IS NULL;
CREATE INDEX idx_invitacion_expira ON invitacion_usuario (expira_en);
