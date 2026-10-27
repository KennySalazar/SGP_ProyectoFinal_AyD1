CREATE TABLE aprobacion_calibracion_ic (
                                           id UUID PRIMARY KEY,
                                           version_formulario_id UUID NOT NULL REFERENCES version_formulario(id),
                                           aprobado_por_id UUID NOT NULL REFERENCES usuario(id),
                                           aprobado_por_nombre VARCHAR(320) NOT NULL,
                                           aprobado_en TIMESTAMPTZ NOT NULL,
                                           observaciones VARCHAR(2000),
                                           CONSTRAINT uq_aprobacion_calibracion_version UNIQUE (version_formulario_id),
                                           CONSTRAINT aprobacion_calibracion_nombre_no_vacio CHECK (length(btrim(aprobado_por_nombre)) > 0)
);