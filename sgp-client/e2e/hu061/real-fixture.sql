-- Solo datos de prueba para CA4/CA7. Nunca ejecutar sobre una base persistente.
-- Esquema/IC mínimo reutilizado de PublicacionRealIcIntegrationTest.
\set ON_ERROR_STOP on
BEGIN;
DO $$ BEGIN
  IF current_database() <> 'hu061_aceptacion' THEN
    RAISE EXCEPTION 'Este fixture requiere la base desechable hu061_aceptacion';
  END IF;
END $$;

INSERT INTO usuario(id,email,password_hash,rol_id,verificado,activado,nombre_completo,nombre_usuario)
SELECT '0199a000-0000-7000-8000-000000000001', 'catedratico-hu061@example.test', u.password_hash,
       r.id,true,true,'Catedratico HU061','catedratico-hu061'
FROM usuario u CROSS JOIN rol r
WHERE u.email='admin-hu061@example.test' AND r.nombre='CATEDRATICO';
INSERT INTO usuario(id,email,password_hash,rol_id,verificado,activado,nombre_completo,nombre_usuario)
SELECT '0199a000-0000-7000-8000-000000000002', 'estudiante-hu061@example.test', u.password_hash,
       r.id,true,true,'Estudiante HU061','estudiante-hu061'
FROM usuario u CROSS JOIN rol r
WHERE u.email='admin-hu061@example.test' AND r.nombre='ESTUDIANTE';

INSERT INTO version_formulario(id,codigo,estado,activa,esquema,reglas_evaluacion_ic,
 hash_contenido,version_algoritmo_ic,creado_por_id,publicado_por_id,publicado_en)
VALUES ('0199a000-0000-7000-8000-000000000004','HU061-REAL','PUBLICADA',true,
 '{"type":"object","required":["vigas"],"properties":{"vigas":{"type":"object","title":"Vigas","required":["existencia","dano"],"properties":{"existencia":{"enum":["PRESENTE","AUSENTE","NO_APLICA"]},"dano":{"enum":["NO_PRESENTA","LEVE","MODERADO","SEVERO"]},"cortado":{"enum":["SI","NO"]}}}}}',
 '{"formulario":"sieca-v1","elementos":[{"elemento_ref":"/vigas","repetible":false,"portante":true,"indicadores":["dano"]}],"reglas_evaluacion_ic":{"version_algoritmo":"ic-integracion-prueba-v1","escala_severidad":{"NO_PRESENTA":0,"LEVE":1,"MODERADO":2,"SEVERO":4},"agregacion":{"indicadores_de_un_elemento":"MAXIMO","instancias_repetibles":"MAXIMO","elementos_ausentes_no_portantes":4,"elementos_ausentes_portantes":4},"rangos_estado":{"BUENO":75,"REGULAR":50,"MALO":0},"conversion_indicadores":{"booleanos":{},"enumerados":{},"numericos":{}},"reglas_anulacion":[{"id":"portante-cortado","descripcion":"Elemento portante cortado","estado_resultante":"MALO","elementos":["/vigas"],"condicion":{"campo":"cortado","igual_a":"SI"}}]}}',
 repeat('a',64),'ic-integracion-prueba-v1','0199a000-0000-7000-8000-000000000001',
 '0199a000-0000-7000-8000-000000000001',now());
INSERT INTO configuracion_ic_elemento(id,version_formulario_id,elemento_ref,peso,descripcion)
VALUES ('0199a000-0000-7000-8000-000000000090','0199a000-0000-7000-8000-000000000004','/vigas',1,'Peso exclusivo de prueba');

INSERT INTO puente(id,codigo,correlativo_municipal,municipio_id,nombre,ruta,ubicacion,creado_por_id)
SELECT '0199a000-0000-7000-8000-000000000003','HU061-REAL-1',1,id,'Puente real Catedratico','CA-1',
 ST_SetSRID(ST_MakePoint(-90.615,14.481),4326)::geography,'0199a000-0000-7000-8000-000000000001'
FROM municipio WHERE codigo_ine='0114';
INSERT INTO puente(id,codigo,correlativo_municipal,municipio_id,nombre,ruta,ubicacion,creado_por_id)
SELECT '0199a000-0000-7000-8000-000000000013','HU061-REAL-2',2,id,'Puente real Estudiante','CA-1',
 ST_SetSRID(ST_MakePoint(-90.615,14.481),4326)::geography,'0199a000-0000-7000-8000-000000000001'
FROM municipio WHERE codigo_ine='0114';
INSERT INTO asignatura(id,codigo,nombre) VALUES ('0199a000-0000-7000-8000-000000000091','HU061','Prueba aislada HU061');
INSERT INTO curso(id,asignatura_id,periodo,catedratico_id,fecha_inicio,fecha_fin)
VALUES ('0199a000-0000-7000-8000-000000000092','0199a000-0000-7000-8000-000000000091','HU061',
 '0199a000-0000-7000-8000-000000000001',current_date-30,current_date+30);
INSERT INTO curso_estudiante(id,curso_id,estudiante_id,inscrito_por_id,seccion)
VALUES ('0199a000-0000-7000-8000-000000000093','0199a000-0000-7000-8000-000000000092',
 '0199a000-0000-7000-8000-000000000002','0199a000-0000-7000-8000-000000000001','A');
INSERT INTO asignacion_puente(id,curso_estudiante_id,puente_id,asignado_por_id)
VALUES ('0199a000-0000-7000-8000-000000000094','0199a000-0000-7000-8000-000000000093',
 '0199a000-0000-7000-8000-000000000013','0199a000-0000-7000-8000-000000000001');
COMMIT;
