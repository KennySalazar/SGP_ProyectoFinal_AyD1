-- Preparación offline, inspecciones y sus componentes repetibles.

-- Contexto verificable descargado para captura offline.
CREATE TABLE preparacion_salida (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    dispositivo_id VARCHAR(100) NOT NULL,
    version_formulario_id UUID NOT NULL REFERENCES version_formulario(id),
    contexto_autorizado JSONB NOT NULL,
    preparado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_preparacion_usuario_fecha ON preparacion_salida (usuario_id, preparado_en DESC);

-- Agregado principal de una inspección de puente.
CREATE TABLE inspeccion (
    id UUID PRIMARY KEY,
    puente_id UUID NOT NULL REFERENCES puente(id),
    version_formulario_id UUID NOT NULL REFERENCES version_formulario(id),
    autor_id UUID NOT NULL REFERENCES usuario(id),
    rol_autor_creacion VARCHAR(40) NOT NULL,
    nombre_autor_capturado VARCHAR(200) NOT NULL,
    asignacion_puente_id UUID REFERENCES asignacion_puente(id),
    preparacion_salida_id UUID REFERENCES preparacion_salida(id),
    inspeccion_base_id UUID,
    tipo VARCHAR(20) NOT NULL DEFAULT 'ORIGINAL',
    supersede_a_id UUID,
    motivo_correccion TEXT,
    estado VARCHAR(30) NOT NULL DEFAULT 'BORRADOR',
    fecha_inspeccion DATE NOT NULL,
    dispositivo_id VARCHAR(100) NOT NULL,
    ubicacion_inicio GEOGRAPHY(POINT, 4326),
    precision_gps_m NUMERIC(10,2),
    distancia_al_puente_m NUMERIC(12,2),
    datos_permanentes_confirmados_en TIMESTAMPTZ,
    identificacion_capturada JSONB,
    datos JSONB NOT NULL DEFAULT '{}'::jsonb,
    creado_dispositivo_en TIMESTAMPTZ,
    enviado_dispositivo_en TIMESTAMPTZ,
    sincronizado_en TIMESTAMPTZ,
    publicado_en TIMESTAMPTZ,
    ultima_actividad_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    eliminado_en TIMESTAMPTZ, eliminado_por_id UUID REFERENCES usuario(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT inspeccion_tipo_valido CHECK (tipo IN ('ORIGINAL', 'CORRECCION')),
    CONSTRAINT inspeccion_estado_valido CHECK (
        estado IN (
            'BORRADOR', 'ENVIADA', 'EN_REVISION', 'CAMBIOS_SOLICITADOS',
            'PUBLICADA', 'RECHAZADA'
        )
    ),
    CONSTRAINT inspeccion_correccion_coherente CHECK (
        (tipo = 'ORIGINAL' AND supersede_a_id IS NULL AND motivo_correccion IS NULL)
        OR (
            tipo = 'CORRECCION'
            AND supersede_a_id IS NOT NULL
            AND motivo_correccion IS NOT NULL
        )
    ),
    CONSTRAINT inspeccion_publicacion_coherente CHECK (estado <> 'PUBLICADA' OR publicado_en IS NOT NULL),
    CONSTRAINT inspeccion_publicada_no_eliminada CHECK (estado <> 'PUBLICADA' OR eliminado_en IS NULL),
    CONSTRAINT inspeccion_base_distinta CHECK (inspeccion_base_id IS NULL OR inspeccion_base_id <> id),
    CONSTRAINT uq_inspeccion_id_puente UNIQUE (id, puente_id),
    CONSTRAINT fk_inspeccion_base_mismo_puente
        FOREIGN KEY (inspeccion_base_id, puente_id)
        REFERENCES inspeccion(id, puente_id),
    CONSTRAINT fk_inspeccion_correccion_mismo_puente
        FOREIGN KEY (supersede_a_id, puente_id)
        REFERENCES inspeccion(id, puente_id)
);
CREATE INDEX idx_inspeccion_puente_fecha ON inspeccion (puente_id, fecha_inspeccion DESC);
CREATE INDEX idx_inspeccion_autor_estado ON inspeccion (autor_id, estado);
CREATE INDEX idx_inspeccion_asignacion_estado ON inspeccion (asignacion_puente_id, estado);
CREATE INDEX idx_inspeccion_version_formulario ON inspeccion (version_formulario_id);
CREATE INDEX idx_inspeccion_datos_gin ON inspeccion USING GIN (datos jsonb_path_ops);
CREATE INDEX idx_inspeccion_ubicacion_gist ON inspeccion USING GIST (ubicacion_inicio);
CREATE UNIQUE INDEX uq_inspeccion_sucesora_publicada ON inspeccion (supersede_a_id) WHERE estado = 'PUBLICADA';

-- Datos técnicos consultables del formulario. Es una extensión opcional 1:1:
-- los borradores pueden no haber completado aún esta parte de la visita.
CREATE TABLE inspeccion_dato_tecnico (
    inspeccion_id UUID PRIMARY KEY REFERENCES inspeccion(id),
    longitud_m NUMERIC(10, 2),
    numero_tramos INTEGER,
    ancho_rodadura_m NUMERIC(10, 2),
    ancho_acera_derecha_m NUMERIC(10, 2),
    ancho_acera_izquierda_m NUMERIC(10, 2),
    altura_libre_superior_m NUMERIC(10, 2),
    altura_libre_sobre_cauce_m NUMERIC(10, 2),
    tipologia_puente VARCHAR(100),
    tipo_cruce VARCHAR(100),
    numero_vias_por_sentido INTEGER,
    material_superestructura VARCHAR(100),
    material_subestructura VARCHAR(100),
    carga_diseno VARCHAR(100),
    anio_construccion SMALLINT,
    trafico_vehiculos_dia INTEGER,
    porcentaje_camiones_buses NUMERIC(5, 2),
    ruta_pavimentada VARCHAR(10),
    alineamiento_horizontal VARCHAR(15),
    esviaje BOOLEAN,
    poblacion_antes VARCHAR(200),
    poblacion_despues VARCHAR(200),
    tipo_cuerpo_agua VARCHAR(50),
    nombre_rio VARCHAR(200),
    encauzamiento VARCHAR(20),
    antecedente_desbordamiento BOOLEAN,
    frecuencia_desbordamiento_anios NUMERIC(8, 2),
    fecha_ultimo_desbordamiento DATE,
    alumbrado_existe BOOLEAN,
    drenajes_aledanos_existen BOOLEAN,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT inspeccion_dato_tecnico_medidas_validas CHECK (
        (longitud_m IS NULL OR longitud_m >= 0)
        AND (ancho_rodadura_m IS NULL OR ancho_rodadura_m >= 0)
        AND (ancho_acera_derecha_m IS NULL OR ancho_acera_derecha_m >= 0)
        AND (ancho_acera_izquierda_m IS NULL OR ancho_acera_izquierda_m >= 0)
        AND (altura_libre_superior_m IS NULL OR altura_libre_superior_m >= 0)
        AND (altura_libre_sobre_cauce_m IS NULL OR altura_libre_sobre_cauce_m >= 0)
    ),
    CONSTRAINT inspeccion_dato_tecnico_porcentaje_valido CHECK (
        porcentaje_camiones_buses IS NULL OR porcentaje_camiones_buses BETWEEN 0 AND 100
    ),
    CONSTRAINT inspeccion_dato_tecnico_tramos_validos CHECK (
        numero_tramos IS NULL OR numero_tramos >= 0
    ),
    CONSTRAINT inspeccion_dato_tecnico_anio_valido CHECK (
        anio_construccion IS NULL OR anio_construccion BETWEEN 1 AND 9999
    ),
    CONSTRAINT inspeccion_dato_tecnico_ruta_pavimentada_valida CHECK (
        ruta_pavimentada IS NULL OR ruta_pavimentada IN ('SI', 'NO', 'PARCIAL')
    ),
    CONSTRAINT inspeccion_dato_tecnico_alineamiento_valido CHECK (
        alineamiento_horizontal IS NULL OR alineamiento_horizontal IN ('TANGENTE', 'CURVA')
    ),
    CONSTRAINT inspeccion_dato_tecnico_encauzamiento_valido CHECK (
        encauzamiento IS NULL OR encauzamiento IN ('RECTO', 'CURVA', 'INDEFINIDO')
    )
);

-- Tramos repetibles consultables de una inspección.
CREATE TABLE inspeccion_tramo (
    id UUID PRIMARY KEY,
    inspeccion_id UUID NOT NULL REFERENCES inspeccion(id),
    instancia_elemento_id UUID NOT NULL,
    orden INTEGER NOT NULL,
    longitud_m NUMERIC(10, 2),
    tipo_seccion VARCHAR(100),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inspeccion_tramo_orden_positivo CHECK (orden > 0),
    CONSTRAINT inspeccion_tramo_longitud_positiva CHECK (longitud_m IS NULL OR longitud_m > 0),
    CONSTRAINT uq_inspeccion_tramo_instancia UNIQUE (inspeccion_id, instancia_elemento_id),
    CONSTRAINT uq_inspeccion_tramo_orden UNIQUE (inspeccion_id, orden)
);

-- Acompañantes registrados en la visita de inspección.
CREATE TABLE inspeccion_acompanante (
    id UUID PRIMARY KEY,
    inspeccion_id UUID NOT NULL REFERENCES inspeccion(id),
    usuario_id UUID REFERENCES usuario(id),
    nombre VARCHAR(200) NOT NULL,
    numero_colegiado VARCHAR(50),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_inspeccion_acompanante_usuario ON inspeccion_acompanante (inspeccion_id, usuario_id) WHERE usuario_id IS NOT NULL;
