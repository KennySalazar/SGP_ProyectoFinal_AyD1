package gt.usac.cunoc.sgp.inspeccion.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record IniciarInspeccionResponse(
    UUID id,
    UUID puenteId,
    String codigoPuente,
    String nombrePuente,
    UUID versionFormularioId,
    String codigoVersionFormulario,
    UUID autorId,
    String rolAutor,
    String nombreAutor,
    String estado,
    LocalDate fechaInspeccion,
    String dispositivoId,
    Double latitudInicio,
    Double longitudInicio,
    BigDecimal precisionGpsM,
    BigDecimal distanciaAlPuenteM,
    boolean advertenciaGps,
    String mensajeAdvertenciaGps,
    boolean datosPermanentesConfirmados,
    DatosPermanentesResponse datosPermanentes,
    List<AcompananteResponse> acompanantes,
    Instant creadoEn) {}
