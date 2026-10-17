package gt.usac.cunoc.sgp.inspeccion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InspeccionBorradorResponse(
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
    JsonNode identificacionCapturada,
    JsonNode datos,
    List<AcompananteResponse> acompanantes,
    long version,
    Instant ultimaActividadEn,
    Instant creadoEn,
    Instant actualizadoEn,
    ProgresoBorradorResponse progreso,
    IndiceCondicionPreliminarResponse icPreliminar) {}
