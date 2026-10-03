package gt.usac.cunoc.sgp.puente.dto;

import java.util.UUID;

public record MunicipioResponse(UUID id, UUID departamentoId, String codigoIne, String nombre) {}
