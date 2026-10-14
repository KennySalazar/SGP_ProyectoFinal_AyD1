# ADR 0003: Calculo del Indice de Condicion

## Estado

Implementado en la rama de HU-018, pendiente de integracion a develop.

## Contexto

HU-018 requiere un calculo objetivo, reproducible y trazable del Indice de
Condicion (IC). Los pesos, las equivalencias de severidad, las reglas de
agregacion y las condiciones criticas pertenecen a la version del formulario.

El esquema sieca-v1 sirve como referencia estructural. Los valores utilizados
en las pruebas son configuraciones de prueba y no representan una calibracion
aprobada por el Catedratico.

## Decision

Implementar el calculo como un servicio de dominio del modulo inspeccion,
sin acceso a repositorios, correo, servicios externos ni reloj.

CalculoIndiceCondicionService recibe EntradaCalculoIc y devuelve
ResultadoCalculoIc. La obtencion de la version, la autorizacion y la persistencia
del resultado corresponden al flujo que invoque este servicio.

HU-018 no agrega un endpoint ni modifica las migraciones existentes.

## Contrato de entrada

La entrada contiene:

- Identificador de la version del formulario.
- Codigo del formulario y version del algoritmo.
- Documento completo de datos de la inspeccion.
- Esquema de la version.
- Catalogo de elementos evaluables.
- Reglas de evaluacion de la version.
- Pesos asociados a las referencias de los elementos.

El consumidor debe entregar datos previamente validados contra el esquema
de su version. El servicio comprueba la estructura necesaria para el calculo,
pero no sustituye la validacion completa de JSON Schema.

El codigo del formulario debe coincidir con el catalogo y la version del
algoritmo debe coincidir con las reglas.

El consumidor debe mantener la entrada estable durante el calculo y conservar
los datos y la configuracion de la version para permitir su reproduccion.

## Formula y precision

La formula es:

IC = 100 * (1 - suma(calificacion * peso) / (4 * suma(peso)))

Las calificaciones deben estar entre 0 y 4 y cada elemento incluido debe
tener un peso positivo.

Se utiliza BigDecimal. El IC se redondea a cinco decimales con HALF_UP.
El estado por rango se determina sobre ese valor redondeado.

Para los rangos establecidos en HU-018:

- BUENO: IC mayor o igual a 75.
- REGULAR: IC mayor o igual a 50 y menor a 75.
- MALO: IC menor a 50.

El motor obtiene los umbrales de rangos_estado de la version del formulario.
La configuracion debe proporcionar umbrales coherentes entre 0 y 100.

## Elementos y agregacion

Los elementos NO_APLICA no participan en el numerador ni en la suma de pesos.
Una lista repetible vacia se excluye; una ruta requerida faltante se rechaza.

La agregacion de indicadores y de instancias repetibles se configura mediante:

- agregacion.indicadores_de_un_elemento
- agregacion.instancias_repetibles

Las reglas admitidas son MAXIMO y PROMEDIO. Cada elemento participa una vez
con su peso y con la calificacion agregada de sus instancias.

La calificacion de elementos AUSENTE se obtiene de:

- agregacion.elementos_ausentes_portantes
- agregacion.elementos_ausentes_no_portantes

Las exclusiones adicionales deben declararse expresamente mediante la lista
elementos_excluidos_ic. No se interpreta un peso faltante como una exclusion.

Los elementos de caracteristicas_cauce se excluyen cuando aplica es el
booleano false.

Si no queda ningun elemento evaluado e incluido, se rechaza el calculo.

## Conversion de indicadores

La version proporciona escala_severidad y conversion_indicadores para
convertir severidades, booleanos, enumerados y mediciones a calificaciones.

Las equivalencias booleanas distinguen su contexto: junta, losa, senal y
drenaje_aledano. La configuracion debe contemplar los valores evaluados.

Para mediciones, los umbrales LEVE, MODERADO y SEVERO son limites superiores
inclusivos, positivos y estrictamente crecientes:

- Cero corresponde a NO_PRESENTA.
- Mayor que cero y hasta LEVE corresponde a LEVE.
- Mayor que LEVE y hasta MODERADO corresponde a MODERADO.
- Mayor que MODERADO y hasta SEVERO corresponde a SEVERO.
- Una medicion superior al limite SEVERO se rechaza.

La calibracion debe cubrir el dominio permitido de cada medicion.
Las equivalencias y los umbrales incompletos se rechazan; no se asignan
valores de calibracion por defecto.

## Condiciones criticas

Las reglas de anulacion pertenecen a la version del formulario.
Una regla activada fuerza estadoCalculado a MALO sin modificar el IC numerico
ni estadoPorRango.

El resultado registra la regla, descripcion, referencia del elemento,
identificador de instancia cuando corresponde, ruta, indicador y valor
detectado.

Una regla pendiente de calibracion o incompleta se rechaza.
La lista reglas_anulacion es obligatoria y puede estar explicitamente vacia.

## Reproducibilidad y trazabilidad

El resultado incluye la version del formulario, la version del algoritmo,
el IC, los estados, las sumas utilizadas, el desglose por elemento e instancia
y las condiciones de anulacion.

HuellaEntradaIc genera una huella SHA-256 de la entrada. Ordena las claves
de objetos y normaliza la representacion numerica, conservando el orden
de las listas.

La misma entrada y configuracion producen el mismo resultado.
La huella permite identificar la entrada, pero no reemplaza su conservacion.

## Verificacion

Las pruebas cubren los criterios de HU-018, las validaciones y un ejemplo
compatible con el esquema real sieca-v1.

La ejecucion hu018-coverage-check de JaCoCo exige el 100 % de lineas,
ramas e instrucciones para los ocho servicios del calculo.

Desde sgp-api:

    mvn spotless:check verify

El workflow de CI ya ejecuta esta comprobacion mediante Maven.

## Consecuencias

El calculo puede utilizarse desde distintos flujos sin depender de HTTP
ni de la base de datos.

HU-019 debe proporcionar la configuracion calibrada correspondiente
a la version. El flujo de publicacion de HU-020 debe integrar el servicio,
conservar su trazabilidad y persistir el resultado.

El 100 % de cobertura demuestra que las pruebas recorren el codigo del
servicio; no garantiza por si solo la ausencia de errores ni reemplaza
la validacion de la calibracion.
