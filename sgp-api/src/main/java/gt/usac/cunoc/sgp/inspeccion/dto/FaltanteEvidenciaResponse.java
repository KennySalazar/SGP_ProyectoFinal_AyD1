package gt.usac.cunoc.sgp.inspeccion.dto;

/** La categoría se publica como código JSON; el catálogo de dominio pertenece a HU-028. */
public record FaltanteEvidenciaResponse(String categoria, int requeridas, long disponibles) {}
