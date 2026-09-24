
INSERT INTO rol (id, nombre, descripcion)
VALUES
    ('0199a2b0-0001-7000-8000-000000000001', 'ADMINISTRADOR', 'Responsable tecnico del sistema'),
    ('0199a2b0-0002-7000-8000-000000000002', 'CATEDRATICO', 'Catedratico o revisor del curso'),
    ('0199a2b0-0003-7000-8000-000000000003', 'PROFESIONAL_EXTERNO', 'Profesional externo verificado'),
    ('0199a2b0-0004-7000-8000-000000000004', 'ESTUDIANTE', 'Estudiante pendiente de asignacion academica')
ON CONFLICT (nombre) DO NOTHING;
