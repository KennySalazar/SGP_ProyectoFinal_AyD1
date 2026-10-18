package gt.usac.cunoc.sgp.archivo.model;

import java.util.UUID;

public record FotografiaRegistrada(
    UUID id,
    UUID inspeccionId,
    CategoriaFoto categoria,
    EstadoArchivo estado,
    String elementoRef,
    UUID instanciaElementoId) {}
