package gt.usac.cunoc.sgp.puente.dto;

import java.util.List;

public record SolicitudRevisionDetalleResponse(
    SolicitudAltaPuenteResponse solicitud,
    String solicitanteEmail,
    List<PuenteCercanoResponse> puentesCercanos,
    long totalPuentesCercanos) {}
