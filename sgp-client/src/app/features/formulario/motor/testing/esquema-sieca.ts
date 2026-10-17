import { EsquemaJson } from '../esquema/modelos';
import esquemaSieca from '../../../../../../../sgp-api/src/main/resources/formulario/sieca-v1/esquema.schema.json';

/**
 * Esquema `sieca-v1` real del backend: así las pruebas del motor detectan cualquier cambio del
 * formulario que el intérprete no soporte.
 */
export const ESQUEMA_SIECA: EsquemaJson = esquemaSieca as unknown as EsquemaJson;
