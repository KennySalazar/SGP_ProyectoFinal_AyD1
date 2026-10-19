package gt.usac.cunoc.sgp.sync.http;

import java.util.Map;

public record RespuestaIdempotente(int codigo, byte[] cuerpo, Map<String, String> encabezados) {}
