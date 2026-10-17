package gt.usac.cunoc.sgp.formulario.dto;

import gt.usac.cunoc.sgp.formulario.model.EstadoVersionFormulario;
import java.time.Instant;
import java.util.UUID;

public record VersionFormularioResumenResponse(
    UUID id,
    String codigo,
    EstadoVersionFormulario estado,
    boolean activa,
    String hashContenido,
    String versionAlgoritmoIc,
    UUID versionAnteriorId,
    String versionAnteriorCodigo,
    UUID creadoPorId,
    UUID publicadoPorId,
    Instant publicadoEn,
    UUID calibradoPorId,
    Instant calibradoEn,
    Instant creadoEn,
    Instant actualizadoEn,
    long version) {}
