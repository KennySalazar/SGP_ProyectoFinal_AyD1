package gt.usac.cunoc.sgp.puente.dto;

public record SolicitudRevisionResponse(
    SolicitudAltaPuenteResponse solicitud, String solicitanteEmail, String revisadoPorEmail) {}
