package gt.usac.cunoc.sgp.foro.dto;

import java.util.UUID;

/**
 * Cuántos comentarios tiene un campo o elemento de una inspección, incluidas las respuestas.
 * Alimenta el marcador junto al dato (RN-FOR-02).
 */
public record AnclaResponse(String elementoRef, UUID instanciaElementoId, long total) {}
