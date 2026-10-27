package gt.usac.cunoc.sgp.foro.dto;

/** Alta de un hilo en un puente: título, categoría y primer comentario (RN-FOR-06). */
public record CrearHiloRequest(String titulo, String categoria, String comentario) {}
