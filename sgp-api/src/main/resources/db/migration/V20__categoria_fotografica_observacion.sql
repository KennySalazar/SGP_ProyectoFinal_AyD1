ALTER TABLE observacion_revision
    ADD COLUMN categoria_fotografica VARCHAR(30),
    ADD CONSTRAINT observacion_categoria_valida CHECK (
        categoria_fotografica IS NULL
        OR categoria_fotografica IN ('ACCESO','SUPERESTRUCTURA','SUBESTRUCTURA','CAUCE','OTRA')
    ),
    ADD CONSTRAINT observacion_categoria_sin_ancla CHECK (
        categoria_fotografica IS NULL
        OR (elemento_ref IS NULL AND instancia_elemento_id IS NULL)
    );

