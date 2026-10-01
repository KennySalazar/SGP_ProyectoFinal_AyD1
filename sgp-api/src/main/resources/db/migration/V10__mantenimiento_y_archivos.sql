-- Planes, órdenes de mantenimiento y metadatos de archivos.

-- Programación periódica de mantenimiento rutinario por puente.
CREATE TABLE plan_mantenimiento_rutinario (
    id UUID PRIMARY KEY,
    puente_id UUID NOT NULL UNIQUE REFERENCES puente(id),
    periodicidad_meses SMALLINT NOT NULL DEFAULT 12
        CHECK (periodicidad_meses BETWEEN 1 AND 12),
    fecha_inicio DATE NOT NULL,
    responsable_usuario_id UUID NOT NULL REFERENCES usuario(id),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    aprobado_por_id UUID NOT NULL REFERENCES usuario(id),
    aprobado_en TIMESTAMPTZ NOT NULL,
    inactivado_en TIMESTAMPTZ,
    motivo_inactivacion TEXT,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT plan_inactivacion_completa CHECK (
        (activo AND inactivado_en IS NULL AND motivo_inactivacion IS NULL)
        OR (
            NOT activo
            AND inactivado_en IS NOT NULL
            AND motivo_inactivacion IS NOT NULL
        )
    ),
    CONSTRAINT uq_plan_id_puente UNIQUE (id, puente_id)
);

-- Reglas versionadas para generar propuestas de mantenimiento.
CREATE TABLE regla_mantenimiento (
    id UUID PRIMARY KEY,
    codigo VARCHAR(80) NOT NULL,
    numero_revision INTEGER NOT NULL CHECK (numero_revision > 0),
    version_formulario_id UUID NOT NULL REFERENCES version_formulario(id),
    elemento_ref VARCHAR(300) NOT NULL,
    condicion JSONB NOT NULL,
    tipo_mantenimiento VARCHAR(20) NOT NULL
        CHECK (tipo_mantenimiento IN ('RUTINARIO', 'PREVENTIVO', 'CORRECTIVO', 'EMERGENCIA')),
    intervencion_sugerida TEXT NOT NULL,
    regla_prioridad JSONB NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT FALSE,
    creado_por_id UUID NOT NULL REFERENCES usuario(id),
    validado_por_id UUID REFERENCES usuario(id),
    validado_en TIMESTAMPTZ,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_regla_mantenimiento_revision UNIQUE (codigo, numero_revision, version_formulario_id)
);
CREATE UNIQUE INDEX uq_regla_mantenimiento_activa ON regla_mantenimiento (codigo, version_formulario_id) WHERE activa;

-- Órdenes propuestas, programadas y ejecutadas.
CREATE TABLE orden_mantenimiento (
    id UUID PRIMARY KEY,
    puente_id UUID NOT NULL REFERENCES puente(id),
    inspeccion_origen_id UUID,
    regla_origen_id UUID REFERENCES regla_mantenimiento(id),
    resultado_ic_origen_id UUID,
    plan_mantenimiento_rutinario_id UUID,
    ciclo_plan INTEGER,
    elemento_ref VARCHAR(300),
    instancia_elemento_id UUID,
    clave_generacion VARCHAR(200),
    tipo VARCHAR(20) NOT NULL
        CHECK (tipo IN ('RUTINARIO', 'PREVENTIVO', 'CORRECTIVO', 'EMERGENCIA')),
    estado VARCHAR(20) NOT NULL DEFAULT 'PROPUESTA'
        CHECK (
            estado IN (
                'PROPUESTA', 'PROGRAMADA', 'EN_EJECUCION',
                'EJECUTADA', 'DESCARTADA', 'CANCELADA'
            )
        ),
    descripcion TEXT NOT NULL,
    prioridad_sugerida VARCHAR(20) NOT NULL
        CHECK (prioridad_sugerida IN ('BAJA', 'MEDIA', 'ALTA', 'CRITICA')),
    prioridad_confirmada VARCHAR(20) NOT NULL
        CHECK (prioridad_confirmada IN ('BAJA', 'MEDIA', 'ALTA', 'CRITICA')),
    justificacion_prioridad TEXT,
    fundamento_generacion JSONB,
    creado_por_id UUID REFERENCES usuario(id),
    aceptado_por_id UUID REFERENCES usuario(id),
    aceptado_en TIMESTAMPTZ,
    fecha_programada DATE,
    motivo_cierre_sin_ejecucion TEXT,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT orden_plan_ciclo_coherente CHECK (
        (plan_mantenimiento_rutinario_id IS NULL AND ciclo_plan IS NULL)
        OR (
            plan_mantenimiento_rutinario_id IS NOT NULL
            AND ciclo_plan > 0
            AND tipo = 'RUTINARIO'
        )
    ),
    CONSTRAINT orden_resultado_origen_requiere_inspeccion CHECK (
        resultado_ic_origen_id IS NULL OR inspeccion_origen_id IS NOT NULL
    ),
    CONSTRAINT orden_programada_completa CHECK (
        estado <> 'PROGRAMADA'
        OR (
            aceptado_por_id IS NOT NULL
            AND aceptado_en IS NOT NULL
            AND fecha_programada IS NOT NULL
        )
    ),
    CONSTRAINT orden_cierre_motivo CHECK (
        estado NOT IN ('DESCARTADA', 'CANCELADA')
        OR motivo_cierre_sin_ejecucion IS NOT NULL
    ),
    CONSTRAINT fk_orden_inspeccion_puente
        FOREIGN KEY (inspeccion_origen_id, puente_id)
        REFERENCES inspeccion(id, puente_id),
    CONSTRAINT fk_orden_resultado_misma_inspeccion
        FOREIGN KEY (resultado_ic_origen_id, inspeccion_origen_id)
        REFERENCES resultado_ic(id, inspeccion_id),
    CONSTRAINT fk_orden_plan_puente
        FOREIGN KEY (plan_mantenimiento_rutinario_id, puente_id)
        REFERENCES plan_mantenimiento_rutinario(id, puente_id)
);
CREATE INDEX idx_orden_puente_estado ON orden_mantenimiento (puente_id, estado);
CREATE INDEX idx_orden_estado_programada ON orden_mantenimiento (estado, fecha_programada);
CREATE UNIQUE INDEX uq_orden_generacion
    ON orden_mantenimiento (inspeccion_origen_id, clave_generacion)
    WHERE clave_generacion IS NOT NULL;
