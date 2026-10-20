package gt.usac.cunoc.sgp.foro.dto;

import java.util.UUID;

/** Comentario nuevo; {@code padreId} lo convierte en respuesta a un comentario principal. */
public record CrearComentarioRequest(String contenido, UUID padreId) {}
