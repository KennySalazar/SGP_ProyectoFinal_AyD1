CREATE UNIQUE INDEX uq_resultado_ic_recalculo
    ON resultado_ic (inspeccion_id, version_configuracion_id, hash_entrada, version_algoritmo)
    WHERE tipo = 'RECALCULO';