CREATE UNIQUE INDEX uq_orden_plan_ciclo_activo
    ON orden_mantenimiento (plan_mantenimiento_rutinario_id, ciclo_plan)
    WHERE estado IN ('PROPUESTA', 'PROGRAMADA', 'EN_EJECUCION', 'EJECUTADA');

-- Información que existe únicamente cuando una orden entra en ejecución.
-- El servicio valida atómicamente su presencia para los estados EN_EJECUCION y EJECUTADA.
CREATE TABLE orden_mantenimiento_ejecucion (
    orden_mantenimiento_id UUID PRIMARY KEY REFERENCES orden_mantenimiento(id),
    iniciado_en TIMESTAMPTZ NOT NULL,
    fecha_ejecucion DATE,
    responsable_usuario_id UUID REFERENCES usuario(id),
    responsable_nombre VARCHAR(200),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT orden_ejecucion_responsable_requerido CHECK (
        responsable_usuario_id IS NOT NULL OR responsable_nombre IS NOT NULL
    ),
    CONSTRAINT orden_ejecucion_fecha_coherente CHECK (
        fecha_ejecucion IS NULL OR fecha_ejecucion >= iniciado_en::date
    )
);

-- Bitácora inmutable de los cambios de estado de las órdenes.
CREATE TABLE historial_estado_orden (
    id UUID PRIMARY KEY,
    orden_mantenimiento_id UUID NOT NULL REFERENCES orden_mantenimiento(id),
    secuencia INTEGER NOT NULL CHECK (secuencia > 0),
    estado_anterior VARCHAR(20),
    estado_nuevo VARCHAR(20) NOT NULL,
    realizado_por_id UUID REFERENCES usuario(id),
    motivo TEXT,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_historial_orden_secuencia UNIQUE (orden_mantenimiento_id, secuencia)
);
CREATE INDEX idx_historial_orden_fecha ON historial_estado_orden (orden_mantenimiento_id, creado_en);

