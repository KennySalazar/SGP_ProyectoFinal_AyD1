package gt.usac.cunoc.sgp.archivo.model;

import gt.usac.cunoc.sgp.sync.model.ReservaOperacion;

/** Si la reserva está completada, claves es null y se reproduce la respuesta sin transferir. */
public record CargaImagenPreparada(ReservaOperacion reserva, ClavesImagen claves) {}
