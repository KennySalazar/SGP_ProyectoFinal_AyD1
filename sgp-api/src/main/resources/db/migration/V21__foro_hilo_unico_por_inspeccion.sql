-- Un único hilo por inspección publicada (HU-064, RN-FOR-03).
-- El evento de publicación y la creación perezosa de las inspecciones anteriores pueden coincidir;
-- el índice garantiza que nunca queden dos hilos para la misma inspección.
CREATE UNIQUE INDEX uq_foro_hilo_inspeccion
    ON foro_hilo (inspeccion_id)
    WHERE inspeccion_id IS NOT NULL;