-- Metadatos físicos de un objeto en MinIO. El tipo discrimina el único vínculo
-- de negocio permitido y evita las tres claves foráneas contextuales opcionales.
CREATE TABLE archivo (
    id UUID PRIMARY KEY,
    tipo_uso VARCHAR(30) NOT NULL CHECK (
        tipo_uso IN (
            'FOTO_INSPECCION', 'DOCUMENTO_INSPECCION',
            'EVIDENCIA_MANTENIMIENTO', 'ACTA_CALIBRACION'
        )
    ),
    categoria_foto VARCHAR(30),
    nombre_original VARCHAR(255),
    nombre_almacenado VARCHAR(255) NOT NULL,
    clave_objeto_original VARCHAR(500) NOT NULL UNIQUE,
    clave_objeto_miniatura VARCHAR(500),
    mime_real VARCHAR(100) NOT NULL,
    tamano_bytes BIGINT NOT NULL CHECK (tamano_bytes > 0),
    checksum_sha256 VARCHAR(64) NOT NULL,
    ancho_px INTEGER,
    alto_px INTEGER,
    ubicacion_captura GEOGRAPHY(POINT, 4326),
    capturado_en TIMESTAMPTZ,
    subido_por_id UUID NOT NULL REFERENCES usuario(id),
    confirmado_en TIMESTAMPTZ NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE'
        CHECK (estado IN ('DISPONIBLE', 'RETIRADO', 'PURGADO')),
    retirado_en TIMESTAMPTZ,
    purgado_en TIMESTAMPTZ,
    motivo_retiro TEXT,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_archivo_id_tipo_uso UNIQUE (id, tipo_uso),
    CONSTRAINT archivo_categoria_foto_valida CHECK (
        categoria_foto IS NULL
        OR categoria_foto IN ('ACCESO', 'SUPERESTRUCTURA', 'SUBESTRUCTURA', 'CAUCE', 'OTRA')
    ),
    CONSTRAINT archivo_categoria_solo_foto CHECK (
        tipo_uso = 'FOTO_INSPECCION' OR categoria_foto IS NULL
    ),
    CONSTRAINT archivo_foto_inspeccion_categoria_obligatoria CHECK (
        tipo_uso <> 'FOTO_INSPECCION' OR categoria_foto IS NOT NULL
    ),
    CONSTRAINT archivo_foto_disponible_metadatos_completos CHECK (
        tipo_uso <> 'FOTO_INSPECCION'
        OR estado <> 'DISPONIBLE'
        OR (
            ubicacion_captura IS NOT NULL
            AND capturado_en IS NOT NULL
            AND clave_objeto_miniatura IS NOT NULL
            AND ancho_px IS NOT NULL
            AND ancho_px > 0
            AND alto_px IS NOT NULL
            AND alto_px > 0
            AND mime_real IN ('image/jpeg', 'image/webp')
        )
    ),
    CONSTRAINT archivo_documento_inspeccion_formato_valido CHECK (
        tipo_uso <> 'DOCUMENTO_INSPECCION'
        OR (mime_real = 'application/pdf' AND tamano_bytes <= 20971520)
    ),
    CONSTRAINT archivo_evidencia_mantenimiento_imagen_valida CHECK (
        tipo_uso <> 'EVIDENCIA_MANTENIMIENTO'
        OR (
            mime_real IN ('image/jpeg', 'image/webp')
            AND ancho_px IS NOT NULL
            AND ancho_px > 0
            AND alto_px IS NOT NULL
            AND alto_px > 0
        )
    )
);
CREATE INDEX idx_archivo_tipo_estado ON archivo (tipo_uso, estado);
CREATE INDEX idx_archivo_checksum_tamano ON archivo (checksum_sha256, tamano_bytes);

-- Vínculo para fotos y documentos pertenecientes a una inspección.
CREATE TABLE archivo_inspeccion (
    archivo_id UUID PRIMARY KEY,
    tipo_uso VARCHAR(30) NOT NULL,
    inspeccion_id UUID NOT NULL REFERENCES inspeccion(id),
    elemento_ref VARCHAR(300),
    instancia_elemento_id UUID,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT archivo_inspeccion_tipo_valido CHECK (
        tipo_uso IN ('FOTO_INSPECCION', 'DOCUMENTO_INSPECCION')
    ),
    CONSTRAINT archivo_inspeccion_instancia_requiere_ref CHECK (
        instancia_elemento_id IS NULL OR elemento_ref IS NOT NULL
    ),
    CONSTRAINT fk_archivo_inspeccion_tipo
        FOREIGN KEY (archivo_id, tipo_uso)
        REFERENCES archivo(id, tipo_uso)
);
CREATE INDEX idx_archivo_inspeccion_inspeccion_tipo
    ON archivo_inspeccion (inspeccion_id, tipo_uso);

-- Vínculo de evidencia fotográfica o documental con una orden de mantenimiento.
CREATE TABLE archivo_evidencia_mantenimiento (
    archivo_id UUID PRIMARY KEY,
    tipo_uso VARCHAR(30) NOT NULL DEFAULT 'EVIDENCIA_MANTENIMIENTO',
    orden_mantenimiento_id UUID NOT NULL REFERENCES orden_mantenimiento(id),
    elemento_ref VARCHAR(300),
    instancia_elemento_id UUID,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT archivo_evidencia_tipo_valido CHECK (
        tipo_uso = 'EVIDENCIA_MANTENIMIENTO'
    ),
    CONSTRAINT archivo_evidencia_instancia_requiere_ref CHECK (
        instancia_elemento_id IS NULL OR elemento_ref IS NOT NULL
    ),
    CONSTRAINT fk_archivo_evidencia_tipo
        FOREIGN KEY (archivo_id, tipo_uso)
        REFERENCES archivo(id, tipo_uso)
);
CREATE INDEX idx_archivo_evidencia_orden
    ON archivo_evidencia_mantenimiento (orden_mantenimiento_id);

-- Acta de calibración que respalda una versión de formulario publicada.
CREATE TABLE archivo_acta_calibracion (
    archivo_id UUID PRIMARY KEY,
    tipo_uso VARCHAR(30) NOT NULL DEFAULT 'ACTA_CALIBRACION',
    version_formulario_id UUID NOT NULL REFERENCES version_formulario(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT archivo_acta_tipo_valido CHECK (tipo_uso = 'ACTA_CALIBRACION'),
    CONSTRAINT fk_archivo_acta_tipo
        FOREIGN KEY (archivo_id, tipo_uso)
        REFERENCES archivo(id, tipo_uso)
);
CREATE INDEX idx_archivo_acta_version
    ON archivo_acta_calibracion (version_formulario_id);
