ALTER TABLE usuario
    ADD COLUMN nombre_completo VARCHAR(200),
    ADD COLUMN nombre_usuario VARCHAR(60),
    ADD COLUMN numero_colegiado VARCHAR(50),
    ADD COLUMN colegiado_verificado_en TIMESTAMPTZ,
    ADD COLUMN colegiado_verificado_por_id UUID REFERENCES usuario(id),
    ADD COLUMN desactivado_en TIMESTAMPTZ,
    ADD COLUMN desactivado_por_id UUID REFERENCES usuario(id),
    ADD COLUMN motivo_desactivacion VARCHAR(1000);

ALTER TABLE usuario
    ADD CONSTRAINT usuario_colegiado_verificacion_completa CHECK (
        (colegiado_verificado_en IS NULL AND colegiado_verificado_por_id IS NULL)
        OR (colegiado_verificado_en IS NOT NULL AND colegiado_verificado_por_id IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_usuario_nombre_usuario_lower
    ON usuario (lower(nombre_usuario))
    WHERE nombre_usuario IS NOT NULL;
CREATE UNIQUE INDEX uq_usuario_numero_colegiado
    ON usuario (numero_colegiado)
    WHERE numero_colegiado IS NOT NULL;
CREATE INDEX idx_usuario_rol_activo ON usuario (rol_id, activo);
CREATE INDEX idx_auditoria_entidad_id_creado ON auditoria (entidad, entidad_id, creado_en DESC);

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

CREATE UNIQUE INDEX uq_invitacion_pendiente_email_rol
    ON invitacion_usuario (email, rol_id)
    WHERE aceptado_en IS NULL AND cancelado_en IS NULL;
CREATE INDEX idx_invitacion_expira ON invitacion_usuario (expira_en);
