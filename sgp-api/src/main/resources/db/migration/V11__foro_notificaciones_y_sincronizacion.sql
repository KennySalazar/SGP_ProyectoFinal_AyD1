-- Foro, notificaciones e infraestructura de sincronización offline.

-- Hilos asociados a un puente y, opcionalmente, a una inspección publicada.
CREATE TABLE foro_hilo (
    id UUID PRIMARY KEY,
    puente_id UUID NOT NULL REFERENCES puente(id),
    inspeccion_id UUID,
    autor_id UUID NOT NULL REFERENCES usuario(id),
    categoria VARCHAR(30) NOT NULL CHECK (
        categoria IN (
            'DISCUSION_TECNICA', 'PROPUESTA_INTERVENCION', 'CONSULTA',
            'REFERENCIA_DOCUMENTAL', 'ALERTA'
        )
    ),
    titulo VARCHAR(250) NOT NULL,
    cerrado_en TIMESTAMPTZ,
    cerrado_por_id UUID REFERENCES usuario(id),
    motivo_cierre TEXT,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT foro_hilo_cierre_completo CHECK (
        (cerrado_en IS NULL AND cerrado_por_id IS NULL AND motivo_cierre IS NULL)
        OR (
            cerrado_en IS NOT NULL
            AND cerrado_por_id IS NOT NULL
            AND motivo_cierre IS NOT NULL
        )
    ),
    CONSTRAINT fk_foro_hilo_inspeccion_puente
        FOREIGN KEY (inspeccion_id, puente_id)
        REFERENCES inspeccion(id, puente_id),
    CONSTRAINT uq_foro_hilo_id UNIQUE (id)
);

CREATE INDEX idx_foro_hilo_puente_fecha ON foro_hilo (puente_id, creado_en DESC);
CREATE INDEX idx_foro_hilo_inspeccion ON foro_hilo (inspeccion_id);

-- Comentarios y respuestas de un único nivel dentro de cada hilo.
CREATE TABLE foro_comentario (
    id UUID PRIMARY KEY,
    hilo_id UUID NOT NULL REFERENCES foro_hilo(id),
    autor_id UUID NOT NULL REFERENCES usuario(id),
    padre_id UUID,
    elemento_ref VARCHAR(300),
    instancia_elemento_id UUID,
    contenido TEXT NOT NULL,
    editado_en TIMESTAMPTZ,
    eliminado_en TIMESTAMPTZ,
    ocultado_en TIMESTAMPTZ,
    ocultado_por_id UUID REFERENCES usuario(id),
    motivo_ocultamiento TEXT,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT foro_comentario_no_autopadre CHECK (
        padre_id IS NULL OR padre_id <> id
    ),
    CONSTRAINT foro_comentario_instancia_ref CHECK (
        instancia_elemento_id IS NULL OR elemento_ref IS NOT NULL
    ),
    CONSTRAINT foro_comentario_ocultamiento_completo CHECK (
        (ocultado_en IS NULL AND ocultado_por_id IS NULL AND motivo_ocultamiento IS NULL)
        OR (
            ocultado_en IS NOT NULL
            AND ocultado_por_id IS NOT NULL
            AND motivo_ocultamiento IS NOT NULL
        )
    ),
    CONSTRAINT uq_foro_comentario_id_hilo UNIQUE (id, hilo_id),
    CONSTRAINT fk_foro_comentario_padre_mismo_hilo
        FOREIGN KEY (padre_id, hilo_id)
        REFERENCES foro_comentario(id, hilo_id)
);

CREATE INDEX idx_foro_comentario_hilo_fecha ON foro_comentario (hilo_id, creado_en);
CREATE INDEX idx_foro_comentario_padre ON foro_comentario (padre_id);

CREATE TABLE comentario_mencion (
    id UUID PRIMARY KEY,
    comentario_id UUID NOT NULL REFERENCES foro_comentario(id),
    usuario_mencionado_id UUID NOT NULL REFERENCES usuario(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_comentario_mencion UNIQUE (comentario_id, usuario_mencionado_id)
);

CREATE INDEX idx_comentario_mencion_usuario_fecha
    ON comentario_mencion (usuario_mencionado_id, creado_en DESC);

-- Eventos mostrados una sola vez por usuario y clave de negocio.
CREATE TABLE notificacion (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    tipo VARCHAR(50) NOT NULL,
    titulo VARCHAR(200) NOT NULL,
    mensaje TEXT NOT NULL,
    entidad VARCHAR(100),
    entidad_id UUID,
    clave_evento VARCHAR(250) NOT NULL,
    leido_en TIMESTAMPTZ,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT notificacion_entidad_coherente CHECK (
        (entidad IS NULL AND entidad_id IS NULL)
        OR (entidad IS NOT NULL AND entidad_id IS NOT NULL)
    ),
    CONSTRAINT uq_notificacion_usuario_evento UNIQUE (usuario_id, clave_evento)
);

CREATE INDEX idx_notificacion_usuario_fecha ON notificacion (usuario_id, creado_en DESC);
CREATE INDEX idx_notificacion_no_leida
    ON notificacion (usuario_id, creado_en DESC)
    WHERE leido_en IS NULL;

-- Reserva y resultado de operaciones HTTP idempotentes.
CREATE TABLE operacion_idempotente (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    clave VARCHAR(100) NOT NULL,
    metodo VARCHAR(10) NOT NULL,
    ruta VARCHAR(300) NOT NULL,
    hash_solicitud VARCHAR(64) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'EN_PROCESO'
        CHECK (estado IN ('EN_PROCESO', 'COMPLETADA')),
    codigo_respuesta INTEGER,
    respuesta JSONB,
    entidad VARCHAR(100),
    entidad_id UUID,
    completado_en TIMESTAMPTZ,
    expira_en TIMESTAMPTZ,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT operacion_completada_coherente CHECK (
        (estado = 'EN_PROCESO' AND codigo_respuesta IS NULL AND completado_en IS NULL)
        OR (
            estado = 'COMPLETADA'
            AND codigo_respuesta IS NOT NULL
            AND completado_en IS NOT NULL
        )
    ),
    CONSTRAINT uq_operacion_usuario_clave UNIQUE (usuario_id, clave)
);

CREATE INDEX idx_operacion_expira
    ON operacion_idempotente (expira_en)
    WHERE expira_en IS NOT NULL;

-- Registro inmutable de conflictos detectados durante una sincronización.
CREATE TABLE conflicto_sincronizacion (
    id UUID PRIMARY KEY,
    inspeccion_id UUID NOT NULL REFERENCES inspeccion(id),
    operacion_idempotente_id UUID NOT NULL REFERENCES operacion_idempotente(id),
    version_servidor BIGINT NOT NULL,
    version_cliente BIGINT,
    copia_servidor JSONB NOT NULL,
    hash_copia_cliente VARCHAR(64) NOT NULL,
    resolucion VARCHAR(30) NOT NULL DEFAULT 'CLIENTE_PREVALECE'
        CHECK (resolucion = 'CLIENTE_PREVALECE'),
    detectado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resuelto_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resuelto_por_id UUID REFERENCES usuario(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_conflicto_operacion_inspeccion
        UNIQUE (operacion_idempotente_id, inspeccion_id)
);

CREATE INDEX idx_conflicto_inspeccion_fecha
    ON conflicto_sincronizacion (inspeccion_id, detectado_en DESC);
