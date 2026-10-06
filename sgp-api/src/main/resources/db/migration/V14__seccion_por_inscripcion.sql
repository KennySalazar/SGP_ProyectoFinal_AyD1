-- La seccion identifica la inscripcion del estudiante, no la oferta academica.
ALTER TABLE curso_estudiante ADD COLUMN seccion VARCHAR(30);

UPDATE curso_estudiante ce
SET seccion = c.seccion
FROM curso c
WHERE c.id = ce.curso_id;

ALTER TABLE curso_estudiante ALTER COLUMN seccion SET NOT NULL;

ALTER TABLE curso DROP CONSTRAINT uq_curso_oferta;
ALTER TABLE curso DROP COLUMN seccion;
ALTER TABLE curso
    ADD CONSTRAINT uq_curso_oferta UNIQUE (asignatura_id, periodo);
