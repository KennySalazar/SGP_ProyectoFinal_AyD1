
-- registrar el usuario como nulo y guardar una marca que identifique al proceso como automatico 
ALTER TABLE auditoria ADD COLUMN proceso_automatico VARCHAR(100);
CREATE INDEX idx_auditoria_creado ON auditoria (creado_en DESC);
